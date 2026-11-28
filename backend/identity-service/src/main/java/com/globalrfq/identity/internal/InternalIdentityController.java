package com.globalrfq.identity.internal;

import com.globalrfq.identity.common.exception.BusinessException;
import com.globalrfq.identity.common.exception.ErrorCode;
import com.globalrfq.identity.common.response.ApiResponse;
import com.globalrfq.identity.common.security.JwtService;
import com.globalrfq.identity.notification.service.RfqWorkflowEmailService;
import com.globalrfq.identity.role.entity.RoleName;
import com.globalrfq.identity.user.entity.User;
import com.globalrfq.identity.user.entity.UserStatus;
import com.globalrfq.identity.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/internal/identity")
public class InternalIdentityController {
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository users;
    private final RfqWorkflowEmailService workflowEmailService;

    public InternalIdentityController(JwtService jwtService, UserRepository users) {
        this(jwtService, users, null);
    }

    @Autowired
    public InternalIdentityController(
            JwtService jwtService,
            UserRepository users,
            RfqWorkflowEmailService workflowEmailService) {
        this.jwtService = jwtService;
        this.users = users;
        this.workflowEmailService = workflowEmailService;
    }

    @GetMapping("/validate")
    public ApiResponse<ValidatedIdentity> validate(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "A valid access token is required");
        }
        try {
            Claims claims = jwtService.parse(authorization.substring(BEARER_PREFIX.length()).trim());
            Long userId = jwtService.toAuthenticatedUser(claims).id();
            User user = users.findWithRolesById(userId)
                    .filter(candidate -> candidate.getStatus() == UserStatus.ACTIVE)
                    .orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_INVALID, "Access token is invalid"));

            List<String> currentRoles = user.getRoles().stream()
                    .map(role -> role.getName().name())
                    .sorted()
                    .toList();
            return ApiResponse.success("Identity validated", new ValidatedIdentity(user.getId(), user.getOfficialEmail(), currentRoles));
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.TOKEN_INVALID, "Access token is invalid");
        }
    }

    @GetMapping("/users/{userId}/eligibility")
    public ApiResponse<UserEligibility> eligibility(@PathVariable long userId, @RequestParam RoleName role) {
        User user = users.findWithRolesById(userId).orElse(null);
        boolean eligible = user != null
                && user.getStatus() == UserStatus.ACTIVE
                && user.getRoles().stream().anyMatch(assignedRole -> assignedRole.getName() == role);
        return ApiResponse.success("User eligibility", new UserEligibility(userId, role.name(), eligible));
    }

    @PostMapping("/notifications/rfq-workflow")
    public ApiResponse<WorkflowEmailDispatchResult> sendRfqWorkflowEmail(
            @Valid @RequestBody RfqWorkflowEmailRequest request) {
        boolean queued = workflowEmailService != null && workflowEmailService.sendWorkflowEmail(
                request.recipientUserId(),
                request.eventType(),
                request.title(),
                request.message(),
                request.rfqPublicId());
        return ApiResponse.success(
                queued ? "Workflow email queued" : "Recipient skipped",
                new WorkflowEmailDispatchResult(queued));
    }

    public record ValidatedIdentity(Long userId, String email, List<String> roles) { }
    public record UserEligibility(Long userId, String requiredRole, boolean eligible) { }
    public record RfqWorkflowEmailRequest(
            @NotNull Long recipientUserId,
            @NotBlank @Size(max = 60) String eventType,
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 1000) String message,
            @Size(max = 64) String rfqPublicId) { }
    public record WorkflowEmailDispatchResult(boolean queued) { }
}
