# Changelog

All notable changes to the **Project Resource Allocation & Scheduling System (PRS)** are documented in this file.

## [v1.1.0-RC1] - Phase 8: Final System Validation, Gap Closure & Release Candidate (2026-10-18)
### Added
- **Mathematical Scheduling Verification Suite (`Phase8AlgorithmValidationTest.java`)**: 7 automated proof cases verifying:
  - Case A: Sequential task ordering, calendar arithmetic, and Critical Path Method ($ES, EF, LS, LF$, slack).
  - Case B: Parallel task independence without false serialization.
  - Case C: Non-critical task total float (slack) calculation ($Float = 3\text{ days}$).
  - Case D: Delay propagation with non-critical slack absorption vs. critical path push.
  - Case E: Resource conflict collision detection on capacity-1 workers across overlapping dates.
  - Case F: Dependency graph cycle rejection via Kahn's topological sort.
  - Case G: Projected completion vs. contractual deadline risk classification.
- **Implementation Status Matrix (`docs/IMPLEMENTATION_STATUS.md`)**: Full 25-subsystem matrix with implementation status, test coverage, and boundaries.
- **Testing Guide (`docs/TESTING.md`)**: Complete verification manual with mathematical test specifications.
- **System Architecture Blueprint (`docs/ARCHITECTURE.md`)**: Topologies, Kahn's algorithm, CPM formulas, and sequence flows.
- **Known Limitations & Boundaries (`docs/KNOWN_LIMITATIONS.md`)**: Documented RCPSP heuristic limits and calendar constraints.
- **Live Latency Benchmarking (`scripts/measure_performance.js`)**: Sub-25ms response times across core scheduling, CPM, and dashboard endpoints.

## [v1.0.0] - Phase 7: Portfolio Launch, Product Polish & Professional Presentation (2026-10-18)
### Added
- **Interactive Multi-Metric SaaS Dashboard**: Live operational KPI cards (Total Projects, Projects at Risk, Resource Conflicts, Overdue Tasks, Milestones, and Resource Workload distribution) powered by real entity aggregations.
- **Deterministic Demo Seed & Reset API**: Added `POST /api/demo/reset` permitting 1-click restoration of the pre-optimization baseline with double-booked assignments for repeatable presentations and interview demonstrations.
- **Chrome DevTools Screenshot Automation**: Headless screenshot generator for documentation (`docs/screenshots/`).
- **Comprehensive Documentation Suite**:
  - `docs/interview_preparation_guide.md`: Comprehensive engineering guide detailing architecture decisions, relational schema, CPM mathematics, heuristic optimization, and LLM security.
  - `docs/deployment_guide.md`: Production containerization and cloud hosting architecture (Render, Railway, AWS ECS).
  - Architecture diagrams (Mermaid) across all 5 key workflows in `README.md`.

## [v0.6.0] - Phase 6: Production Readiness, Security, Testing & Hardening (2026-10-18)
### Added
- Comprehensive automated test suite: 44 backend integration and unit tests passing in 20.9s; 6 AI service tests passing.
- Global exception handling and sanitized structured error responses (`CustomGlobalExceptionHandler`).
- SQL indexing migrations (`V6__performance_indexes.sql`) for optimal dependency graph traversal and task assignment lookups.
- Docker Compose multi-service topology configuration (`docker-compose.yml`) for PostgreSQL, Backend, Frontend, and AI Service.
- Production readiness test suite (`Phase6ProductionReadinessIntegrationTest`).

## [v0.5.0] - Phase 5: AI Intelligence Layer & Project Management Assistant (2026-10-18)
### Added
- FastAPI Python AI microservice on port 8000 integrated with Spring Boot backend via REST gateway.
- Grounded contextual prompt engine retrieving real project metrics, critical path status, and conflict logs.
- Strict guardrails: Human-in-the-loop requirement, prompt-injection sanitization, and deterministic schedule calculations offloaded from the LLM.
- Fallback heuristic reasoning engine ensuring instant, accurate explanations even without external LLM keys.

## [v0.4.0] - Phase 4: Resource-Aware Schedule Optimization & Intelligent Recommendations (2026-10-17)
### Added
- Schedule optimization engine generating candidate alternatives for resource over-allocations and deadline slips.
- Multi-objective scoring algorithm ranking candidates by delay reduction, resource reallocation cost, and schedule churn.
- Formal human-in-the-loop recommendation workflow (`PENDING`, `APPROVED`, `APPLIED`, `REJECTED`) with transactional audit trail (`OptimizationAuditLog`).

## [v0.3.0] - Phase 3: Critical Path Analysis, Gantt Chart & Delay Propagation (2026-10-17)
### Added
- Critical Path Method (CPM) engine calculating Earliest/Latest Start/Finish, total float/slack, and bottleneck task identification.
- Delay propagation engine computing downstream ripple effects across topological dependency DAGs.
- Interactive Gantt chart visualization with SVG timeline bars, critical path highlighting, and dependency lines.
- Schedule baseline comparison tracking forecasted delivery against approved commitments.

## [v0.2.0] - Phase 2: Dependency-Aware & Resource-Aware Scheduling (2026-10-16)
### Added
- Task dependency Directed Acyclic Graph (DAG) validation with Kahn's algorithm cycle detection.
- Resource allocation validation detecting temporal overlaps and double-booking across workers and equipment.
- Working-day calendar calculation skipping weekends and non-working days.

## [v0.1.0] - Phase 1: Core Project Management & Authentication (2026-10-15)
### Added
- Spring Boot 3.5.6 with Java 21 modular backend architecture.
- PostgreSQL relational schema managed with Flyway migrations.
- JWT-based stateless authentication with BCrypt password hashing and Spring Security RBAC.
- CRUD entities: Projects, Tasks, Clients, Workers, Vendors, Resources, and Assignments.
- Modern React 18, TypeScript, and Tailwind CSS responsive interface.
