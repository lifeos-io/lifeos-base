package com.lifeos.base.web.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the authenticated user's ID (the validated JWT's {@code sub}) into a controller parameter of type
 * {@link java.util.UUID}:
 *
 * <pre>{@code
 * @GetMapping("/goals")
 * List<GoalDto> goals(@CurrentUser UUID userId) { ... }
 * }</pre>
 *
 * The value never comes from the {@code X-User-Id} header. A request without a valid JWT is rejected before
 * the controller runs; a {@code sub} that is not a UUID is answered with 401 {@code UNAUTHENTICATED}.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CurrentUser {
}
