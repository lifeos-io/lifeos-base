package com.lifeos.base.http;

/**
 * Header names shared by the gateway, the services and Kafka record headers.
 *
 * <p>{@link #USER_ID} is written only by the gateway, from the JWT {@code sub} claim; any value sent by the
 * client is stripped there. Services never use it for authorization: they validate the JWT themselves and
 * take the user from its {@code sub}. The header is informational (logs, tracing).
 *
 * <p>{@link #CORRELATION_ID} is accepted from the client when valid (see
 * {@link com.lifeos.base.correlation.CorrelationIds}), generated otherwise, and carried through outbox
 * events and Kafka record headers so one request can be followed across services.
 */
public final class LifeOsHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String CORRELATION_ID = "X-Correlation-Id";

    private LifeOsHeaders() {
    }
}
