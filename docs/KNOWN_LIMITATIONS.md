# Known Limitations & Engineering Boundaries
## Project Resource Allocation & Scheduling System (PRS)
### Release Candidate 1.0.0-RC1

This document transparently outlines the current engineering trade-offs, design boundaries, and known limitations of the **Project Resource Allocation & Scheduling System (PRS)** as of the Phase 16 Release Candidate.

---

### 1. Heuristic Schedule Optimization vs. Exact MILP Solvers
- **Current Behavior**: The schedule optimization engine generates a bounded candidate set (up to 20 candidates per run) using targeted heuristics: worker substitution, vendor outsourcing, and slack-aware shifting.
- **Limitation**: The Resource-Constrained Project Scheduling Problem (RCPSP) is strongly **NP-hard**. Heuristic local search discovers high-utility feasible solutions in $<150\text{ ms}$, but cannot mathematically guarantee global optimality across large project portfolios ($>500$ concurrent tasks).
- **Mitigation**: Status Quo (Candidate 0) is always evaluated as an objective baseline, and candidates are strictly filtered against mandatory hard constraints before ranking.

### 2. Precedence-Only CPM Model
- **Current Behavior**: The Critical Path Method (CPM) calculation uses the classical precedence-only network formulation (Forward and Backward passes across the dependency DAG).
- **Limitation**: Total float represents schedule slack purely based on task precedence dependencies. If a non-critical task shares a capacity-1 worker with another task, scheduling both concurrently creates a resource conflict that is flagged by `ConflictDetector` rather than absorbed into the precedence float.
- **Mitigation**: The system decouples Precedence CPM from Resource Conflict Detection and integrates both into the Optimization Engine.

### 3. Unified Corporate Working-Day Calendar
- **Current Behavior**: The `WorkingDayCalculator` enforces a standard Monday–Friday work week and skips weekends (Saturday and Sunday).
- **Limitation**: Regional statutory holidays (e.g., bank holidays) and per-worker individual shift patterns or leave schedules are not dynamically loaded from external HR systems.
- **Mitigation**: Project managers can manually model scheduled worker absence by toggling worker availability to `UNAVAILABLE`.

### 4. Discrete (Capacity-1) Worker Allocation
- **Current Behavior**: A task assignment commits an entire worker to a task during its active date window (capacity-1 model).
- **Limitation**: Fractional FTE allocations (e.g. allocating 50% of a developer's time to Task A and 50% to Task B simultaneously) are not supported in the discrete collision model.
- **Mitigation**: Tasks are sized as dedicated discrete work packages for clear operational accountability.

### 5. Multi-Project Cross-Dependency Synchronization
- **Current Behavior**: Task dependencies are scoped within individual projects. Cross-project worker double-booking is fully detected across all tenant projects, but an edge connecting Task $X$ in Project 1 directly to Task $Y$ in Project 2 is not supported in the active DAG schema.
- **Mitigation**: Project managers schedule inter-project handoffs using project milestones and start date coordination.

### 6. Email Transport & Outbox Retries
- **Current Behavior**: Email notification transport operates in Mock Development Mode by default, logging outbound emails to database outbox tables (`notification_outbox`) with bounded retry tracking.
- **Limitation**: Live outbound delivery over port 587/465 requires valid SMTP server credentials configured in environment variables (`SPRING_MAIL_HOST`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`).
- **Mitigation**: In-app notifications and webhooks provide instant, zero-configuration notification delivery out of the box.

### 7. External Calendar Synchronization
- **Current Behavior**: PRS pushes task milestones and schedules to external calendars (Google Calendar and Microsoft Outlook) via one-way event creation and update mapping with duplicate prevention.
- **Limitation**: Bi-directional two-way synchronization (e.g., dragging an event in Google Calendar and having PRS alter its CPM schedule) is not supported, ensuring the backend Java scheduling engine remains the single authoritative source of truth.
- **Mitigation**: Out-of-the-box demo mode simulates OAuth token acquisition; live third-party synchronization requires registering tenant application credentials with Google Cloud Console / Microsoft Azure Entra ID.

### 8. Machine Learning Delay Prediction Ethical Gate
- **Current Behavior**: The ML delay prediction pipeline requires a minimum threshold of $N \ge 100$ completed task outcome records before exposing probabilistic risk scores.
- **Limitation**: On a fresh installation, the ML delay risk endpoint returns `NOT_READY` with null probabilities and presents authoritative CPM deadline analysis instead.
- **Mitigation**: Prevents misleading artificial predictions when historical data is scarce. Model training activates automatically once the completed task ledger reaches 100 records.

### 9. AI Service Dual-Mode Fallback
- **Current Behavior**: The AI Assistant operates in zero-dependency heuristic fallback mode out-of-the-box, providing grounded explanations citing authoritative CPM, conflict, and budget data without external network calls.
- **Limitation**: Deep conversational query synthesis requires an external OpenAI API key (`OPENAI_API_KEY`).
- **Mitigation**: Fallback mode is 100% deterministic, offline, and free, ensuring the platform remains fully demonstrable in isolated evaluation environments.

### 10. Account Provisioning & Email Verification in Local Development
- **Current Behavior**: When creating or inviting members or requesting a password reset without a configured external SMTP provider, the system operates in controlled local-development mode: cryptographically secure activation and reset tokens are generated, saved, and rendered directly in the administrator's UI (and in local development logs/responses for automated test harnesses) so accounts can be verified and activated instantly without external email dependencies.
- **Limitation**: In live production, external email delivery requires configuring `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, and `SPRING_MAIL_PASSWORD`.
- **Mitigation**: Prevents fake email verification in local development while maintaining identical cryptographic token lifecycle and expiration validation.
