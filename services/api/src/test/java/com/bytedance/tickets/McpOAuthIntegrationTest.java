package com.bytedance.tickets;

import com.bytedance.tickets.repository.AuthRepository;
import com.bytedance.tickets.service.AuthService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
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
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
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
                "spring.datasource.url=jdbc:h2:mem:mcp-integration-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
        }
)
class McpOAuthIntegrationTest {

    private static final String REDIRECT_URI = "http://127.0.0.1:3456/callback";

    @LocalServerPort
    int port;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AuthRepository authRepository;

    final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    private String origin() {
        return "http://localhost:" + port;
    }

    private String mcpResource() {
        return origin() + "/mcp";
    }

    // ---- fixtures ---------------------------------------------------------

    private record SeededUser(String id, String sessionToken) {
    }

    private SeededUser seedUser() {
        String rawToken = UUID.randomUUID() + "-" + UUID.randomUUID();
        String userId = authRepository.saveUser("open_" + UUID.randomUUID(), "Alice", "https://example.com/a.png");
        authRepository.createSession(
                AuthService.hashToken(rawToken),
                userId,
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(1));
        return new SeededUser(userId, rawToken);
    }

    private String registerClient() throws Exception {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("client_name", "Integration Client");
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

    private record Pkce(String verifier, String challenge) {
    }

    private Pkce pkce() throws Exception {
        byte[] random = new byte[32];
        new java.security.SecureRandom().nextBytes(random);
        String verifier = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        return new Pkce(verifier, challenge);
    }

    private String authorize(String clientId, String sessionToken, String codeChallenge, String state)
            throws Exception {
        String query = "response_type=code"
                + "&client_id=" + enc(clientId)
                + "&redirect_uri=" + enc(REDIRECT_URI)
                + "&state=" + enc(state)
                + "&scope=" + enc("tickets:read")
                + "&code_challenge=" + enc(codeChallenge)
                + "&code_challenge_method=S256"
                + "&resource=" + enc(mcpResource());

        var request = HttpRequest.newBuilder(URI.create(origin() + "/oauth2/authorize?" + query))
                .header("Cookie", AuthService.SESSION_COOKIE + "=" + sessionToken)
                .GET()
                .build();
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(302);
        String location = response.headers().firstValue("Location").orElse("");
        assertThat(location).startsWith(REDIRECT_URI);
        return parameter(location, "code");
    }

    private HttpResponse<String> tokenExchange(String clientId, String code, String verifier) throws Exception {
        String form = "grant_type=authorization_code"
                + "&code=" + enc(code)
                + "&client_id=" + enc(clientId)
                + "&redirect_uri=" + enc(REDIRECT_URI)
                + "&code_verifier=" + enc(verifier)
                + "&resource=" + enc(mcpResource());

        var request = HttpRequest.newBuilder(URI.create(origin() + "/oauth2/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode jwtPayload(String accessToken) throws Exception {
        String payload = accessToken.split("\\.")[1];
        return objectMapper.readTree(Base64.getUrlDecoder().decode(payload));
    }

    private HttpResponse<String> mcpRpc(String json, String bearer) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(mcpResource()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode rpcResult(HttpResponse<String> response) throws Exception {
        String body = response.body();
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        String json = body;
        if (contentType.contains("text/event-stream")) {
            json = body.lines()
                    .filter(line -> line.startsWith("data:"))
                    .map(line -> line.substring(5).trim())
                    .findFirst()
                    .orElseThrow();
        }
        return objectMapper.readTree(json);
    }

    // ---- tests ------------------------------------------------------------

    @Test
    void standardClientCompletesOAuthFlowAndCallsGetCurrentUser() throws Exception {
        SeededUser user = seedUser();
        String clientId = registerClient();
        Pkce pkce = pkce();
        String code = authorize(clientId, user.sessionToken(), pkce.challenge(), "flow-state");

        var tokenResponse = tokenExchange(clientId, code, pkce.verifier());
        assertThat(tokenResponse.statusCode()).isEqualTo(200);
        var tokenJson = objectMapper.readTree(tokenResponse.body());
        String accessToken = tokenJson.path("access_token").asText();
        assertThat(accessToken).isNotBlank();
        assertThat(tokenJson.path("token_type").asText().toLowerCase()).isEqualTo("bearer");

        var claims = jwtPayload(accessToken);
        assertThat(claims.path("iss").asText()).isEqualTo(origin());
        JsonNode audNode = claims.path("aud");
        List<String> audiences = new java.util.ArrayList<>();
        if (audNode.isArray()) {
            audNode.forEach(value -> audiences.add(value.asText()));
        } else if (!audNode.isMissingNode()) {
            audiences.add(audNode.asText());
        }
        assertThat(audiences).contains(mcpResource());
        assertThat(claims.path("sub").asText()).isEqualTo(user.id());
        assertThat(claims.path("scope").asText()).contains("tickets:read");

        var initialize = Map.of(
                "jsonrpc", "2.0", "id", 1, "method", "initialize",
                "params", Map.of(
                        "protocolVersion", "2025-06-18",
                        "capabilities", Map.of(),
                        "clientInfo", Map.of("name", "fixture", "version", "1.0")));
        mcpRpc(objectMapper.writeValueAsString(initialize), accessToken);

        var call = Map.of(
                "jsonrpc", "2.0", "id", 2, "method", "tools/call",
                "params", Map.of("name", "get_current_user", "arguments", Map.of()));
        var rpcResponse = mcpRpc(objectMapper.writeValueAsString(call), accessToken);
        var rpcJson = rpcResult(rpcResponse);
        String text = rpcJson.path("result").path("content").get(0).path("text").asText();
        var currentUser = objectMapper.readTree(text);
        assertThat(currentUser.path("id").asText()).isEqualTo(user.id());
        assertThat(currentUser.path("name").asText()).isEqualTo("Alice");
    }

    @Test
    void anonymousMcpRequestIsRejected() throws Exception {
        var body = Map.of("jsonrpc", "2.0", "id", 1, "method", "tools/list");
        var response = mcpRpc(objectMapper.writeValueAsString(body), null);
        assertThat(response.statusCode()).isEqualTo(401);
        String wwwAuthenticate = response.headers().firstValue("WWW-Authenticate").orElse("");
        assertThat(wwwAuthenticate).contains("Bearer");
        assertThat(wwwAuthenticate).contains("resource_metadata");
    }

    @Test
    void wrongAudienceTokenIsRejected() throws Exception {
        SeededUser user = seedUser();
        var keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        var keyPair = keyPairGenerator.generateKeyPair();
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .build();

        Instant now = Instant.now();
        var claimsSet = new JWTClaimsSet.Builder()
                .issuer(origin())
                .subject(user.id())
                .audience("https://evil.example/mcp")
                .claim("scope", "tickets:read")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .build();
        var signed = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claimsSet);
        signed.sign(new RSASSASigner(rsaKey.toPrivateKey()));

        var body = Map.of("jsonrpc", "2.0", "id", 1, "method", "tools/list");
        var response = mcpRpc(objectMapper.writeValueAsString(body), signed.serialize());
        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void feishuTokenIsRejected() throws Exception {
        SeededUser user = seedUser();
        var body = Map.of("jsonrpc", "2.0", "id", 1, "method", "tools/list");
        var response = mcpRpc(objectMapper.writeValueAsString(body), user.sessionToken());
        assertThat(response.statusCode()).isEqualTo(401);
    }

    @Test
    void authorizationCodeCannotBeReplayed() throws Exception {
        String clientId = registerClient();
        Pkce pkce = pkce();
        SeededUser user = seedUser();
        String code = authorize(clientId, user.sessionToken(), pkce.challenge(), "replay-state");

        var first = tokenExchange(clientId, code, pkce.verifier());
        assertThat(first.statusCode()).isEqualTo(200);

        var replay = tokenExchange(clientId, code, pkce.verifier());
        assertThat(replay.statusCode()).isEqualTo(400);
        assertThat(replay.body()).contains("invalid_grant");
    }

    // ---- helpers ----------------------------------------------------------

    private String parameter(String location, String name) {
        String query = location.substring(location.indexOf('?') + 1);
        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv[0].equals(name)) {
                return kv[1];
            }
        }
        throw new IllegalStateException("Missing parameter " + name);
    }

    private String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
