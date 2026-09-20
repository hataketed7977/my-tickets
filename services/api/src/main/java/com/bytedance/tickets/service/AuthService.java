package com.bytedance.tickets.service;

import com.bytedance.tickets.exception.ApiException;
import com.bytedance.tickets.model.ApiModels;
import com.bytedance.tickets.repository.AuthRepository;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
public final class AuthService {
    public static final String SESSION_COOKIE = "ticket_session";
    public static final String OAUTH_STATE_COOKIE = "ticket_oauth_state";

    private final AuthRepository repository;
    private final RestClient restClient;
    private final String appId;
    private final String appSecret;
    private final String redirectUri;

    public AuthService(
            AuthRepository repository,
            RestClient.Builder restClientBuilder,
            @Value("${app.feishu.app-id}") String appId,
            @Value("${app.feishu.app-secret}") String appSecret,
            @Value("${app.feishu.redirect-uri}") String redirectUri
    ) {
        this.repository = repository;
        this.restClient = restClientBuilder.build();
        this.appId = appId;
        this.appSecret = appSecret;
        this.redirectUri = redirectUri;
    }

    @PostConstruct
    void removeExpiredSessions() {
        repository.deleteExpiredSessions(OffsetDateTime.now(ZoneOffset.UTC));
    }

    public ApiModels.AuthConfig config() {
        return new ApiModels.AuthConfig(isFeishuConfigured());
    }

    public List<ApiModels.AppUser> listUsers() {
        return repository.listUsers();
    }

    public ApiModels.AppUser getSessionUser(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return repository.findSessionUser(
                hashToken(token),
                OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    public void destroyUserSessions(String userId) {
        repository.deleteUserSessions(userId);
    }

    public String createOAuthState() {
        return UUID.randomUUID() + "-" + UUID.randomUUID();
    }

    public URI buildFeishuAuthorizeUri(String state) {
        requireFeishuConfiguration();
        var query = "client_id=" + encode(appId)
                + "&response_type=code"
                + "&redirect_uri=" + encode(redirectUri)
                + "&state=" + encode(state);
        return URI.create(
                "https://accounts.feishu.cn/open-apis/authen/v1/authorize?" + query
        );
    }

    public String loginWithFeishu(String code) {
        requireFeishuConfiguration();
        var tokenBody = new LinkedMultiValueMap<String, String>();
        tokenBody.add("grant_type", "authorization_code");
        tokenBody.add("client_id", appId);
        tokenBody.add("client_secret", appSecret);
        tokenBody.add("code", code);
        tokenBody.add("redirect_uri", redirectUri);

        FeishuTokenResponse tokenResponse;
        try {
            tokenResponse = restClient.post()
                    .uri("https://accounts.feishu.cn/oauth/v3/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(tokenBody)
                    .retrieve()
                    .body(FeishuTokenResponse.class);
        } catch (Exception error) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "获取飞书访问凭证失败"
            );
        }
        if (tokenResponse == null
                || tokenResponse.code() != 0
                || tokenResponse.accessToken() == null) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    tokenResponse != null && tokenResponse.errorDescription() != null
                            ? tokenResponse.errorDescription()
                            : "获取飞书访问凭证失败"
            );
        }

        FeishuUserInfoResponse userResponse;
        try {
            userResponse = restClient.get()
                    .uri("https://open.feishu.cn/open-apis/authen/v1/user_info")
                    .headers(headers ->
                            headers.setBearerAuth(tokenResponse.accessToken())
                    )
                    .retrieve()
                    .body(FeishuUserInfoResponse.class);
        } catch (Exception error) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "获取飞书用户信息失败"
            );
        }
        var profile = userResponse == null ? null : userResponse.data();
        if (userResponse == null
                || userResponse.code() != 0
                || profile == null
                || profile.openId() == null) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    userResponse != null && userResponse.msg() != null
                            ? userResponse.msg()
                            : "获取飞书用户信息失败"
            );
        }

        String userId = repository.saveUser(
                profile.openId(),
                profile.name(),
                profile.avatarUrl()
        );
        return createSession(userId);
    }

    private String createSession(String userId) {
        String token = UUID.randomUUID() + "-" + UUID.randomUUID();
        repository.createSession(
                hashToken(token),
                userId,
                OffsetDateTime.now(ZoneOffset.UTC).plusDays(7)
        );
        return token;
    }

    public static String hashToken(String token) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(token.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private boolean isFeishuConfigured() {
        return !appId.isBlank()
                && !appSecret.isBlank()
                && !redirectUri.isBlank();
    }

    private void requireFeishuConfiguration() {
        if (!isFeishuConfigured()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "飞书 OAuth 尚未配置"
            );
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private record FeishuTokenResponse(
            int code,
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("error_description") String errorDescription
    ) {
    }

    private record FeishuUserInfoResponse(
            int code,
            String msg,
            FeishuProfile data
    ) {
    }

    private record FeishuProfile(
            String name,
            @JsonProperty("avatar_url") String avatarUrl,
            @JsonProperty("open_id") String openId
    ) {
    }
}
