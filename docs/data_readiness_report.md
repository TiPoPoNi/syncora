# Historical Analytics & Machine Learning Delay Prediction: Data-Readiness Report

**Project:** Project Resource Allocation & Scheduling System (PRS)  
**System Phase:** Phase 9 — Historical Analytics and ML-Based Project Delay Prediction  
**Status:** **RESEARCH / EVALUATION** (Sample Gate Active: 5 / 100 Completed Tasks)  
**Date:** October 2026  

---

## 1. Executive Summary

In strict adherence to software engineering ethics and mathematical integrity principles, **PRS does not train or deploy predictive machine learning models on insufficient sample sizes, nor does it invent synthetic predictions or fabricated accuracy figures**.

A rigorous data-readiness audit was performed on the active PRS database:
1. **Actual Completed Task Outcomes:** 5 verified completed task records exist in the canonical demonstration environment (4 on-time, 1 delayed by 3 working days due to cloud quota delays).
2. **Evaluation Gate Threshold:** A minimum threshold of **100 verified historical task completions** is required before a statistical machine-learning model is permitted to serve live probabilistic delay predictions in production.
3. **Current Operating Mode:**
   - **Historical Analytics:** Fully operational and active. Tracks completed tasks, on-time delivery rate, schedule variance in working days, delay reason distribution, and team member delivery reliability.
   - **Deterministic CPM Scheduling:** Authoritative and completely decoupled from ML. The system continues to compute critical paths, float/slack, resource contention windows, and propagated dates deterministically.
   - **Scikit-Learn ML Pipeline:** Complete, reproducible, and evaluated offline in `ai-service/app/ml/` using benchmark fixtures. When queried for live active tasks, it transparently returns `NOT_READY` status with unassessed risk, clear limitations disclosures, and no fabricated numbers.

---

## 2. Prediction Target Specification

### 2.1 Prediction Problem
**Task Delay Risk:** Estimate the binary probability that an active or scheduled task will finish later than its approved planned finish date, relying solely on information available at the time of assessment.

### 2.2 Target Labels
- **Positive Label ($y = 1$):** Actual completion date occurs strictly after the approved planned finish date ($T_{\text{actual}} > T_{\text{planned}}$), resulting in $\text{variance} > 0$ working days.
- **Negative Label ($y = 0$):** Actual completion date occurs on or before the approved planned finish date ($T_{\text{actual}} \le T_{\text{planned}}$), resulting in $\text{variance} \le 0$ working days.

### 2.3 Edge Case Governance
| Scenario | Governance Policy |
| :--- | :--- |
| **Incomplete / Active Tasks** | Excluded from training target labels. Only evaluated for risk forecasting using known state. |
| **Cancelled Tasks** | Excluded from supervised training sets; archived separately with cancellation audit metadata. |
| **Reopened Tasks** | Outcome record updated only after final terminal completion, with rework duration tracked in history. |
| **Approved Baseline Changes** | Planned finish date is linked to the approved baseline in effect at prediction timestamp. Target label measures performance against the approved baseline, not unapproved forecast fluctuations. |
| **Project-Level Delay Miss** | Treated as a distinct prediction problem; task-level variance is never conflated with final project deadline risk. |

---

## 3. Historical Data Audit Results

An audit of the operational database yielded the following metrics:

| Metric | Measured Value | Threshold Requirement | Compliance Status |
| :--- | :--- | :--- | :--- |
| **Completed Task Records** | 5 | $\ge 100$ | ⚠️ Gated (`NOT_READY`) |
| **On-Time Tasks** | 4 (80.0%) | Sufficient class balance | Passed |
| **Delayed Tasks** | 1 (20.0%) | Sufficient minority representation | Passed |
| **Average Schedule Variance** | +0.6 working days | Tracked per task | Passed |
| **Missing Planned Finish Dates** | 0% (Required by schema) | 0% missing | Passed |
| **Missing Actual Finish Dates** | 0% on archived outcomes | 0% missing | Passed |
| **Delay Reasons Captured** | 100% of delayed tasks | $\ge 90\%$ | Passed |
| **Schedule Baselines** | Snapshotted at approval | Immutable versioned baselines | Passed |

---

## 4. Historical Outcome Recording Enhancements (Phase 9)

To ensure future model training can reconstruct exact prediction-time states without data leakage, Flyway migration `V7__historical_analytics_and_outcomes.sql` established the following domain enhancements:

1. **`tasks` Table Columns:**
   - `actual_start_date` (`DATE`): Date execution actually commenced.
   - `actual_finish_date` (`DATE`): Date task reached terminal completion.
2. **`task_outcome_records` Table:**
   - Immutable historical ledger recording `task_id`, `project_id`, `task_name`, `planned_finish_date`, `actual_finish_date`, `variance_working_days`, `is_delayed`, `delay_reason`, `assigned_worker_id`, `completed_at`.
   - Populated via `POST /api/tasks/{taskId}/complete`.
3. **`task_delay_predictions` Table:**
   - Audits all model assessments with `task_id`, `model_version`, `prediction_status`, `delay_risk_score`, `risk_level`, `prediction_timestamp`, and JSON snapshot of inputs for retrospective scoring.

---

## 5. Promotion Gates: From NOT READY to ENABLED

| Implementation Stage | Status | Criteria & Prerequisites |
| :--- | :--- | :--- |
| **NOT READY** | **ACTIVE FOR LIVE DATA** | Fewer than 100 verified completed tasks ($N < 100$). System returns `NOT_READY`, `delay_risk_score: null`. |
| **RESEARCH / EVALUATION** | **ACTIVE IN CI/CD** | Pipeline code, feature extractors, temporal split, and synthetic benchmarks passing all unit tests. |
| **EVALUATED** | Planned ($N \ge 100$) | Chronological train/test split evaluated; PR-AUC and ROC-AUC beat majority-class baseline with statistical significance. |
| **ENABLED** | Planned ($N \ge 250$) | Model deployed behind authenticated endpoint with continuous calibration monitoring and drift detection. |
