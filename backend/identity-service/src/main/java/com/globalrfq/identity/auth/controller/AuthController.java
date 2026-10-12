package com.globalrfq.identity.auth.controller;

import com.globalrfq.identity.auth.dto.request.LoginRequest;
import com.globalrfq.identity.auth.dto.response.AuthResponse;
import com.globalrfq.identity.auth.dto.response.AuthResult;
import com.globalrfq.identity.auth.service.AuthService;
import com.globalrfq.identity.auth.service.RefreshCookieService;
import com.globalrfq.identity.common.response.ApiResponse;
import com.globalrfq.identity.common.security.AuthenticatedUser;
import com.globalrfq.identity.common.util.ClientInfo;
import com.globalrfq.identity.user.dto.UserSummary;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookieService cookieService;

    public AuthController(AuthService authService, RefreshCookieService cookieService) {
        this.authService = authService;
        this.cookieService = cookieService;
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                           HttpServletRequest httpRequest,
                                           HttpServletResponse httpResponse) {
        AuthResult result = authService.login(request, ClientInfo.from(httpRequest));
        setRefreshCookie(httpResponse, result);
        return ApiResponse.success("Login successful", result.response());
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(
            @CookieValue(name = RefreshCookieService.COOKIE_NAME, required = false) String refreshToken,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        AuthResult result = authService.refresh(refreshToken, ClientInfo.from(httpRequest));
        setRefreshCookie(httpResponse, result);
        return ApiResponse.success("Token refreshed", result.response());
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @CookieValue(name = RefreshCookieService.COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse httpResponse) {
        authService.logout(refreshToken);
        httpResponse.addHeader(HttpHeaders.SET_COOKIE, cookieService.clear().toString());
        return ApiResponse.success("Logged out");
    }

    @GetMapping("/me")
    public ApiResponse<UserSummary> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.success("Current user", authService.currentUser(principal));
    }

    private void setRefreshCookie(HttpServletResponse response, AuthResult result) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookieService.create(result.refreshToken(), result.refreshMaxAgeSeconds()).toString());
    }
}