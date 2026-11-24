# Testing Guide & Verification Suite
## Project Resource Allocation & Scheduling System (PRS)

This document provides complete instructions for executing the automated verification suite, mathematical proof cases, and production build checks across the frontend, backend, and AI microservice.

---

## 1. Automated Test Summary & Verification Results

| Test Category | Suite / Command | Target Component | Tests Executed | Status | Elapsed Time |
| :--- | :--- | :--- | :---: | :---: | :---: |
| **Cost & Budget (Phase 13)** | `mvn test -Dtest=Phase13CostManagementIntegrationTest` | Budget lifecycle, revision history, vendor quotations, purchase order commitments, delivery challan tracking, cost variance analysis, cross-tenant rejection | 5 | **PASSED** | 1.95 s |
| **Multi-Tenancy (Phase 12)** | `mvn test -Dtest=Phase12MultiTenantIntegrationTest` | Workspace isolation, cross-org assignment prevention, optimizer worker isolation, suspended org blocking, last admin protection, global admin escalation defense, CSV export scoping | 9 | **PASSED** | 2.12 s |
| **Portfolio Analytics (Phase 11)** | `mvn test -Dtest=Phase11PortfolioAnalyticsIntegrationTest` | Portfolio KPIs, project variance, zero-denominator safeguards, resource workload, vendor reliability, client authorization isolation, CSV formula defense | 8 | **PASSED** | 1.84 s |
| **Workflow Automation (Phase 10)** | `mvn test -Dtest=Phase10WorkflowAutomationIntegrationTest` | In-app notices, preferences, deduplication, email mock, calendar/CSV exports, vendor CSV import | 8 | **PASSED** | 1.85 s |
| **Historical & ML (Phase 9)** | `mvn test -Dtest=Phase9HistoricalAnalyticsIntegrationTest` | Outcome recording, reliability matrix, data gate | 5 | **PASSED** | 0.80 s |
| **Algorithm Validation** | `mvn test -Dtest=Phase8AlgorithmValidationTest` | Cases A through G (Mathematical CPM & Scheduling proofs) | 7 | **PASSED** | 0.02 s |
| **Production Readiness** | `mvn test -Dtest=Phase6ProductionReadinessIntegrationTest` | Concurrency, exception sanitization, access control | 5 | **PASSED** | 0.78 s |
| **AI Integration** | `mvn test -Dtest=Phase5ApiIntegrationTest` | Ephemeral context Q&A, hypothetical scenarios | 5 | **PASSED** | 0.85 s |
| **Optimization API** | `mvn test -Dtest=Phase4ApiIntegrationTest` | Recommendation proposals, approval & rollback | 5 | **PASSED** | 0.88 s |
| **CPM & Delay API** | `mvn test -Dtest=Phase3ApiIntegrationTest` | Precedence CPM, delay propagation endpoints | 5 | **PASSED** | 0.65 s |
| **Core CRUD & Auth** | `mvn test -Dtest=ApiIntegrationTests` | JWT token validation, project/task lifecycle | 5 | **PASSED** | 0.54 s |
| **CPM Service Unit** | `mvn test -Dtest=CriticalPathServiceTest` | Forward/backward pass, float calculations | 6 | **PASSED** | 0.05 s |
| **Delay Unit** | `mvn test -Dtest=DelayPropagationServiceTest` | Downstream ripple traversal & baseline freeze | 3 | **PASSED** | 0.03 s |
| **Candidate Validator** | `mvn test -Dtest=CandidateValidatorTest` | Hard constraint checks & skill matches | 4 | **PASSED** | 0.02 s |
| **Scheduling Engine** | `mvn test -Dtest=SchedulingEngineTest` | Topological sorting & working-day calendars | 6 | **PASSED** | 0.02 s |
| **AI & ML Python Suite** | `pytest ai-service/tests` | Health, grounded prompt builder, scenario logic, anti-leakage ML pipeline | 12 | **PASSED** | 2.85 s |
| **Frontend Production** | `npm run build` (Vite 5.4.21) | TypeScript type safety, JSX transpilation, bundling | 1565 mods | **PASSED** | 13.18 s |

**Total Automated Tests: 98 / 98 Passing (100%)** (86 Java + 12 Python)

---

## 2. Mathematical Validation Proofs (Cases A – G)

Implemented and verified in `com.prs.Phase8AlgorithmValidationTest`:

### Case A: Sequential Tasks
- **Specification**: Task A (2d) $\to$ Task B (3d) $\to$ Task C (2d).
- **Start**: Monday, 2026-10-19.
- **Expected**:
  - Task A: Mon Oct 19 – Tue Oct 20 ($ES=0, EF=2$)
  - Task B: Wed Oct 21 – Fri Oct 23 ($ES=2, EF=5$)
  - Task C: Mon Oct 26 – Tue Oct 27 ($ES=5, EF=7$, skips Sat Oct 24 & Sun Oct 25)
  - Minimum Project Duration: **7 working days**.
  - Critical Path: `[Task A, Task B, Task C]` (all total float = 0).
- **Result**: **PASSED**.

