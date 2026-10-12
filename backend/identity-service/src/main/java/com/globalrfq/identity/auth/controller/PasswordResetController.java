package com.globalrfq.identity.auth.controller;

import com.globalrfq.identity.auth.dto.request.ForgotPasswordRequest;
import com.globalrfq.identity.auth.dto.request.ResetPasswordRequest;
import com.globalrfq.identity.auth.dto.request.VerifyOtpRequest;
import com.globalrfq.identity.auth.dto.response.ResetTokenResponse;
import com.globalrfq.identity.auth.service.PasswordResetService;
import com.globalrfq.identity.common.response.ApiResponse;
import com.globalrfq.identity.common.util.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class PasswordResetController {

    private final PasswordResetService service;

    public PasswordResetController(PasswordResetService service) {
        this.service = service;
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request,
                                            HttpServletRequest http) {
        service.requestOtp(request, ClientInfo.from(http));
        return ApiResponse.success("If an account exists for this email, a verification code has been sent");
    }

    @PostMapping("/verify-reset-otp")
    public ApiResponse<ResetTokenResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request,
                                                     HttpServletRequest http) {
        return ApiResponse.success("Code verified", service.verifyOtp(request, ClientInfo.from(http)));
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        service.resetPassword(request);
        return ApiResponse.success("Password changed successfully. Please sign in with your new password");
    }
}