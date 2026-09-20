package com.bytedance.tickets.controller;

import com.bytedance.tickets.model.ApiModels;
import com.bytedance.tickets.security.SessionInterceptor;
import com.bytedance.tickets.service.WorkOrderService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/work-orders")
public final class WorkOrderController {
    private final WorkOrderService workOrders;

    public WorkOrderController(WorkOrderService workOrders) {
        this.workOrders = workOrders;
    }

    @GetMapping("/tickets")
    public ApiModels.TicketListResponse listTickets(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String categoryId
    ) {
        return workOrders.listTickets(
                page,
                pageSize,
                search,
                status,
                priority,
                categoryId
        );
    }

    @GetMapping("/tickets/{id}")
    public ApiModels.TicketItem getTicket(@PathVariable String id) {
        return workOrders.getTicket(id);
    }

    @PostMapping("/tickets")
    public ApiModels.TicketItem createTicket(
            @Valid @RequestBody ApiModels.CreateTicketRequest request,
            HttpServletRequest servletRequest
    ) {
        var user = currentUser(servletRequest);
        return workOrders.createTicket(request, user.id());
    }

    @PatchMapping("/tickets/{id}")
    public ApiModels.TicketItem updateTicket(
            @PathVariable String id,
            @Valid @RequestBody ApiModels.UpdateTicketRequest request
    ) {
        return workOrders.updateTicket(id, request);
    }

    @DeleteMapping("/tickets/{id}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void deleteTicket(@PathVariable String id) {
        workOrders.deleteTicket(id);
    }

    @GetMapping("/categories")
    public List<ApiModels.IssueCategoryItem> listCategories() {
        return workOrders.listCategories();
    }

    @PostMapping("/categories")
    public ApiModels.IssueCategoryItem createCategory(
            @Valid @RequestBody ApiModels.CreateIssueCategoryRequest request
    ) {
        return workOrders.createCategory(request);
    }

    @PatchMapping("/categories/{id}")
    public ApiModels.IssueCategoryItem updateCategory(
            @PathVariable String id,
            @Valid @RequestBody ApiModels.UpdateIssueCategoryRequest request
    ) {
        return workOrders.updateCategory(id, request);
    }

    @DeleteMapping("/categories/{id}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void deleteCategory(@PathVariable String id) {
        workOrders.deleteCategory(id);
    }

    private ApiModels.AppUser currentUser(HttpServletRequest request) {
        return (ApiModels.AppUser) request.getAttribute(
                SessionInterceptor.CURRENT_USER_ATTRIBUTE
        );
    }
}
