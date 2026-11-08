package com.lifeos.base.messaging;

/**
 * An event that can never be processed as-is (not an envelope, or a payload that does not match its type).
 * Retrying does not help: exclude it from {@code @RetryableTopic} so it goes straight to the dead-letter topic.
 */
public class MalformedEventException extends RuntimeException {

    public MalformedEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
