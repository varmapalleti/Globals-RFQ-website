package com.globalrfq.identity.notification.provider;

import com.globalrfq.identity.common.config.NotificationProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
import software.amazon.awssdk.services.sesv2.model.SendEmailResponse;
import software.amazon.awssdk.services.sesv2.model.SesV2Exception;

@Component
@ConditionalOnProperty(name = "app.notification.email-provider", havingValue = "ses")
public class SesEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SesEmailSender.class);

    private final SesV2Client client;
    private final NotificationProperties props;

    @Autowired
    public SesEmailSender(NotificationProperties props) {
        this(buildClient(props), props);
    }

    SesEmailSender(SesV2Client client, NotificationProperties props) {
        validateConfiguration(props);
        this.client = client;
        this.props = props;
    }

    private static SesV2Client buildClient(NotificationProperties props) {
        validateConfiguration(props);
        return SesV2Client.builder()
                .region(Region.of(props.sesRegion().trim()))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build();
    }

    private static void validateConfiguration(NotificationProperties props) {
        if (props.sesRegion() == null || props.sesRegion().isBlank()) {
            throw new IllegalStateException("AWS_REGION / app.notification.ses-region is required when EMAIL_PROVIDER=ses");
        }
        if (props.fromEmail() == null || props.fromEmail().isBlank()) {
            throw new IllegalStateException("MAIL_FROM_ADDRESS / app.notification.from-email is required when EMAIL_PROVIDER=ses");
        }
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

        if (props.replyToEmail() != null && !props.replyToEmail().isBlank()) {
            request.replyToAddresses(props.replyToEmail().trim());
        }
        if (props.sesConfigurationSet() != null && !props.sesConfigurationSet().isBlank()) {
            request.configurationSetName(props.sesConfigurationSet().trim());
        }

        try {
            SendEmailResponse response = client.sendEmail(request.build());
            log.info("SES email accepted messageId={} configSet={}",
                    response.messageId(),
                    (props.sesConfigurationSet() == null || props.sesConfigurationSet().isBlank())
                            ? "none" : props.sesConfigurationSet().trim());
        } catch (SesV2Exception ex) {
            String awsCode = ex.awsErrorDetails() != null ? ex.awsErrorDetails().errorCode() : "UNKNOWN";
            String awsMsg = ex.awsErrorDetails() != null ? ex.awsErrorDetails().errorMessage() : ex.getMessage();
            log.error("SES send failed status={} errorCode={} requestId={} message={}",
                    ex.statusCode(), awsCode, ex.requestId(), awsMsg);
            throw ex;
        }
    }

    @PreDestroy
    void close() {
        client.close();
    }
}
