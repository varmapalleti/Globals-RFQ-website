package com.globalrfq.identity.notification.provider;

import com.globalrfq.identity.common.config.NotificationProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

@Component
@ConditionalOnProperty(name = "app.notification.email-provider", havingValue = "ses")
public class SesEmailSender implements EmailSender {

    private final SesV2Client client;
    private final NotificationProperties props;

    public SesEmailSender(NotificationProperties props) {
        this.props = props;
        this.client = SesV2Client.builder()
                .region(Region.of(props.sesRegion()))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Override
    public String providerName() { return "AWS_SES"; }

    @Override
    public boolean isMock() { return false; }

    @Override
    public void send(EmailMessage message) {
        Message content = Message.builder()
                .subject(Content.builder().data(message.subject()).charset("UTF-8").build())
                .body(Body.builder()
                        .html(Content.builder().data(message.htmlBody()).charset("UTF-8").build())
                        .text(Content.builder().data(message.textBody()).charset("UTF-8").build())
                        .build())
                .build();

        SendEmailRequest.Builder request = SendEmailRequest.builder()
                .fromEmailAddress(message.from())
                .destination(Destination.builder().toAddresses(message.to()).build())
                .content(EmailContent.builder().simple(content).build());

        if (props.sesConfigurationSet() != null && !props.sesConfigurationSet().isBlank()) {
            request.configurationSetName(props.sesConfigurationSet());
        }
        client.sendEmail(request.build());
    }

    @PreDestroy
    void close() {
        client.close();
    }
}