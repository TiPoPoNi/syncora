# ADR 001: Deterministic Scheduling Engine and Constraint Satisfaction Architecture

- **Status:** Accepted
- **Deciders:** Engineering Lead, System Architect
- **Date:** October 2026

---

## Context
A mission-critical project resource scheduling system must manage task dependencies, critical path calculations, resource concurrency conflicts, and deadline risk. Two architectural options were evaluated for generating schedule dates and optimizing resource allocations:
1. **Option A (Stochastic / Generative LLM Prediction)**: Prompt an AI model to guess schedule dates and resource assignments.
2. **Option B (Deterministic Graph Algorithms in Core Java Backend)**: Implement classical operations research algorithms (Kahn's Topological Sort, CPM Forward/Backward Passes, and Multi-Objective Constraint Evaluation) directly in the Java/Spring Boot service.

## Decision
We adopted **Option B: Deterministic Graph Algorithms in Java**.
- All date arithmetic, critical path sequence identification, free/total float calculations, and delay propagation ripple effects are computed deterministically.
- Hard constraints (topological cycle freedom, precedence working days, worker skill prerequisites, worker availability, and completed task immutability) are non-negotiable invariants that cannot be traded away.
- Optimization uses a multi-objective scoring formula ($S = 0.40 S_{\text{deadline}} + 0.25 S_{\text{time}} + 0.15 S_{\text{conflicts}} + 0.10 S_{\text{disruption}} + 0.10 S_{\text{balance}}$) with human-in-the-loop managerial approval before transactional application.

## Consequences
- **Positive:** Mathematically verifiable schedules, sub-20ms calculation latency, zero hallucination of dates or task relations, and 100% reproducible test suites (44/44 automated tests).
- **Negative:** Requires strict modeling of task inputs and working-day calendars in the core Java domain.
