package com.bytedance.tickets.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Resolves the canonical issuer origin and MCP resource URI. When a public base URL is
 * configured it is used as-is; otherwise the origin is derived from the running embedded
 * server, which keeps tests working with a RANDOM_PORT.
 */
@Component
public final class SecurityBaseUrls {

    public static final String MCP_PATH = "/mcp";

    private final String configuredBaseUrl;
    private final Environment environment;

    public SecurityBaseUrls(
            @Value("${app.security.public-base-url:}") String configuredBaseUrl,
            Environment environment
    ) {
        this.configuredBaseUrl = configuredBaseUrl;
        this.environment = environment;
    }

    public String origin() {
        if (StringUtils.hasText(configuredBaseUrl)) {
            return configuredBaseUrl.replaceAll("/+$", "");
        }
        String local = environment.getProperty("local.server.port");
        if (StringUtils.hasText(local)) {
            return "http://localhost:" + local;
        }
        return "http://localhost:" + environment.getProperty("server.port", "8080");
    }

    public String issuer() {
        return origin();
    }

    public String mcpResource() {
        return origin() + MCP_PATH;
    }
}
