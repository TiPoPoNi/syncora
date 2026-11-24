# ADR 002: Trusted Data Boundary and Read-Only AI Service Isolation

- **Status:** Accepted
- **Deciders:** Security Architect, Backend Lead
- **Date:** October 2026

---

## Context
Integrating conversational AI into a multi-tenant business management application introduces security risks, including unauthorized cross-project data access, prompt injection, and unauthorized schedule mutations. Two integration architectures were considered:
1. **Option A (Direct Database Access)**: Connect the Python AI service directly to PostgreSQL and give the LLM write permissions or direct DB access tools.
2. **Option B (Spring Boot Authorization Gatekeeper with Ephemeral Context)**: Retain Spring Boot as the sole authoritative gatekeeper for database access, authentication, authorization, and project context serialization. The Python service receives only sanitized, authorized JSON snapshots via internal REST calls.

## Decision
We adopted **Option B: Spring Boot Gatekeeper with Read-Only AI Boundary**.
- The Python FastAPI AI service has **no database credentials and no connection to PostgreSQL**.
- Spring Boot validates JWT claims and enforces project manager authorization before constructing an in-memory JSON context snapshot.
- The AI layer is strictly **read-only**: it explains, summarizes, and simulates, but cannot modify active schedules or reassign workers.
- What-If simulations run in Java memory without saving changes to PostgreSQL (`isHypothetical: true`).
- If the AI service fails or times out, Spring Boot degrades gracefully to deterministic rule-based explanations without interrupting core project management operations.

## Consequences
- **Positive:** Zero risk of SQL injection via LLM, zero risk of cross-project data leakage across tenants, guaranteed active schedule immutability during simulations, and independent AI service scaling.
- **Negative:** Requires serializing structured JSON context over HTTP for each assistant query.
