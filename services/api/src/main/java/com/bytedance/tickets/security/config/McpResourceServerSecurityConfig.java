package com.bytedance.tickets.security.config;

import com.bytedance.tickets.security.SecurityBaseUrls;
import org.springaicommunity.mcp.security.server.oauth2.authentication.BearerResourceMetadataTokenAuthenticationEntryPoint;
import org.springaicommunity.mcp.security.server.oauth2.jwt.JwtResourceValidator;
import org.springaicommunity.mcp.security.server.oauth2.metadata.OAuth2ProtectedResourceMetadataEndpointFilter;
import org.springaicommunity.mcp.security.server.oauth2.metadata.ResourceIdentifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;

import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;

@Configuration(proxyBeanMethods = false)
class McpResourceServerSecurityConfig {

    private static final String PROTECTED_RESOURCE_METADATA = "/.well-known/oauth-protected-resource/**";

    private final SecurityBaseUrls baseUrls;
    private final RSAPublicKey jwtVerificationKey;

    McpResourceServerSecurityConfig(SecurityBaseUrls baseUrls, KeyPair authorizationServerKeyPair) {
        this.baseUrls = baseUrls;
        this.jwtVerificationKey = (RSAPublicKey) authorizationServerKeyPair.getPublic();
    }

    @Bean
    @Order(2)
    SecurityFilterChain mcpResourceFilterChain(HttpSecurity http) throws Exception {
        ResourceIdentifier resourceIdentifier = new ResourceIdentifier(SecurityBaseUrls.MCP_PATH);

        OAuth2ProtectedResourceMetadataEndpointFilter protectedResourceMetadataFilter =
                new OAuth2ProtectedResourceMetadataEndpointFilter(resourceIdentifier);
        protectedResourceMetadataFilter.setProtectedResourceMetadataCustomizer(metadata -> metadata
                .authorizationServer(baseUrls.issuer())
                .resourceName("Ticket Center MCP")
                .bearerMethod("header")
                .scope("tickets:read"));

        http
                .securityMatcher(SecurityBaseUrls.MCP_PATH, PROTECTED_RESOURCE_METADATA)
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PROTECTED_RESOURCE_METADATA).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.decoder(lazyMcpJwtDecoder(resourceIdentifier)))
                        .authenticationEntryPoint(
                                new BearerResourceMetadataTokenAuthenticationEntryPoint(resourceIdentifier)))
                .addFilterBefore(protectedResourceMetadataFilter, AbstractPreAuthenticatedProcessingFilter.class);
        return http.build();
    }

    JwtDecoder lazyMcpJwtDecoder(ResourceIdentifier resourceIdentifier) {
        // The issuer can depend on the embedded server port, so validators are still
        // created lazily. Signature verification uses the colocated authorization
        // server's public key and does not make an HTTPS request back through ingress.
        java.util.concurrent.atomic.AtomicReference<JwtDecoder> delegate = new java.util.concurrent.atomic.AtomicReference<>();
        return token -> {
            JwtDecoder decoder = delegate.get();
            if (decoder == null) {
                synchronized (delegate) {
                    decoder = delegate.get();
                    if (decoder == null) {
                        decoder = buildMcpJwtDecoder(baseUrls.issuer(), resourceIdentifier);
                        delegate.set(decoder);
                    }
                }
            }
            return decoder.decode(token);
        };
    }

    private JwtDecoder buildMcpJwtDecoder(String issuer, ResourceIdentifier resourceIdentifier) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(jwtVerificationKey).build();
        OAuth2TokenValidator<Jwt> validator = JwtValidators.createDefaultWithValidators(
                new JwtIssuerValidator(issuer),
                new JwtResourceValidator(resourceIdentifier));
        decoder.setJwtValidator(validator);
        return decoder;
    }
}
