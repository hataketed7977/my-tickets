package com.bytedance.tickets;

import com.bytedance.tickets.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

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
    private static final String TEST_TOKEN = "mcp-test-token";

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
        jdbc.update("""
                INSERT INTO user_session (id_hash, user_id, expires_at)
                VALUES (?, 'test-user', DATEADD('DAY', 1, CURRENT_TIMESTAMP))
                """, AuthService.hashToken(TEST_TOKEN));
    }

    private HttpResponse<String> post(String json, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/mcp"))
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

    @Test
    void rejectsAnonymousAndInvalidBearer() throws Exception {
        var anonymous = post(jsonRpcRequest(1, "initialize", initializeParams()), null);
        assertThat(anonymous.statusCode()).isEqualTo(401);
        assertThat(anonymous.body()).contains("请先登录");

        var invalidBearer = post(jsonRpcRequest(2, "tools/list", "{}"), "invalid-token");
        assertThat(invalidBearer.statusCode()).isEqualTo(401);
        assertThat(invalidBearer.body()).contains("请先登录");
    }

    @Test
    void servesGetCurrentUserToolOverStatelessHttp() throws Exception {
        var initializeResponse = post(
                jsonRpcRequest(1, "initialize", initializeParams()),
                TEST_TOKEN
        );
        assertThat(initializeResponse.statusCode()).isEqualTo(200);
        assertThat(initializeResponse.headers().firstValue("Content-Type").orElse(""))
                .startsWith("application/json");
        var initializeJson = objectMapper.readTree(initializeResponse.body());
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
                TEST_TOKEN
        );
        assertThat(initializedNotification.statusCode()).isEqualTo(202);

        var toolsResponse = post(jsonRpcRequest(2, "tools/list", "{}"), TEST_TOKEN);
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
                TEST_TOKEN
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
