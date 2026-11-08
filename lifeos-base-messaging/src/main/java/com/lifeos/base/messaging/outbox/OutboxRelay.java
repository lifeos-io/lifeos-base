package com.lifeos.base.messaging.outbox;

import com.lifeos.base.http.LifeOsHeaders;
import com.lifeos.base.messaging.EventEnvelope;
import com.lifeos.base.messaging.EventEnvelopeCodec;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publishes committed outbox rows to Kafka, oldest first, and marks them published.
 *
 * <p><b>Ordering.</b> One user's events must reach Kafka in the order they were written. Two service replicas
 * relaying different batches at the same time could break that, so each run first takes a transaction-scoped
 * PostgreSQL advisory lock; a replica that cannot get it skips the run. Within a run, the first failed send
 * stops the batch, so a later event never overtakes an earlier one.
 *
 * <p><b>Delivery.</b> At least once: a crash after Kafka acknowledged but before the commit republishes the row.
 * Consumers de-duplicate on {@code eventId} (see the inbox).
 */
public class OutboxRelay {

    /** Advisory lock key shared by every replica of a service (each service has its own database). */
    public static final long LOCK_KEY = 0x4C4F_4F42_5245_4C41L; // "LOOBRELA"

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int MAX_ERROR_LENGTH = 2000;

    private final JdbcClient jdbc;
    private final TransactionTemplate transactions;
    private final KafkaTemplate<String, String> kafka;
    private final EventEnvelopeCodec codec;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private final String topic;
    private final int batchSize;
    private final Duration sendTimeout;

    public OutboxRelay(JdbcClient jdbc, TransactionTemplate transactions, KafkaTemplate<String, String> kafka,
                       EventEnvelopeCodec codec, JsonMapper jsonMapper, Clock clock, String topic, int batchSize,
                       Duration sendTimeout) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.kafka = kafka;
        this.codec = codec;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
        this.topic = topic;
        this.batchSize = batchSize;
        this.sendTimeout = sendTimeout;
    }

    /**
     * Publishes one batch.
     *
     * @return the number of rows published, or {@code -1} when another replica holds the relay lock
     * @throws OutboxPublishException when a send failed; rows before it are published and committed
     */
    public int relayOnce() {
        RunResult result = transactions.execute(status -> {
            Boolean locked = jdbc.sql("SELECT pg_try_advisory_xact_lock(:key)")
                    .param("key", LOCK_KEY)
                    .query(Boolean.class)
                    .single();
            if (!Boolean.TRUE.equals(locked)) {
                return new RunResult(-1, null);
            }
            List<PendingEvent> batch = jdbc.sql("""
                            SELECT seq, event_id, event_type, event_version, user_id, correlation_id,
                                   payload::text AS payload, occurred_at
                            FROM outbox_event
                            WHERE published_at IS NULL
                            ORDER BY seq
                            LIMIT :limit
                            FOR UPDATE SKIP LOCKED
                            """)
                    .param("limit", batchSize)
                    .query(OutboxRelay::pendingEvent)
                    .list();
            int published = 0;
            for (PendingEvent event : batch) {
                try {
                    send(event);
                } catch (Exception e) {
                    recordFailure(event, e);
                    return new RunResult(published, e);
                }
                jdbc.sql("UPDATE outbox_event SET published_at = :now WHERE seq = :seq")
                        .param("now", Timestamp.from(clock.instant()))
                        .param("seq", event.seq())
                        .update();
                published++;
            }
            return new RunResult(published, null);
        });
        if (result.failure() != null) {
            throw new OutboxPublishException(result.published(), result.failure());
        }
        return result.published();
    }

    /** Deletes published rows older than {@code retention}. Unpublished rows are never deleted. */
    public int purgePublishedBefore(Duration retention) {
        return jdbc.sql("DELETE FROM outbox_event WHERE published_at < :cutoff")
                .param("cutoff", Timestamp.from(clock.instant().minus(retention)))
                .update();
    }

    private void send(PendingEvent event) throws Exception {
        EventEnvelope envelope = new EventEnvelope(event.eventId(), event.eventType(), event.eventVersion(),
                event.occurredAt().toInstant(), event.userId(), event.correlationId(),
                jsonMapper.readTree(event.payload()));
        ProducerRecord<String, String> record =
                new ProducerRecord<>(topic, event.userId().toString(), codec.toJson(envelope));
        header(record, "eventId", event.eventId().toString());
        header(record, "eventType", event.eventType());
        header(record, "eventVersion", Integer.toString(event.eventVersion()));
        if (event.correlationId() != null) {
            header(record, LifeOsHeaders.CORRELATION_ID, event.correlationId());
        }
        kafka.send(record).get(sendTimeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void recordFailure(PendingEvent event, Exception e) {
        String message = String.valueOf(e.getMessage());
        log.warn("Publishing outbox event {} ({}) failed; it will be retried", event.eventId(), event.eventType(), e);
        jdbc.sql("UPDATE outbox_event SET attempts = attempts + 1, last_error = :error WHERE seq = :seq")
                .param("error", message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message)
                .param("seq", event.seq())
                .update();
    }

    private static void header(ProducerRecord<String, String> record, String name, String value) {
        record.headers().add(name, value.getBytes(StandardCharsets.UTF_8));
    }

    private static PendingEvent pendingEvent(ResultSet rs, int row) throws SQLException {
        return new PendingEvent(
                rs.getLong("seq"),
                rs.getObject("event_id", UUID.class),
                rs.getString("event_type"),
                rs.getInt("event_version"),
                rs.getObject("user_id", UUID.class),
                rs.getString("correlation_id"),
                rs.getString("payload"),
                rs.getTimestamp("occurred_at"));
    }

    private record PendingEvent(long seq, UUID eventId, String eventType, int eventVersion, UUID userId,
                                String correlationId, String payload, Timestamp occurredAt) {
    }

    private record RunResult(int published, Exception failure) {
    }
}
