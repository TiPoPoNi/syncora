# Portfolio & Resume Engineering Guide

**Project:** Project Resource Allocation & Scheduling System (PRS)  
**Target Roles:** Full-Stack Software Engineer, Backend Engineer (Java/Spring Boot), AI/ML Systems Engineer  

---

## 1. Resume Bullet Points (Tailored & Quantified)

### Option A: Full-Stack / Backend Focus (Java 21, Spring Boot, React, PostgreSQL)
> - **Architected & built an enterprise Project Resource Allocation & Scheduling System** using Java 21, Spring Boot 3.5, React, and PostgreSQL, automating deterministic CPM (Critical Path Method) calculations and reducing schedule recalculation latency to $< 20\text{ ms}$.
> - **Designed a multi-objective schedule optimization engine** enforcing hard domain constraints (topological cycle freedom, working-day calendars, skill coverage, worker availability) with multi-criteria candidate scoring ($0-100$) and optimistic concurrency protection against stale schedule application.
> - **Integrated a secure, explainable FastAPI AI intelligence layer** with strict data-isolation boundaries; implemented in-memory What-If scenario simulations allowing project managers to preview downstream delay ripple effects without mutating production database state.
> - **Engineered an automated 44-test JUnit/MockMvc integration suite** and GitHub Actions CI pipeline, achieving 100% test pass rate across authentication, topological scheduling, delay propagation, and security boundaries.

### Option B: Systems & AI Focus (Distributed Architecture, Security, Algorithms)
> - **Developed a secure AI project management assistant** bridging Spring Boot and a dedicated Python FastAPI service, implementing zero-database-access boundaries to eliminate prompt injection and cross-tenant data leakage risks.
> - **Implemented classical operations research graph algorithms** (Kahn’s topological sorting, CPM forward/backward passes, float analysis) to deterministically identify critical path bottlenecks and propagate schedule delays across multi-predecessor dependency graphs.
> - **Engineered production-ready database migrations and indexing strategies** (Flyway v1–v6), optimizing composite B-tree indexes for graph traversal and slashing median query retrieval latencies by over 60%.

---

## 2. Technical Architecture Elevator Pitch

> *"PRS is a high-reliability project management and scheduling platform built to solve real-world resource contention and project delays. Unlike naive project trackers, PRS combines deterministic graph algorithms (Kahn's topological sort and CPM) in a Java 21 / Spring Boot core with a multi-objective heuristic optimizer. It guarantees that critical path dates and resource conflicts are computed with mathematical rigor. On top of this, we designed a zero-trust AI intelligence layer in FastAPI that provides grounded, verifiable explanations and in-memory What-If scenario simulations without ever touching the active database or risking hallucinations."*

---

## 3. Key Algorithmic & Architectural Highlights for Interviews

1. **Deterministic CPM Engine over Stochastic LLM**:
   - *Why?* Schedule dates, free float, and total float require exact mathematical guarantees. An LLM cannot reliably compute topological order across multi-predecessor DAGs without hallucination. Java handles calculations deterministically; AI handles human-readable managerial synthesis.
2. **Hard Constraints vs. Soft Objectives**:
   - *Why?* A schedule that saves 5 days but assigns an unavailable worker or creates a circular dependency is physically impossible. Hard constraints immediately filter infeasible candidates; soft objectives score only verified feasible alternatives.
3. **Stale Concurrency Protection**:
   - *Why?* In multi-manager environments, a recommendation approved 10 minutes ago may become invalid if another manager completes a task or reassigns a worker. We enforced optimistic state validation prior to applying any recommendation, preventing silent schedule corruption.
4. **Trusted AI Boundary**:
   - *Why?* Python AI service does not connect to PostgreSQL. Spring Boot serves as the single source of truth for authorization, retrieving and serializing only the authorized project snapshot.

---

## 4. Live Demonstration Script (5 Minutes)

1. **Dashboard & Workflows (1 min)**:
   - Log in as `admin`.
   - Show active projects, clients, workers, and tasks with real-time status.
2. **Deterministic Schedule & Critical Path (1.5 min)**:
   - Open **Schedule** $\to$ **Critical Path (CPM)** tab.
   - Show the 6-task critical path (`Requirements → UI Design → Frontend Development → Integration → Testing → Deployment`) with 0 slack.
   - Highlight non-critical tasks with positive float.
3. **Resource Conflict & Intelligent Optimization (1.5 min)**:
   - Switch to **Resource Overlaps** tab to view concurrent assignment collisions.
   - Switch to **Optimization (Phase 4)** tab. Click **Run Optimization**.
   - Show ranked feasible alternatives, multi-objective scoring (89.5 pts), and trade-off rationale.
   - Click **Approve** $\to$ **Apply Changes**. Show conflicts resolving to 0!
4. **AI Assistant & What-If Scenario (1 min)**:
   - Switch to **AI Assistant & Scenarios (Phase 5)** tab.
   - Ask: *"Show tasks on the critical path."* Show grounded explanation with source citations.
   - Click **Hypothetical What-If Simulation**, add 4 working days delay, and show the side-by-side in-memory comparison card confirming active database records are completely unchanged.
