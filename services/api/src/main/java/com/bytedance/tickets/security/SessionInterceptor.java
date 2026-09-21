package com.bytedance.tickets.security;

import com.bytedance.tickets.exception.ApiException;
import com.bytedance.tickets.service.AuthService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

@Component
public final class SessionInterceptor implements HandlerInterceptor {
    public static final String CURRENT_USER_ATTRIBUTE = "currentUser";

    private final AuthService authService;

    public SessionInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }
        var token = cookieValue(request, AuthService.SESSION_COOKIE);
        if (token == null) {
            token = bearerToken(request);
        }
        var user = authService.getSessionUser(token);
        if (user == null) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "请先登录"
            );
        }
        request.setAttribute(CURRENT_USER_ATTRIBUTE, user);
        return true;
    }

    private String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private String bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7).trim();
        }
        return null;
    }
}
