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
        "app.feishu.app-id=",
        "app.feishu.app-secret=",
        "app.feishu.redirect-uri=",
        "spring.datasource.url=jdbc:h2:mem:tickets-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
})
@AutoConfigureMockMvc
class WorkOrderApiTest {
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
                .andExpect(jsonPath("$.feishuConfigured").value(false));

        mockMvc.perform(
                        post("/api/auth/local-login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"userId\":\"test-reporter\"}")
                )
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/work-orders/tickets"))
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
                        post("/api/work-orders/tickets")
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
                        get("/api/work-orders/tickets/{id}", ticketId)
                                .cookie(sessionCookie)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ticketId));

        mockMvc.perform(
                        patch("/api/work-orders/tickets/{id}", ticketId)
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
                        delete("/api/work-orders/tickets/{id}", ticketId)
                                .cookie(sessionCookie)
                )
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        get("/api/work-orders/tickets/{id}", ticketId)
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
                        post("/api/work-orders/categories")
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
                        patch("/api/work-orders/categories/{id}", categoryId)
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
                        delete("/api/work-orders/categories/{id}", categoryId)
                                .cookie(sessionCookie)
                )
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        delete("/api/work-orders/categories/{id}", categoryId)
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
                        post("/api/work-orders/tickets")
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
                        delete("/api/work-orders/categories/{id}", categoryId)
                                .cookie(sessionCookie)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(
                        "该分类下仍有工单，无法删除"
                ));
    }

    @Test
    void removedModulesAreNotExposed() throws Exception {
        var sessionCookie = new Cookie(
                AuthService.SESSION_COOKIE,
                TEST_TOKEN
        );

        mockMvc.perform(
                        get("/api/work-orders/overview")
                                .cookie(sessionCookie)
                )
                .andExpect(status().isNotFound());

        mockMvc.perform(
                        get("/api/work-orders/groups")
                                .cookie(sessionCookie)
                )
                .andExpect(status().isNotFound());
    }
}
