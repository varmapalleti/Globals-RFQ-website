package com.globalrfq.identity.user.controller;

import com.globalrfq.identity.common.response.ApiResponse;
import com.globalrfq.identity.common.security.AuthenticatedUser;
import com.globalrfq.identity.role.entity.RoleName;
import com.globalrfq.identity.user.dto.*;
import com.globalrfq.identity.user.entity.UserStatus;
import com.globalrfq.identity.user.service.UserManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@Validated
public class UserController {
    private final UserManagementService service;

    public UserController(UserManagementService service) { this.service = service; }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ApiResponse<UserDetailsResponse> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                                   @Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.success("User invitation created", service.create(principal, request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ApiResponse<PageResponse<UserDetailsResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) RoleName role,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return ApiResponse.success("Users", service.list(principal, search, role, status, page, size, sort, direction));
    }

    @GetMapping("/eligible")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'QUOTATION_MANAGER')")
    public ApiResponse<java.util.List<EligibleUserOption>> eligible(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @RequestParam RoleName role) {
        return ApiResponse.success("Eligible users", service.listEligibleByRole(principal, role));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ApiResponse<UserDetailsResponse> get(@AuthenticationPrincipal AuthenticatedUser principal,
                                                @PathVariable @Positive long id) {
        return ApiResponse.success("User", service.get(principal, id));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ApiResponse<UserDetailsResponse> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                                   @PathVariable @Positive long id,
                                                   @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.success("User updated", service.update(principal, id, request));
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ApiResponse<UserDetailsResponse> replaceRoles(@AuthenticationPrincipal AuthenticatedUser principal,
                                                          @PathVariable @Positive long id,
                                                          @Valid @RequestBody AssignRolesRequest request) {
        return ApiResponse.success("User roles updated", service.replaceRoles(principal, id, request));
    }

    @PostMapping("/{id}/freeze")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<UserDetailsResponse> freeze(@AuthenticationPrincipal AuthenticatedUser principal,
                                                   @PathVariable @Positive long id,
                                                   @Valid @RequestBody FreezeUserRequest request) {
        return ApiResponse.success("User frozen", service.freeze(principal, id, request.reason()));
    }

    @PostMapping("/{id}/unfreeze")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<UserDetailsResponse> unfreeze(@AuthenticationPrincipal AuthenticatedUser principal,
                                                     @PathVariable @Positive long id) {
        return ApiResponse.success("User unfrozen", service.unfreeze(principal, id));
    }

    @PostMapping("/{id}/unlock")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ApiResponse<UserDetailsResponse> unlock(@AuthenticationPrincipal AuthenticatedUser principal,
                                                   @PathVariable @Positive long id) {
        return ApiResponse.success("User unlocked", service.unlock(principal, id));
    }

    @PostMapping("/{id}/resend-invite")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ApiResponse<Void> resendInvite(@AuthenticationPrincipal AuthenticatedUser principal,
                                          @PathVariable @Positive long id) {
        service.resendInvite(principal, id);
        return ApiResponse.success("Invitation resent");
    }
}
