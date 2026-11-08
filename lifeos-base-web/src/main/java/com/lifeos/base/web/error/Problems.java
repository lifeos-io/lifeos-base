package com.lifeos.base.web.error;

import com.lifeos.base.error.ErrorCode;
import com.lifeos.base.web.correlation.CorrelationIdFilter;
import java.net.URI;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;

/**
 * Builds the RFC 9457 problem body every Life OS service returns.
 *
 * <pre>
 * {
 *   "type": "urn:lifeos:error:NOT_FOUND",
 *   "title": "Resource not found",
 *   "status": 404,
 *   "detail": "Goal not found",
 *   "instance": "/api/strategy/goals/0197...",
 *   "code": "NOT_FOUND",
 *   "correlationId": "0197..."
 * }
 * </pre>
 *
 * Clients branch on {@code code}. Extra members come from {@code LifeOsException#details()}; they may not
 * reuse a standard or Life OS member name.
 */
public final class Problems {

    public static final String TYPE_PREFIX = "urn:lifeos:error:";
    public static final String CODE = "code";
    public static final String CORRELATION_ID = "correlationId";

    private static final Set<String> RESERVED =
            Set.of("type", "title", "status", "detail", "instance", CODE, CORRELATION_ID);

    private static final Logger log = LoggerFactory.getLogger(Problems.class);

    private Problems() {
    }

    public static ProblemDetail of(ErrorCode code, String detail) {
        return of(code, detail, Map.of());
    }

    public static ProblemDetail of(ErrorCode code, String detail, Map<String, ?> details) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                org.springframework.http.HttpStatusCode.valueOf(code.status()), detail);
        problem.setType(URI.create(TYPE_PREFIX + code.code()));
        problem.setTitle(code.title());
        problem.setProperty(CODE, code.code());
        details.forEach((key, value) -> {
            if (RESERVED.contains(key)) {
                log.warn("Ignoring problem detail '{}' for {}: it is a reserved member name", key, code.code());
            } else {
                problem.setProperty(key, value);
            }
        });
        return withCorrelationId(problem);
    }

    /**
     * Adds {@code code}, {@code type} and {@code correlationId} to a problem Spring MVC built for one of its own
     * exceptions, keeping Spring's title and detail. A problem that already has a {@code code} is left as is.
     */
    public static ProblemDetail decorate(ProblemDetail problem, ErrorCode code) {
        Map<String, Object> properties = problem.getProperties();
        if (properties == null || !properties.containsKey(CODE)) {
            problem.setType(URI.create(TYPE_PREFIX + code.code()));
            problem.setProperty(CODE, code.code());
        }
        return withCorrelationId(problem);
    }

    private static ProblemDetail withCorrelationId(ProblemDetail problem) {
        String correlationId = CorrelationIdFilter.current();
        if (correlationId != null) {
            problem.setProperty(CORRELATION_ID, correlationId);
        }
        return problem;
    }
}
