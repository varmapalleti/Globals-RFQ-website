# RFQ Service on AWS

This is the deployment runbook for the backend RFQ module. It does not provision AWS resources. Keep the RFQ database and uploaded business documents private; do not put AWS access keys in source files, Postman, or browser configuration.

## Recommended first production topology

```text
Customers / Staff
      |
Route 53 + ACM HTTPS + (AWS WAF)
      |
Public Application Load Balancer
      |
API Gateway tasks (ECS Fargate, private subnets)
      |-------------------------|
Internal service discovery     Identity + RFQ tasks (ECS Fargate, private subnets)
                                |                 |
                         RDS MySQL private   Private S3 bucket
                                                   |
                                           SQS -> scanner worker
```

Expose only the API Gateway through the load balancer. Do not expose RFQ port 8082, Identity, Eureka, MySQL 3306, or the S3 bucket publicly. Keep the Gateway as the application entry point. The current Eureka client setup can be retained for the first AWS release, but run a highly available Eureka deployment across Availability Zones; evaluate AWS Cloud Map / ECS Service Connect as a later native replacement rather than operating two discovery systems indefinitely.

## AWS resources to request or create

| Need | AWS service | Initial setup |
| --- | --- | --- |
| Network isolation | VPC, public/private subnets in at least two Availability Zones, security groups, route tables | Load balancer in public subnets; application tasks and database in private subnets. Add NAT or VPC endpoints for required AWS APIs. |
| Java service hosting | ECS with AWS Fargate | Separate services/tasks for Gateway, Identity, RFQ, Eureka, and the attachment scanner worker. Configure health checks, deployment rollback, and at least two tasks for production critical services as capacity and budget allow. |
| Container images | ECR | Private repositories per service; scan images, tag immutable release versions, and retain rollback versions. Fargate runs container images. You can have CodeBuild build/push them so Docker does not need to run on your development laptop. |
| Public HTTPS | Application Load Balancer, ACM, Route 53; AWS WAF if internet-facing | Route HTTPS to Gateway only. Redirect HTTP to HTTPS. WAF is recommended for the public API and should be tuned against real traffic. |
| RFQ relational data | Amazon RDS for MySQL | Private subnets, encryption, automated backups and point-in-time recovery. Use Multi-AZ for production availability; test restores, not only backup creation. Give each service its own database/schema and credentials. |
| RFQ source files and product attachments | Amazon S3 | Dedicated private bucket, Block Public Access at account and bucket level, bucket versioning, default encryption, TLS-only bucket policy, and an agreed retention/lifecycle policy. Application downloads remain authorized by RFQ Service; do not return public object URLs. |
| Runtime secrets | AWS Secrets Manager (or Parameter Store for non-secret settings) | Store DB credentials, JWT signing secret, Gateway shared secret, Identity internal token, scanner token, and any future SES credentials/configuration. Inject only the secrets each task needs. |
| Service permissions | IAM task execution role and per-service task roles | Execution role pulls ECR images, writes container logs, and reads task-definition secrets. RFQ task role gets only the S3 object actions under the RFQ bucket prefix. No long-lived IAM user keys in ECS. |
| Logs and metrics | CloudWatch Logs, metrics, alarms; CloudTrail for AWS account/API audit | Centralize structured logs with correlation IDs; alarm on task health, 5xx, latency, DB connections/storage, S3 access failures, and scanner backlog. Keep audit retention according to client policy. |
| Build/deploy | CodeBuild/CodePipeline or GitHub Actions using OIDC | Build, run checks, publish immutable ECR images, deploy to a test environment, then promote to production with approval and rollback. |
| Malware scanning | ECS Fargate scanner worker; optionally SQS for durable work delivery | Current RFQ code has a secured pending-scan/read-content/result API contract, but no scanner engine is included. The worker must scan each uploaded object and report `CLEAN` or `INFECTED`; downloads stay blocked before `CLEAN`. If SQS is used, enqueue work only after attachment metadata commits (or retry 404s), configure retries and a dead-letter queue, and ensure the scanner can reach RFQ Service privately. |
| Future email | Amazon SES | Separate later work after sender/domain verification and IAM authorization. Not required for RFQ file storage or current workflow. |

AWS documents ECS task roles as the way for application code to access S3, separate from the task execution role used by ECS/Fargate for image pulls, logs, and task startup. [ECS task IAM roles](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task-iam-roles.html) · [ECS task execution role](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/task_execution_IAM_role.html)

## S3 bucket security baseline

