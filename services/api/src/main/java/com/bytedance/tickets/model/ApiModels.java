package com.bytedance.tickets.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

public final class ApiModels {
    private ApiModels() {
    }

    public record AppUser(
            String id,
            String name,
            String avatarUrl
    ) {
    }

    public record AuthConfig(boolean feishuConfigured) {
    }

    public record TicketItem(
            String id,
            String ticketNo,
            String title,
            String description,
            String status,
            String priority,
            String categoryId,
            String categoryName,
            String reporterUserId,
            String assigneeUserId,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            OffsetDateTime resolvedAt
    ) {
    }

    public record TicketListResponse(
            List<TicketItem> items,
            long total,
            int page,
            int pageSize
    ) {
    }

    public record IssueCategoryItem(
            String id,
            String name,
            String description,
            boolean isActive
    ) {
    }

    public record CreateTicketRequest(
            @NotBlank @Size(min = 2, max = 160) String title,
            @NotBlank @Size(min = 5, max = 5000) String description,
            @NotBlank String categoryId,
            @NotBlank
            @Pattern(regexp = "low|medium|high|urgent")
            String priority
    ) {
        public CreateTicketRequest {
            title = trim(title);
            description = trim(description);
            categoryId = trim(categoryId);
            priority = trim(priority);
        }
    }

    public static final class UpdateTicketRequest {
        @Size(min = 2, max = 160)
        private String title;
        @Size(min = 5, max = 5000)
        private String description;
        private String categoryId;
        @Pattern(regexp = "open|in_progress|resolved|closed")
        private String status;
        @Pattern(regexp = "low|medium|high|urgent")
        private String priority;
        private String assigneeUserId;
        private boolean assigneeUserIdPresent;

        public String title() {
            return title;
        }

        public String description() {
            return description;
        }

        public String categoryId() {
            return categoryId;
        }

        public String status() {
            return status;
        }

        public String priority() {
            return priority;
        }

        public String assigneeUserId() {
            return assigneeUserId;
        }

        public boolean assigneeUserIdPresent() {
            return assigneeUserIdPresent;
        }

        public void setTitle(String value) {
            title = trim(value);
        }

        public void setDescription(String value) {
            description = trim(value);
        }

        public void setCategoryId(String value) {
            categoryId = trim(value);
        }

        public void setStatus(String value) {
            status = trim(value);
        }

        public void setPriority(String value) {
            priority = trim(value);
        }

        public void setAssigneeUserId(String value) {
            assigneeUserId = trimToNull(value);
            assigneeUserIdPresent = true;
        }
    }

    public record CreateIssueCategoryRequest(
            @NotBlank @Size(min = 2, max = 100) String name,
            @Size(max = 500) String description
    ) {
        public CreateIssueCategoryRequest {
            name = trim(name);
            description = trim(description);
        }
    }

    public static final class UpdateIssueCategoryRequest {
        @Size(min = 2, max = 100)
        private String name;
        @Size(max = 500)
        private String description;
        private Boolean isActive;

        public String name() {
            return name;
        }

        public String description() {
            return description;
        }

        public Boolean isActive() {
            return isActive;
        }

        public void setName(String value) {
            name = trim(value);
        }

        public void setDescription(String value) {
            description = trim(value);
        }

        public void setIsActive(Boolean value) {
            isActive = value;
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String trimToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }

}
