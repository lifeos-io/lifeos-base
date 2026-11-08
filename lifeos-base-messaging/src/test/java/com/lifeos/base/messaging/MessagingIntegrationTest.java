package com.lifeos.base.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifeos.base.correlation.CorrelationIds;
import com.lifeos.base.messaging.inbox.Inbox;
import com.lifeos.base.messaging.outbox.Outbox;
import com.lifeos.base.messaging.outbox.OutboxMessage;
import com.lifeos.base.messaging.outbox.OutboxRelay;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Outbox, relay and inbox against real PostgreSQL and Kafka (Testcontainers). */
@Testcontainers
@SpringBootTest(classes = MessagingIntegrationTest.TestApp.class, properties = {
        "spring.application.name=test-service",
        "lifeos.messaging.outbox.topic=" + MessagingIntegrationTest.TOPIC,
        "lifeos.messaging.outbox.relay-enabled=false"
})
class MessagingIntegrationTest {

    static final String TOPIC = "lifeos.test.events.v1";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.9.1");

    @Autowired
    Outbox outbox;
    @Autowired
    OutboxRelay relay;
    @Autowired
    Inbox inbox;
    @Autowired
    EventEnvelopeCodec codec;
    @Autowired
    JsonMapper jsonMapper;
    @Autowired
    DataSource dataSource;
    @Autowired
    PlatformTransactionManager transactionManager;

    JdbcClient jdbc;
    TransactionTemplate tx;

    record GoalChangedV1(UUID goalId, String title, int n) {
    }

    @BeforeEach
    void setUp() {
        jdbc = JdbcClient.create(dataSource);
        tx = new TransactionTemplate(transactionManager);
        jdbc.sql("TRUNCATE outbox_event, inbox_message, goal").update();
    }

    @Test
    void libraryTablesLiveNextToTheServiceMigrationsWithTheirOwnHistory() {
        assertEquals(1, count("SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success"));
        assertEquals(1, count("SELECT count(*) FROM " + MessagingSchemaMigration.HISTORY_TABLE
                + " WHERE version = '1' AND success"));
        assertEquals(0, count("SELECT count(*) FROM goal"));
        assertEquals(0, count("SELECT count(*) FROM outbox_event"));
    }

    @Test
    void appendOutsideATransactionIsRejected() {
        assertThrows(IllegalStateException.class, () -> outbox.append(message(UUID.randomUUID(), 1)));
    }

