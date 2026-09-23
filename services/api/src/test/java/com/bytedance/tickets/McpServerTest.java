package com.bytedance.tickets;

import com.bytedance.tickets.service.AuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

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
                "spring.datasource.url=jdbc:h2:mem:mcp-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
        }
)
class McpServerTest {
    private static final String REDIRECT_URI = "http://127.0.0.1:3456/callback";

    @LocalServerPort
    int port;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper objectMapper;

    final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void setUpSession() {
        jdbc.update("DELETE FROM ticket");
        jdbc.update("DELETE FROM user_session");
        jdbc.update("DELETE FROM app_user");
        jdbc.update("""
                INSERT INTO app_user (id, feishu_open_id, name, avatar_url)
                VALUES ('test-user', 'mcp-open-id', '测试用户',
                        'https://cdn.example.com/avatar.png')
                """);
    }

    private String origin() {
        return "http://localhost:" + port;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private HttpResponse<String> post(String json, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(origin() + "/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String jsonRpcRequest(long id, String method, String params) {
        return """
                {"jsonrpc":"2.0","id":%d,"method":"%s","params":%s}
                """.formatted(id, method, params);
    }

    private static String jsonRpcNotification(String method, String params) {
        return """
                {"jsonrpc":"2.0","method":"%s","params":%s}
                """.formatted(method, params);
    }

    private static String initializeParams() {
        return """
                {"protocolVersion":"%s","capabilities":{},
                 "clientInfo":{"name":"test-client","version":"1.0"}}
                """.formatted(McpSchema.LATEST_PROTOCOL_VERSION);
    }

    private String obtainAccessToken() throws Exception {
        // Dynamic client registration
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("client_name", "Transport Client");
        metadata.put("grant_types", List.of("authorization_code", "refresh_token"));
        metadata.put("response_types", List.of("code"));
        metadata.put("redirect_uris", List.of(REDIRECT_URI));
        metadata.put("scope", "tickets:read");
        metadata.put("token_endpoint_auth_method", "none");
        var register = HttpRequest.newBuilder(URI.create(origin() + "/oauth2/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(metadata)))
                .build();
        var registerResponse = httpClient.send(register, HttpResponse.BodyHandlers.ofString());
        assertThat(registerResponse.statusCode()).isEqualTo(201);
        String clientId = objectMapper.readTree(registerResponse.body()).path("client_id").asText();

        // PKCE
        byte[] random = new byte[32];
        new java.security.SecureRandom().nextBytes(random);
        String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        String challenge = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256")
                        .digest(verifier.getBytes(StandardCharsets.US_ASCII)));

        // A session cookie authenticates the resource owner at the authorize endpoint.
        String rawSession = "mcp-test-session-token";
        jdbc.update("""
                INSERT INTO user_session (id_hash, user_id, expires_at)
                VALUES (?, 'test-user', DATEADD('DAY', 1, CURRENT_TIMESTAMP))
                """, AuthService.hashToken(rawSession));

        String query = "response_type=code"
                + "&client_id=" + enc(clientId)
                + "&redirect_uri=" + enc(REDIRECT_URI)
                + "&state=transport-state"
                + "&scope=tickets:read"
                + "&code_challenge=" + enc(challenge)
                + "&code_challenge_method=S256"
                + "&resource=" + enc(origin() + "/mcp");
        var authorize = HttpRequest.newBuilder(URI.create(origin() + "/oauth2/authorize?" + query))
                .header("Cookie", AuthService.SESSION_COOKIE + "=" + rawSession)
                .GET()
                .build();
        var authorizeResponse = httpClient.send(authorize, HttpResponse.BodyHandlers.ofString());
        assertThat(authorizeResponse.statusCode()).isEqualTo(302);
        String location = authorizeResponse.headers().firstValue("Location").orElse("");
        String code = URI.create(location).getQuery().split("code=")[1].split("&")[0];

        // Token exchange
        String form = "grant_type=authorization_code"
                + "&code=" + code
                + "&client_id=" + enc(clientId)
                + "&redirect_uri=" + enc(REDIRECT_URI)
                + "&code_verifier=" + enc(verifier)
                + "&resource=" + enc(origin() + "/mcp");
        var tokenRequest = HttpRequest.newBuilder(URI.create(origin() + "/oauth2/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        var tokenResponse = httpClient.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
        assertThat(tokenResponse.statusCode()).isEqualTo(200);
        return objectMapper.readTree(tokenResponse.body()).path("access_token").asText();
    }

    @Test
    void rejectsAnonymousAndMalformedBearer() throws Exception {
        var anonymous = post(jsonRpcRequest(1, "initialize", initializeParams()), null);
        assertThat(anonymous.statusCode()).isEqualTo(401);
        String wwwAuthenticate = anonymous.headers().firstValue("WWW-Authenticate").orElse("");
        assertThat(wwwAuthenticate).contains("Bearer");
        assertThat(wwwAuthenticate).contains("resource_metadata");

        var malformedBearer = post(jsonRpcRequest(2, "tools/list", "{}"), "not-a-jwt");
        assertThat(malformedBearer.statusCode()).isEqualTo(401);
    }

    @Test
    void servesGetCurrentUserToolOverStatelessHttp() throws Exception {
        String accessToken = obtainAccessToken();

        var initializeResponse = post(
                jsonRpcRequest(1, "initialize", initializeParams()),
                accessToken
        );
        assertThat(initializeResponse.statusCode()).isEqualTo(200);
        assertThat(initializeResponse.headers().firstValue("Content-Type").orElse(""))
                .startsWith("application/json");
        JsonNode initializeJson = objectMapper.readTree(initializeResponse.body());
        assertThat(initializeJson.path("jsonrpc").asText()).isEqualTo("2.0");
        assertThat(initializeJson.path("id").asLong()).isEqualTo(1);
        assertThat(initializeJson.path("result").path("protocolVersion").asText())
                .isEqualTo(McpSchema.LATEST_PROTOCOL_VERSION);
        assertThat(initializeJson.path("result").path("serverInfo").path("name").asText())
                .isEqualTo("ticket-center-mcp");
        assertThat(initializeResponse.headers().firstValue("mcp-session-id"))
                .as("stateless transport must not require a session id")
                .isEmpty();

        var initializedNotification = post(
                jsonRpcNotification("notifications/initialized", "{}"),
                accessToken
        );
        assertThat(initializedNotification.statusCode()).isEqualTo(202);

        var toolsResponse = post(jsonRpcRequest(2, "tools/list", "{}"), accessToken);
        assertThat(toolsResponse.statusCode()).isEqualTo(200);
        var toolNames = objectMapper.readTree(toolsResponse.body())
                .path("result").path("tools")
                .findValuesAsText("name");
        assertThat(toolNames).containsExactly("get_current_user");

        var callResponse = post(
                jsonRpcRequest(3, "tools/call",
                        """
                        {"name":"get_current_user","arguments":{}}
                        """),
                accessToken
        );
        assertThat(callResponse.statusCode()).isEqualTo(200);
        var toolResult = objectMapper.readTree(callResponse.body()).path("result");
        assertThat(toolResult.path("isError").asBoolean()).isFalse();
        var user = objectMapper.readTree(
                toolResult.path("content").get(0).path("text").asText()
        );
        assertThat(user.path("id").asText()).isEqualTo("test-user");
        assertThat(user.path("name").asText()).isEqualTo("测试用户");
        assertThat(user.path("avatarUrl").asText())
                .isEqualTo("https://cdn.example.com/avatar.png");
    }
}
