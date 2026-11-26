# AWS SES setup for identity-service

Identity-service already has an SES v2 sender and an encrypted notification outbox with asynchronous delivery and retries. Local development defaults to mock email so invitation, verification, and password-reset flows can be tested without AWS credentials. This guide configures real SES sending without putting credentials in source code, `.env.example`, Postman, or application properties.

## 1. AWS account and SES prerequisites

1. Choose one AWS Region for this service and use it consistently for the SES identity, configuration set, and `AWS_REGION`.
2. Verify the sender domain (preferred) or sender email address in SES. For a domain, publish the SES DKIM DNS records and also configure SPF and DMARC for the sending domain.
3. Confirm SES sending is enabled in that Region. New SES accounts are sandboxed per Region. In the sandbox, recipients must also be verified and sending quotas are restricted. Request production access before sending to unverified customer addresses.
4. If using a configuration set, create it in the same Region and attach event destinations for delivery, bounce, complaint, reject, and send events. The current service can name that configuration set on sends; operational bounce/complaint processing still needs an agreed event destination/consumer.
5. Create a least-privilege runtime identity permitted to send through SES for the verified sender identity. Do not give the service broad AWS administrator permissions.

AWS requires the `FromEmailAddress` identity to be verified. Sandbox accounts can send only to verified recipients or the SES mailbox simulator. Production access is separate for each Region. See [SES SendEmail](https://docs.aws.amazon.com/ses/latest/APIReference-V2/API_SendEmail.html), [SES sandbox and production access](https://docs.aws.amazon.com/ses/latest/dg/request-production-access.html), and [SES identity verification](https://docs.aws.amazon.com/ses/latest/dg/creating-identities.html).

## 2. Credential handling

`SesEmailSender` uses the AWS SDK for Java 2.x default credentials provider chain. It does not need access keys in Java code. For local development, use an AWS named profile or IAM Identity Center/SSO session and set `AWS_PROFILE` in the run configuration. The SDK can also obtain credentials from workload identity, ECS task roles, or EC2 instance roles. In production, prefer a short-lived workload role and restrict its policy to the SES send operation and approved identity. Do not commit static keys or put them in Postman.

The AWS SDK documents the [Java 2.x default credentials chain](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html) and [profile configuration](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-profiles.html).

## 3. Identity-service settings

Set these values in the identity-service IntelliJ run configuration or its launch environment, then restart the service:

```text
SPRING_PROFILES_ACTIVE=dev
EMAIL_PROVIDER=ses
AWS_REGION=ap-southeast-1
AWS_PROFILE=globalrfq-ses-dev
MAIL_FROM_ADDRESS=no-reply@example.com
MAIL_FROM_NAME=Global RFQ
SES_CONFIGURATION_SET=GlobalRFQTransactional
```

Use a sender address/domain verified in that exact Region. `SES_CONFIGURATION_SET` is optional; leave it empty if none is configured. The application already reads these settings from `application.yml`. Keep `EMAIL_PROVIDER=mock` for Postman tests that should not send real email.

No AWS credentials belong in Postman. Postman sends identity API requests to the gateway; the identity service sends mail using its own AWS runtime identity.

## 4. Test a real SES flow

1. Configure a disposable active test user whose chosen email address is verified in SES if the account is still in the sandbox.
2. Set `EMAIL_PROVIDER=ses`, the matching `AWS_REGION`, verified sender, and local AWS profile/role. Restart identity-service so Spring creates the SES client with those settings.
3. In Postman, call `POST /api/auth/forgot-password` using that verified user's email and the matching `emailType`.
4. The API intentionally returns a generic response. Notification dispatch is asynchronous, so check the identity-service log and notification outbox state, then check the recipient mailbox and spam folder.
5. If delivery fails, inspect the exception class and SES message ID in service telemetry, verify the sender/recipient identities and configuration set in the same Region, and check the SES account sending status/quota. The outbox retries failed sends; avoid repeatedly triggering production messages during debugging.

Common configuration failures:

| Symptom | Likely cause |
| --- | --- |
| Unable to load credentials | AWS profile/SSO is not configured for the process, or the runtime role is absent. |
| Access denied | Runtime role lacks the SES send permission or is restricted to a different sender identity/Region. |
| Message rejected / sender not verified | `MAIL_FROM_ADDRESS` is not a verified SES identity in `AWS_REGION`. |
| Recipient rejected | SES remains in sandbox and the destination is not verified. |
| Configuration-set error | The configured set does not exist in the selected Region or the name is misspelled. |
| API says success but no mail | Dispatch is asynchronous; inspect notification outbox status, service logs, SES event destinations, and bounce/complaint events. |

## 5. Production checklist

- Verify the sender domain and publish DKIM; publish SPF/DMARC aligned with the client's sending policy.
- Obtain SES production access in the chosen Region and set realistic sending quotas.
- Use a workload role with least privilege; rotate/remove long-lived development keys.
- Enable a configuration set with delivery/bounce/complaint telemetry and assign an owner to process those events. The current repository does not yet consume SES bounce/complaint webhooks.
- Monitor outbox retry/failure rates, SES sending reputation, bounce rate, and complaint rate. Establish suppression and incident response procedures before customer traffic.
- Keep the outbox encryption key stable while encrypted notification rows remain pending; follow the identity-service report before key rotation.
- Restore shared gateway rate limiting before any internet-facing production release; Redis is intentionally deferred during local API testing.
