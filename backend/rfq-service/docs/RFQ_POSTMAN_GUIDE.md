# RFQ Service API Testing Guide for Postman

This guide describes the RFQ backend API exposed through the API Gateway. It is intended for manual API verification and bug discovery. It does not change or test the frontend.

## 1. Required services and accounts

Start these services in order:

1. MySQL with the RFQ schema account configured.
2. Eureka Server.
3. Identity Service.
4. API Gateway.
5. RFQ Service.

RFQ Service runs on port `8082` by default; Gateway runs on `8080`. Call business endpoints through Gateway at `http://localhost:8080`. Flyway runs migrations on RFQ Service startup. The `GATEWAY_SHARED_SECRET` must be the same in Gateway and RFQ Service, and `IDENTITY_INTERNAL_SHARED_SECRET` must match the Identity Service internal token. Do not send either secret from Postman; Gateway injects its private service header.

Use valid access tokens from Identity Service for separate users with these roles:

- `ADMIN` (or `SUPER_ADMIN`): import and assign RFQs, update rules, record manual send/customer decisions, resolve escalations, assign Marketing, and view reports.
- `QUOTATION_MANAGER`: edit drafts and submit immutable quotation versions while currently assigned. Can assign a reviewer only on an RFQ where Admin explicitly grants that permission.
- `REVIEWER`: review versions only while currently assigned.
- `MARKETING_EXECUTIVE`: access an RFQ after Admin assigns it following customer approval.

Each operation is also checked against the current Identity account and roles. A valid token alone does not give access to an RFQ: non-admin users need the required active assignment. Reassigned users may read historical records where supported, but cannot edit the current draft or review a version unless currently assigned.

## 2. Import the collection and configure variables

Import `backend/postman/GlobalRFQ-RFQ-Service.postman_collection.json` into Postman. Create a local environment (recommended) and set:

| Variable | Example / purpose |
| --- | --- |
| `baseUrl` | `http://localhost:8080` |
| `adminToken` | Identity access token for an Admin |
| `quotationManagerToken` | Identity access token for the assigned Quotation Manager |
| `reviewerToken` | Identity access token for the assigned Reviewer |
| `marketingToken` | Identity access token for Marketing Executive |
| `rfqId` | Filled from the RFQ response after import confirmation |
| `importPreviewToken` | Filled from the import preview response |
| `quotationVersionId` | Filled after submission |
| `reviewerReassignmentRequestId` | Filled after a Reviewer requests reassignment |
| `attachmentId` | Filled from an uploaded attachment response |
| `quotationManagerUserId` | Active Identity user ID to assign |
| `reviewerUserId` | Active Identity user ID to assign |
| `replacementReviewerUserId` | A different active Identity user with the `REVIEWER` role |
| `marketingUserId` | Active Identity user ID to assign |
| `sourceRfqId` | Source PR/RFQ number, e.g. `112477` |
| `sourceSystem` | e.g. `CUSTOMER_SOFTWARE` |
| `scannerBaseUrl` | `http://localhost:8082` for trusted scanner-contract checks only |
| `scannerToken` | Same local scanner token configured in RFQ Service; keep private |

Identity tokens can be obtained through the Identity collection's Login request. Login separately as each role and copy the returned access token into the corresponding variable. The RFQ collection deliberately never sends `X-Gateway-Secret`.

## 3. Recommended end-to-end flow

Run the requests below in order for the main customer workflow:

1. Admin previews a customer `.xlsx`, `.xls`, or text-based `.pdf` file.
2. Admin checks the detected fields, manager hint, timezone warning, raw-data preservation, and mapping warnings. If needed, correct `normalizedRfq` in the confirm request while keeping its line IDs/quantities valid.
3. Admin confirms the preview token. RFQ and attachment metadata are committed in one database transaction, and the original source file is integrity-checked from staging. Rollbacks remove the promoted file; a periodic cleanup removes expired/orphaned staging files. The token is one-time and expires after the configured preview TTL (default 30 minutes).
4. Admin assigns the active Quotation Manager and Reviewer. If Admin grants reviewer assignment permission on that RFQ, its assigned Manager can assign or reassign only the Reviewer.
5. The Quotation Manager saves a draft and submits a version with a unique `Idempotency-Key`.
6. The Reviewer approves or rejects that submitted version. Approval is blocked if the submitted version's snapshotted mandatory fields are missing. Rejection requires a reason.
7. If rejected below the default formal rejection limit of 3, the Manager edits a new draft and submits another version. At the configured limit the RFQ enters `ESCALATED`; Admin must authorize another revision or close it.
8. After reviewer approval, Admin sends the approved quotation through the existing customer software, then records that send in RFQ Service. RFQ Service does not send it to the customer's original system itself.
9. After the customer responds in the existing software, Admin records `APPROVED`, `REJECTED`, or `CLARIFICATION_REQUIRED` in RFQ Service. For `APPROVED`, Admin assigns the RFQ to an active `MARKETING_EXECUTIVE`.
10. Admin can review audit history, download an RFQ export, and create operational reports.

