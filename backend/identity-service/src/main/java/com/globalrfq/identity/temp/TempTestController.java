package com.globalrfq.identity.temp;

import com.globalrfq.identity.common.exception.BusinessException;
import com.globalrfq.identity.common.exception.ErrorCode;
import com.globalrfq.identity.common.exception.ResourceNotFoundException;
import com.globalrfq.identity.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/test")
public class TempTestController {

    private static final Logger log = LoggerFactory.getLogger(TempTestController.class);

    public record SampleRequest(@NotBlank String name, @Email @NotBlank String email) {}

    @GetMapping("/ok")
    public ApiResponse<String> ok() {
        log.info("Inside ok endpoint");
        return ApiResponse.success("Working", "hello");
    }

    @GetMapping("/not-found")
    public void notFound() { throw new ResourceNotFoundException("User", 99); }

    @GetMapping("/business")
    public void business() { throw new BusinessException(ErrorCode.ACCOUNT_LOCKED, "Account is locked"); }

    @GetMapping("/crash")
    public void crash() { throw new IllegalStateException("secret internal detail"); }

    @PostMapping("/validate")
    public ApiResponse<Void> validate(@Valid @RequestBody SampleRequest request) {
        return ApiResponse.success("Valid");
    }
}