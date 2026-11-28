package com.globalrfq.identity.notification.service;

import com.globalrfq.identity.common.config.FrontendProperties;
import com.globalrfq.identity.notification.model.NotificationRequest;
import com.globalrfq.identity.notification.model.NotificationTemplate;
import com.globalrfq.identity.user.entity.User;
import com.globalrfq.identity.user.entity.UserStatus;
import com.globalrfq.identity.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class RfqWorkflowEmailService {

    private static final Logger log = LoggerFactory.getLogger(RfqWorkflowEmailService.class);
    private static final Pattern UUID_PATTERN = Pattern.compile("^[0-9a-fA-F-]{36}$");

    private final UserRepository users;
    private final NotificationService notifications;
    private final FrontendProperties frontend;

    public RfqWorkflowEmailService(
            UserRepository users,
            NotificationService notifications,
            FrontendProperties frontend) {
        this.users = users;
        this.notifications = notifications;
        this.frontend = frontend;
    }

    public boolean sendWorkflowEmail(
            Long recipientUserId,
            String eventType,
            String title,
            String message,
            String rfqPublicId) {
        User user = users.findById(recipientUserId).orElse(null);
        if (user == null || user.getStatus() == UserStatus.DISABLED
                || user.getOfficialEmail() == null || user.getOfficialEmail().isBlank()) {
            log.debug("Skipping RFQ workflow email; recipientUserId={} unavailable or disabled", recipientUserId);
            return false;
        }

        String fullName = (user.getFullName() == null || user.getFullName().isBlank())
                ? "Team Member"
                : user.getFullName().trim();

        Map<String, String> variables = Map.of(
                "fullName", fullName,
                "eventLabel", formatEventLabel(eventType),
                "title", title.trim(),
                "message", message.trim(),
                "actionUrl", buildActionUrl(rfqPublicId)
        );

        notifications.send(NotificationRequest.email(
                NotificationTemplate.RFQ_WORKFLOW_EVENT,
                user.getOfficialEmail().trim(),
                variables
        ));
        return true;
    }

    private String buildActionUrl(String rfqPublicId) {
        String base = frontend.baseUrl() == null ? "http://localhost:5173" : frontend.baseUrl().trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (rfqPublicId != null && UUID_PATTERN.matcher(rfqPublicId.trim()).matches()) {
            return base + "/rfqs/" + rfqPublicId.trim();
        }
        return base + "/rfqs";
    }

    private String formatEventLabel(String eventType) {
        if (eventType == null || eventType.isBlank()) {
            return "RFQ Notification";
        }
        return Arrays.stream(eventType.trim().split("_"))
                .filter(part -> !part.isBlank())
                .map(part -> {
                    String upper = part.toUpperCase(Locale.ROOT);
                    if ("RFQ".equals(upper)) return "RFQ";
                    String lower = part.toLowerCase(Locale.ROOT);
                    return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
                })
                .collect(Collectors.joining(" "));
    }
}
