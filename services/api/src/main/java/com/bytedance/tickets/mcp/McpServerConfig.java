package com.bytedance.tickets.mcp;

import com.bytedance.tickets.exception.ApiException;
import com.bytedance.tickets.model.ApiModels;
import com.bytedance.tickets.security.SessionInterceptor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.handler.MappedInterceptor;

@Configuration(proxyBeanMethods = false)
class McpServerConfig {

    @Bean
    MappedInterceptor mcpSessionInterceptor(SessionInterceptor sessionInterceptor) {
        return new MappedInterceptor(new String[] {"/mcp"}, sessionInterceptor);
    }

    @Bean
    ToolCallback getCurrentUserTool() {
        return FunctionToolCallback.builder("get_current_user", () -> {
                    var request = ((ServletRequestAttributes) RequestContextHolder
                            .currentRequestAttributes())
                            .getRequest();
                    var user = (ApiModels.AppUser) request.getAttribute(
                            SessionInterceptor.CURRENT_USER_ATTRIBUTE
                    );
                    if (user == null) {
                        throw new ApiException(HttpStatus.UNAUTHORIZED, "请先登录");
                    }
                    return user;
                })
                .description("获取当前登录用户的 ID、姓名和头像。")
                .build();
    }
}
