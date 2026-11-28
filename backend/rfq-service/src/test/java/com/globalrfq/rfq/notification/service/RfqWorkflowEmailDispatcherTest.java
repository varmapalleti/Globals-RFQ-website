package com.globalrfq.rfq.notification.service;

import com.globalrfq.rfq.common.auth.IdentityAuthorizationClient;
import com.globalrfq.rfq.common.auth.IdentityUnavailableException;
import com.globalrfq.rfq.notification.entity.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RfqWorkflowEmailDispatcherTest {

    @Mock
    private IdentityAuthorizationClient identityClient;

    @InjectMocks
    private RfqWorkflowEmailDispatcher dispatcher;

    @Test
    void dispatchForwardsWorkflowNotificationToIdentityService() {
        dispatcher.dispatch(
                42L,
                NotificationType.QUOTATION_APPROVED,
                "Quotation approved for RFQ-110627",
                "Reviewer approved quotation V1.",
                "11111111-1111-1111-1111-111111111111");

        verify(identityClient).sendWorkflowNotificationEmail(
                42L,
                "QUOTATION_APPROVED",
                "Quotation approved for RFQ-110627",
                "Reviewer approved quotation V1.",
                "11111111-1111-1111-1111-111111111111");
    }

    @Test
    void dispatchSwallowsIdentityFailuresSoWorkflowNeverBreaks() {
        doThrow(new IdentityUnavailableException("Identity offline"))
                .when(identityClient)
                .sendWorkflowNotificationEmail(anyLong(), anyString(), anyString(), anyString(), any());

        assertDoesNotThrow(() -> dispatcher.dispatch(
                99L,
                NotificationType.RED_FLAG_RAISED,
                "Red flag raised on RFQ-110627",
                "Urgent clarification needed",
                "11111111-1111-1111-1111-111111111111"));
    }
}
