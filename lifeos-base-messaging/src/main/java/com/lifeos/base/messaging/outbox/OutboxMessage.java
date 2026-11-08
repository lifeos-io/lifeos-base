package com.lifeos.base.messaging.outbox;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * An event to publish, as the service describes it. The outbox adds the envelope fields
 * (event ID, time, correlation ID).
 *
 * @param aggregateType the kind of thing that changed, e.g. {@code Goal}
 * @param aggregateId   its ID
 * @param userId        whose data it is; becomes the Kafka key, so one user's events stay in order
 * @param eventType     PascalCase event name from the catalog, e.g. {@code GoalChanged}
 * @param eventVersion  payload schema version (>= 1)
 * @param payload       serialized with the application's JsonMapper; must match the event's JSON Schema
 */
public record OutboxMessage(
        String aggregateType,
        UUID aggregateId,
        UUID userId,
        String eventType,
        int eventVersion,
        Object payload) {

    private static final Pattern EVENT_TYPE = Pattern.compile("[A-Z][A-Za-z0-9]*");

    public OutboxMessage {
        Objects.requireNonNull(aggregateType, "aggregateType");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(payload, "payload");
        if (!EVENT_TYPE.matcher(eventType).matches()) {
            throw new IllegalArgumentException("eventType must be PascalCase, got '" + eventType + "'");
        }
        if (eventVersion < 1) {
            throw new IllegalArgumentException("eventVersion must be >= 1");
        }
    }
}