### Case B: Parallel Tasks (No Unnecessary Serialization)
- **Specification**: Task A (2d) and Task B (5d) are independent roots. Task C (2d) depends on both A and B.
- **Start**: Monday, 2026-10-19.
- **Expected**:
  - Task A starts Mon Oct 19, finishes Tue Oct 20 ($ES=0, EF=2$).
  - Task B starts Mon Oct 19, finishes Fri Oct 23 ($ES=0, EF=5$).
  - Task C waits for $\max(EF_A, EF_B) = 5$, starts Mon Oct 26, finishes Tue Oct 27 ($ES=5, EF=7$).
  - Minimum Project Duration: **7 working days**.
- **Result**: **PASSED**. Independent tasks are not artificially serialized.

### Case C: Non-Critical Task Total Float (Slack)
- **Specification**: Topology from Case B.
- **Expected**:
  - Project Duration = 7 working days ($T_{max} = 7$).
  - $LF_C = 7, LS_C = 5$.
  - Successor of A is C: $LF_A = LS_C = 5, LS_A = 5 - 2 = 3$.
  - Total Float for Task A: $\text{Float}_A = LS_A - ES_A = 3 - 0 =$ **3 working days**.
  - Total Float for Task B: $LF_B = 5, LS_B = 0 \implies \text{Float}_B =$ **0 working days** (Critical).
- **Result**: **PASSED**. Task A has exactly 3 days of total slack; Task B and C are critical.

### Case D: Delay Propagation & Slack Absorption
- **Specification**: Topology from Case B/C.
- **Expected**:
  - A delay of 2 working days on Task A (duration 2 $\to$ 4) does **not** push Task C because $\text{Float}_A = 3 \ge 2$ (absorbed by slack).
  - A delay of 2 working days on Task B (duration 5 $\to$ 7) pushes Task C's start from Oct 26 to Oct 28 (finish Thu Oct 29).
- **Result**: **PASSED**. Dates recalculate strictly from dependency constraints.

### Case E: Resource Conflict Detection
- **Specification**: Capacity-1 worker assigned to Task 1 (Oct 19–21) and Task 2 (Oct 20–22).
- **Expected**:
  - Overlap detected between Oct 20 and Oct 21.
  - Conflict count = 1.
- **Result**: **PASSED**.

### Case F: Circular Dependency Cycle Detection
- **Specification**: Task A $\to$ Task B $\to$ Task C $\to$ Task A.
- **Expected**:
  - Kahn's algorithm detects circular loop.
  - Throws `IllegalStateException: Cannot schedule tasks: Circular dependency detected in the project.`
- **Result**: **PASSED**.

### Case G: Deadline Analysis
- **Specification**: Calculated completion date compared with project deadline.
- **Expected**:
  - Completion date > Deadline $\implies$ Flagged as delayed (`isDelayed = true`).
  - Extended deadline $\ge$ Completion date $\implies$ Flagged on-schedule (`isDelayed = false`).
- **Result**: **PASSED**.

### Case H: Deliverables, Anti-Self-Approval & Task Completion Gates (Phase 14)
- **Specification**: `Phase14DeliverablesApprovalIntegrationTest.java` (7 comprehensive integration tests).
- **Covered Scenarios**:
  1. Deliverable creation, validation & project scoping (`POST /api/projects/{id}/deliverables`).
  2. Document file upload with extension validation (.exe rejected), SHA-256 checksum calculation, server storage key generation, versioning ($v1, v2$), and authorized binary streaming download.
  3. Workflow lifecycle transitions (`SUBMITTED` $\to$ `UNDER_REVIEW` $\to$ `CHANGES_REQUESTED` $\to$ revision $v2$ $\to$ `APPROVED`), Anti-Self-Approval policy enforcement (responsible user cannot approve their own deliverable unless Admin), and preserved approval audit ledger.
  4. Task completion gate enforcement (`TaskApprovalGate`): task status update to `COMPLETED` is rejected while mandatory deliverable is unapproved; once deliverable is approved, task completes successfully.
  5. Overdue approval delay impact analysis (`GET /api/deliverables/{id}/delay-impact`): calculates days overdue, identified blocked tasks, and checks Critical Path (`CPM`) membership for schedule slip forecast.
  6. Client review portal access & confidentiality protection: client user accesses client-visible non-confidential deliverables; downloading confidential documents returns 403 Forbidden.
  7. Multi-tenant workspace isolation: cross-tenant deliverable mutation is rejected with `TenantAccessDeniedException`.
- **Result**: **PASSED (7/7 tests)**.

---

## 3. Execution Commands

### Backend Java Suite (93 Tests)
```powershell
$env:JAVA_HOME = "$pwd\jdk21\jdk-21.0.2+13"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\apache-maven-3.9.6\bin\mvn.cmd test -f backend/pom.xml
```

### Phase 14 Deliverables & Approval Suite (7 Tests)
```powershell
$env:JAVA_HOME = "$pwd\jdk21\jdk-21.0.2+13"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\apache-maven-3.9.6\bin\mvn.cmd test -Dtest=Phase14DeliverablesApprovalIntegrationTest -f backend/pom.xml
```

### Python AI Service Suite (12 Tests)
```powershell
$env:PYTHONPATH = "$pwd\ai-service"
& "C:\Users\rishi\AppData\Local\Programs\Python\Python312\python.exe" -m pytest ai-service/tests
```

### Frontend Production Build
```powershell
cd frontend
node ./node_modules/vite/bin/vite.js build
```

### Performance & Latency Benchmark
```powershell
node scripts/measure_performance.js
```
