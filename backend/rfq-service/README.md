# RFQ Service

Backend service for customer RFQ intake, quotation preparation, internal review, customer decision tracking, assignment, attachments, exports, and operational reporting. The UI is outside this service.

## Local dependencies

- Java 21 and the Maven wrapper in this directory
- MySQL with a dedicated RFQ database and least-privilege service account
- Eureka Server
- Identity Service
- API Gateway
- A trusted attachment malware scanner for releasing uploaded files
- For production, an AWS private S3 bucket and an ECS task role limited to the configured RFQ object prefix

Redis, Docker, and AWS SES are not required by this service. No email integration is currently configured.

## Configuration

Copy `.env.example` into your local secret manager or IDE environment configuration. Spring Boot does not load a plain `.env` file automatically. Set the variables in the process environment or configure them in the IDE run profile. Do not commit real passwords or tokens.

Important values:

| Variable | Purpose |
| --- | --- |
| `RFQ_DB_URL`, `RFQ_DB_USERNAME`, `RFQ_DB_PASSWORD` | MySQL connection. Flyway applies migrations on startup; Hibernate validates the resulting schema. |
| `GATEWAY_SHARED_SECRET` | At least 32 random characters; must match the API Gateway. Required for application routes other than health and the internal scanner route. |
| `IDENTITY_INTERNAL_SHARED_SECRET` | Separate shared secret for RFQ-to-Identity internal authorization calls. Must match Identity Service. |
| `EUREKA_SERVER_URL` | Eureka endpoint. The example points to authenticated local Eureka at `http://localhost:8761/eureka/`. |
| `RFQ_STORAGE_PROVIDER` | `local` for development (default) or `s3` for AWS. The `prod` profile defaults to `s3`. |
| `RFQ_STORAGE_DIR` | Private filesystem root for local development storage; S3 mode uses the operating system's temporary directory for short-lived file validation. |
| `AWS_REGION`, `RFQ_S3_BUCKET`, `RFQ_S3_PREFIX` | Region, private bucket name, and object prefix when `RFQ_STORAGE_PROVIDER=s3`. The ECS task role supplies credentials; do not configure static AWS keys in production. |
| `RFQ_DEFAULT_TIME_ZONE` | IANA timezone used for new requests without an explicit timezone. Default: `Asia/Kolkata`. Existing RFQs retain their saved timezone. |
| `RFQ_ATTACHMENT_SCANNER_TOKEN` | At least 32 random characters, shared with the trusted scanner process. Files remain unavailable to users until marked `CLEAN`. |
| `RFQ_IMPORT_PREVIEW_TTL_MINUTES` | Lifetime of a staged import preview. Default: 30 minutes. |

The default `dev` profile uses a local MySQL URL for `global_rfq`; set `RFQ_DB_URL` to override it. The `prod` profile requires `RFQ_DB_URL` explicitly. In IntelliJ, configure `RFQ_DB_URL` in the RFQ Service run configuration and remove any stale or malformed `SPRING_DATASOURCE_URL`: Spring gives that standard property higher precedence than the YAML value. The startup check reports this override clearly if the effective URL does not begin with `jdbc:`. A plain `.env` file is not loaded automatically.

The sample service port is `8082`. Application APIs should be called through the API Gateway (`8080` by default). The internal scanner endpoint is intentionally not routed through the Gateway and is intended for trusted scanner-to-service communication only.

Attachments support PDF, Word, Excel, CSV, text, PNG, and JPEG files. The API infers and verifies the media type from file content; clients select a business category, not a MIME type. A caller may include `lineNumber` when uploading a line-specific product image, drawing, or quotation support file. The source RFQ document can remain attached at RFQ level. Bytes are stored locally in development or under `global-rfq/attachments/` and `global-rfq/imports/` in S3 in AWS mode. Attachment metadata and the SHA-256 digest remain in MySQL. Downloads are authorized by RFQ Service and blocked until the trusted scanner marks the file `CLEAN`; no public or presigned S3 URL is issued. The PDF importer extracts text only; scanned/image-only PDFs need OCR or manual field correction during import review.

## Start and compile

Start MySQL, Eureka, Identity Service, and API Gateway first. Configure the RFQ environment variables, then run:

```powershell
cd backend/rfq-service
.\mvnw.cmd spring-boot:run
```

Compile only:

```powershell
.\mvnw.cmd -DskipTests compile
```

This service expects the Gateway to inject `X-Gateway-Secret`. Do not place that secret in Postman or expose it to the browser.

## Postman

Import [`GlobalRFQ-RFQ-Service.postman_collection.json`](../postman/GlobalRFQ-RFQ-Service.postman_collection.json) and follow the complete role-by-role flow in [`docs/RFQ_POSTMAN_GUIDE.md`](docs/RFQ_POSTMAN_GUIDE.md). Use separate valid Identity access tokens for Admin, Quotation Manager, Reviewer, and Marketing Executive. The collection does not contain gateway secrets.

## Deployment notes

Before production rollout, provision the private S3 bucket and IAM task role, connect a real malware scanning worker, define backups and retention, migrate existing local objects if any, and exercise recovery and concurrent-update behavior in a production-like environment. The code contains the S3 storage adapter, but it has not been tested against your AWS account/bucket. AWS setup and rollout steps are in [`docs/RFQ_AWS_DEPLOYMENT.md`](docs/RFQ_AWS_DEPLOYMENT.md). See the Postman guide for the scanner callback contract and current API scope.
