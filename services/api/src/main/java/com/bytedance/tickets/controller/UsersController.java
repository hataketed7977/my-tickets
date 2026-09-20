package com.bytedance.tickets.controller;

import com.bytedance.tickets.model.ApiModels;
import com.bytedance.tickets.service.AuthService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public final class UsersController {
    private final AuthService authService;

    public UsersController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    public List<ApiModels.AppUser> listUsers() {
        return authService.listUsers();
    }
}
