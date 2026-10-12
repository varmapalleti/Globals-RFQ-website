package com.globalrfq.identity.auth.controller;

import com.globalrfq.identity.auth.dto.request.ResendVerificationRequest;
import com.globalrfq.identity.auth.dto.request.VerifyEmailRequest;
import com.globalrfq.identity.auth.service.EmailVerificationService;
import com.globalrfq.identity.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class EmailVerificationController {

    private final EmailVerificationService service;

    public EmailVerificationController(EmailVerificationService service) {
        this.service = service;
    }

    @PostMapping("/verify-email")
    public ApiResponse<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        service.verify(request.token());
        return ApiResponse.success("Email verified successfully");
    }

    @PostMapping("/resend-verification")
    public ApiResponse<Void> resend(@Valid @RequestBody ResendVerificationRequest request) {
        service.resend(request);
        return ApiResponse.success("If the email needs verification, a new link has been sent");
    }
}
