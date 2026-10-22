# Backend service platform: gateway and discovery

## Current request path

```mermaid
flowchart LR
    Client[Browser or Postman] --> Gateway[API Gateway :8080]
    Gateway -->|service lookup| Eureka[Eureka :8761]
    Gateway -->|lb://identity-service| Identity[Identity Service :8081]
    Identity --> MySQL[(Identity MySQL database)]
    Identity --> Eureka
    Gateway --> Eureka
```

The gateway is the public API entry point. It has an explicit route allowlist for the identity API; Eureka's automatic route generation is disabled, so registering a new backend service does not expose it automatically. Identity continues to validate JWTs and enforce role/ownership rules. The gateway supplies the private `X-Gateway-Secret` header; callers must not know or send that secret.

Identity and the gateway register with a Eureka server protected by HTTP Basic credentials. The Eureka UI and registry API require authentication. Only the Eureka health endpoint is unauthenticated for infrastructure probes. Production must keep the registry on a private network and use HTTPS/TLS for registry traffic.

Redis and Docker have been removed for the current local API-testing phase. The gateway therefore has no distributed per-IP rate limiter right now. Identity still applies account lockout and database-backed password-reset OTP limits, but this temporary configuration is not a production release configuration. Before production, restore a shared gateway rate limiter (Redis or a managed equivalent) or enforce equivalent limits at a trusted edge/WAF. Configure `TRUSTED_PROXY_COUNT` only when the gateway accepts traffic exclusively through the stated trusted proxy chain; the default uses the network peer address and ignores caller-supplied forwarding headers.

## Local startup

Requirements: Java 21 and a reachable MySQL database for identity-service. No Docker or Redis process is required. Maven Wrapper scripts are checked in for each Java service.

1. Generate the gateway secret once, then copy its printed value to the `GATEWAY_SHARED_SECRET` environment variable in both the identity and gateway terminals. It is a local development secret and must not be committed.

   ```powershell
   $bytes = New-Object byte[] 32
   $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
   $rng.GetBytes($bytes)
   $env:GATEWAY_SHARED_SECRET = [Convert]::ToBase64String($bytes)
   $env:GATEWAY_SHARED_SECRET
   $rng.Dispose()
   ```

   In the identity-service and api-gateway terminals, set `$env:GATEWAY_SHARED_SECRET` to that same generated value.

2. Start `backend/eureka-server` in a terminal:

   ```powershell
   Set-Location backend/eureka-server
   .\mvnw.cmd spring-boot:run
   ```

   Local Eureka credentials default to username `eureka` and password `eureka`. For a local override, set the same `EUREKA_USERNAME` and `EUREKA_PASSWORD` in the Eureka server, identity-service, and api-gateway run configurations. Both clients build their local Eureka URL from those values unless `EUREKA_SERVER_URL` is explicitly set. If you set `EUREKA_SERVER_URL`, give the identical URL, including URL-encoded credentials, to both clients.

3. Start `backend/identity-service` in a second terminal. Set its database and JWT variables (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and a Base64 `JWT_SECRET` with at least 32 decoded bytes), plus the gateway secret above. Its `.env.example` lists the identity variables. Maven does not load `.env` files automatically; pass variables through the terminal or IDE run configuration. For API testing, leave `EMAIL_PROVIDER=mock`; mock messages and test tokens are available in the dev application logs.

   ```powershell
   Set-Location backend/identity-service
   .\mvnw.cmd spring-boot:run
   ```

4. Start `backend/api-gateway` in a third terminal with the same gateway secret and Eureka credentials/URL as identity-service. Its default Eureka URL targets the local registry. No Redis URL is required.

If Identity shows a successful Eureka registration (`204`) but Gateway reports `No servers available for service: identity-service`, verify the Gateway's own `EUREKA_SERVER_URL` and credentials first: registration confirms only that Identity reached its configured registry. Then verify that `IDENTITY-SERVICE` appears in that same registry and restart Gateway after changing its Eureka settings so its discovery client reloads the correct registry.

   ```powershell
   Set-Location backend/api-gateway
   .\mvnw.cmd spring-boot:run
   ```

The gateway health check is `http://localhost:9080/actuator/health`. Eureka's authenticated dashboard is `http://localhost:8761`; its health check is `http://localhost:8762/actuator/health`. The registry should show `API-GATEWAY` and `IDENTITY-SERVICE` after both clients register.

Identity health is available at `http://localhost:8081/actuator/health`. The application API should still be called through the gateway at `http://localhost:8080` so the gateway-secret service-hop check stays active.

## Postman entry point

Use `http://localhost:8080` as the Postman environment's `baseUrl`. Keep identity paths unchanged, for example:

- `POST {{baseUrl}}/api/auth/login`
- `POST {{baseUrl}}/api/auth/refresh`
- `GET {{baseUrl}}/api/auth/me`
- `GET {{baseUrl}}/api/users`
- `GET {{baseUrl}}/api/roles`
- `GET {{baseUrl}}/api/audit-logs`

Postman should send the user access token as `Authorization: Bearer {{accessToken}}` on protected endpoints. Let its cookie jar retain the HttpOnly refresh cookie. Do not add `X-Gateway-Secret` to Postman. The gateway adds that header on the private service hop. `X-Correlation-Id` is optional; the gateway validates or generates it and returns it in the response.

The app endpoints are routed only under `/api/auth/**`, `/api/users/**`, `/api/roles/**`, and `/api/audit-logs/**`. Unknown paths are not dynamically routed. Gateway management and health endpoints use port 9080 and are not exposed through the public API port 8080.

## Production configuration and limits

Set `SPRING_PROFILES_ACTIVE=prod` and inject secrets through the deployment secret manager. Gateway requires `GATEWAY_SHARED_SECRET` (at least 32 characters), `EUREKA_SERVER_URL`, and `CORS_ALLOWED_ORIGINS`. Identity additionally requires its database, JWT, and outbox encryption secrets. Eureka requires `EUREKA_USERNAME` and `EUREKA_PASSWORD`; clients use an authenticated `EUREKA_SERVER_URL` such as `https://user:encoded-password@registry.internal/eureka/`. URL-encode special characters in URL credentials. Use TLS and private network rules for gateway-to-service, service-to-Eureka, and database traffic.

Keep identity's port private in deployment security groups/network policy. The shared header is a second service-hop check and does not replace network isolation. Health probes can reach identity directly by design; application API paths require the gateway secret.

This repository now has a single Eureka server instance for development and initial integration. A single registry instance is a production availability risk. Before production launch, deploy at least two peer-aware Eureka nodes across failure domains (or use a managed service registry), put the gateway behind a TLS-terminating load balancer, and restore shared edge rate limiting. Prometheus endpoints expose metrics only; scrape them from a private monitoring network.

Routes are intentionally maintained as an allowlist. When another service is created, add its routes and its private-hop authentication deliberately. Keep business authorization in each owning service; the gateway is not a substitute for service-side security.