## 4. Endpoint reference

All routes below are relative to `{{baseUrl}}`. Unless noted otherwise, requests require `Authorization: Bearer <role token>` and the Gateway forwards them to RFQ Service. JSON requests use `Content-Type: application/json`. Success bodies are wrapped as `{ "success": true, "message": "...", "data": ..., "correlationId": "..." }`. Exports/downloads return binary response bodies.

### Intake, lookup, assignments, and workflow

| Method and path | Role | Purpose / expected success |
| --- | --- | --- |
| `POST /api/rfqs/imports/preview` | Admin | `multipart/form-data`: `file`, optional `sourceSystem`, optional `sourceRfqId`. Returns parsed/mapped request, warnings, `previewToken`, and `expiresAt`. |
| `POST /api/rfqs/imports/confirm` | Admin who created preview | JSON `{ "previewToken": "...", "normalizedRfq": null }` (or an edited valid `CreateRfqRequest`). Creates RFQ plus source-file attachment; returns RFQ and attachment. |
| `POST /api/rfqs` | Admin | Create an RFQ manually from JSON without requiring a file. Returns HTTP 201. In the UI, Admin can choose “Enter details manually”; attaching a source document is optional and, when selected, is uploaded after RFQ creation. |
| `GET /api/rfqs?page=0&size=20` | Admin / assigned user | Paginated RFQs. Admin sees all; other users see assigned RFQs. `size` is 1-100. |
| `GET /api/rfqs?page=0&size=20&search=112477&status=IMPORTED&sort=createdAt` | Admin / assigned user | Server-side search by RFQ number, customer, or request title; optional workflow status; sort by `createdAt` or `requestedDeliveryDate`. |
| `GET /api/rfqs/dashboard/summary` | Any authenticated role | Admin gets portfolio status totals; other roles get counts for RFQs associated with their user ID. |
| `GET /api/rfqs?page=0&size=20&sourceRfqId=112477` | Admin / assigned user | Exact lookup by customer RFQ number. Access remains scoped: Admin sees matching RFQs globally; other users see only matching RFQs assigned to them. |
| `GET /api/rfqs/{{rfqId}}` | Admin / assigned or historical participant | RFQ fields, line items, current assignments, and status. |
| `GET /api/rfqs/{{rfqId}}/history` | Admin / assigned participant | Append-only workflow event history. |
| `POST /api/rfqs/{{rfqId}}/assignments` | Admin; assigned Manager for reviewer only if delegated | Assign/reassign role `QUOTATION_MANAGER`, `REVIEWER`, or `MARKETING_EXECUTIVE`. Reassignment requires a reason. Marketing assignment only after customer approval. |
| `POST /api/rfqs/{{rfqId}}/reviewer-reassignment-requests` | Currently assigned Reviewer | Ask Admin to replace the Reviewer; requires a reason and `Idempotency-Key`. Only one request may be pending per RFQ. |
| `GET /api/rfqs/{{rfqId}}/reviewer-reassignment-requests` | Admin / requesting Reviewer | Admin sees all requests for the RFQ; a Reviewer sees only their own. |
| `POST /api/rfqs/{{rfqId}}/reviewer-reassignment-requests/{{requestId}}/resolve` | Admin | Approve or decline with an audit reason. Approval requires a different active Identity user ID with the `REVIEWER` role and performs the reassignment in the same transaction. |
| `GET /api/rfqs/{{rfqId}}/reviewer-assignment-delegation` | Admin | Read whether the Manager may assign the Reviewer on this RFQ. |
| `PATCH /api/rfqs/{{rfqId}}/reviewer-assignment-delegation` | Admin | Grant/revoke Manager reviewer assignment permission for this RFQ. Change is recorded in its workflow history. |
| `POST /api/rfqs/{{rfqId}}/escalation-resolution` | Admin | Resolve an escalated RFQ with `AUTHORIZE_REVISION` or `CLOSE`, and a reason. |
| `POST /api/rfqs/{{rfqId}}/customer-rejection-resolution` | Admin | Resolve customer rejection with `AUTHORIZE_REVISION` or `CLOSE`, and a reason. |

