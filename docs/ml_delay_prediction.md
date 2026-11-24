# Machine Learning Task Delay Risk Prediction: Pipeline & Architecture

**Project:** Project Resource Allocation & Scheduling System (PRS)  
**System Phase:** Phase 9 — Historical Analytics and ML-Based Project Delay Prediction  
**Status:** Pipeline Implemented & Validated (`NOT_READY` for live inference until $N \ge 100$)  
**Implementation Technology:** Python 3.12, Scikit-learn 1.9, NumPy 2.5, FastAPI, Pydantic  

---

## 1. Overview & System Flow

The ML Task Delay Prediction subsystem provides early-warning risk indicators for task slippage before deadlines arrive. It is designed around two strict architectural rules:

1. **Deterministic Separation:** The deterministic CPM scheduling engine (Java) is 100% authoritative for deadlines, float/slack, critical paths, and resource conflict detection. The ML model produces supplementary advisory risk scores without mutating schedules or dependencies.
2. **Zero Lookahead Leakage:** Every feature input must represent information known strictly at or before the prediction timestamp. Terminal completion dates, post-completion delays, or status fields encoding outcomes are strictly prohibited from feature vectors.

```
+-----------------------------+
| Operational Database        |
| (Tasks, Baselines, Delays)  |
+--------------+--------------+
               |
               v
+-----------------------------+       +-------------------------------+
| Deterministic CPM Scheduler | ----> | Real-time Slack, Float, Path  |
+--------------+--------------+       +---------------+---------------+
               |                                      |
               v                                      v
+-------------------------------------------------------------+
| Feature Extractor (12 Time-Aware Features, No Leakage)      |
+------------------------------+------------------------------+
                               |
                               v
+-------------------------------------------------------------+
| Governance Gate (Check N >= 100 Completed Task Records)     |
+---------------+-----------------------------+---------------+
                | (< 100)                     | (>= 100)
                v                             v
+-------------------------------+ +---------------------------+
| Status: NOT_READY             | | Scikit-Learn Pipeline     |
| Risk Level: UNASSESSED        | | (StandardScaler + LogReg) |
| Score: null                   | +-------------+-------------+
+---------------+---------------+               |
                |                               v
                +-----------------> Manager Dashboard
                                    (Clear Distinction: Deterministic Status vs ML Risk)
```

---

## 2. Feature Dictionary & Anti-Leakage Constraints

The model uses 12 validated, leak-free features extracted strictly from prediction-time data:

| Feature Name | Type | Description | Source at Prediction Time | Leakage Prevention Rule |
| :--- | :--- | :--- | :--- | :--- |
| `estimated_duration` | Integer | Planned duration in working days | Approved task specification | Fixed prior to execution |
| `remaining_duration` | Integer | Estimated work days remaining | `ceil(duration * (1 - progress/100))` | Evaluated at snapshot date |
| `progress_percent` | Float | Current percent completion [0..100] | Progress logged by assignee | Cannot use post-finish 100% |
| `days_to_deadline` | Integer | Working days between now and planned finish | Calendar math using WorkingDayCalculator | Independent of actual finish date |
| `priority_score` | Integer | Ordinal priority (Low=1, Med=2, High=3, Critical=4) | Task metadata | Pre-execution assignment |
| `predecessor_count` | Integer | In-degree count in dependency DAG | Prerequisite task count | Static graph structure |
| `successor_count` | Integer | Out-degree count in dependency DAG | Downstream dependent tasks | Static graph structure |
| `is_critical` | Integer | Binary (1 if Total Float = 0, else 0) | Deterministic CPM Engine | Real-time topological analysis |
| `slack_working_days`| Integer | Total float in working days | Deterministic CPM Engine | Real-time topological analysis |
| `conflict_count` | Integer | Overlapping resource assignments | Resource Conflict Detector | In-memory capacity check |
| `worker_active_load`| Integer | Concurrent tasks assigned to worker | Worker assignment roster | Real-time roster count |
| `reported_delays` | Integer | Delay events logged prior to now | `task_delays` where report date <= now | Future delay events excluded |

---

## 3. Scikit-Learn Model Pipeline

The machine learning pipeline is built with standard Scikit-learn primitives (`ai-service/app/ml/model_pipeline.py`):

```python
Pipeline([
    ('scaler', StandardScaler()),
    ('classifier', LogisticRegression(
        class_weight='balanced',
        solver='liblinear',
        random_state=42,
        max_iter=1000
    ))
])
```

### 3.1 Temporal Train / Test Splitting
Random $k$-fold cross-validation introduces data leakage when tasks share project timelines. To prevent this, the pipeline enforces **temporal chronological splitting**:
- Sorts training records strictly by `planned_start_date` or completion timestamp.
- The earliest 75% of historical tasks form the training set.
- The most recent 25% of tasks form the evaluation set.

### 3.2 Evaluation Against Majority-Class Baseline
Every trained model is evaluated against the naive **Majority-Class Baseline** (predicting the most common outcome, e.g., On-Time). The pipeline computes:
- **Precision:** $\frac{TP}{TP + FP}$
- **Recall:** $\frac{TP}{TP + FN}$
- **F1 Score:** $2 \cdot \frac{\text{Precision} \cdot \text{Recall}}{\text{Precision} + \text{Recall}}$
- **PR-AUC (Average Precision):** Area under the Precision-Recall curve (recommended for imbalanced datasets).
- **ROC-AUC:** Area under the ROC curve.
- **Brier Score:** Calibration assessment $\frac{1}{N}\sum (p_i - y_i)^2$.

---

## 4. API Endpoints (`ai-service/app/api/ml_routes.py`)

### 4.1 Check ML Status
```http
GET /ml/status
```
**Response:**
```json
{
  "status": "NOT_READY",
  "reason": "Active training records (5) do not meet the minimum sample gate of 100 completed tasks.",
  "active_samples": 5,
  "required_samples": 100,
  "last_evaluated": null,
  "active_model_version": null
}
```

### 4.2 Predict Task Delay Risk
```http
POST /ml/predict-delay-risk
```
**Request Body:**
```json
{
  "task_id": 1,
  "estimated_duration": 5,
  "remaining_duration": 5,
  "progress_percent": 0.0,
  "days_to_deadline": 5,
  "priority": "HIGH",
  "predecessor_count": 0,
  "successor_count": 2,
  "is_critical": true,
  "slack_working_days": 0,
  "conflict_count": 0,
  "worker_active_load": 1,
  "reported_delays_count": 0,
  "has_vendor_dependency": false,
  "sample_count": 5
}
```
**Response:**
```json
{
  "task_id": 1,
  "prediction_target": "TASK_DELAY_RISK",
  "prediction_status": "NOT_READY",
  "delay_risk_score": null,
  "risk_level": "UNASSESSED",
  "model_version": "None (Data Threshold Gate: 5/100 completed tasks)",
  "contributing_features": [
    "Task lies on the Critical Path (Float = 0 days)",
    "High priority milestone"
  ],
  "limitations": "Active production dataset contains 5 completed task records. A minimum of 100 verified task outcomes is required to train a reliable Scikit-Learn baseline. Model training pipeline is validated on test fixtures in ai-service/app/ml/.",
  "prediction_timestamp": "2026-10-18T22:26:53Z"
}
```

### 4.3 Evaluate Model Pipeline
```http
POST /ml/evaluate-pipeline
```
Executes a reproducible temporal train/test evaluation on benchmark fixtures (default: 200 synthetic validation records) and returns comprehensive evaluation metrics without contaminating production storage.
