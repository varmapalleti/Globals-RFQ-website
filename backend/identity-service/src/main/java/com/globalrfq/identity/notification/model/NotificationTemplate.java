package com.globalrfq.identity.notification.model;

public enum NotificationTemplate {

    VERIFY_EMAIL(
            NotificationChannel.EMAIL,
            "Verify your email address - Global RFQ",
            """
            <p>Hello {{fullName}},</p>
            <p>Please verify your {{emailLabel}} email address. This link expires in {{expiryHours}} hours.</p>
            <p><a href="{{verificationLink}}">Verify email address</a></p>
            <p>If you did not expect this email, you can ignore it.</p>
            """,
            """
            Hello {{fullName}},
            Verify your {{emailLabel}} email address using this link (valid for {{expiryHours}} hours):
            {{verificationLink}}
            If you did not expect this email, you can ignore it.
            """),

    PASSWORD_RESET_OTP(
            NotificationChannel.EMAIL,
            "Your password reset code - Global RFQ",
            """
            <p>Hello {{fullName}},</p>
            <p>Your password reset code is:</p>
            <p style="font-size:24px;font-weight:bold;letter-spacing:4px;">{{otp}}</p>
            <p>This code expires in {{expiryMinutes}} minutes. Never share it with anyone.</p>
            <p>If you did not request this, please ignore this email.</p>
            """,
            """
            Hello {{fullName}},
            Your password reset code is: {{otp}}
            It expires in {{expiryMinutes}} minutes. Never share it with anyone.
            If you did not request this, please ignore this email.
            """),

    PASSWORD_CHANGED(
            NotificationChannel.EMAIL,
            "Your password was changed - Global RFQ",
            """
            <p>Hello {{fullName}},</p>
            <p>Your account password was changed on {{changedAt}}.</p>
            <p>If this was not you, contact your administrator immediately.</p>
            """,
            """
            Hello {{fullName}},
            Your account password was changed on {{changedAt}}.
            If this was not you, contact your administrator immediately.
            """);

    private final NotificationChannel channel;
    private final String subject;
    private final String htmlBody;
    private final String textBody;

    NotificationTemplate(NotificationChannel channel, String subject, String htmlBody, String textBody) {
        this.channel = channel;
        this.subject = subject;
        this.htmlBody = htmlBody;
        this.textBody = textBody;
    }

    public NotificationChannel getChannel() { return channel; }
    public String getSubject() { return subject; }
    public String getHtmlBody() { return htmlBody; }
    public String getTextBody() { return textBody; }
}