### Quotation and customer decision

| Method and path | Role | Purpose / expected success |
| --- | --- | --- |
| `PUT /api/rfqs/{{rfqId}}/quotation-draft` | Current assigned Manager | Create/update the mutable draft. Repeated saves update the draft; they do not create submitted versions. |
| `GET /api/rfqs/{{rfqId}}/quotation-draft` | Current assigned Manager | Get the current draft. |
| `POST /api/rfqs/{{rfqId}}/quotation-versions` | Current assigned Manager | Submit an immutable version. Requires `Idempotency-Key` (8-64 characters). Returns HTTP 201 and snapshots the configured mandatory fields. |
| `GET /api/rfqs/{{rfqId}}/quotation-versions` | Admin / historical participant | List versions newest first. |
| `GET /api/rfqs/{{rfqId}}/quotation-versions/{{quotationVersionId}}` | Admin / historical participant | Get an immutable version, mandatory-field snapshot, and any review decision. |
| `POST /api/rfqs/{{rfqId}}/quotation-versions/{{quotationVersionId}}/review` | Current assigned Reviewer | Record one `APPROVED` or `REJECTED` decision. Rejection requires `reason`; a version cannot receive a second decision. |
| `POST /api/rfqs/{{rfqId}}/customer-submissions` | Admin | Record that an internally approved version was manually sent via the existing customer software. Requires `Idempotency-Key`. |
| `POST /api/rfqs/{{rfqId}}/customer-decision` | Admin | Record customer response for the latest sent version. Requires `Idempotency-Key`. `APPROVED` is followed by Admin assigning `MARKETING_EXECUTIVE`. |

### Attachments and exports

| Method and path | Role | Purpose / expected success |
| --- | --- | --- |
| `POST /api/rfqs/{{rfqId}}/attachments` | Admin, current Manager, or current Reviewer by category | Multipart `file` plus `category`: `CUSTOMER_REQUEST`, `CUSTOMER_RESPONSE`, `QUOTATION_SUPPORT`, or `REVIEW_SUPPORT`; optional `lineNumber` associates a product image, drawing, or support document with one RFQ item. Media type is inferred from the file content. Upload is size/type/signature checked and queued as `PENDING_SCAN`. |
| `GET /api/rfqs/{{rfqId}}/attachments` | Admin / active assignee | List attachment metadata and scanner state. |
| `GET /api/rfqs/{{rfqId}}/attachments/{{attachmentId}}/download` | Admin / active assignee | Download only after scan state is `CLEAN`; uses attachment disposition and no-store headers. |
| `GET /api/rfqs/{{rfqId}}/export?format=xlsx` | Admin / authorized RFQ participant | Export RFQ and latest submitted quotation to Excel. Use `format=pdf` for PDF, optionally `quotationVersionId=<id>` to select a version. |
| `GET /api/reports/rfqs/summary?from=2026-10-01&through=2026-10-31&status=CUSTOMER_APPROVED` | Admin | Operational summary filtered by optional local date range/status. Date boundaries use `RFQ_DEFAULT_TIME_ZONE`. |
| `GET /api/reports/rfqs/export?format=xlsx&from=2026-10-01&through=2026-10-31` | Admin | Export report to `xlsx` or `pdf`, with the same filters and configured local timezone. |

### Admin configuration

| Method and path | Role | Purpose / expected success |
| --- | --- | --- |
| `GET /api/admin/rfq-rules/mandatory-fields` | Admin | List currently required fields. |
| `PUT /api/admin/rfq-rules/mandatory-fields` | Admin | Replace required-field keys; every submission snapshots the rules in effect at submission time. |
| `GET /api/admin/rfq-rules/rejection-limit` | Admin | Read formal rejection limit (default `3`). |
| `PATCH /api/admin/rfq-rules/rejection-limit` | Admin | Set limit from 1 to 20 with an audit reason. |
| `GET /api/admin/rfq-rules/audit` | Admin | Last 100 administrative rule changes, with before/after values and actor. |

### Internal scanner contract (trusted system only)

