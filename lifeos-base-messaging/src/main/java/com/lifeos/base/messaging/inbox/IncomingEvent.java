package com.lifeos.base.messaging.inbox;

import com.lifeos.base.messaging.EventEnvelope;
import com.lifeos.base.messaging.EventEnvelopeCodec;
import com.lifeos.base.messaging.MalformedEventException;

/** An event handed to a handler by {@link Inbox}: the envelope plus typed access to its payload. */
public final class IncomingEvent {

    private final EventEnvelope envelope;
    private final EventEnvelopeCodec codec;

    IncomingEvent(EventEnvelope envelope, EventEnvelopeCodec codec) {
        this.envelope = envelope;
        this.codec = codec;
    }

    public EventEnvelope envelope() {
        return envelope;
    }

    public String eventType() {
        return envelope.eventType();
    }

    public int eventVersion() {
        return envelope.eventVersion();
    }

    public java.util.UUID userId() {
        return envelope.userId();
    }

    /** @throws MalformedEventException when the payload does not fit {@code type} */
    public <T> T payloadAs(Class<T> type) {
        return codec.payloadAs(envelope, type);
    }

    public boolean is(String eventType, int eventVersion) {
        return envelope.eventType().equals(eventType) && envelope.eventVersion() == eventVersion;
    }
}
