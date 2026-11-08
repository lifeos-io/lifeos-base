package com.lifeos.base.error;

/** Error codes shared by every service and the gateway. Service-specific codes live in each service. */
public enum CommonErrorCode implements ErrorCode {

    VALIDATION_FAILED(400, "Request validation failed"),
    MALFORMED_REQUEST(400, "Malformed request"),
    UNAUTHENTICATED(401, "Authentication required"),
    FORBIDDEN(403, "Access denied"),
    NOT_FOUND(404, "Resource not found"),
    CONFLICT(409, "Request conflicts with the current state"),
    /** Optimistic lock failure: the resource changed since the client read it. */
    CONCURRENT_MODIFICATION(409, "Resource was modified concurrently"),
    RATE_LIMITED(429, "Too many requests"),
    INTERNAL_ERROR(500, "Internal error"),
    /** A dependency (another service, the LLM provider, the database) is unavailable. */
    UPSTREAM_UNAVAILABLE(503, "Upstream service unavailable");

    private final int status;
    private final String title;

    CommonErrorCode(int status, String title) {
        this.status = status;
        this.title = title;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public int status() {
        return status;
    }

    @Override
    public String title() {
        return title;
    }
}
