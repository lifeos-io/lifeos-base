package com.lifeos.base.web.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes a problem straight to the servlet response, for code that runs outside Spring MVC (the security
 * filter chain's 401/403 handlers). Inside controllers, return the {@link ProblemDetail} instead.
 */
public class ProblemWriter {

    private final JsonMapper jsonMapper;

    public ProblemWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, ProblemDetail problem)
            throws IOException {
        if (problem.getInstance() == null) {
            problem.setInstance(URI.create(request.getRequestURI()));
        }
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getOutputStream(), toMap(problem));
    }

    /** Flat RFC 9457 shape: extension members sit next to the standard ones, not under "properties". */
    static Map<String, Object> toMap(ProblemDetail problem) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", problem.getType().toString());
        body.put("title", problem.getTitle());
        body.put("status", problem.getStatus());
        if (problem.getDetail() != null) {
            body.put("detail", problem.getDetail());
        }
        if (problem.getInstance() != null) {
            body.put("instance", problem.getInstance().toString());
        }
        if (problem.getProperties() != null) {
            body.putAll(problem.getProperties());
        }
        return body;
    }
}
