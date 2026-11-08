package com.lifeos.base.web.correlation;

import com.lifeos.base.correlation.CorrelationIds;
import com.lifeos.base.http.LifeOsHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Establishes the correlation ID of a request before anything else runs (security included), so every log
 * line and every error response of the request carries it.
 *
 * <p>The incoming {@code X-Correlation-Id} is kept when valid, otherwise replaced (see {@link CorrelationIds}).
 * The value is put in the MDC under {@link CorrelationIds#MDC_KEY}, echoed in the response header and removed
 * from the MDC when the request ends, so pooled or virtual threads never leak it into the next request.
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = CorrelationIds.orNew(request.getHeader(LifeOsHeaders.CORRELATION_ID));
        String previous = MDC.get(CorrelationIds.MDC_KEY);
        MDC.put(CorrelationIds.MDC_KEY, correlationId);
        response.setHeader(LifeOsHeaders.CORRELATION_ID, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            if (previous == null) {
                MDC.remove(CorrelationIds.MDC_KEY);
            } else {
                MDC.put(CorrelationIds.MDC_KEY, previous);
            }
        }
    }

    /** The current request's correlation ID, or {@code null} outside a request. */
    public static String current() {
        return MDC.get(CorrelationIds.MDC_KEY);
    }
}
