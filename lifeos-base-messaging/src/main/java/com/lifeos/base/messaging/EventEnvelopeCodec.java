package com.lifeos.base.messaging;

import java.time.Instant;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Converts {@link EventEnvelope} to and from its JSON form. */
public class EventEnvelopeCodec {

    private final JsonMapper jsonMapper;

    public EventEnvelopeCodec(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public String toJson(EventEnvelope envelope) {
        ObjectNode node = jsonMapper.createObjectNode();
        node.put("eventId", envelope.eventId().toString());
        node.put("eventType", envelope.eventType());
        node.put("eventVersion", envelope.eventVersion());
        node.put("occurredAt", envelope.occurredAt().toString());
        node.put("userId", envelope.userId().toString());
        if (envelope.correlationId() != null) {
            node.put("correlationId", envelope.correlationId());
        }
        node.set("payload", envelope.payload());
        return jsonMapper.writeValueAsString(node);
    }

    /** @throws MalformedEventException when the JSON is not a valid envelope; retrying will not help */
    public EventEnvelope fromJson(String json) {
        try {
            JsonNode node = jsonMapper.readTree(json);
            JsonNode correlationId = node.get("correlationId");
            return new EventEnvelope(
                    UUID.fromString(required(node, "eventId").asString()),
                    required(node, "eventType").asString(),
                    required(node, "eventVersion").asInt(),
                    Instant.parse(required(node, "occurredAt").asString()),
                    UUID.fromString(required(node, "userId").asString()),
                    correlationId == null || correlationId.isNull() ? null : correlationId.asString(),
                    required(node, "payload"));
        } catch (JacksonException | IllegalArgumentException | java.time.DateTimeException e) {
            throw new MalformedEventException("Not a valid Life OS event envelope: " + e.getMessage(), e);
        }
    }

    public JsonNode payloadToTree(Object payload) {
        return jsonMapper.valueToTree(payload);
    }

    public <T> T payloadAs(EventEnvelope envelope, Class<T> type) {
        try {
            return jsonMapper.treeToValue(envelope.payload(), type);
        } catch (JacksonException e) {
            throw new MalformedEventException(
                    "Payload of " + envelope.eventType() + " v" + envelope.eventVersion() + " does not match "
                            + type.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    private static JsonNode required(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("missing " + field);
        }
        return value;
    }
}
