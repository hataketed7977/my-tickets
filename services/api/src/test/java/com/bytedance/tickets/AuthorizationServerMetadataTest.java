package com.bytedance.tickets;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

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
                "spring.datasource.url=jdbc:h2:mem:as-metadata-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
        }
)
class AuthorizationServerMetadataTest {

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    final HttpClient httpClient = HttpClient.newHttpClient();

    private HttpResponse<String> get(String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void metadataExposesOAuthEndpoints() throws Exception {
        var response = get("/.well-known/oauth-authorization-server");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .contains("application/json");

        var json = objectMapper.readTree(response.body());
        var origin = "http://localhost:" + port;
        assertThat(json.path("issuer").asText()).isEqualTo(origin);
        assertThat(json.path("authorization_endpoint").asText())
                .isEqualTo(origin + "/oauth2/authorize");
        assertThat(json.path("token_endpoint").asText())
                .isEqualTo(origin + "/oauth2/token");
        assertThat(json.path("jwks_uri").asText())
                .isEqualTo(origin + "/oauth2/jwks");
        assertThat(json.path("code_challenge_methods_supported"))
                .map(node -> node.asText())
                .contains("S256");
        assertThat(json.path("registration_endpoint").asText()).isNotBlank();
    }

    @Test
    void protectedResourceMetadataIsPublic() throws Exception {
        var response = get("/.well-known/oauth-protected-resource/mcp");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .contains("application/json");

        var json = objectMapper.readTree(response.body());
        var origin = "http://localhost:" + port;
        assertThat(json.path("resource").asText()).isEqualTo(origin + "/mcp");
        assertThat(json.path("authorization_servers"))
                .map(node -> node.asText())
                .contains(origin);
        assertThat(json.path("scopes_supported"))
                .map(node -> node.asText())
                .contains("tickets:read");
    }
}