These routes are intentionally not available through the Gateway. They are not normal user APIs. Use only a local trusted scanner process, directly against `http://localhost:8082`, with `X-Attachment-Scanner-Token: {{scannerToken}}`:

- `GET /internal/attachment-scanner/pending` — list pending scans.
- `GET /internal/attachment-scanner/attachments/{{attachmentId}}/content` — retrieve raw attachment bytes for scanning.
- `POST /internal/attachment-scanner/attachments/{{attachmentId}}/result` — post `state: CLEAN` or `INFECTED`, scanner name and optional scan reference.

The API implements the secure callback contract, but a real antivirus engine/worker still needs to be deployed and connected. Until a trusted scanner posts `CLEAN`, user downloads correctly fail with a conflict. Do not manually mark unscanned production files clean just to make a test pass. For production AWS storage and migration guidance, see [`RFQ_AWS_DEPLOYMENT.md`](RFQ_AWS_DEPLOYMENT.md).

## 5. Main request examples

### Create directly without file import

```json
{
  "sourceSystem": "CUSTOMER_SOFTWARE",
  "sourceRfqId": "112477",
  "sourceReference": "EVENT-68798",
  "sourceQuotationManagerName": "PALLETI",
  "customerName": "V2X Systems Corporation",
  "customerCompany": "V2X Systems Corporation",
  "buyerName": "Marty Barclay",
  "buyerEmail": "v2x.rfqadmin@example.com",
  "buyerPhone": null,
  "requestTitle": "Request for Quote 112477",
  "dueAtLocal": "2026-10-22T08:00:00",
  "dueTimeZone": "Asia/Kolkata",
  "requestedDeliveryDate": "2026-12-31",
  "shipToText": "OAKLAND CROSS-DOCK, 9757 SAN LEANDRO ST, OAKLAND, CA 94603, USA",
  "sourceUrl": "https://example.invalid/rfq/68798",
  "termsAndConditions": "Net 60. No substitutions without buyer approval.",
  "sourceData": { "importedQuotationManagerHint": "PALLETI" },
  "lineItems": [
    {
      "sourceLineId": "1",
      "lineNumber": 1,
      "description": "Motor, AC, 3 phase, 5.5 kW, 1000 RPM, 6 pole, IEC 132M, IP55",
      "requestedQuantity": 2,
      "unitOfMeasure": "EA",
      "customerPartNumber": "6105-09-V30-0008",
      "requestedManufacturer": null,
      "requestedPartNumber": null,
      "procurementCategory": "Heavy Equipment",
      "taaRequired": true,
      "technicalSpecifications": "Customer's full technical description",
      "sourceData": { "unmappedCustomerColumn": "source value" }
    }
  ]
}
```

`dueAtLocal` is a local wall-clock value; do not append `Z`. Use `Asia/Kolkata` for the current default unless the RFQ explicitly supplies another IANA timezone. Changing `RFQ_DEFAULT_TIME_ZONE` affects new records/imports only; it does not convert existing RFQ deadlines.

### Import preview and confirm

1. Send `POST /api/rfqs/imports/preview` as form-data: `file` = source workbook/PDF, `sourceSystem` = `CUSTOMER_SOFTWARE`, `sourceRfqId` = `112477` if the parser cannot reliably infer it.
2. Review `data.normalizedRfq`, `data.fieldMappings`, and `data.warnings`. `sourceQuotationManagerName` is only a source hint; it does not automatically map a free-text name to an Identity user.
3. Copy `data.previewToken` to `importPreviewToken`.
4. Send JSON to confirm:

```json
{
  "previewToken": "{{importPreviewToken}}",
  "normalizedRfq": null
}
```

If correcting fields, replace `null` with a complete valid `CreateRfqRequest`. Preserve each `sourceLineId` and line reference. The original parsed source data remains preserved under import metadata, and the original file is attached to the imported RFQ. A token is valid only once, for the Admin that created it, and until `expiresAt`.

### Attach a product image or line drawing

Send `POST /api/rfqs/{{rfqId}}/attachments` as multipart form-data with `file` set to the image/document, `category=QUOTATION_SUPPORT`, and `lineNumber=1`. Use the currently assigned Quotation Manager token (or Admin token). Supported image extensions are PNG and JPEG; supported document extensions include PDF, Word, and Excel. The server checks file content rather than trusting the browser's MIME label. The response identifies the line number and starts at `PENDING_SCAN`; download stays unavailable until the trusted scanner records `CLEAN`. Omit `lineNumber` for RFQ-wide documents such as the original customer request.

