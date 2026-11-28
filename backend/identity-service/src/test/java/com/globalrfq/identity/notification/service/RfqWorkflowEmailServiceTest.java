package com.globalrfq.identity.notification.service;

import com.globalrfq.identity.common.config.FrontendProperties;
import com.globalrfq.identity.common.config.NotificationProperties;
import com.globalrfq.identity.notification.model.NotificationChannel;
import com.globalrfq.identity.notification.model.NotificationRequest;
import com.globalrfq.identity.notification.model.NotificationTemplate;
import com.globalrfq.identity.notification.provider.EmailMessage;
import com.globalrfq.identity.notification.provider.MockEmailSender;
import com.globalrfq.identity.user.entity.User;
import com.globalrfq.identity.user.entity.UserStatus;
import com.globalrfq.identity.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RfqWorkflowEmailServiceTest {

    @Mock
    private UserRepository users;

    @Mock
    private NotificationService notifications;

    @Test
    void queuesRenderedEmailForActiveUserWithRfqDeepLink() {
        User user = new User();
        user.setId(15L);
        user.setFullName("Rohan Kulkarni");
        user.setOfficialEmail("rohan@globalrfq.local");
        user.setStatus(UserStatus.ACTIVE);
        when(users.findById(15L)).thenReturn(Optional.of(user));

        var service = new RfqWorkflowEmailService(
                users,
                notifications,
                new FrontendProperties("http://localhost:5173/"));

        boolean queued = service.sendWorkflowEmail(
                15L,
                "RFQ_ASSIGNED",
                "Assigned to RFQ-110627",
                "You have been assigned as quotation manager on RFQ-110627.",
                "11111111-1111-1111-1111-111111111111");

        assertTrue(queued);
        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notifications).send(captor.capture());

        NotificationRequest req = captor.getValue();
        assertEquals(NotificationChannel.EMAIL, req.channel());
        assertEquals(NotificationTemplate.RFQ_WORKFLOW_EVENT, req.template());
        assertEquals("rohan@globalrfq.local", req.recipient());
        assertEquals("RFQ Assigned", req.variables().get("eventLabel"));
        assertEquals("http://localhost:5173/rfqs/11111111-1111-1111-1111-111111111111",
                req.variables().get("actionUrl"));

        var rendered = new TemplateRenderer().render(req.template(), req.variables());
        assertEquals("[Global RFQ] Assigned to RFQ-110627", rendered.subject());
        assertTrue(rendered.textBody().contains("Open RFQ Workspace: http://localhost:5173/rfqs/11111111-1111-1111-1111-111111111111"));

        var mockSender = new MockEmailSender(new NotificationProperties(
                "mock", "mock", "no-reply@globalrfq.com", "Global RFQ", "", "ap-south-1", "", true, ""));
        assertDoesNotThrow(() -> mockSender.send(new EmailMessage(
                "Global RFQ <no-reply@globalrfq.com>",
                req.recipient(),
                rendered.subject(),
                rendered.htmlBody(),
                rendered.textBody())));
    }

    @Test
    void skipsDisabledOrMissingRecipientWithoutThrowing() {
        User disabled = new User();
        disabled.setId(22L);
        disabled.setOfficialEmail("disabled@globalrfq.local");
        disabled.setStatus(UserStatus.DISABLED);
        when(users.findById(22L)).thenReturn(Optional.of(disabled));
        when(users.findById(99L)).thenReturn(Optional.empty());

        var service = new RfqWorkflowEmailService(
                users,
                notifications,
                new FrontendProperties("http://localhost:5173"));

        assertFalse(service.sendWorkflowEmail(22L, "RED_FLAG_RAISED", "Flag", "Details", null));
        assertFalse(service.sendWorkflowEmail(99L, "QUOTATION_REJECTED", "Rejected", "Reason", null));
        verifyNoInteractions(notifications);
    }
}
