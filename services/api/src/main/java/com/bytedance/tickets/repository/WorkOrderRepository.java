package com.bytedance.tickets.repository;

import com.bytedance.tickets.model.ApiModels;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class WorkOrderRepository {
    private static final String TICKET_SELECT = """
            SELECT
              t.id,
              t.ticket_no,
              t.title,
              t.description,
              t.status,
              t.priority,
              t.category_id,
              c.name AS category_name,
              t.reporter_user_id,
              t.assignee_user_id,
              t.created_at,
              t.updated_at,
              t.resolved_at
            FROM ticket t
            JOIN issue_category c ON c.id = t.category_id
            """;

    private static final String CATEGORY_SELECT = """
            SELECT id, name, description, is_active
            FROM issue_category
            """;

    private final JdbcTemplate jdbc;

    public WorkOrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ApiModels.TicketListResponse listTickets(
            int page,
            int pageSize,
            String searchQuery,
            String status,
            String priority,
            String categoryId
    ) {
        var conditions = new ArrayList<String>();
        var parameters = new ArrayList<Object>();

        if (hasText(searchQuery)) {
            conditions.add(
                    "(LOWER(t.title) LIKE LOWER(?) OR t.ticket_no LIKE ?)"
            );
            String search = "%" + searchQuery + "%";
            parameters.add(search);
            parameters.add(search);
        }
        addFilter(conditions, parameters, "t.status = ?", status);
        addFilter(conditions, parameters, "t.priority = ?", priority);
        addFilter(conditions, parameters, "t.category_id = ?", categoryId);

        String where = conditions.isEmpty()
                ? ""
                : " WHERE " + String.join(" AND ", conditions);
        var listParameters = new ArrayList<>(parameters);
        listParameters.add(pageSize);
        listParameters.add((page - 1) * pageSize);
        var items = jdbc.query(
                TICKET_SELECT + where
                        + " ORDER BY t.updated_at DESC LIMIT ? OFFSET ?",
                (row, index) -> mapTicket(row),
                listParameters.toArray()
        );
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ticket t" + where,
                Long.class,
                parameters.toArray()
        );
        return new ApiModels.TicketListResponse(
                items,
                total == null ? 0 : total,
                page,
                pageSize
        );
    }

    public ApiModels.TicketItem findTicket(String id) {
        var tickets = jdbc.query(
                TICKET_SELECT + " WHERE t.id = ?",
                (row, index) -> mapTicket(row),
                id
        );
        return tickets.isEmpty() ? null : tickets.getFirst();
    }

    public void createTicket(
            String id,
            String ticketNumber,
            ApiModels.CreateTicketRequest request,
            String reporterUserId
    ) {
        jdbc.update(
                """
                INSERT INTO ticket (
                  id, ticket_no, title, description, priority, category_id,
                  reporter_user_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                id,
                ticketNumber,
                request.title().trim(),
                request.description().trim(),
                request.priority(),
                request.categoryId(),
                reporterUserId
        );
    }

    public void updateTicket(
            String id,
            ApiModels.UpdateTicketRequest request,
            OffsetDateTime resolvedAt
    ) {
        var updates = new ArrayList<String>();
        var parameters = new ArrayList<Object>();
        addUpdate(updates, parameters, "title", trim(request.title()));
        addUpdate(
                updates,
                parameters,
                "description",
                trim(request.description())
        );
        addUpdate(updates, parameters, "priority", request.priority());
        addUpdate(updates, parameters, "status", request.status());
        if (request.assigneeUserIdPresent()) {
            addNullableUpdate(
                    updates,
                    parameters,
                    "assignee_user_id",
                    request.assigneeUserId()
            );
        }
        if (request.status() != null) {
            addNullableUpdate(
                    updates,
                    parameters,
                    "resolved_at",
                    resolvedAt
            );
        }
        addUpdate(updates, parameters, "category_id", request.categoryId());

        if (updates.isEmpty()) {
            return;
        }
        updates.add("updated_at = CURRENT_TIMESTAMP");
        parameters.add(id);
        jdbc.update(
                "UPDATE ticket SET "
                        + String.join(", ", updates)
                        + " WHERE id = ?",
                parameters.toArray()
        );
    }

    public void deleteTicket(String id) {
        jdbc.update("DELETE FROM ticket WHERE id = ?", id);
    }

    public List<ApiModels.IssueCategoryItem> listCategories() {
        return jdbc.query(
                CATEGORY_SELECT + " ORDER BY name",
                (row, index) -> mapCategory(row)
        );
    }

    public ApiModels.IssueCategoryItem findCategory(String id) {
        var categories = jdbc.query(
                CATEGORY_SELECT + " WHERE id = ?",
                (row, index) -> mapCategory(row),
                id
        );
        return categories.isEmpty() ? null : categories.getFirst();
    }

    public void createCategory(
            String id,
            ApiModels.CreateIssueCategoryRequest request
    ) {
        jdbc.update(
                """
                INSERT INTO issue_category (id, name, description)
                VALUES (?, ?, ?)
                """,
                id,
                request.name().trim(),
                trimOrEmpty(request.description())
        );
    }

    public void updateCategory(
            String id,
            ApiModels.UpdateIssueCategoryRequest request
    ) {
        var updates = new ArrayList<String>();
        var parameters = new ArrayList<Object>();
        addUpdate(updates, parameters, "name", trim(request.name()));
        addUpdate(
                updates,
                parameters,
                "description",
                trim(request.description())
        );
        addUpdate(updates, parameters, "is_active", request.isActive());

        if (updates.isEmpty()) {
            return;
        }
        parameters.add(id);
        jdbc.update(
                "UPDATE issue_category SET "
                        + String.join(", ", updates)
                        + " WHERE id = ?",
                parameters.toArray()
        );
    }

    public void deleteCategory(String id) {
        jdbc.update("DELETE FROM issue_category WHERE id = ?", id);
    }

    public boolean activeCategoryExists(String id) {
        return count(
                """
                SELECT COUNT(*) FROM issue_category
                WHERE id = ? AND is_active = TRUE
                """,
                id
        ) > 0;
    }

    public boolean userExists(String id) {
        return count("SELECT COUNT(*) FROM app_user WHERE id = ?", id) > 0;
    }

    private long count(String sql, String value) {
        Long count = jdbc.queryForObject(sql, Long.class, value);
        return count == null ? 0 : count;
    }

    private ApiModels.TicketItem mapTicket(ResultSet row)
            throws SQLException {
        return new ApiModels.TicketItem(
                row.getString("id"),
                row.getString("ticket_no"),
                row.getString("title"),
                row.getString("description"),
                row.getString("status"),
                row.getString("priority"),
                row.getString("category_id"),
                row.getString("category_name"),
                row.getString("reporter_user_id"),
                row.getString("assignee_user_id"),
                timestamp(row, "created_at"),
                timestamp(row, "updated_at"),
                timestamp(row, "resolved_at")
        );
    }

    private ApiModels.IssueCategoryItem mapCategory(ResultSet row)
            throws SQLException {
        return new ApiModels.IssueCategoryItem(
                row.getString("id"),
                row.getString("name"),
                row.getString("description"),
                row.getBoolean("is_active")
        );
    }

    private OffsetDateTime timestamp(ResultSet row, String column)
            throws SQLException {
        return row.getObject(column, OffsetDateTime.class);
    }

    private void addFilter(
            List<String> conditions,
            List<Object> parameters,
            String condition,
            String value
    ) {
        if (hasText(value)) {
            conditions.add(condition);
            parameters.add(value);
        }
    }

    private void addUpdate(
            List<String> updates,
            List<Object> parameters,
            String column,
            Object value
    ) {
        if (value != null) {
            addNullableUpdate(updates, parameters, column, value);
        }
    }

    private void addNullableUpdate(
            List<String> updates,
            List<Object> parameters,
            String column,
            Object value
    ) {
        updates.add(column + " = ?");
        parameters.add(value);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String trimOrEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
