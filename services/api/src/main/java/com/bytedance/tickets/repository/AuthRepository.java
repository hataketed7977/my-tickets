package com.bytedance.tickets.repository;

import com.bytedance.tickets.model.ApiModels;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public class AuthRepository {
    private static final String USER_SELECT = """
            SELECT id, name, avatar_url
            FROM app_user
            """;

    private final JdbcTemplate jdbc;

    public AuthRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void deleteExpiredSessions(OffsetDateTime now) {
        jdbc.update("DELETE FROM user_session WHERE expires_at <= ?", now);
    }

    public List<ApiModels.AppUser> listUsers() {
        return jdbc.query(
                USER_SELECT + " ORDER BY name",
                (row, index) -> mapUser(row)
        );
    }

    public ApiModels.AppUser findSessionUser(
            String tokenHash,
            OffsetDateTime now
    ) {
        var users = jdbc.query(
                """
                SELECT u.id, u.name, u.avatar_url
                FROM user_session s
                JOIN app_user u ON u.id = s.user_id
                WHERE s.id_hash = ?
                  AND s.expires_at > ?
                """,
                (row, index) -> mapUser(row),
                tokenHash,
                now
        );
        return users.isEmpty() ? null : users.getFirst();
    }

    public void deleteUserSessions(String userId) {
        jdbc.update("DELETE FROM user_session WHERE user_id = ?", userId);
    }

    public String saveUser(String openId, String name, String avatarUrl) {
        String userId = findUserIdByOpenId(openId);
        if (userId == null) {
            userId = UUID.randomUUID().toString();
            jdbc.update(
                    """
                    INSERT INTO app_user (id, feishu_open_id, name, avatar_url)
                    VALUES (?, ?, ?, ?)
                    """,
                    userId,
                    openId,
                    name,
                    avatarUrl
            );
        } else {
            jdbc.update(
                    """
                    UPDATE app_user
                    SET name = ?, avatar_url = ?
                    WHERE id = ?
                    """,
                    name,
                    avatarUrl,
                    userId
            );
        }
        return userId;
    }

    public void createSession(
            String tokenHash,
            String userId,
            OffsetDateTime expiresAt
    ) {
        jdbc.update(
                """
                INSERT INTO user_session (id_hash, user_id, expires_at)
                VALUES (?, ?, ?)
                """,
                tokenHash,
                userId,
                expiresAt
        );
    }

    private String findUserIdByOpenId(String openId) {
        var ids = jdbc.query(
                "SELECT id FROM app_user WHERE feishu_open_id = ?",
                (row, index) -> row.getString("id"),
                openId
        );
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private ApiModels.AppUser mapUser(ResultSet row) throws SQLException {
        return new ApiModels.AppUser(
                row.getString("id"),
                row.getString("name"),
                row.getString("avatar_url")
        );
    }
}