### Assignment and optional delegation

```json
{
  "assignmentRole": "QUOTATION_MANAGER",
  "assignedUserId": 42,
  "reason": null
}
```

Repeat with `assignmentRole: "REVIEWER"` and a valid reviewer ID. Use `reason` when replacing an existing active assignee. For Marketing, use `MARKETING_EXECUTIVE`; it is rejected until status is `CUSTOMER_APPROVED`. To let the assigned Manager choose the Reviewer for this RFQ, Admin calls `PATCH /api/rfqs/{{rfqId}}/reviewer-assignment-delegation`:

```json
{
  "quotationManagerCanAssignReviewer": true,
  "reason": "Allow this RFQ's assigned Manager to select its Reviewer"
}
```

The setting is per RFQ and defaults to false. It does not allow a Manager to assign a different Manager, Marketing, or any user on an RFQ they do not currently own. Admin can revoke it; existing assignments remain, but the Manager cannot change the Reviewer afterward.

The current Reviewer may request reassignment. Send `Idempotency-Key: rfq-112477-review-reassign-v1`; reuse it only to retry the same request body. A changed reason requires a new key.

```json
{ "reason": "I have a conflict of interest for this request." }
```

Admin lists the requests, then approves with a replacement reviewer (Identity eligibility is checked) or declines:

```json
{
  "decision": "APPROVE",
  "replacementReviewerId": 43,
  "reason": "Reassigned to an independent reviewer."
}
```

```json
{
  "decision": "DECLINE",
  "reason": "The current assignment remains appropriate."
}
```

The request itself does not change RFQ status or reviewer decision. Approval creates a new active assignment; the previous assignment and both request and decision remain in audit history.

### Save draft and submit a version

Use each RFQ source line's `sourceLineId` from the intake response. Draft body:

```json
{
  "currency": "USD",
  "items": [
    {
      "sourceLineId": "1",
      "description": "5.5 kW AC motor",
      "quantity": 2,
      "unitPrice": 1851.43,
      "unitOfMeasure": "EA",
      "manufacturer": "ABB",
      "partNumber": "EMM13556-S-A",
      "taaCompliant": true,
      "deliveryTimeline": "15 business days",
      "warranty": "30 days",
      "note": "Supplier reference: 314184",
      "productUrl": "https://example.invalid/product"
    }
  ],
  "freight": 0,
  "otherCharges": 0,
  "discount": 0,
  "taxAmount": 0,
  "deliveryTimeline": "15 business days",
  "warranty": "30 days",
  "notes": "Quote valid for 30 days"
}
```

Send this first with `PUT /quotation-draft`, then with `POST /quotation-versions` and header `Idempotency-Key: quote-112477-v1`. Re-send the exact same submission with the same key to safely retry after a timeout. If payload changes, use a new key. Totals are calculated server-side: line total = quantity × unit price; grand total = subtotal + freight + other charges + tax − discount. Values use two decimal places. A quote with missing price fields can be submitted, but its computed totals remain incomplete and a Reviewer cannot approve when any snapshotted required fields are missing.

### Review, send to customer, and record decision

Approve:

```json
{ "decision": "APPROVED", "reason": null }
```

Reject (reason required):

```json
{ "decision": "REJECTED", "reason": "Confirm motor enclosure rating and revise lead time." }
```

Once approved, Admin sends the quotation using the existing external software, then records that completed action:

```json
{
  "quotationVersionId": 1,
  "externalReference": "CUSTOMER-EVENT-68798",
  "notes": "Sent through the existing customer portal by Admin."
}
```

Use `Idempotency-Key: rfq-112477-send-v1`. After receiving the customer's response externally, record for the same latest sent version:

```json
{
  "decision": "APPROVED",
  "quotationVersionId": 1,
  "notes": "Customer approval received in source software.",
  "sourceReference": "CUSTOMER-EVENT-68798"
}
```

Use a different key, e.g. `rfq-112477-decision-v1`. Once status becomes `CUSTOMER_APPROVED`, assign a user whose Identity role is `MARKETING_EXECUTIVE`.

### Admin rule bodies

```json
{
  "requiredFields": ["currency", "items.quantity", "items.unitPrice", "deliveryTimeline", "warranty"],
  "reason": "Require the approved quote fields for the next submissions"
}
```

