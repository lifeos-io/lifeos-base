package com.lifeos.base.web.security;

import com.lifeos.base.http.LifeOsHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Detects requests whose {@code X-User-Id} does not match the JWT {@code sub}. The gateway always overwrites
 * the header from the JWT, so a mismatch means the request did not come through the gateway (or the gateway
 * is misconfigured). Authorization is unaffected: services use the JWT, never the header. The header value
 * is client-controlled and is therefore not logged.
 */
public class UserIdHeaderConsistencyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(UserIdHeaderConsistencyFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(LifeOsHeaders.USER_ID);
        if (header != null) {
            Optional<UUID> subject = CurrentUserArgumentResolver.currentUserId();
            if (subject.isPresent() && !subject.get().toString().equalsIgnoreCase(header.trim())) {
                log.warn("{} header does not match the JWT subject {} on {} {}; the header is ignored",
                        LifeOsHeaders.USER_ID, subject.get(), request.getMethod(), request.getRequestURI());
            }
        }
        chain.doFilter(request, response);
    }
}
