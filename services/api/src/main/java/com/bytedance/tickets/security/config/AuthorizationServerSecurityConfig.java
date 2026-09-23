package com.bytedance.tickets.security.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import com.bytedance.tickets.security.MapRegisteredClientRepository;
import com.bytedance.tickets.security.McpOAuthPolicyFilter;
import com.bytedance.tickets.security.SecurityBaseUrls;
import com.bytedance.tickets.security.SessionAuthenticationBridgeFilter;
import com.bytedance.tickets.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springaicommunity.mcp.security.authorizationserver.config.McpAuthorizationServerConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2RefreshTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.ResourceIdentifierAudienceTokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class AuthorizationServerSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerFilterChain(HttpSecurity http, SecurityBaseUrls baseUrls,
                                                        ObjectMapper objectMapper, AuthService authService,
                                                        RequestCache authorizationRequestCache) throws Exception {
        http
                .securityMatcher(
                        "/oauth2/**",
                        "/.well-known/oauth-authorization-server",
                        "/.well-known/openid-configuration"
                )
                .with(McpAuthorizationServerConfigurer.mcpAuthorizationServer(), Customizer.withDefaults())
                .requestCache(cache -> cache.requestCache(authorizationRequestCache))
                .addFilterBefore(new McpOAuthPolicyFilter(baseUrls, objectMapper), SecurityContextHolderFilter.class)
                .addFilterAfter(new SessionAuthenticationBridgeFilter(authService), SecurityContextHolderFilter.class)
                .exceptionHandling(exc -> exc.defaultAuthenticationEntryPointFor(
                        (request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_FOUND);
                            response.setHeader(HttpHeaders.LOCATION, "/api/auth/feishu");
                        },
                        PathPatternRequestMatcher.withDefaults()
                                .matcher(HttpMethod.GET, "/oauth2/authorize")))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
        return http.build();
    }

    @Bean
    RequestCache authorizationRequestCache() {
        // Only the GET authorization request is worth remembering; per-session storage
        // bounds the number of cached requests.
        HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
        requestCache.setRequestMatcher(
                PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/oauth2/authorize"));
        return requestCache;
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder().build();
    }

    @Bean
    RegisteredClientRepository registeredClientRepository() {
        // Starts empty: clients are added through the token-less DCR endpoint.
        return new MapRegisteredClientRepository();
    }

    @Bean
    OAuth2AuthorizationService authorizationService() {
        return new InMemoryOAuth2AuthorizationService();
    }

    @Bean
    KeyPair authorizationServerKeyPair() {
        return generateRsaKey();
    }

    @Bean
    JWKSource<SecurityContext> jwkSource(KeyPair authorizationServerKeyPair) {
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) authorizationServerKeyPair.getPublic())
                .privateKey(authorizationServerKeyPair.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .build();
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    @Bean
    OAuth2TokenGenerator<?> tokenGenerator(JWKSource<SecurityContext> jwkSource) {
        // Publishes the RFC 8707 resource parameter as the JWT audience.
        JwtGenerator jwtGenerator = new JwtGenerator(new NimbusJwtEncoder(jwkSource));
        jwtGenerator.setJwtCustomizer(context -> {
            new ResourceIdentifierAudienceTokenCustomizer().customize(context);
            if (org.springframework.security.oauth2.server.authorization.OAuth2TokenType.ACCESS_TOKEN
                    .equals(context.getTokenType()) && !context.getAuthorizedScopes().isEmpty()) {
                context.getClaims().claim(org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames.SCOPE,
                        String.join(" ", context.getAuthorizedScopes()));
            }
        });
        return new DelegatingOAuth2TokenGenerator(
                jwtGenerator,
                new OAuth2AccessTokenGenerator(),
                new OAuth2RefreshTokenGenerator());
    }

    private static KeyPair generateRsaKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate RSA key pair", ex);
        }
    }
}
