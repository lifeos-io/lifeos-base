package com.lifeos.base.correlation;

import com.lifeos.base.id.UuidV7;
import java.util.regex.Pattern;

/**
 * Rules for correlation IDs, shared by the gateway filter, the servlet filter and the Kafka consumers.
 *
 * <p>A client-supplied value ends up in logs and in Kafka headers, so it is accepted only when it is short and
 * made of safe characters; anything else (empty, too long, spaces, CR/LF, quotes) is replaced by a fresh ID.
 * This prevents log forging and header injection through {@code X-Correlation-Id}.
 */
public final class CorrelationIds {

    /** Key under which the current correlation ID is stored in the logging MDC. */
    public static final String MDC_KEY = "correlationId";

    /** Longest accepted client-supplied value. A UUID is 36 characters. */
    public static final int MAX_LENGTH = 64;

    private static final Pattern ALLOWED = Pattern.compile("[A-Za-z0-9._:-]{1," + MAX_LENGTH + "}");

    private CorrelationIds() {
    }

    /** A new, time-ordered correlation ID. */
    public static String newId() {
        return UuidV7.next().toString();
    }

    /** Whether {@code candidate} may be used as a correlation ID as-is. */
    public static boolean isValid(String candidate) {
        return candidate != null && ALLOWED.matcher(candidate).matches();
    }

    /** {@code candidate} when it is valid, otherwise a new ID. Never returns {@code null}. */
    public static String orNew(String candidate) {
        return isValid(candidate) ? candidate : newId();
    }
}
