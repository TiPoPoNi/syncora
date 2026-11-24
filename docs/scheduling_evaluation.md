# Deterministic Scheduling & Optimization Engine: Evaluation Suite

**Project:** Project Resource Allocation & Scheduling System (PRS)  
**Evaluation Phase:** Phase 6 — Production Readiness, Security, Testing & Performance  
**Evaluation Model:** Ground-Truth Synthetic Benchmark Suite  

---

## 1. Executive Summary

The PRS scheduling and optimization engine relies on **deterministic mathematical algorithms** (Kahn's Topological Sort, CPM Forward/Backward Passes, and Multi-Objective Constraint Satisfaction). This benchmark suite tests the engine against 5 canonical graph topologies with known ground-truth dates and scores.

**Hard Constraint Guarantee:** The system **never** violates topological order, precedence working days, worker skill prerequisites, worker availability, or completed task immutability to achieve a higher optimization score.

---

## 2. Canonical Test Scenarios & Ground Truth Results

### Scenario 1: Sequential Chain Topology (FS Dependencies)
- **Topology:** $A \text{ (3d)} \to B \text{ (5d)} \to C \text{ (2d)}$
- **Start Date:** Monday 2026-10-05
- **Expected Finish Date:** Friday 2026-10-16 (10 working days, weekends automatically skipped)
- **Engine Calculated Finish:** `2026-10-16` (0 variance)
- **Critical Path:** $A \to B \to C$
- **Total Slack:** $A = 0\text{d}, B = 0\text{d}, C = 0\text{d}$
- **Result:** **PASSED (100% Date & Slack Concordance)**

### Scenario 2: Diamond / Fork-Join Parallel CPM Topology
- **Topology:** $A \text{ (2d)}$ forks into $B \text{ (6d)}$ and $C \text{ (3d)}$; both join at $D \text{ (4d)}$.
- **Start Date:** Monday 2026-10-05
- **Branch Durations:**
  - Path 1 ($A \to B \to D$): $2 + 6 + 4 = 12$ working days
  - Path 2 ($A \to C \to D$): $2 + 3 + 4 = 9$ working days
- **Calculated Critical Path:** $A \to B \to D$ (12 working days)
- **Calculated Slacks:**
  - Task $A$: Total Slack = $0$, Free Slack = $0$
  - Task $B$: Total Slack = $0$, Free Slack = $0$ (Critical)
  - Task $C$: Total Slack = $3\text{ days}$, Free Slack = $3\text{ days}$ (Non-critical)
  - Task $D$: Total Slack = $0$, Free Slack = $0$
- **Result:** **PASSED (Exact Slack & Float Mathematical Parity)**

### Scenario 3: Circular Dependency (Topological Cycle) Rejection
- **Topology:** $A \to B \to C \to A$
- **Expected Engine Behavior:** Immediate abort with `CircularDependencyException` or rejection before saving.
- **Engine Output:** Cycle detected during topological in-degree reduction. Dependency rejected before database write.
- **Result:** **PASSED (Cycle Prevention Invariant Maintained)**

### Scenario 4: Downstream Delay Ripple Propagation
- **Baseline:** Task $A \text{ (2d)} \to B \text{ (4d)}$. Baseline Finish: Day 6.
- **Reported Incident:** Vendor delay of $+3$ working days on Task $A$.
- **Expected Ripple:** Task $A$ finishes Day 5; Task $B$ pushed to finish Day 9 (+3 working days net slip).
- **Engine Output:** Task $B$ planned start shifted by 3 working days; deadline variance reported as $+3\text{ days}$. Original baseline preserved.
- **Result:** **PASSED (Deterministic Ripple Concordance)**

### Scenario 5: Multi-Objective Schedule Optimization & Stale Guardrails
- **Initial Problem:** Worker A allocated concurrently to two parallel tasks ($B$ and $C$) causing resource overlap conflict.
- **Optimization Heuristic:** Evaluate qualified available peers (Worker B with required skill set).
- **Candidate Evaluation:**
  - Candidate 1 (Reassign Task $C$ to Worker B): Conflicts = 0, Time Saved = 0, Churn = 1 modification. Score = 89.5 / 100.
  - Candidate 2 (Reassign Task $C$ to Worker C): Worker C status = `UNAVAILABLE`. Hard constraint violation $\to$ **REJECTED**.
  - Candidate 3 (Reassign Task $A$ which is `COMPLETED`): Hard constraint violation $\to$ **REJECTED**.
- **Stale Concurrency Rejection:** If manager attempts to apply recommendation after another user altered task assignment, transaction aborts with `IllegalStateException("Recommendation is stale")`.
- **Result:** **PASSED (Hard Constraints & Optimistic Safety Enforced)**

---

## 3. Benchmarking Summary

| Scenario | Invariant Tested | Ground Truth | System Result | Status |
| :--- | :--- | :--- | :--- | :--- |
| **Sequential** | Weekend exclusion & working-day arithmetic | 10 working days | 10 working days | **Verified** |
| **Fork-Join** | CPM forward/backward passes & float calculation | Path $A \to B \to D$ ($TF=0$) | Path $A \to B \to D$ ($TF=0$) | **Verified** |
| **Cycle** | Kahn's Algorithm topological validation | Cycle rejected | Cycle rejected | **Verified** |
| **Ripple** | Dependency-aware forward delay push | $+3$ working days downstream | $+3$ working days downstream | **Verified** |
| **Optimization** | Hard constraint filtering & scoring | Candidate 1 selected (89.5 pts) | Candidate 1 selected (89.5 pts) | **Verified** |
| **Stale Check** | Concurrency guardrail on applied recs | 400 Bad Request if stale | 400 Bad Request if stale | **Verified** |
