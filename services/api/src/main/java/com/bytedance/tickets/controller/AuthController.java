package com.bytedance.tickets.controller;

import com.bytedance.tickets.exception.ApiException;
import com.bytedance.tickets.model.ApiModels;
import com.bytedance.tickets.security.SessionInterceptor;
import com.bytedance.tickets.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public final class AuthController {
    private final AuthService authService;
    private final boolean cookieSecure;
    private final String webBaseUrl;

    public AuthController(
            AuthService authService,
            @Value("${app.cookie-secure}") boolean cookieSecure,
            @Value("${app.web-base-url}") String webBaseUrl
    ) {
        this.authService = authService;
        this.cookieSecure = cookieSecure;
        this.webBaseUrl = webBaseUrl.replaceAll("/$", "");
    }

    @GetMapping("/config")
    public ApiModels.AuthConfig config() {
        return authService.config();
    }

    @GetMapping("/feishu")
    public void loginWithFeishu(HttpServletResponse response)
            throws IOException {
        String state = authService.createOAuthState();
        addCookie(
                response,
                AuthService.OAUTH_STATE_COOKIE,
                state,
                Duration.ofMinutes(10)
        );
        response.sendRedirect(authService.buildFeishuAuthorizeUri(state).toString());
    }

    @GetMapping("/feishu/callback")
    public void handleFeishuCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @CookieValue(
                    name = AuthService.OAUTH_STATE_COOKIE,
                    required = false
            ) String expectedState,
            HttpServletResponse response
    ) throws IOException {
        if (error != null) {
            response.sendRedirect(webBaseUrl + "/login?error=access_denied");
            return;
        }

        if (expectedState != null) {
            handleWebCallback(code, state, expectedState, response);
        } else {
            handleCliCallback(code, state, response);
        }
    }

    private void handleWebCallback(
            String code,
            String state,
            String expectedState,
            HttpServletResponse response
    ) throws IOException {
        if (code == null
                || state == null
                || !state.equals(expectedState)) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "飞书登录状态校验失败，请重新登录"
            );
        }
        String sessionToken = authService.loginWithFeishu(code);
        clearCookie(response, AuthService.OAUTH_STATE_COOKIE);
        addCookie(
                response,
                AuthService.SESSION_COOKIE,
                sessionToken,
                Duration.ofDays(7)
        );
        response.sendRedirect(webBaseUrl + "/");
    }

    private void handleCliCallback(
            String code,
            String state,
            HttpServletResponse response
    ) throws IOException {
        if (code == null || state == null) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "飞书登录状态校验失败，请重新登录"
            );
        }
        authService.loginWithFeishu(code, state);
        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().write(
                "<html><body style=\"font-family:sans-serif;text-align:center;"
                        + "padding:40px\"><h1>登录成功</h1>"
                        + "<p>请返回 CLI 继续操作。</p></body></html>"
        );
    }

    @PostMapping("/cli/session")
    public Map<String, String> createCliSession() {
        String state = authService.createOAuthState();
        String authorizeUrl = authService.buildFeishuAuthorizeUri(state).toString();
        return Map.of("sessionId", state, "authorizeUrl", authorizeUrl);
    }

    @GetMapping("/cli/session/{sessionId}")
    public Map<String, Object> getCliSession(@PathVariable String sessionId) {
        var result = authService.completeCliLogin(sessionId);
        if (result == null) {
            return Map.of("status", "pending");
        }
        return Map.of(
                "status", "ready",
                "token", result.token(),
                "user", result.user()
        );
    }

    @GetMapping("/me")
    public ApiModels.AppUser currentUser(HttpServletRequest request) {
        return (ApiModels.AppUser) request.getAttribute(
                SessionInterceptor.CURRENT_USER_ATTRIBUTE
        );
    }

    @PostMapping("/logout")
    public Map<String, Boolean> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        var user = (ApiModels.AppUser) request.getAttribute(
                SessionInterceptor.CURRENT_USER_ATTRIBUTE
        );
        authService.destroyUserSessions(user.id());
        clearCookie(response, AuthService.SESSION_COOKIE);
        return Map.of("ok", true);
    }

    private void addCookie(
            HttpServletResponse response,
            String name,
            String value,
            Duration maxAge
    ) {
        var cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearCookie(HttpServletResponse response, String name) {
        addCookie(response, name, "", Duration.ZERO);
    }
}
