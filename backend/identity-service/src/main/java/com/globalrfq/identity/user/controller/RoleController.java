package com.globalrfq.identity.user.controller;

import com.globalrfq.identity.common.response.ApiResponse;
import com.globalrfq.identity.common.security.AuthenticatedUser;
import com.globalrfq.identity.user.dto.RoleOption;
import com.globalrfq.identity.user.service.UserManagementService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
public class RoleController {
    private final UserManagementService service;

    public RoleController(UserManagementService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ApiResponse<List<RoleOption>> assignable(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success("Assignable roles", service.assignableRoles(principal));
    }
}
