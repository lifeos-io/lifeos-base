package com.lifeos.base.messaging;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code lifeos.messaging.*}. Kafka connection settings come from Spring Boot's {@code spring.kafka.*}.
 *
 * <pre>
 * lifeos.messaging.outbox.topic: lifeos.strategy.events.v1
 * lifeos.messaging.inbox.consumer: coach        # defaults to spring.application.name
 * </pre>
 */
@ConfigurationProperties("lifeos.messaging")
public record LifeOsMessagingProperties(
        @DefaultValue Outbox outbox,
        @DefaultValue Inbox inbox,
        @DefaultValue Schema schema) {

    /**
     * @param enabled       false for a service that only consumes (e.g. Bildirim)
     * @param topic         the service's event topic; required when enabled
     * @param relayEnabled  run the background relay; turn off to call {@code OutboxRelay.relayOnce()} yourself
     * @param pollInterval  pause between relay runs
     * @param failureBackoff pause after a failed send (e.g. Kafka down) before trying again
     * @param batchSize     rows per relay run
     * @param sendTimeout   wait for the Kafka acknowledgement of one event
     * @param retention     how long published rows are kept before they are deleted
     */
    public record Outbox(
            @DefaultValue("true") boolean enabled,
            String topic,
            @DefaultValue("true") boolean relayEnabled,
            @DefaultValue("500ms") Duration pollInterval,
            @DefaultValue("5s") Duration failureBackoff,
            @DefaultValue("100") int batchSize,
            @DefaultValue("10s") Duration sendTimeout,
            @DefaultValue("7d") Duration retention) {
    }

    /**
     * @param consumer  default consumer name for {@code Inbox.handle(record, handler)}
     * @param retention how long processed event IDs are remembered; keep it above the Kafka topic retention
     */
    public record Inbox(
            String consumer,
            @DefaultValue("30d") Duration retention) {
    }

    /** @param migrate create/upgrade the outbox and inbox tables on startup (own Flyway history table) */
    public record Schema(@DefaultValue("true") boolean migrate) {
    }
}
