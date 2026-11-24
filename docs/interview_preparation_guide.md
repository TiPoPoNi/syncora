# Technical Interview Preparation Guide
## Project Resource Allocation & Scheduling System (PRS)

This guide provides deep technical rationales, architectural decisions, and algorithm walkthroughs grounded directly in the implementation of the **Project Resource Allocation & Scheduling System**. Use this guide to prepare for technical deep-dives, systems design interviews, and portfolio presentations.

---

## 1. Application Architecture

### Q1: Why React and Spring Boot?
- **Spring Boot (Java 21)**: Provides a mature, enterprise-grade foundation with strong type safety, declarative transaction management (`@Transactional`), compile-time verification, and robust dependency injection. Enterprise scheduling involves stateful graph algorithms and relational integrity, which benefit heavily from Java's strong concurrency primitives, deterministic execution, and Spring Data JPA.
- **React 18 + TypeScript + Tailwind CSS**: Delivers a reactive component-driven UI with strong compile-time contracts matching backend DTOs. Fast rendering of SVG Gantt charts, tabular matrices, and real-time state transitions ensures a smooth desktop experience without heavy chart libraries.
- **Contract Decoupling**: Frontend and backend are separated via clean RESTful DTO boundaries, enabling independent deployments, easy automated testing, and headless execution.

### Q2: Why PostgreSQL?
- **Relational Integrity**: Projects, tasks, dependencies, worker assignments, and audit trails are deeply relational. PostgreSQL provides strict foreign key constraints (`ON DELETE CASCADE`), index-backed join performance, and transactional atomicity (ACID).
- **Complex Analytical Queries & Window Functions**: Traversing project task states, computing resource capacity distributions, and aggregating timeline float requires structured SQL joins rather than document-store nesting.
- **Extensibility**: PostgreSQL supports native JSONB (used in `OptimizationAuditLog` and AI conversation history) and date/time arithmetic with microsecond precision.

### Q3: Why a Modular Monolith with an AI Microservice?
- **Single Core Deployable**: Scheduling calculations, CPM, delay propagation, and resource validation need transactional consistency and low-latency database access. Distributing core scheduling into microservices would introduce distributed transactions, 2-phase commits, and eventual consistency risks without any business need.
- **Isolated AI Service**: The AI assistant runs as an independent lightweight Python service (`ai-service` on port 8000) using FastAPI. Python is the industry standard for LLM integrations (LangChain, OpenAI, Hugging Face). Decoupling it shields the core Spring Boot ERP/scheduling engine from AI library overhead, memory footprints, and Python runtime dependencies while allowing the AI layer to be mocked or scaled independently.

### Q4: How does the Frontend communicate with the Backend?
- **RESTful Gateway**: React communicates via Axios with a common base URL (`/api`), standardized JSON payloads, and bearer token authorization (`Authorization: Bearer <jwt>`).
- **Unified Error Protocol**: All errors return standard JSON schemas (`{ timestamp, status, error, message, path }`) with sanitized production details.

---

## 2. Database and API Design

### Q5: Key Relational Schema Decisions
- **Join Table with Temporal Metadata**: `task_assignments` links `tasks` to `workers` or `vendors`, while task start/finish dates establish temporal spans.
- **Explicit Dependency Model**: `task_dependencies` defines directed edges (`predecessor_id`, `successor_id`, `dependency_type`), enabling graph representations directly in memory.
- **Separate Baseline Table**: `schedule_baselines` stores the approved historical snapshot (`baseline_start_date`, `baseline_end_date`), enabling variance tracking without mutating active forecast records.
- **Audit Logging**: `optimization_audit_logs` records every recommendation event with the acting user, candidate JSON diff, and approval timestamp.

### Q6: Transaction Boundaries & Isolation
- **Declarative Boundaries**: Core mutation workflows (e.g., `ScheduleOptimizationService.applyRecommendation`, `DelayPropagationService.propagateDelay`) use `@Transactional(isolation = Isolation.READ_COMMITTED)`.
- **Atomic State Updates**: When a schedule recommendation is applied, three database operations execute inside a single transaction:
  1. Update `task_assignments` (worker reassignment or date shift).
  2. Re-trigger `SchedulingEngine.calculateSchedule` to compute updated earliest dates.
  3. Update recommendation status to `APPLIED` and append to `optimization_audit_logs`.
  If any step fails, the entire transaction rolls back, preventing partial schedule corruption.

### Q7: Authentication, Authorization, and Security
- **Stateless JWT**: Standard RFC 7519 tokens signed with HMAC-SHA256 (`Keys.hmacShaKeyFor`). Expiration is set to 24 hours.
- **Role-Based Access Control (RBAC)**: Managed by Spring Security filter chains (`JwtAuthenticationFilter`). Endpoints are secured via annotations (`@PreAuthorize("hasRole('ADMIN')")`).
- **Credential Storage**: Passwords hashed using standard BCrypt with work factor 10. No plaintext credentials ever reach logs or database records.

---

## 3. Scheduling Algorithms & Critical Path Method (CPM)

