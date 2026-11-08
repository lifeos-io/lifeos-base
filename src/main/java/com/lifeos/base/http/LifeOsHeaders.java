package com.lifeos.base.http;

/**
 * Header contract between the gateway and the services.
 * {@link #USER_ID} is written only by the gateway, from the JWT {@code sub} claim;
 * any value sent by the client is stripped at the gateway.
 */
public final class LifeOsHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String CORRELATION_ID = "X-Correlation-Id";

    private LifeOsHeaders() {
    }
}
