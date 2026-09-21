package com.bytedance.tickets;

import com.bytedance.tickets.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.feishu.app-id=cli_test_app",
        "app.feishu.app-secret=test_secret",
        "app.feishu.redirect-uri=http://localhost:55888/api/auth/feishu/callback",
        "spring.datasource.url=jdbc:h2:mem:tickets-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
})
@AutoConfigureMockMvc
class TicketApiTest {
    private static final String TEST_TOKEN = "test-session-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUpSession() {
        jdbc.update("DELETE FROM ticket");
        jdbc.update("DELETE FROM user_session");
        jdbc.update("DELETE FROM app_user");
        jdbc.update("UPDATE issue_category SET is_active = TRUE");
        jdbc.update(
                """
                INSERT INTO app_user (
                  id, feishu_open_id, name
                ) VALUES (
                  'test-reporter', 'test-open-id', '测试提交人'
                )
                """
        );
        jdbc.update(
                """
                INSERT INTO user_session (id_hash, user_id, expires_at)
                VALUES (?, 'test-reporter', DATEADD('DAY', 1, CURRENT_TIMESTAMP))
                """,
                AuthService.hashToken(TEST_TOKEN)
        );
    }

    @Test
    void exposesOnlyFeishuAuthentication() throws Exception {
        mockMvc.perform(get("/api/auth/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.feishuConfigured").value(true));

        mockMvc.perform(
                        post("/api/auth/local-login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"userId\":\"test-reporter\"}")
                )
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/tickets"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.message").value("请先登录"));
    }

    @Test
    void supportsTicketCrud() throws Exception {
        var sessionCookie = new Cookie(
                AuthService.SESSION_COOKIE,
                TEST_TOKEN
        );
        String categoryId = "52222222-2222-4222-8222-222222222222";

        var createResult = mockMvc.perform(
                        post("/api/tickets")
                                .cookie(sessionCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "title": "验证工单创建",
                                          "description": "确认工单可以正常创建。",
                                          "categoryId": "%s",
                                          "priority": "high"
                                        }
                                        """.formatted(categoryId))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryName").value("账号与权限"))
                .andExpect(jsonPath("$.title").value("验证工单创建"))
                .andReturn();

        String ticketId = objectMapper.readTree(
                createResult.getResponse().getContentAsString()
        ).get("id").asText();

        mockMvc.perform(
                        get("/api/tickets/{id}", ticketId)
                                .cookie(sessionCookie)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticketId));

        mockMvc.perform(
                        patch("/api/tickets/{id}", ticketId)
                                .cookie(sessionCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "title": "验证工单修改",
                                          "description": "确认工单内容可以正常修改。",
                                          "status": "in_progress",
                                          "priority": "urgent"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("验证工单修改"))
                .andExpect(jsonPath("$.status").value("in_progress"))
                .andExpect(jsonPath("$.priority").value("urgent"));

        mockMvc.perform(
                        delete("/api/tickets/{id}", ticketId)
                                .cookie(sessionCookie)
                )
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        get("/api/tickets/{id}", ticketId)
                                .cookie(sessionCookie)
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void supportsCategoryCrud() throws Exception {
        var sessionCookie = new Cookie(
                AuthService.SESSION_COOKIE,
                TEST_TOKEN
        );

        var createResult = mockMvc.perform(
                        post("/api/categories")
                                .cookie(sessionCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "打印服务",
                                          "description": "处理打印相关问题"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("打印服务"))
                .andReturn();

        String categoryId = objectMapper.readTree(
                createResult.getResponse().getContentAsString()
        ).get("id").asText();

        mockMvc.perform(
                        patch("/api/categories/{id}", categoryId)
                                .cookie(sessionCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "打印机服务",
                                          "description": "处理打印机相关问题"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("打印机服务"));

        mockMvc.perform(
                        delete("/api/categories/{id}", categoryId)
                                .cookie(sessionCookie)
                )
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        delete("/api/categories/{id}", categoryId)
                                .cookie(sessionCookie)
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsDeletingCategoryUsedByTicket() throws Exception {
        var sessionCookie = new Cookie(
                AuthService.SESSION_COOKIE,
                TEST_TOKEN
        );
        String categoryId = "52222222-2222-4222-8222-222222222222";

        mockMvc.perform(
                        post("/api/tickets")
                                .cookie(sessionCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "title": "分类删除保护",
                                          "description": "确认已使用分类不会被删除。",
                                          "categoryId": "%s",
                                          "priority": "medium"
                                        }
                                        """.formatted(categoryId))
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        delete("/api/categories/{id}", categoryId)
                                .cookie(sessionCookie)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(
                        "该分类下仍有工单，无法删除"
                ));
    }

    @Test
    void allowsEditingTicketWithItsInactiveCategory() throws Exception {
        var sessionCookie = new Cookie(
                AuthService.SESSION_COOKIE,
                TEST_TOKEN
        );
        String categoryId = "52222222-2222-4222-8222-222222222222";
        String ticketId = createTicket(sessionCookie, categoryId);

        mockMvc.perform(
                        patch("/api/categories/{id}", categoryId)
                                .cookie(sessionCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"isActive\":false}")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false));

        mockMvc.perform(
                        patch("/api/tickets/{id}", ticketId)
                                .cookie(sessionCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "title": "停用分类下仍可编辑",
                                          "categoryId": "%s"
                                        }
                                        """.formatted(categoryId))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("停用分类下仍可编辑"))
                .andExpect(jsonPath("$.categoryId").value(categoryId));
    }

    @Test
    void preservesResolvedAtWhenTerminalTicketIsEdited() throws Exception {
        var sessionCookie = new Cookie(
                AuthService.SESSION_COOKIE,
                TEST_TOKEN
        );
        String categoryId = "52222222-2222-4222-8222-222222222222";
        String ticketId = createTicket(sessionCookie, categoryId);

        mockMvc.perform(
                        patch("/api/tickets/{id}", ticketId)
                                .cookie(sessionCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"resolved\"}")
                )
                .andExpect(status().isOk());

        String resolvedAt = "2024-01-02T03:04:05Z";
        jdbc.update(
                "UPDATE ticket SET resolved_at = ? WHERE id = ?",
                java.time.OffsetDateTime.parse(resolvedAt),
                ticketId
        );

        mockMvc.perform(
                        patch("/api/tickets/{id}", ticketId)
                                .cookie(sessionCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "title": "终态工单内容调整",
                                          "status": "resolved"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resolvedAt").value(resolvedAt));
    }

    @Test
    void validatesNormalizedTextFields() throws Exception {
        var sessionCookie = new Cookie(
                AuthService.SESSION_COOKIE,
                TEST_TOKEN
        );
        String categoryId = "52222222-2222-4222-8222-222222222222";

        mockMvc.perform(
                        post("/api/tickets")
                                .cookie(sessionCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "title": " a ",
                                          "description": "这是有效的问题描述",
                                          "categoryId": "%s",
                                          "priority": "medium"
                                        }
                                        """.formatted(categoryId))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors.title").exists());

        mockMvc.perform(
                        patch("/api/categories/{id}", categoryId)
                                .cookie(sessionCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"  \"}")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors.name").exists());
    }

    @Test
    void removedModulesAreNotExposed() throws Exception {
        var sessionCookie = new Cookie(
                AuthService.SESSION_COOKIE,
                TEST_TOKEN
        );

        mockMvc.perform(
                        get("/api/overview")
                                .cookie(sessionCookie)
                )
                .andExpect(status().isNotFound());

        mockMvc.perform(
                        get("/api/groups")
                                .cookie(sessionCookie)
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void supportsBearerTokenAuthentication() throws Exception {
        mockMvc.perform(
                        get("/api/auth/me")
                                .header("Authorization", "Bearer " + TEST_TOKEN)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("test-reporter"));

        mockMvc.perform(
                        get("/api/auth/me")
                                .header("Authorization", "Bearer invalid-token")
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.message").value("请先登录"));
    }

    @Test
    void supportsCliSessionLoginFlow() throws Exception {
        var createResult = mockMvc.perform(post("/api/auth/cli/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").exists())
                .andExpect(jsonPath("$.authorizeUrl").exists())
                .andReturn();

        String sessionId = objectMapper.readTree(
                createResult.getResponse().getContentAsString()
        ).get("sessionId").asText();

        mockMvc.perform(get("/api/auth/cli/session/{id}", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pending"));

        jdbc.update(
                """
                INSERT INTO user_session (id_hash, user_id, expires_at, oauth_state)
                VALUES (?, 'test-reporter', DATEADD('DAY', 1, CURRENT_TIMESTAMP), ?)
                """,
                AuthService.hashToken("cli-callback-token"),
                sessionId
        );

        var readyResult = mockMvc.perform(
                        get("/api/auth/cli/session/{id}", sessionId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ready"))
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.user.id").value("test-reporter"))
                .andReturn();

        String issuedToken = objectMapper.readTree(
                readyResult.getResponse().getContentAsString()
        ).get("token").asText();

        mockMvc.perform(get("/api/auth/cli/session/{id}", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pending"));

        mockMvc.perform(
                        get("/api/auth/me")
                                .header("Authorization", "Bearer " + issuedToken)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("test-reporter"));
    }

    private String createTicket(Cookie sessionCookie, String categoryId)
            throws Exception {
        var result = mockMvc.perform(
                        post("/api/tickets")
                                .cookie(sessionCookie)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "title": "测试工单",
                                          "description": "用于验证工单更新行为。",
                                          "categoryId": "%s",
                                          "priority": "medium"
                                        }
                                        """.formatted(categoryId))
                )
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(
                result.getResponse().getContentAsString()
        ).get("id").asText();
    }
}
