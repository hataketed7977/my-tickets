package com.bytedance.tickets.config;

import com.bytedance.tickets.security.SessionInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public final class WebConfig implements WebMvcConfigurer {
    private final SessionInterceptor sessionInterceptor;
    private final String clientOrigin;

    public WebConfig(
            SessionInterceptor sessionInterceptor,
            @Value("${app.client-origin}") String clientOrigin
    ) {
        this.sessionInterceptor = sessionInterceptor;
        this.clientOrigin = clientOrigin;
    }

    @Override
    public void addInterceptors(
            org.springframework.web.servlet.config.annotation.InterceptorRegistry registry
    ) {
        registry.addInterceptor(sessionInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/config",
                        "/api/auth/feishu",
                        "/api/auth/feishu/callback",
                        "/api/auth/local-login"
                );
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(clientOrigin)
                .allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
