package com.lifeos.base.messaging.outbox;

/** A relay run stopped at a failed send. Events before it were published and committed. */
public class OutboxPublishException extends RuntimeException {

    private final int publishedBeforeFailure;

    public OutboxPublishException(int publishedBeforeFailure, Throwable cause) {
        super("Outbox relay stopped after " + publishedBeforeFailure + " event(s): " + cause.getMessage(), cause);
        this.publishedBeforeFailure = publishedBeforeFailure;
    }

    public int publishedBeforeFailure() {
        return publishedBeforeFailure;
    }
}
