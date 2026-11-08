package com.lifeos.base.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lifeos.base.correlation.CorrelationIds;
import com.lifeos.base.error.CommonErrorCode;
import com.lifeos.base.error.LifeOsException;
import com.lifeos.base.web.security.CurrentUser;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Starts a real servlet container with only the Life OS auto-configuration and a test controller, and calls it
 * over HTTP with real RS256-signed JWTs, so filter order, the security chain and the problem bodies are tested
 * exactly as a service would run them.
 */
@SpringBootTest(
        classes = LifeOsWebIntegrationTest.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "lifeos.security.public-paths=/public/**")
class LifeOsWebIntegrationTest {

    private static final KeyPair TRUSTED = rsaKeyPair();
    private static final KeyPair UNTRUSTED = rsaKeyPair();
    private static final UUID USER = UUID.fromString("0197a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b");

    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper json = JsonMapper.builder().build();

    @LocalServerPort
    int port;

    @Autowired
    Clock clock;

    @Test
    void requestWithoutTokenGetsUnauthenticatedProblem() throws Exception {
        HttpResponse<String> response = get("/me", null, null);

        assertEquals(401, response.statusCode());
        assertTrue(response.headers().firstValue("WWW-Authenticate").orElse("").startsWith("Bearer"));
        assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("application/problem+json"));
        JsonNode body = json.readTree(response.body());
        assertEquals("UNAUTHENTICATED", body.get("code").asString());
        assertEquals("urn:lifeos:error:UNAUTHENTICATED", body.get("type").asString());
        assertEquals("/me", body.get("instance").asString());
        String headerId = response.headers().firstValue("X-Correlation-Id").orElseThrow();
        assertEquals(headerId, body.get("correlationId").asString());
    }

    @Test
    void tokenSignedByAnotherKeyIsRejected() throws Exception {
        HttpResponse<String> response = get("/me", token(UNTRUSTED.getPrivate(), USER.toString()), null);
        assertEquals(401, response.statusCode());
        assertEquals("UNAUTHENTICATED", json.readTree(response.body()).get("code").asString());
    }

    @Test
    void currentUserComesFromTheJwtSubjectNotTheHeader() throws Exception {
        UUID spoofed = UUID.randomUUID();
        HttpResponse<String> response = get("/me", token(TRUSTED.getPrivate(), USER.toString()),
                Map.of("X-User-Id", spoofed.toString()));

        assertEquals(200, response.statusCode());
        assertEquals(USER.toString(), json.readTree(response.body()).get("userId").asString());
    }

    @Test
    void nonUuidSubjectIsUnauthenticated() throws Exception {
        HttpResponse<String> response = get("/me", token(TRUSTED.getPrivate(), "service-account-x"), null);
        assertEquals(401, response.statusCode());
        assertEquals("UNAUTHENTICATED", json.readTree(response.body()).get("code").asString());
    }

    @Test
    void publicPathsNeedNoToken() throws Exception {
        assertEquals(200, get("/public/ping", null, null).statusCode());
    }

    @Test
    void validCorrelationIdIsKeptAndInvalidOneIsReplaced() throws Exception {
        HttpResponse<String> kept = get("/public/ping", null, Map.of("X-Correlation-Id", "mobile-123"));
        assertEquals("mobile-123", kept.headers().firstValue("X-Correlation-Id").orElseThrow());

        HttpResponse<String> replaced = get("/public/ping", null, Map.of("X-Correlation-Id", "bad id<script>"));
        String id = replaced.headers().firstValue("X-Correlation-Id").orElseThrow();
        assertNotEquals("bad id<script>", id);
        assertTrue(CorrelationIds.isValid(id));
    }

    @Test
    void lifeOsExceptionBecomesProblemWithItsCodeAndDetails() throws Exception {
        HttpResponse<String> response = authorizedGet("/fail/lifeos", Map.of("X-Correlation-Id", "corr-1"));

        assertEquals(409, response.statusCode());
        JsonNode body = json.readTree(response.body());
        assertEquals("CONFLICT", body.get("code").asString());
        assertEquals("urn:lifeos:error:CONFLICT", body.get("type").asString());
        assertEquals("Goal is archived", body.get("detail").asString());
        assertEquals("ABANDONED", body.get("goalStatus").asString());
        assertEquals("corr-1", body.get("correlationId").asString());
    }

    @Test
    void unexpectedExceptionHidesItsMessage() throws Exception {
        HttpResponse<String> response = authorizedGet("/fail/boom", null);

        assertEquals(500, response.statusCode());
        assertEquals("INTERNAL_ERROR", json.readTree(response.body()).get("code").asString());
        assertFalse(response.body().contains("secret"));
    }

    @Test
    void optimisticLockFailureIsConcurrentModification() throws Exception {
        HttpResponse<String> response = authorizedGet("/fail/lock", null);
        assertEquals(409, response.statusCode());
        assertEquals("CONCURRENT_MODIFICATION", json.readTree(response.body()).get("code").asString());
    }

    @Test
    void accessDeniedInsideControllerIsForbidden() throws Exception {
        HttpResponse<String> response = authorizedGet("/fail/denied", null);
        assertEquals(403, response.statusCode());
        assertEquals("FORBIDDEN", json.readTree(response.body()).get("code").asString());
    }

    @Test
    void beanValidationListsTheInvalidFields() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/validate"))
                .header("Authorization", "Bearer " + token(TRUSTED.getPrivate(), USER.toString()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"title\":\"\"}"))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
        JsonNode body = json.readTree(response.body());
        assertEquals("VALIDATION_FAILED", body.get("code").asString());
        assertEquals("title", body.get("errors").get(0).get("field").asString());
    }

    @Test
    void malformedJsonIsMalformedRequest() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri("/validate"))
                .header("Authorization", "Bearer " + token(TRUSTED.getPrivate(), USER.toString()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{not json"))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(400, response.statusCode());
        assertEquals("MALFORMED_REQUEST", json.readTree(response.body()).get("code").asString());
    }

    @Test
    void unknownPathIsNotFound() throws Exception {
        HttpResponse<String> response = authorizedGet("/does-not-exist", null);
        assertEquals(404, response.statusCode());
        assertEquals("NOT_FOUND", json.readTree(response.body()).get("code").asString());
    }

    @Test
    void clockIsUtc() {
        assertEquals(ZoneOffset.UTC, clock.getZone());
    }

    // --- helpers -------------------------------------------------------------------------------------------

    private HttpResponse<String> authorizedGet(String path, Map<String, String> headers) throws Exception {
        return get(path, token(TRUSTED.getPrivate(), USER.toString()), headers);
    }

    private HttpResponse<String> get(String path, String bearer, Map<String, String> headers)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri(path)).GET();
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        if (headers != null) {
            headers.forEach(builder::header);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private static String token(PrivateKey key, String subject) throws JOSEException {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private static KeyPair rsaKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // --- test application ----------------------------------------------------------------------------------

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(TestController.class)
    static class TestApp {

        /** Stands in for Keycloak's JWKS: trusts only the TRUSTED key. */
        @Bean
        JwtDecoder jwtDecoder() {
            return NimbusJwtDecoder.withPublicKey((RSAPublicKey) TRUSTED.getPublic()).build();
        }
    }

    record CreateGoal(@NotBlank String title) {
    }

    @RestController
    static class TestController {

        @GetMapping("/me")
        Map<String, String> me(@CurrentUser UUID userId) {
            return Map.of("userId", userId.toString());
        }

        @GetMapping("/public/ping")
        String ping() {
            return "pong";
        }

        @GetMapping("/fail/lifeos")
        void lifeos() {
            throw new LifeOsException(CommonErrorCode.CONFLICT, "Goal is archived", Map.of("goalStatus", "ABANDONED"));
        }

        @GetMapping("/fail/boom")
        void boom() {
            throw new IllegalStateException("secret database detail");
        }

        @GetMapping("/fail/lock")
        void lock() {
            throw new OptimisticLockingFailureException("version mismatch");
        }

        @GetMapping("/fail/denied")
        void denied() {
            throw new AccessDeniedException("not your goal");
        }

        @PostMapping("/validate")
        void validate(@Valid @RequestBody CreateGoal body) {
        }
    }
}
