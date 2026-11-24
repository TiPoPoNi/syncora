import os
import joblib
import numpy as np
from datetime import datetime, timedelta
from typing import Tuple, Dict, Any, List
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import StandardScaler
from sklearn.linear_model import LogisticRegression
from sklearn.tree import DecisionTreeClassifier
from sklearn.metrics import (
    accuracy_score,
    precision_score,
    recall_score,
    f1_score,
    roc_auc_score,
    confusion_matrix
)
from .schemas import PipelineEvaluationMetrics
from .feature_extractor import FEATURE_NAMES

MODEL_DIR = os.path.join(os.path.dirname(__file__), "models")

def temporal_train_test_split(
    X: np.ndarray,
    y: np.ndarray,
    timestamps: List[datetime],
    test_ratio: float = 0.25
) -> Tuple[np.ndarray, np.ndarray, np.ndarray, np.ndarray]:
    """
    Chronological train/test split preventing future-data leakage into the past.
    """
    sorted_indices = sorted(range(len(timestamps)), key=lambda k: timestamps[k])
    X_sorted = X[sorted_indices]
    y_sorted = y[sorted_indices]
    
    split_idx = int(len(X_sorted) * (1.0 - test_ratio))
    return (
        X_sorted[:split_idx],
        y_sorted[:split_idx],
        X_sorted[split_idx:],
        y_sorted[split_idx:]
    )

def generate_benchmark_fixtures(n_samples: int = 200, random_seed: int = 42) -> Tuple[np.ndarray, np.ndarray, List[datetime]]:
    """
    Generates deterministic benchmark evaluation fixtures to validate the ML training & evaluation pipeline.
    Note: These are explicitly labelled as synthetic fixtures for pipeline verification, not real historical claims.
    """
    rng = np.random.default_rng(random_seed)
    
    durations = rng.integers(1, 15, size=n_samples)
    progress = rng.integers(0, 100, size=n_samples)
    remaining = durations * (1.0 - progress / 100.0)
    priority = rng.choice([1.0, 2.0, 3.0], size=n_samples, p=[0.2, 0.5, 0.3])
    is_critical = rng.choice([0.0, 1.0], size=n_samples, p=[0.6, 0.4])
    slack = np.where(is_critical == 1.0, 0.0, rng.integers(1, 8, size=n_samples))
    predecessors = rng.integers(0, 4, size=n_samples)
    successors = rng.integers(0, 4, size=n_samples)
    conflicts = rng.choice([0.0, 1.0], size=n_samples, p=[0.75, 0.25])
    worker_load = rng.integers(0, 5, size=n_samples)
    delays_reported = rng.choice([0.0, 1.0, 2.0], size=n_samples, p=[0.7, 0.2, 0.1])
    vendor_involved = rng.choice([0.0, 1.0], size=n_samples, p=[0.8, 0.2])
    
    X = np.column_stack([
        durations, remaining, progress, priority, is_critical,
        slack, predecessors, successors, conflicts, worker_load,
        delays_reported, vendor_involved
    ]).astype(np.float32)
    
    # Ground truth: Latent risk function + stochastic variance
    latent_risk = (
        0.35 * is_critical +
        0.30 * conflicts +
        0.25 * (delays_reported > 0) +
        0.15 * (worker_load >= 3) -
        0.20 * (slack >= 3) +
        rng.normal(0, 0.2, size=n_samples)
    )
    y = (latent_risk > 0.35).astype(int)
    
    # Generate temporal dates spanning the last 180 days
    base_date = datetime(2026, 4, 1)
    timestamps = [base_date + timedelta(days=int(i * (180 / n_samples))) for i in range(n_samples)]
    
    return X, y, timestamps

def train_and_evaluate_pipeline(
    X_train: np.ndarray,
    y_train: np.ndarray,
    X_test: np.ndarray,
    y_test: np.ndarray,
    model_type: str = "logistic_regression",
    model_version: str = "v1.0-benchmark"
) -> Tuple[Pipeline, PipelineEvaluationMetrics]:
    """
    Trains and evaluates a calibrated baseline Scikit-Learn model against majority class baseline.
    """
    if model_type == "decision_tree":
        clf = DecisionTreeClassifier(max_depth=4, class_weight="balanced", random_state=42)
    else:
        clf = LogisticRegression(class_weight="balanced", max_iter=500, random_state=42)
        
    pipeline = Pipeline([
        ("scaler", StandardScaler()),
        ("classifier", clf)
    ])
    
    pipeline.fit(X_train, y_train)
    y_pred = pipeline.predict(X_test)
    y_proba = pipeline.predict_proba(X_test)[:, 1] if hasattr(pipeline, "predict_proba") else y_pred
    
    # Baseline: Majority Class
    majority_class = int(np.bincount(y_train).argmax())
    baseline_pred = np.full_like(y_test, majority_class)
    baseline_acc = float(accuracy_score(y_test, baseline_pred))
    
    model_acc = float(accuracy_score(y_test, y_pred))
    prec = float(precision_score(y_test, y_pred, zero_division=0))
    rec = float(recall_score(y_test, y_pred, zero_division=0))
    f1 = float(f1_score(y_test, y_pred, zero_division=0))
    
    try:
        roc_auc = float(roc_auc_score(y_test, y_proba))
    except Exception:
        roc_auc = 0.5
        
    tn, fp, fn, tp = confusion_matrix(y_test, y_pred, labels=[0, 1]).ravel()
    
    metrics = PipelineEvaluationMetrics(
        model_type=model_type,
        model_version=model_version,
        sample_size=len(X_train) + len(X_test),
        train_size=len(X_train),
        test_size=len(X_test),
        positive_label_count=int(np.sum(y_train) + np.sum(y_test)),
        negative_label_count=int(len(y_train) + len(y_test) - np.sum(y_train) - np.sum(y_test)),
        baseline_majority_accuracy=round(baseline_acc, 4),
        model_accuracy=round(model_acc, 4),
        precision=round(prec, 4),
        recall=round(rec, 4),
        f1_score=round(f1, 4),
        roc_auc=round(roc_auc, 4),
        confusion_matrix={"tp": int(tp), "fp": int(fp), "tn": int(tn), "fn": int(fn)},
        is_production_ready=False,
        governance_notes="Verified on reproducible synthetic fixtures. Production deployment blocked until real dataset reaches >= 100 completed tasks."
    )
    
    return pipeline, metrics

def save_model_artifacts(pipeline: Pipeline, metrics: PipelineEvaluationMetrics):
    os.makedirs(MODEL_DIR, exist_ok=True)
    model_path = os.path.join(MODEL_DIR, "task_delay_risk_v1.joblib")
    joblib.dump(pipeline, model_path)
    
    import json
    metadata_path = os.path.join(MODEL_DIR, "metadata.json")
    with open(metadata_path, "w") as f:
        json.dump(metrics.model_dump(), f, indent=2)

def load_model_artifacts() -> Tuple[Any, Any]:
    model_path = os.path.join(MODEL_DIR, "task_delay_risk_v1.joblib")
    metadata_path = os.path.join(MODEL_DIR, "metadata.json")
    if os.path.exists(model_path) and os.path.exists(metadata_path):
        import json
        pipeline = joblib.load(model_path)
        with open(metadata_path, "r") as f:
            metadata = json.load(f)
        return pipeline, metadata
    return None, None
