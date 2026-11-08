package com.lifeos.base.messaging.outbox;

import com.lifeos.base.correlation.CorrelationIds;
import com.lifeos.base.id.UuidV7;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

/**
 * Records events in {@code outbox_event} inside the caller's transaction:
 *
 * <pre>{@code
 * @Transactional
 * public Goal rename(UUID userId, UUID goalId, String title) {
 *     Goal goal = goals.rename(goalId, title);
 *     outbox.append(new OutboxMessage("Goal", goalId, userId, "GoalChanged", 1, GoalChangedV1.of(goal)));
 *     return goal;
 * }
 * }</pre>
 *
 * Either both the change and the event are committed or neither is; the relay publishes committed rows to
 * Kafka. Works with JPA too: JDBC joins the JPA transaction on the same DataSource.
 */
public class Outbox {

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public Outbox(JdbcClient jdbc, JsonMapper jsonMapper, Clock clock) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    /**
     * @return the new event's ID
     * @throws IllegalStateException when called outside a transaction: an event without its business change
     *                               (or the other way round) is exactly what the outbox exists to prevent
     */
    public UUID append(OutboxMessage message) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "Outbox.append must run inside the transaction that makes the business change");
        }
        UUID eventId = UuidV7.next();
        jdbc.sql("""
                        INSERT INTO outbox_event (event_id, aggregate_type, aggregate_id, event_type, event_version,
                                                  user_id, correlation_id, payload, occurred_at)
                        VALUES (:eventId, :aggregateType, :aggregateId, :eventType, :eventVersion,
                                :userId, :correlationId, CAST(:payload AS jsonb), :occurredAt)
                        """)
                .param("eventId", eventId)
                .param("aggregateType", message.aggregateType())
                .param("aggregateId", message.aggregateId())
                .param("eventType", message.eventType())
                .param("eventVersion", message.eventVersion())
                .param("userId", message.userId())
                .param("correlationId", MDC.get(CorrelationIds.MDC_KEY))
                .param("payload", jsonMapper.writeValueAsString(message.payload()))
                .param("occurredAt", Timestamp.from(clock.instant()))
                .update();
        return eventId;
    }
}
