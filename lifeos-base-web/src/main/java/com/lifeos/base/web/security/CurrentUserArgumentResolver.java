package com.lifeos.base.web.security;

import com.lifeos.base.error.CommonErrorCode;
import com.lifeos.base.error.LifeOsException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/** Resolves {@link CurrentUser} parameters from the JWT in the security context. */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        if (!parameter.hasParameterAnnotation(CurrentUser.class)) {
            return false;
        }
        if (!UUID.class.equals(parameter.getParameterType())) {
            throw new IllegalStateException("@CurrentUser must be used on a java.util.UUID parameter: " + parameter);
        }
        return true;
    }

    @Override
    public UUID resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        return currentUserId().orElseThrow(() ->
                new LifeOsException(CommonErrorCode.UNAUTHENTICATED, "Authentication is required."));
    }

    /** The {@code sub} of the current JWT as a UUID, if there is an authenticated JWT with a UUID subject. */
    public static Optional<UUID> currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return Optional.empty();
        }
        return parseUuid(jwt.getSubject());
    }

    static Optional<UUID> parseUuid(String value) {
        if (value == null) {
            return Optional.empty();
        }
        try {
            UUID uuid = UUID.fromString(value);
            // UUID.fromString accepts short forms like "1-2-3-4-5"; require the canonical 36-char text.
            return uuid.toString().equalsIgnoreCase(value) ? Optional.of(uuid) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
