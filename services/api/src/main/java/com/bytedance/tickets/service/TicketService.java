package com.bytedance.tickets.service;

import com.bytedance.tickets.exception.ApiException;
import com.bytedance.tickets.model.ApiModels;
import com.bytedance.tickets.repository.TicketRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public final class TicketService {
    private final TicketRepository repository;

    public TicketService(TicketRepository repository) {
        this.repository = repository;
    }

    public ApiModels.TicketListResponse listTickets(
            int requestedPage,
            int requestedPageSize,
            String search,
            String status,
            String priority,
            String categoryId
    ) {
        int page = Math.max(requestedPage, 1);
        int pageSize = Math.min(Math.max(requestedPageSize, 1), 100);
        return repository.listTickets(
                page,
                pageSize,
                search,
                status,
                priority,
                categoryId
        );
    }

    public ApiModels.TicketItem getTicket(String id) {
        var ticket = repository.findTicket(id);
        if (ticket == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "工单不存在");
        }
        return ticket;
    }

    public ApiModels.TicketItem createTicket(
            ApiModels.CreateTicketRequest request,
            String reporterUserId
    ) {
        assertActiveCategory(request.categoryId());
        String id = UUID.randomUUID().toString();
        repository.createTicket(
                id,
                createTicketNumber(),
                request,
                reporterUserId
        );
        return getTicket(id);
    }

    public ApiModels.TicketItem updateTicket(
            String id,
            ApiModels.UpdateTicketRequest request
    ) {
        var existing = getTicket(id);
        if (request.categoryId() != null
                && !request.categoryId().equals(existing.categoryId())) {
            assertActiveCategory(request.categoryId());
        }
        if (request.assigneeUserIdPresent()) {
            assertUserExists(request.assigneeUserId());
        }

        OffsetDateTime resolvedAt = existing.resolvedAt();
        if (request.status() != null
                && !request.status().equals(existing.status())) {
            resolvedAt = isTerminalStatus(request.status())
                    ? OffsetDateTime.now(ZoneOffset.UTC)
                    : null;
        }
        repository.updateTicket(id, request, resolvedAt);
        return getTicket(id);
    }

    public void deleteTicket(String id) {
        getTicket(id);
        repository.deleteTicket(id);
    }

    public List<ApiModels.IssueCategoryItem> listCategories() {
        return repository.listCategories();
    }

    public ApiModels.IssueCategoryItem createCategory(
            ApiModels.CreateIssueCategoryRequest request
    ) {
        String id = UUID.randomUUID().toString();
        try {
            repository.createCategory(id, request);
        } catch (DuplicateKeyException error) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "问题分类名称已存在"
            );
        }
        return getCategory(id);
    }

    public ApiModels.IssueCategoryItem updateCategory(
            String id,
            ApiModels.UpdateIssueCategoryRequest request
    ) {
        getCategory(id);
        try {
            repository.updateCategory(id, request);
        } catch (DuplicateKeyException error) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "问题分类名称已存在"
            );
        }
        return getCategory(id);
    }

    public void deleteCategory(String id) {
        getCategory(id);
        try {
            repository.deleteCategory(id);
        } catch (DataIntegrityViolationException error) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "该分类下仍有工单，无法删除"
            );
        }
    }

    private ApiModels.IssueCategoryItem getCategory(String id) {
        var category = repository.findCategory(id);
        if (category == null) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "问题分类不存在"
            );
        }
        return category;
    }

    private void assertActiveCategory(String id) {
        if (!repository.activeCategoryExists(id)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "请选择有效的问题分类"
            );
        }
    }

    private void assertUserExists(String userId) {
        if (userId != null
                && !userId.isBlank()
                && !repository.userExists(userId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "用户不存在");
        }
    }

    private boolean isTerminalStatus(String status) {
        return "resolved".equals(status) || "closed".equals(status);
    }

    private String createTicketNumber() {
        String day = LocalDate.now(ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.BASIC_ISO_DATE);
        String suffix = UUID.randomUUID()
                .toString()
                .substring(0, 6)
                .toUpperCase();
        return "TK-" + day + "-" + suffix;
    }
}