    @Test
    void rolledBackBusinessChangeLeavesNoEvent() {
        UUID goalId = UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            jdbc.sql("INSERT INTO goal (id, title) VALUES (:id, 'x')").param("id", goalId).update();
            outbox.append(message(UUID.randomUUID(), 1));
            status.setRollbackOnly();
        });
        assertEquals(0, count("SELECT count(*) FROM goal"));
        assertEquals(0, count("SELECT count(*) FROM outbox_event"));
    }

    @Test
    void committedEventsArePublishedInOrderAsEnvelopes() {
        UUID user = UUID.randomUUID();
        MDC.put(CorrelationIds.MDC_KEY, "corr-42");
        try {
            tx.executeWithoutResult(status -> {
                for (int n = 1; n <= 3; n++) {
                    outbox.append(message(user, n));
                }
            });
        } finally {
            MDC.remove(CorrelationIds.MDC_KEY);
        }

        assertEquals(3, relay.relayOnce());
        assertEquals(0, relay.relayOnce());
        assertEquals(0, count("SELECT count(*) FROM outbox_event WHERE published_at IS NULL"));

        List<ConsumerRecord<String, String>> records = consume(user, 3);
        for (int i = 0; i < 3; i++) {
            ConsumerRecord<String, String> record = records.get(i);
            assertEquals(user.toString(), record.key());
            EventEnvelope envelope = codec.fromJson(record.value());
            assertEquals("GoalChanged", envelope.eventType());
            assertEquals(1, envelope.eventVersion());
            assertEquals(user, envelope.userId());
            assertEquals("corr-42", envelope.correlationId());
            assertEquals(i + 1, codec.payloadAs(envelope, GoalChangedV1.class).n());
            assertEquals(envelope.eventId().toString(), header(record, "eventId"));
            assertEquals("corr-42", header(record, "X-Correlation-Id"));
        }
    }

    @Test
    void relaySkipsTheRunWhileAnotherReplicaHoldsTheLock() throws Exception {
        tx.executeWithoutResult(status -> outbox.append(message(UUID.randomUUID(), 1)));
        try (Connection other = dataSource.getConnection(); Statement st = other.createStatement()) {
            st.execute("SELECT pg_advisory_lock(" + OutboxRelay.LOCK_KEY + ")");
            assertEquals(-1, relay.relayOnce());
            assertEquals(1, count("SELECT count(*) FROM outbox_event WHERE published_at IS NULL"));
            st.execute("SELECT pg_advisory_unlock(" + OutboxRelay.LOCK_KEY + ")");
        }
        assertEquals(1, relay.relayOnce());
    }

    @Test
    void inboxRunsTheHandlerOncePerEventAndConsumer() {
        String json = envelopeJson(UUID.randomUUID(), "corr-in");
        AtomicInteger calls = new AtomicInteger();

        assertTrue(inbox.handle("coach", json, event -> calls.incrementAndGet()));
        assertFalse(inbox.handle("coach", json, event -> calls.incrementAndGet()));
        assertTrue(inbox.handle("trigger", json, event -> calls.incrementAndGet()));

        assertEquals(2, calls.get());
        assertEquals(2, count("SELECT count(*) FROM inbox_message"));
    }

    @Test
    void failedHandlerRollsBackItsWritesAndTheInboxRow() {
        String json = envelopeJson(UUID.randomUUID(), null);
        UUID goalId = UUID.randomUUID();

        assertThrows(IllegalStateException.class, () -> inbox.handle("coach", json, event -> {
            jdbc.sql("INSERT INTO goal (id, title) VALUES (:id, 'x')").param("id", goalId).update();
            throw new IllegalStateException("handler failed");
        }));
        assertEquals(0, count("SELECT count(*) FROM goal"));
        assertEquals(0, count("SELECT count(*) FROM inbox_message"));

        assertTrue(inbox.handle("coach", json, event -> { }));
    }

    @Test
    void handlerSeesTheEventsCorrelationIdAndTypedPayload() {
        AtomicReference<String> seenCorrelation = new AtomicReference<>();
        AtomicReference<GoalChangedV1> seenPayload = new AtomicReference<>();

        inbox.handle("coach", envelopeJson(UUID.randomUUID(), "corr-mdc"), event -> {
            seenCorrelation.set(MDC.get(CorrelationIds.MDC_KEY));
            seenPayload.set(event.payloadAs(GoalChangedV1.class));
        });

        assertEquals("corr-mdc", seenCorrelation.get());
        assertEquals("Ship it", seenPayload.get().title());
        assertNull(MDC.get(CorrelationIds.MDC_KEY));
    }

    @Test
    void recordValueThatIsNotAnEnvelopeIsMalformed() {
        assertThrows(MalformedEventException.class, () -> inbox.handle("coach", "{\"hello\":1}", event -> { }));
        assertThrows(MalformedEventException.class, () -> inbox.handle("coach", "not json", event -> { }));
    }

    @Test
    void purgeRemovesOnlyOldPublishedRows() {
        tx.executeWithoutResult(status -> {
            outbox.append(message(UUID.randomUUID(), 1));
            outbox.append(message(UUID.randomUUID(), 2));
        });
        jdbc.sql("UPDATE outbox_event SET published_at = now() - interval '8 days' WHERE seq = "
                + "(SELECT min(seq) FROM outbox_event)").update();

        assertEquals(1, relay.purgePublishedBefore(Duration.ofDays(7)));
        assertEquals(1, count("SELECT count(*) FROM outbox_event WHERE published_at IS NULL"));
    }

    // --- helpers -------------------------------------------------------------------------------------------

    private OutboxMessage message(UUID user, int n) {
        UUID goalId = UUID.randomUUID();
        return new OutboxMessage("Goal", goalId, user, "GoalChanged", 1, new GoalChangedV1(goalId, "Goal " + n, n));
    }

    private String envelopeJson(UUID eventId, String correlationId) {
        UUID goalId = UUID.randomUUID();
        JsonNode payload = jsonMapper.valueToTree(new GoalChangedV1(goalId, "Ship it", 1));
        return codec.toJson(new EventEnvelope(eventId, "GoalChanged", 1, Instant.now(), UUID.randomUUID(),
                correlationId, payload));
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        return new String(record.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
    }

    private static List<ConsumerRecord<String, String>> consume(UUID user, int expected) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<ConsumerRecord<String, String>> received = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(config)) {
            consumer.subscribe(List.of(TOPIC));
            long deadline = System.currentTimeMillis() + 30_000;
            while (received.size() < expected && System.currentTimeMillis() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    if (user.toString().equals(record.key())) {
                        received.add(record);
                    }
                });
            }
        }
        assertEquals(expected, received.size(), "records received for user " + user);
        assertNotNull(received.getFirst().value());
        return received;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {
    }
}
