package com.lifeos.base.web.error;

import com.lifeos.base.error.CommonErrorCode;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * A {@code @Version} conflict (e.g. two devices editing the same goal) becomes 409
 * {@code CONCURRENT_MODIFICATION}, so the client can reload and retry instead of seeing a 500.
 * Registered only when spring-tx is on the classpath.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE - 20)
public class OptimisticLockExceptionHandler {

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handle(OptimisticLockingFailureException ex) {
        ProblemDetail problem = Problems.of(CommonErrorCode.CONCURRENT_MODIFICATION,
                "This item was changed in the meantime. Reload it and try again.");
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }
}
