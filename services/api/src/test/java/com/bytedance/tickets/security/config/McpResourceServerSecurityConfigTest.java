package com.bytedance.tickets.security.config;

import com.bytedance.tickets.security.SecurityBaseUrls;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springaicommunity.mcp.security.server.oauth2.metadata.ResourceIdentifier;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class McpResourceServerSecurityConfigTest {

    @Test
    void decodesWithLocalSigningKeyWithoutFetchingPublicJwks() throws Exception {
        var keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        var keyPair = keyPairGenerator.generateKeyPair();
        var baseUrls = new SecurityBaseUrls(
                "https://unreachable.invalid",
                new MockEnvironment());
        var config = new McpResourceServerSecurityConfig(baseUrls, keyPair);
        var decoder = config.lazyMcpJwtDecoder(new ResourceIdentifier("/mcp"));

        Instant now = Instant.now();
        var claims = new JWTClaimsSet.Builder()
                .issuer("https://unreachable.invalid")
                .subject("user-1")
                .audience("https://unreachable.invalid/mcp")
                .claim("scope", "tickets:read")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(300)))
                .build();
        var token = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        token.sign(new RSASSASigner((RSAPrivateKey) keyPair.getPrivate()));

        var request = new MockHttpServletRequest();
        request.setScheme("https");
        request.setServerName("unreachable.invalid");
        request.setServerPort(443);
        request.setRequestURI("/mcp");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        try {
            assertThat(decoder.decode(token.serialize()).getSubject()).isEqualTo("user-1");
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }
}