Supported keys include `currency`, `freight`, `otherCharges`, `discount`, `taxAmount`, `items`, `deliveryTimeline`, `warranty`, `notes`, and item fields `items.description`, `items.quantity`, `items.unitPrice`, `items.unitOfMeasure`, `items.manufacturer`, `items.partNumber`, `items.taaCompliant`, `items.deliveryTimeline`, `items.warranty`, `items.note`, `items.productUrl`.

`items.taaCompliant` may be added to the Admin mandatory-field configuration. It is not mandatory by default. A submitted `true` records the quotation manager's compliance statement; RFQ Service does not independently certify supplier claims or require evidence until the client defines that policy.

```json
{ "maxFormalRejections": 3, "reason": "Set the pilot escalation threshold" }
```

```json
{ "quotationManagerCanAssignReviewer": false, "reason": "Return this RFQ's reviewer assignment to Admin control" }
```

Rule changes are audited. Each submitted version retains the mandatory-field rules in effect at its submission time.

### Attachments

Use `multipart/form-data` with `file` plus `category`. Supported categories are `CUSTOMER_REQUEST`, `CUSTOMER_RESPONSE`, `QUOTATION_SUPPORT`, and `REVIEW_SUPPORT`. Supported extensions are PDF, Word, Excel, CSV, TXT, PNG, and JPEG (subject to content checks and 25 MB default size limit). The file is stored privately under a random storage key; the original name is metadata only. Uploads start `PENDING_SCAN`. The list API returns scanner state; download should fail before a `CLEAN` result and be denied for `INFECTED`.

Scanner result example (trusted local scanner only):

```json
{ "state": "CLEAN", "scannerName": "local-test-scanner", "scanReference": "scan-2026-001" }
```

For a negative scanner test, use `INFECTED` and verify the download is denied. The scanner token must be configured in the RFQ process; otherwise these internal endpoints return a service-unavailable error. The scanner callback contract is not a substitute for a production malware-scanning engine.

### Client update: categories, TAA and long V2X values

The client review email requests support for `TAA Compliant`, `Heavy Equipment`, `Consumables`, `Materials`, `Tools`, and future categories. Each RFQ line now supports a flexible `procurementCategory` text value instead of a closed enum. This accepts new category labels without a code/schema release. The category appears in the RFQ API response, both workbook tabs, and PDF export.

TAA is separately represented as `taaRequired` on an imported customer line and `taaCompliant` on a quoted line. Both are nullable: `null` means that the source/manager did not provide an answer. `taaRequired: true` means the customer request requires a compliant offer; `taaCompliant: true` records the manager's supplier claim. Excel import recognizes headers such as `Category`, `Commodity`, `Product Category`, `TAA`, `TAA Compliant`, and `TAA Required`. Yes/no, true/false, 1/0, required/not-required, and compliant/non-compliant values map to booleans. If a TAA source value is not recognizable, the preview warns and the original source value remains preserved.

V2X customer part numbers, requested manufacturer/part numbers, and quoted manufacturer/part numbers accept 255 characters. The backend and export logic do not shorten those values. Excel uses wrapped text and bounded widths; PDF display wraps long words across lines without dropping characters.

The imported source workbook is also preserved as an attachment, including its original sheets and formatting. Generated workbooks retain the request and quotation tabs (`Sheet1` and `Sheet2`).

Postman cases: import a line with `procurementCategory: "Consumables"`, `taaRequired: true`, then quote it with `taaCompliant: true`; repeat with null values and an unknown category, and verify all values in GET RFQ plus XLSX/PDF exports. Include a 34-character part number with no spaces to verify wrapping without truncation. The API does not block reviewer approval solely because a TAA value is false or absent; configure `items.taaCompliant` as mandatory if the business chooses that policy.

## 6. Negative and security scenarios to run

Check these explicitly in Postman. Error bodies include `success: false`, numeric `status`, stable `code`, `message`, `path`, timestamp, and correlation ID.

