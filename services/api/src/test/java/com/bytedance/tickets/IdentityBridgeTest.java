package com.bytedance.tickets;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.bytedance.tickets.repository.AuthRepository;
import com.bytedance.tickets.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.feishu.app-id=cli_test_app",
                "app.feishu.app-secret=test_secret",
                "app.feishu.redirect-uri=http://localhost:55888/api/auth/feishu/callback",
                "spring.datasource.url=jdbc:h2:mem:identity-bridge-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
        }
)
class IdentityBridgeTest {

    private static final String REDIRECT_URI = "http://127.0.0.1:2345/callback";

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AuthRepository authRepository;

    @Autowired
    AuthService authService;

    final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private String origin() {
        return "http://localhost:" + port;
    }

    private String createUserWithSession(String rawToken) {
        String userId = authRepository.saveUser("open_" + UUID.randomUUID(), "Alice", "https://example.com/a.png");
        authRepository.createSession(
                AuthService.hashToken(rawToken),
                userId,
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));
        return userId;
    }

    private String registerClient() throws Exception {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("client_name", "Bridge Client");
        metadata.put("grant_types", List.of("authorization_code", "refresh_token"));
        metadata.put("response_types", List.of("code"));
        metadata.put("redirect_uris", List.of(REDIRECT_URI));
        metadata.put("scope", "tickets:read");
        metadata.put("token_endpoint_auth_method", "none");

        var request = HttpRequest.newBuilder(URI.create(origin() + "/oauth2/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(metadata)))
                .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(201);
        return objectMapper.readTree(response.body()).path("client_id").asText();
    }

    private static String s256(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private String authorizeQuery(String clientId, String state) throws Exception {
        return "response_type=code"
                + "&client_id=" + enc(clientId)
                + "&redirect_uri=" + enc(REDIRECT_URI)
                + "&state=" + enc(state)
                + "&code_challenge=" + enc(s256("bridge-verifier"))
                + "&code_challenge_method=S256"
                + "&resource=" + enc(origin() + "/mcp");
    }

    private HttpResponse<String> getAuthorize(String query, String cookieHeader) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(origin() + "/oauth2/authorize?" + query))
                .GET();
        if (cookieHeader != null) {
            builder.header("Cookie", cookieHeader);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void validSessionBecomesAuthorizePrincipal() throws Exception {
        String rawToken = UUID.randomUUID() + "-" + UUID.randomUUID();
        String userId = createUserWithSession(rawToken);
        String clientId = registerClient();

        var response = getAuthorize(authorizeQuery(clientId, "ok-state"),
                AuthService.SESSION_COOKIE + "=" + rawToken);

        assertThat(response.statusCode()).isEqualTo(302);
        String location = response.headers().firstValue("Location").orElse("");
        assertThat(location).startsWith(REDIRECT_URI);
        assertThat(location).contains("state=ok-state");
        assertThat(location).contains("code=");
        assertThat(location).doesNotContain("/api/auth/feishu");
        assertThat(userId).isNotBlank();
    }

    @Test
    void invalidSessionIsNotAuthenticated() throws Exception {
        String clientId = registerClient();

        var response = getAuthorize(authorizeQuery(clientId, "bad-state"),
                AuthService.SESSION_COOKIE + "=not-a-real-token");

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location").orElse(""))
                .startsWith("/api/auth/feishu");
    }

    @Test
    void loginResumesOriginalAuthorization() throws Exception {
        String clientId = registerClient();
        String query = authorizeQuery(clientId, "resume-state");

        // Pre-login: interrupted and redirected to login; a session holding the saved
        // authorization request is created.
        var interrupted = getAuthorize(query, null);
        assertThat(interrupted.statusCode()).isEqualTo(302);
        assertThat(interrupted.headers().firstValue("Location").orElse(""))
                .startsWith("/api/auth/feishu");
        String jsessionId = cookieValue(interrupted, "JSESSIONID");
        assertThat(jsessionId).isNotBlank();

        // Post-login: a valid application session is present; replaying the same
        // authorization completes the same transaction and issues a code.
        String rawToken = UUID.randomUUID() + "-" + UUID.randomUUID();
        createUserWithSession(rawToken);
        String cookieHeader = "JSESSIONID=" + jsessionId + "; "
                + AuthService.SESSION_COOKIE + "=" + rawToken;

        var resumed = getAuthorize(query, cookieHeader);
        assertThat(resumed.statusCode()).isEqualTo(302);
        String location = resumed.headers().firstValue("Location").orElse("");
        assertThat(location).startsWith(REDIRECT_URI);
        assertThat(location).contains("state=resume-state");
        assertThat(location).contains("code=");
    }

    private String cookieValue(HttpResponse<String> response, String name) {
        for (String cookie : response.headers().allValues("Set-Cookie")) {
            if (cookie.startsWith(name + "=")) {
                return cookie.substring(name.length() + 1, cookie.indexOf(';'));
            }
        }
        return null;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
