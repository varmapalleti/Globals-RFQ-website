package com.globalrfq.identity.notification.provider;

import com.globalrfq.identity.common.config.NotificationProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SesEmailSenderTest {

    @Mock
    private SesV2Client client;

    @Test
    void validatesRequiredRegionAndFromEmail() {
        var missingRegion = new NotificationProperties(
                "ses", "mock", "no-reply@globalrfq.com", "Global RFQ", "", "", "", false, "");
        var missingFrom = new NotificationProperties(
                "ses", "mock", "", "Global RFQ", "", "ap-south-1", "", false, "");

        assertThrows(IllegalStateException.class, () -> new SesEmailSender(client, missingRegion));
        assertThrows(IllegalStateException.class, () -> new SesEmailSender(client, missingFrom));
    }

    @Test
    void sendsEmailWithReplyToAndConfigurationSet() {
        var props = new NotificationProperties(
                "ses", "mock", "no-reply@globalrfq.com", "Global RFQ",
                "support@globalrfq.com", "ap-south-1", "globalrfq-transactional", false, "");
        when(client.sendEmail(any(SendEmailRequest.class)))
                .thenReturn(SendEmailResponse.builder().messageId("ses-msg-123").build());

        var sender = new SesEmailSender(client, props);
        sender.send(new EmailMessage(
                "Global RFQ <no-reply@globalrfq.com>",
                "buyer@example.com",
                "Quotation approved",
                "<p>Approved</p>",
                "Approved"));

        ArgumentCaptor<SendEmailRequest> captor = ArgumentCaptor.forClass(SendEmailRequest.class);
        verify(client).sendEmail(captor.capture());

        SendEmailRequest sent = captor.getValue();
        assertEquals("Global RFQ <no-reply@globalrfq.com>", sent.fromEmailAddress());
        assertEquals(List.of("buyer@example.com"), sent.destination().toAddresses());
        assertEquals(List.of("support@globalrfq.com"), sent.replyToAddresses());
        assertEquals("globalrfq-transactional", sent.configurationSetName());
        assertEquals("Quotation approved", sent.content().simple().subject().data());
    }

    @Test
    void propagatesSesExceptionForOutboxRetry() {
        var props = new NotificationProperties(
                "ses", "mock", "no-reply@globalrfq.com", "Global RFQ",
                "", "ap-south-1", "", false, "");
        SesV2Exception sesError = (SesV2Exception) SesV2Exception.builder()
                .statusCode(400)
                .requestId("req-1")
                .awsErrorDetails(AwsErrorDetails.builder()
                        .errorCode("MessageRejected")
                        .errorMessage("Email address is not verified.")
                        .build())
                .build();
        when(client.sendEmail(any(SendEmailRequest.class))).thenThrow(sesError);

        var sender = new SesEmailSender(client, props);
        SesV2Exception thrown = assertThrows(SesV2Exception.class, () -> sender.send(new EmailMessage(
                "Global RFQ <no-reply@globalrfq.com>",
                "unverified@example.com",
                "Test",
                "<p>Hi</p>",
                "Hi")));
        assertEquals(400, thrown.statusCode());
    }
}
