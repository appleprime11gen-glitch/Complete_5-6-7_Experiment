package com.example.postapi.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.postapi.dto.ApiResponse;
import com.example.postapi.dto.RolesRequest;
import com.example.postapi.dto.UserView;
import com.example.postapi.service.UserManagementService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserManagementService userManagementService;

    public AdminController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserView>>> listUsers() {
        return ResponseEntity.ok(new ApiResponse<>("success", "Users fetched", userManagementService.listUsers()));
    }

    @PutMapping("/{id}/roles")
    public ResponseEntity<ApiResponse<UserView>> updateRoles(@PathVariable String id,
            @Valid @RequestBody RolesRequest request) {
        return ResponseEntity.ok(new ApiResponse<>("success", "Roles updated",
                userManagementService.updateRoles(id, request.getRoles())));
    }
}