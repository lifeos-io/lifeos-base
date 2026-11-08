package com.lifeos.base.error;

import java.util.Map;
import java.util.Objects;

/**
 * An expected failure with a stable {@link ErrorCode}. The web module maps it to a problem response;
 * anything else that escapes a request is reported as {@link CommonErrorCode#INTERNAL_ERROR}.
 *
 * <p>The message becomes the problem's {@code detail} and is shown to the client, so it must be written for
 * the user and must not contain internals (SQL, stack details, other users' data). Put diagnostic context in
 * the cause or the logs instead. {@link #details()} are extra, client-safe members of the problem
 * (e.g. {@code "limitMinutes": 240}).
 */
public class LifeOsException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> details;

    public LifeOsException(ErrorCode errorCode, String detail) {
        this(errorCode, detail, Map.of(), null);
    }

    public LifeOsException(ErrorCode errorCode, String detail, Map<String, ?> details) {
        this(errorCode, detail, details, null);
    }

    public LifeOsException(ErrorCode errorCode, String detail, Map<String, ?> details, Throwable cause) {
        super(Objects.requireNonNull(detail, "detail"), cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
        this.details = Map.copyOf(Objects.requireNonNull(details, "details"));
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    /** Client-safe extra members of the problem response; immutable, never {@code null}. */
    public Map<String, Object> details() {
        return details;
    }
}