Create a general purpose S3 bucket in the selected AWS Region. Enable all four S3 Block Public Access settings. Keep Object Ownership as bucket-owner-enforced (ACLs disabled). Require TLS in the bucket policy. Enable versioning and choose the retention/lifecycle settings with the client before applying expiration rules. S3 blocks public access through bucket/account-level controls, and new objects are encrypted by default with SSE-S3; use SSE-KMS if the client needs customer-managed key control, key-policy governance, or key-level audit requirements. [S3 Block Public Access](https://docs.aws.amazon.com/AmazonS3/latest/userguide/access-control-block-public-access.html) · [S3 encryption](https://docs.aws.amazon.com/AmazonS3/latest/userguide/bucket-encryption.html)

The RFQ task role should be scoped to only the object prefix, for example:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
      "Resource": "arn:aws:s3:::YOUR_PRIVATE_RFQ_BUCKET/global-rfq/*"
    }
  ]
}
```

Do not add `s3:ListBucket` unless an operational feature actually needs it. If the bucket requires SSE-KMS, add only the required KMS permissions for that specific key and configure the key policy to trust the RFQ task role. Leave bucket-level encryption enabled as the default so all writes are encrypted even if the application does not set an encryption header.

## RFQ Service configuration

Development remains local filesystem storage. For AWS set these container environment values in the RFQ task definition (use Secrets Manager references for secrets):

| Variable | AWS value |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `RFQ_STORAGE_PROVIDER` | `s3` (prod profile defaults to S3) |
| `AWS_REGION` | Same Region as the bucket, e.g. the client-approved Region |
| `RFQ_S3_BUCKET` | Private RFQ bucket name |
| `RFQ_S3_PREFIX` | `global-rfq` or the approved environment prefix |
| `RFQ_DB_URL` | JDBC URL for the private RDS MySQL endpoint, database `global_rfq`, TLS parameters enabled |
| `RFQ_DB_USERNAME`, `RFQ_DB_PASSWORD` | Dedicated RFQ DB user/password from Secrets Manager |
| `EUREKA_SERVER_URL` | Internal Eureka endpoint (or later replace discovery with the chosen AWS-native mechanism) |
| `GATEWAY_SHARED_SECRET` | Secret shared only between Gateway and RFQ tasks |
| `IDENTITY_INTERNAL_SHARED_SECRET` | Separate secret shared only between RFQ and Identity |
| `RFQ_ATTACHMENT_SCANNER_TOKEN` | Secret shared only with the scanner worker |
| `RFQ_DEFAULT_TIME_ZONE` | `Asia/Kolkata` currently; this affects display/date parsing defaults, while stored event timestamps remain UTC |
| `RFQ_MAX_FILE_SIZE`, `RFQ_MAX_REQUEST_SIZE` | Keep aligned with load balancer, Gateway, servlet, and application limits (`25MB` / `30MB` currently) |

The application uses the AWS SDK default credentials provider chain, which resolves the ECS task role on Fargate. Do not configure `AWS_ACCESS_KEY_ID` or `AWS_SECRET_ACCESS_KEY` in the production task.

## Move existing local attachments to S3

Before switching an environment from local storage to S3, pause uploads/imports and confirm no import preview is still needed. The current local layout stores attachment objects directly under `RFQ_STORAGE_DIR` using their UUID keys, and staging files under `RFQ_STORAGE_DIR/imports/`. Attachment records in MySQL retain those UUID keys, so upload each existing attachment to `global-rfq/attachments/<UUID>` in S3:

```powershell
$bucket = "YOUR_PRIVATE_RFQ_BUCKET"
$prefix = "global-rfq"
$source = "C:\path\to\rfq-files"
aws s3 cp $source "s3://$bucket/$prefix/attachments/" --recursive --exclude "imports/*"
```

Verify object counts and SHA-256 values against the `rfq_attachments` table before deploying with `RFQ_STORAGE_PROVIDER=s3`. Do not migrate expired import previews. Keep the source directory as a protected rollback copy until the S3 deployment and download checks pass. Do not run this copy command against production until the destination bucket, Region, and AWS role are verified.

## Rollout and verification order

1. Select the AWS account and Region with the client; confirm data residency, retention, recovery objectives, expected users/files, and budget.
2. Create VPC/subnets/security groups, then private RDS MySQL and the RFQ schema/user. Test TLS connectivity from a private ECS task.
3. Create the private S3 bucket and encryption/versioning controls. Create the RFQ task role with prefix-scoped permissions.
4. Put service secrets in Secrets Manager; create execution roles and task definitions.
5. Build and deploy Eureka, Identity, RFQ, then Gateway and scanner worker. Keep only Gateway reachable through the ALB.
6. Run Flyway migrations on the RFQ task, verify health, Eureka registration, Identity authorization, and workflow endpoints.
7. Upload PDF, Excel, Word, and PNG/JPEG samples. Confirm metadata, optional line-item association, pending scan state, scanner result, and authorized download after `CLEAN`.
8. Verify one user from each role, a rejected upload, forbidden cross-RFQ access, customer approval followed by Marketing assignment, database backup restore, and application rollback.
9. Enable production alarms and dashboards. Agree retention and lifecycle rules before deleting old versions or objects.

## Still required before calling AWS production live

- AWS account, approved Region, domain name, and client data residency approval.
- IAM administrator/team to create least-privilege roles and resource policies.
- Approved DB sizing, user/file volume, availability, RPO/RTO, backup retention, attachment retention, and monthly budget.
- Real antivirus scanner/worker and operational ownership.
- Load, recovery, security, and end-to-end workflow testing in a production-like environment.
- Confirm final items still marked open in the requirements document (pricing/tax/discount/rounding, partial/alternative quotes, reviewer rules, client-software integration evidence, Marketing completion, retention, and reporting needs).
