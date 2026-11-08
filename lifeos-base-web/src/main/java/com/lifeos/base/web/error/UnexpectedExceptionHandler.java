package com.lifeos.base.web.error;

import com.lifeos.base.error.CommonErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Last resort: any exception no other advice handled becomes 500 {@code INTERNAL_ERROR} with a generic detail.
 * The real message and stack trace go only to the log (with the correlation ID from the MDC), never to the client.
 *
 * <p>Kept in its own advice with the lowest precedence: Spring picks the first advice (by order) that has
 * any matching handler, so a catch-all inside a higher-priority advice would hide every more specific one.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class UnexpectedExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(UnexpectedExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handle(Exception ex) {
        log.error("Unhandled exception", ex);
        ProblemDetail problem = Problems.of(CommonErrorCode.INTERNAL_ERROR, "Something went wrong on our side.");
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }
}
