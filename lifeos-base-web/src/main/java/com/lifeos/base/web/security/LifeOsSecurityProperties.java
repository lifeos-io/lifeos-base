package com.lifeos.base.web.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code lifeos.security.*}. JWT validation itself is configured with Spring Boot's standard properties:
 * {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} (Keycloak realm URL) and
 * {@code spring.security.oauth2.resourceserver.jwt.audiences}.
 *
 * @param publicPaths request paths reachable without a token (path patterns). Everything else needs a valid JWT.
 */
@ConfigurationProperties("lifeos.security")
public record LifeOsSecurityProperties(
        @DefaultValue({"/actuator/health", "/actuator/health/**", "/actuator/info", "/error"})
        List<String> publicPaths) {
}
