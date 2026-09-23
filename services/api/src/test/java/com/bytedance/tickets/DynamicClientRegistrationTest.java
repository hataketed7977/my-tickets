package com.bytedance.tickets;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.feishu.app-id=cli_test_app",
                "app.feishu.app-secret=test_secret",
                "app.feishu.redirect-uri=http://localhost:55888/api/auth/feishu/callback",
                "spring.datasource.url=jdbc:h2:mem:dcr-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
        }
)
class DynamicClientRegistrationTest {

    private static final String REDIRECT_URI = "http://127.0.0.1:1234/callback";

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private String origin() {
        return "http://localhost:" + port;
    }

    private HttpResponse<String> register(Map<String, Object> metadata) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(origin() + "/oauth2/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(metadata)))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private Map<String, Object> validMetadata() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("client_name", "Test MCP Client");
        metadata.put("grant_types", List.of("authorization_code", "refresh_token"));
        metadata.put("response_types", List.of("code"));
        metadata.put("redirect_uris", List.of(REDIRECT_URI));
        metadata.put("scope", "tickets:read");
        metadata.put("token_endpoint_auth_method", "none");
        return metadata;
    }

    private String registerValidClient() throws Exception {
        var response = register(validMetadata());
        assertThat(response.statusCode()).isEqualTo(201);
        return objectMapper.readTree(response.body()).path("client_id").asText();
    }

    private static String s256(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    @Test
    void registersClientWithValidMetadata() throws Exception {
        var response = register(validMetadata());
        assertThat(response.statusCode()).isEqualTo(201);

        var json = objectMapper.readTree(response.body());
        assertThat(json.path("client_id").asText()).isNotBlank();
        assertThat(json.path("client_secret").asText()).isBlank();
    }

    @Test
    void rejectsInvalidRedirectUri() throws Exception {
        Map<String, Object> metadata = validMetadata();
        metadata.put("redirect_uris", List.of(REDIRECT_URI + "#fragment"));

        var response = register(metadata);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("error");
    }

    @Test
    void rejectsUnsupportedGrantAndScope() throws Exception {
        Map<String, Object> metadata = validMetadata();
        metadata.put("grant_types", List.of("client_credentials"));
        metadata.put("scope", "tickets:read openid admin");

        var response = register(metadata);
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("error");
    }

    @Test
    void anonymousAuthorizeRedirectsToLogin() throws Exception {
        String clientId = registerValidClient();
        String query = "response_type=code"
                + "&client_id=" + enc(clientId)
                + "&redirect_uri=" + enc(REDIRECT_URI)
                + "&state=xyz"
                + "&code_challenge=" + enc(s256("test-verifier"))
                + "&code_challenge_method=S256"
                + "&resource=" + enc(origin() + "/mcp");

        var request = HttpRequest.newBuilder(URI.create(origin() + "/oauth2/authorize?" + query))
                .GET()
                .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(302);
        assertThat(response.headers().firstValue("Location").orElse(""))
                .startsWith("/api/auth/feishu");
    }

    @Test
    void tokenExchangeRejectsWrongResource() throws Exception {
        String clientId = registerValidClient();
        String form = "grant_type=authorization_code"
                + "&code=dummy-code"
                + "&client_id=" + enc(clientId)
                + "&redirect_uri=" + enc(REDIRECT_URI)
                + "&resource=" + enc("https://evil.example/mcp");

        var request = HttpRequest.newBuilder(URI.create(origin() + "/oauth2/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("invalid_target");
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
