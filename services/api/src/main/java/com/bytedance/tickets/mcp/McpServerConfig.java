package com.bytedance.tickets.mcp;

import com.bytedance.tickets.exception.ApiException;
import com.bytedance.tickets.model.ApiModels;
import com.bytedance.tickets.repository.AuthRepository;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Configuration(proxyBeanMethods = false)
class McpServerConfig {

    @Bean
    ToolCallback getCurrentUserTool(AuthRepository authRepository) {
        return FunctionToolCallback.builder("get_current_user", () -> {
                    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                    String userId = authentication == null ? null : authentication.getName();
                    ApiModels.AppUser user = userId == null ? null : authRepository.findById(userId);
                    if (user == null) {
                        throw new ApiException(HttpStatus.UNAUTHORIZED, "请先登录");
                    }
                    return user;
                })
                .description("获取当前登录用户的 ID、姓名和头像。")
                .build();
    }
}
