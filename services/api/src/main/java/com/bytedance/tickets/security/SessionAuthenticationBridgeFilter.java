package com.bytedance.tickets.security;

import com.bytedance.tickets.model.ApiModels;
import com.bytedance.tickets.service.AuthService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Bridges an existing application session (created by the Feishu login) into the Spring
 * Security context used by the authorization server. The authenticated principal name is
 * the internal {@code app_user.id}; it is only registered on the authorization-server
 * filter chain.
 */
public final class SessionAuthenticationBridgeFilter extends OncePerRequestFilter {

    private static final List<SimpleGrantedAuthority> AUTHORITIES =
            List.of(new SimpleGrantedAuthority("ROLE_USER"));

    private final AuthService authService;

    public SessionAuthenticationBridgeFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            ApiModels.AppUser user = authService.getSessionUser(sessionToken(request));
            if (user != null) {
                var authentication = UsernamePasswordAuthenticationToken.authenticated(
                        user.id(), null, AUTHORITIES);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }
        chain.doFilter(request, response);
    }

    private String sessionToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (AuthService.SESSION_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
