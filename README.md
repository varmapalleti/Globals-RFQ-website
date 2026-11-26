# Global RFQ Management System

Enterprise-grade Request for Quotation (RFQ) lifecycle management platform built with Spring Boot 4 / Spring Cloud microservices, MySQL 8, Flyway migrations, and a React 19 + TypeScript + TanStack Router frontend.

## Architecture

- **Frontend (`frontend/`)**: React 19, TypeScript, Vite, TanStack Router, TanStack Query, Tailwind CSS, Zod + React Hook Form (`http://localhost:5173`)
- **API Gateway (`backend/api-gateway/`)**: Spring Cloud Gateway WebFlux, Resilience4j circuit breakers, bounded token-bucket IP rate limiter, CORS & gateway secret propagation (`http://localhost:8080`)
- **Eureka Server (`backend/eureka-server/`)**: Private service discovery registry (`http://localhost:8761`, management `8762`)
- **Identity Service (`backend/identity-service/`)**: Authentication, JWT access + HttpOnly refresh token rotation, email verification, OTP password reset, RBAC (`SUPER_ADMIN`, `ADMIN`, `QUOTATION_MANAGER`, `REVIEWER`, `MARKETING_EXECUTIVE`, `VIEWER`), audit logging (`http://localhost:8081`)
- **RFQ Service (`backend/rfq-service/`)**: RFQ intake & Excel/PDF import preview/confirm, assignments & reviewer delegation, quotation drafts & immutable versions, configurable mandatory fields & rejection threshold escalation, customer submission/decisions, marketing handoff, red flags, purchase orders, in-app notifications, Excel/CSV/PDF exports (`http://localhost:8082`)

## Quick Start (Docker Compose)

1. Copy the environment template:
   ```bash
   cp .env.docker.example .env
   ```
   Update the placeholder secrets in `.env` before non-local deployment.
2. Start the entire stack (MySQL 8, Eureka, Identity, RFQ, Gateway, Frontend):
   ```bash
   docker compose up --build
   ```
3. Access the application:
   - **Web UI**: http://localhost:5173
   - **API Gateway**: http://localhost:8080
   - **Eureka Registry**: http://localhost:8761
   - **Identity OpenAPI / UI** *(non-prod profiles)*: http://localhost:8081/swagger-ui.html (`/v3/api-docs`)
   - **RFQ OpenAPI / UI** *(non-prod profiles)*: http://localhost:8082/swagger-ui.html (`/v3/api-docs`)

## Verification Commands

### Frontend
```bash
cd frontend
npm ci
npx vite build
npm run typecheck
npm run lint
npm test
npm run build
```

### Backend
```bash
cd backend/api-gateway && ./mvnw test
cd backend/rfq-service && ./mvnw test
cd backend/identity-service && ./mvnw test-compile
cd backend/eureka-server && ./mvnw test-compile
```