1. Missing/expired access token on a protected API → `401 UNAUTHORIZED`.
2. Valid token without required role or assignment → `403 ACCESS_DENIED`.
3. Call a protected RFQ Service route directly without Gateway header → `403 ACCESS_DENIED` (do not send Gateway secret in Postman to make this pass).
4. Admin imports unsupported file extension, corrupt Excel/PDF, ambiguous/invalid item headers, no source RFQ identifier, or missing company → validation error; no RFQ is created.
5. Confirm unknown, expired, already consumed, or another Admin's preview token → not found, invalid transition, or forbidden; no duplicate RFQ is created.
6. Import same `sourceSystem` + `sourceRfqId` twice → conflict; existing record remains intact.
7. Assign inactive/wrong-role Identity user → validation/access error; assignment is not created.
8. Reassign without a reason → validation error. Old Manager may not save or submit after reassignment.
9. Manager tries to assign Reviewer before Admin grants permission for that RFQ, or tries to assign a role other than Reviewer after the grant → forbidden.
10. Submit quotation with a line ID not present on the RFQ → validation error.
11. Submit without `Idempotency-Key` or with a key shorter than 8 chars → request validation error. Reuse the same key with changed payload → conflict; exact replay should return the existing version.
12. Reviewer tries to approve with missing snapshotted required fields → conflict. Rejecting without a reason → validation error.
13. Try to alter a submitted version; there is intentionally no update endpoint. Submitting after reviewer approval without an authorized revision path → invalid workflow transition.
14. Reviewer submits a second decision for the same version → conflict.
15. Record customer send before reviewer approval, or for an unapproved version → invalid workflow transition.
16. Record customer decision before send, for a version other than latest sent, or with `PENDING` → invalid workflow/validation error.
17. Assign Marketing before customer approval → invalid workflow transition.
18. Upload a wrong extension/signature pair, encrypted/invalid PDF, unsupported type, empty file, or oversized file → rejected; no usable attachment is created.
19. Download `PENDING_SCAN` or `INFECTED` attachment → denied/unavailable; only `CLEAN` downloads.
20. Export with invalid format or unknown quotation version → `400` or `404` as applicable.
21. Report with start date later than end date or invalid status → `400 VALIDATION_FAILED` / `BAD_REQUEST`.
22. Use `size=101` in RFQ listing → validation error; only 1-100 rows per page are allowed.
23. A Reviewer without an active assignment, an already-pending duplicate request, a non-Admin resolution, a replacement user without active `REVIEWER` role, or resolution of an already-resolved request must fail without changing the active assignment.

Capture the response status, body, request URL, correlation ID, and server log entry when reporting a defect. Never include passwords, access tokens, or service secrets in bug reports.

## 7. Error codes and expected HTTP status

| Code | HTTP | Common cause |
| --- | ---: | --- |
| `BAD_REQUEST` | 400 | Malformed request/query or unsupported export format |
| `VALIDATION_FAILED` | 400 | Missing/invalid field, invalid enum, missing rejection reason |
| `PAYLOAD_TOO_LARGE` | 413 | Upload exceeds configured request limit |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | Wrong content type (e.g. multipart confirm instead of JSON) |
| `UNAUTHORIZED` | 401 | Missing, invalid, or expired bearer token |
| `ACCESS_DENIED` | 403 | Role, assignment, Gateway, or scanner authorization failed |
| `RESOURCE_NOT_FOUND` | 404 | RFQ, version, attachment, or preview token not found |
| `DUPLICATE_RESOURCE` | 409 | Duplicate source RFQ, reused key with different request, or uniqueness conflict |
| `INVALID_WORKFLOW_TRANSITION` | 409 | Action is not allowed at the current status |
| `CONCURRENT_MODIFICATION` | 409 | Optimistic lock conflict; reload before retrying |
| `IDENTITY_UNAVAILABLE` | 503 | RFQ Service cannot reach Identity for live validation |
| `INTERNAL_ERROR` | 500 | Unexpected failure; retain correlation ID for diagnosis |

## 8. Current API boundaries and production follow-up

- Admin manually records the send to, and response from, the existing customer software. This RFQ API does not integrate with that external system.
- Email/SES and automated user notifications are not connected.
- The attachment scanner endpoints are a protected internal integration contract; a trusted malware scanner must be deployed before production use. Files remain blocked until clean.
- Files currently use a private local filesystem storage directory. Multi-instance production should use durable shared object storage and a retention/backup policy.
- The current report endpoints provide status/decision totals, date filtering, and active assignment workload; advanced turnaround/approval-rate analytics can be added after business definitions are confirmed.
- RFQ due timestamps preserve a timezone identifier per record; report date filters use the configurable `RFQ_DEFAULT_TIME_ZONE=Asia/Kolkata`. Changing the environment variable does not rewrite existing records.
- `PALLETI` is imported as a quotation-manager name hint. Admin must resolve it to an active Identity user before assigning.
