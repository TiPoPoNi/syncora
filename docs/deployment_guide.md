# Production Deployment & Hosting Guide
## Project Resource Allocation & Scheduling System (PRS)

This document provides complete instructions for deploying the **Project Resource Allocation & Scheduling System** to production hosting platforms (Render, Railway, Docker Swarm, or AWS ECS) using secure, cost-conscious, containerized infrastructure.

---

## 1. System Architecture & Topology

The production application consists of 4 containerized services communicating over an internal private network:

```
[Public Internet]
       │
       ▼ (HTTPS :443)
┌──────────────────────────────────────────────┐
│ Reverse Proxy / CDN (Caddy / Nginx / Cloud) │
└──────────────────────┬───────────────────────┘
                       │
       ┌───────────────┴───────────────┐
       ▼                               ▼
┌──────────────┐               ┌──────────────┐
│   Frontend   │               │   Backend    │
│ (Nginx :80)  │               │ (Spring Boot │
│  React/Vite  │               │    :8080)    │
└──────────────┘               └──────┬───────┘
                                      │
            ┌─────────────────────────┼─────────────────────────┐
            ▼                                                   ▼
┌─────────────────────────┐                   ┌─────────────────────────┐
│     PostgreSQL 16       │                   │       AI Service        │
│   (Private Port :5432)  │                   │    (FastAPI :8000)      │
│   Volume: postgres_data │                   │   Internal network only │
└─────────────────────────┘                   └─────────────────────────┘
```

---

## 2. Production Environment Configuration

Create a `.env.production` file outside version control. **Never commit production secrets.**

```env
# Database Settings
POSTGRES_DB=prs_production
POSTGRES_USER=prs_db_user
POSTGRES_PASSWORD=generate_strong_64char_password_here
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/prs_production
SPRING_DATASOURCE_USERNAME=prs_db_user
SPRING_DATASOURCE_PASSWORD=generate_strong_64char_password_here

# Backend Security
JWT_SECRET=generate_strong_base64_hmac_sha256_key_with_at_least_256_bits
JWT_EXPIRATION_MS=86400000
CORS_ALLOWED_ORIGINS=https://prs-app.yourdomain.com

# AI Microservice (Optional - falls back to deterministic heuristic mode)
AI_SERVICE_URL=http://ai-service:8000
OPENAI_API_KEY=sk-proj-your-production-key-here

# Frontend Configuration
VITE_API_URL=https://prs-api.yourdomain.com/api
```

---

## 3. Docker Compose Production Deployment

The project root contains `docker-compose.yml` pre-configured for automated startup.

### A. Prerequisite Checks
Ensure Docker Engine $\ge 24.0$ and Docker Compose $\ge 2.20$ are installed:
```bash
docker --version
docker compose version
```

### B. Launching the Multi-Container Stack
```bash
# 1. Build and start all 4 services in detached mode
docker compose up -d --build

# 2. Verify container statuses
docker compose ps

# 3. Stream real-time logs
docker compose logs -f backend
```

### C. Automated Migrations & Health Checks
- **Flyway Migrations**: Run automatically on Spring Boot backend boot (`V1__init.sql` through `V6__performance_indexes.sql`).
- **Health Verification**:
  ```bash
  curl -I http://localhost:8080/actuator/health
  curl -I http://localhost:8000/health
  curl -I http://localhost:5173/
  ```

---

## 4. Single-Command Cloud Hosting Guides

### Option 1: Render.com (Recommended Free/Low-Cost Tier)
1. **PostgreSQL**: Create a new *PostgreSQL Database* on Render (Internal Database URL automatically created).
2. **Backend**: Create a new *Web Service* using Docker:
   - Root directory: `./backend`
   - Set environment variables: `SPRING_DATASOURCE_URL`, `JWT_SECRET`, `SPRING_PROFILES_ACTIVE=prod`.
3. **AI Service**: Create a new *Web Service* using Docker:
   - Root directory: `./ai-service`
   - Set environment variable: `OPENAI_API_KEY`.
4. **Frontend**: Create a *Static Site* or Docker service:
   - Build command: `npm run build`
   - Publish directory: `dist`
   - Environment variable: `VITE_API_URL=https://<your-backend-url>/api`.

### Option 2: Railway.app (One-Click Git Push)
1. Link your GitHub repository in the Railway dashboard.
2. Add a PostgreSQL service from the catalog.
3. Railway automatically detects `docker-compose.yml` and spins up the frontend, backend, database, and AI service on an isolated virtual network.

---

## 5. Security & Maintenance Checklist

- [x] **Private Database**: PostgreSQL port `5432` is bound only to internal Docker networks; never expose port 5432 to `0.0.0.0`.
- [x] **Database Backups**:
  ```bash
  docker compose exec postgres pg_dump -U prs_db_user prs_production > prs_backup_$(date +%Y%m%d).sql
  ```
- [x] **SSL / TLS**: Terminate HTTPS at the cloud load balancer or use Cloudflare / Caddy for automated Let's Encrypt certificates.
- [x] **Demo State Reset**: In demo environments, trigger `POST /api/demo/reset` with admin credentials to restore the canonical pre-optimization evaluation baseline.
