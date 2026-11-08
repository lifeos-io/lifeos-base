package com.lifeos.base.web.error;

import com.lifeos.base.error.CommonErrorCode;
import com.lifeos.base.error.ErrorCode;
import com.lifeos.base.error.LifeOsException;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns expected failures into problem responses with a stable {@code code}:
 * {@link LifeOsException}, security exceptions thrown inside controllers, bean validation, and every
 * Spring MVC exception that {@link ResponseEntityExceptionHandler} knows (malformed JSON, 404, 405, ...).
 *
 * <p>Ordered late on purpose: a service's own {@code @RestControllerAdvice} with a higher precedence wins.
 * Anything not handled here falls through to {@link UnexpectedExceptionHandler}.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE - 10)
public class LifeOsExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(LifeOsExceptionHandler.class);

    @ExceptionHandler(LifeOsException.class)
    public ResponseEntity<ProblemDetail> handleLifeOs(LifeOsException ex) {
        ErrorCode code = ex.errorCode();
        if (code.status() >= 500) {
            log.error("{}: {}", code.code(), ex.getMessage(), ex);
        } else {
            log.debug("{}: {}", code.code(), ex.getMessage());
        }
        return respond(Problems.of(code, ex.getMessage(), ex.details()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
        return respond(Problems.of(CommonErrorCode.FORBIDDEN, "You are not allowed to perform this action."));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        return respond(Problems.of(CommonErrorCode.UNAUTHENTICATED, "Authentication is required."));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getBindingResult().getAllErrors().stream()
                .map(error -> Map.of(
                        "field", error instanceof FieldError field ? field.getField() : error.getObjectName(),
                        "message", messageOf(error.getDefaultMessage())))
                .toList();
        ProblemDetail problem = Problems.of(
                CommonErrorCode.VALIDATION_FAILED, "One or more fields are invalid.", Map.of("errors", errors));
        return ResponseEntity.status(problem.getStatus()).headers(headers).body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<Map<String, String>> errors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> Map.of(
                                "field", String.valueOf(result.getMethodParameter().getParameterName()),
                                "message", messageOf(error.getDefaultMessage()))))
                .toList();
        ProblemDetail problem = Problems.of(
                CommonErrorCode.VALIDATION_FAILED, "One or more parameters are invalid.", Map.of("errors", errors));
        return ResponseEntity.status(problem.getStatus()).headers(headers).body(problem);
    }

    /** Every other Spring MVC exception: keep Spring's title/detail, add our code and correlation ID. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            Problems.decorate(problem, codeFor(statusCode));
        }
        return response;
    }

    static ErrorCode codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 401 -> CommonErrorCode.UNAUTHENTICATED;
            case 403 -> CommonErrorCode.FORBIDDEN;
            case 404 -> CommonErrorCode.NOT_FOUND;
            case 409 -> CommonErrorCode.CONFLICT;
            case 429 -> CommonErrorCode.RATE_LIMITED;
            case 503 -> CommonErrorCode.UPSTREAM_UNAVAILABLE;
            default -> status.is5xxServerError() ? CommonErrorCode.INTERNAL_ERROR : CommonErrorCode.MALFORMED_REQUEST;
        };
    }

    private static ResponseEntity<ProblemDetail> respond(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }

    private static String messageOf(@Nullable String message) {
        return message == null ? "is invalid" : message;
    }
}
