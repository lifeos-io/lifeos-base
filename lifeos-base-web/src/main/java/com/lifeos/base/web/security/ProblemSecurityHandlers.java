package com.lifeos.base.web.security;

import com.lifeos.base.error.CommonErrorCode;
import com.lifeos.base.web.error.ProblemWriter;
import com.lifeos.base.web.error.Problems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * 401 and 403 from the security filter chain as problem responses. The standard bearer-token handlers run
 * first so the {@code WWW-Authenticate} header (RFC 6750) is still set; only the body is ours. The token
 * error description is not echoed to the client.
 */
public final class ProblemSecurityHandlers {

    private ProblemSecurityHandlers() {
    }

    public static AuthenticationEntryPoint entryPoint(ProblemWriter writer) {
        BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
        return (HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) -> {
            bearer.commence(request, response, ex);
            write(writer, request, response,
                    Problems.of(CommonErrorCode.UNAUTHENTICATED, "A valid access token is required."));
        };
    }

    public static AccessDeniedHandler accessDeniedHandler(ProblemWriter writer) {
        BearerTokenAccessDeniedHandler bearer = new BearerTokenAccessDeniedHandler();
        return (HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) -> {
            bearer.handle(request, response, ex);
            write(writer, request, response,
                    Problems.of(CommonErrorCode.FORBIDDEN, "You are not allowed to perform this action."));
        };
    }

    private static void write(ProblemWriter writer, HttpServletRequest request, HttpServletResponse response,
                              org.springframework.http.ProblemDetail problem) throws IOException {
        if (!response.isCommitted()) {
            writer.write(request, response, problem);
        }
    }
}