### Q8: Dependency Graph Validation & Cycle Detection
- **Directed Acyclic Graph (DAG)**: Tasks represent vertices $V$, dependencies represent directed edges $E = (u, v)$ where $u$ must finish before $v$ starts.
- **Kahn's Algorithm (Topological Sort)**:
  1. Compute in-degree for all vertices.
  2. Enqueue all vertices with in-degree 0.
  3. While queue is not empty, dequeue vertex $u$, append to sorted order, and decrement in-degree of all successors $v$. If in-degree becomes 0, enqueue $v$.
  4. If vertices in sorted order $< |V|$, a circular dependency cycle exists; throw `CyclicDependencyException` identifying the loop.

### Q9: Resource Conflict Detection
- **Overlap Formula**: Two tasks $T_A$ and $T_B$ assigned to the same worker create a collision if:
  $$\max(Start_A, Start_B) \le \min(Finish_A, Finish_B)$$
- **Working Day Calculator**: Excludes Saturdays and Sundays from work duration, projecting accurate calendar dates for multi-day efforts.

### Q10: Critical Path Method (CPM) Mathematics
- **Forward Pass (Earliest Dates)**:
  - $ES_i = \max_{p \in Pred(i)} (EF_p)$ (for root tasks, $ES_i = \text{Project Start}$)
  - $EF_i = ES_i + \text{Duration}_i$
  - Overall project completion date $T_{max} = \max_{i} (EF_i)$
- **Backward Pass (Latest Dates)**:
  - $LF_i = \min_{s \in Succ(i)} (LS_s)$ (for terminal tasks, $LF_i = T_{max}$)
  - $LS_i = LF_i - \text{Duration}_i$
- **Total Float (Slack)**:
  $$\text{Float}_i = LS_i - ES_i = LF_i - EF_i$$
- **Critical Path Identification**: Tasks with $\text{Float}_i = 0$ constitute the critical path. Any delay in these bottleneck tasks propagates day-for-day to the final delivery date.

### Q11: Delay Propagation Mechanics
- When a vendor or worker reports a delay of $\Delta$ days on task $T_k$:
  1. Revised finish date becomes $EF_k' = EF_k + \Delta$.
  2. Downstream subgraph $G_{down}(T_k)$ is traversed in topological order.
  3. For each successor $S$, if $ES_S < EF_k'$, $ES_S' = EF_k'$ and $EF_S' = ES_S' + \text{Duration}_S$.
  4. Compare new completion date $T_{max}'$ against approved project deadline:
     $$\text{Slippage} = \max(0, T_{max}' - \text{Deadline})$$

---

## 4. Schedule Optimization & Recommendation Engine

### Q12: Hard Constraints vs. Soft Objectives
- **Hard Constraints (Must Never Be Violated)**:
  - Dependency precedence: No task can begin before all predecessors finish.
  - Resource uniqueness: No worker can be assigned to multiple concurrent tasks.
  - Skill compatibility: A worker must possess the required skillset for assigned tasks.
- **Soft Objectives (To Be Minimized)**:
  - Total project completion delay.
  - Resource reassignment churn (number of altered assignments).
  - Cost of premium resources/vendors.

### Q13: Multi-Objective Candidate Generation & Scoring
- **Heuristic Exploration**:
  - Strategy A: Resource substitution (search for unallocated workers with matching skills).
  - Strategy B: Sequence interleaving / date shift (shift non-critical predecessor tasks within available float).
  - Strategy C: Fast-tracking parallel execution where dependencies permit.
- **Composite Scoring Function**:
  $$Score = w_1 \cdot \text{DelayReduction} - w_2 \cdot \text{ReassignmentCost} - w_3 \cdot \text{ScheduleChurn}$$
  Candidates are ranked descending by score, presenting the highest-utility option first.

### Q14: Human-in-the-Loop Approval Workflow
- The optimizer **never** directly alters the live schedule. It creates stateful proposals:
  `PENDING` $\rightarrow$ Project Manager reviews $\rightarrow$ `APPROVED` $\rightarrow$ `APPLIED` (or `REJECTED`).
- Guarantees organizational control, prevents algorithmic instability, and creates a traceable compliance record in `optimization_audit_logs`.

---

## 5. AI Architecture & Security Guardrails

### Q15: Why the LLM Does NOT Calculate Schedules
- **Hallucination Risk**: Large Language Models are probabilistic token predictors, fundamentally incapable of exact graph topological sorting, calendar day-counting, or deterministic integer optimization.
- **Architectural Division of Labor**:
  - Spring Boot backend executes exact discrete math (CPM, DAG traversal, conflict collision).
  - Python AI service formats the structured mathematical output into natural language explanations, hypothetical impact summaries, and executive briefings.

### Q16: Security Guardrails & Prompt Injection Defense
- **Grounded Context Injection**: The backend pulls real entity states and passes them as read-only structured JSON to the LLM system prompt.
- **Input Sanitization**: User input is checked for prompt-injection markers (`ignore previous instructions`, `system prompt override`).
- **Read-Only Scope**: The AI service has no write endpoints to the database. It cannot approve recommendations, reassign workers, or mutate deadlines. All actions must be performed by an authenticated human manager via standard Spring Boot APIs.
