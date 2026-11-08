package com.lifeos.base.messaging;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * The wire format of every Life OS event (schema: {@code lifeos-contracts/schemas/envelope.schema.json}).
 * It is the Kafka record value as JSON; the Kafka key is {@link #userId()}.
 *
 * @param eventId       UUID v7, unique per event; consumers de-duplicate on it
 * @param eventType     e.g. {@code GoalChanged}
 * @param eventVersion  payload schema version; a breaking change is a new version
 * @param occurredAt    when the business change happened (UTC)
 * @param userId        Keycloak {@code sub} of the user the event is about
 * @param correlationId request that caused the event, if any
 * @param payload       event-specific body, validated against {@code <EventType>.v<N>.schema.json}
 */
public record EventEnvelope(
        UUID eventId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        UUID userId,
        String correlationId,
        JsonNode payload) {

    public EventEnvelope {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(payload, "payload");
        if (eventVersion < 1) {
            throw new IllegalArgumentException("eventVersion must be >= 1");
        }
    }
}
