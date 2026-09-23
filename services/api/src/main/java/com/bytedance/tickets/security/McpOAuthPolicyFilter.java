package com.bytedance.tickets.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Enforces MCP-specific OAuth policy on the authorization-server endpoints: a strict
 * allowlist for DCR metadata and RFC 8707 canonical resource validation on the
 * authorization and token requests. The request body is buffered and replayed so the
 * downstream filters can read it.
 */
public final class McpOAuthPolicyFilter extends OncePerRequestFilter {

    private static final String REGISTER_PATH = "/oauth2/register";
    private static final String AUTHORIZE_PATH = "/oauth2/authorize";
    private static final String TOKEN_PATH = "/oauth2/token";

    private static final Set<String> ALLOWED_GRANT_TYPES = Set.of("authorization_code", "refresh_token");
    private static final Set<String> ALLOWED_RESPONSE_TYPES = Set.of("code");
    private static final Set<String> ALLOWED_SCOPES = Set.of("tickets:read");
    private static final Set<String> ALLOWED_AUTH_METHODS =
            Set.of("none", "client_secret_post", "client_secret_basic");

    private final SecurityBaseUrls baseUrls;
    private final ObjectMapper objectMapper;

    public McpOAuthPolicyFilter(SecurityBaseUrls baseUrls, ObjectMapper objectMapper) {
        this.baseUrls = baseUrls;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();

        if (HttpMethod.POST.matches(request.getMethod()) && REGISTER_PATH.equals(path)) {
            byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
            if (!isValidRegistration(body)) {
                writeError(response, "invalid_client_metadata");
                return;
            }
            chain.doFilter(new CachedBodyRequest(request, body), response);
            return;
        }

        if (HttpMethod.GET.matches(request.getMethod()) && AUTHORIZE_PATH.equals(path)) {
            String resource = request.getParameter("resource");
            if (resource != null && !resource.equals(baseUrls.mcpResource())) {
                writeError(response, "invalid_target");
                return;
            }
        }

        if (HttpMethod.POST.matches(request.getMethod()) && TOKEN_PATH.equals(path)) {
            byte[] body = StreamUtils.copyToByteArray(request.getInputStream());
            String resource = formParameter(body, "resource");
            if (resource != null && !resource.equals(baseUrls.mcpResource())) {
                writeError(response, "invalid_target");
                return;
            }
            chain.doFilter(new CachedBodyRequest(request, body), response);
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isValidRegistration(byte[] body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            if (!node.isObject()) {
                return true;
            }
            JsonNode grantTypes = node.get("grant_types");
            if (grantTypes != null && !validateAllowlist(grantTypes, ALLOWED_GRANT_TYPES, "authorization_code")) {
                return false;
            }
            JsonNode responseTypes = node.get("response_types");
            if (responseTypes != null && !validateAllowlist(responseTypes, ALLOWED_RESPONSE_TYPES, "code")) {
                return false;
            }
            JsonNode scope = node.get("scope");
            if (scope != null) {
                String[] scopes = scope.asText().trim().split("\\s+");
                for (String value : scopes) {
                    if (!ALLOWED_SCOPES.contains(value)) {
                        return false;
                    }
                }
            }
            JsonNode authMethod = node.get("token_endpoint_auth_method");
            if (authMethod != null && !ALLOWED_AUTH_METHODS.contains(authMethod.asText())) {
                return false;
            }
            return true;
        } catch (IOException ex) {
            // Let the downstream DCR filter report malformed JSON itself.
            return true;
        }
    }

    private boolean validateAllowlist(JsonNode array, Set<String> allowed, String requiredValue) {
        if (!array.isArray() || array.isEmpty()) {
            return false;
        }
        boolean hasRequired = false;
        for (JsonNode value : array) {
            String text = value.asText();
            if (!allowed.contains(text)) {
                return false;
            }
            if (requiredValue.equals(text)) {
                hasRequired = true;
            }
        }
        return hasRequired;
    }

    private String formParameter(byte[] body, String name) {
        String raw = new String(body, StandardCharsets.UTF_8);
        for (String pair : raw.split("&")) {
            int idx = pair.indexOf('=');
            String key = idx < 0 ? pair : pair.substring(0, idx);
            if (URLDecoder.decode(key, StandardCharsets.UTF_8).equals(name)) {
                String value = idx < 0 ? "" : pair.substring(idx + 1);
                return URLDecoder.decode(value, StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private void writeError(HttpServletResponse response, String errorCode) throws IOException {
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), Map.of("error", errorCode));
    }

    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;
        private Map<String, List<String>> parameters;

        private CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public java.io.BufferedReader getReader() {
            return new java.io.BufferedReader(new InputStreamReader(new ByteArrayInputStream(body),
                    StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }

        @Override
        public String getParameter(String name) {
            List<String> values = parsedParameters().get(name);
            return values == null || values.isEmpty() ? null : values.get(0);
        }

        @Override
        public String[] getParameterValues(String name) {
            List<String> values = parsedParameters().get(name);
            return values == null ? null : values.toArray(String[]::new);
        }

        @Override
        public java.util.Enumeration<String> getParameterNames() {
            return Collections.enumeration(parsedParameters().keySet());
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            Map<String, String[]> result = new LinkedHashMap<>();
            parsedParameters().forEach((name, values) -> result.put(name, values.toArray(String[]::new)));
            return Collections.unmodifiableMap(result);
        }

        private Map<String, List<String>> parsedParameters() {
            if (parameters == null) {
                Map<String, List<String>> parsed = new LinkedHashMap<>();
                addPairs(super.getQueryString(), parsed);
                addPairs(new String(body, StandardCharsets.UTF_8), parsed);
                parameters = parsed;
            }
            return parameters;
        }

        private void addPairs(String raw, Map<String, List<String>> target) {
            if (raw == null || raw.isEmpty()) {
                return;
            }
            for (String pair : raw.split("&")) {
                int idx = pair.indexOf('=');
                String key = URLDecoder.decode(idx < 0 ? pair : pair.substring(0, idx), StandardCharsets.UTF_8);
                String value = URLDecoder.decode(idx < 0 ? "" : pair.substring(idx + 1), StandardCharsets.UTF_8);
                target.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
            }
        }
    }
}
