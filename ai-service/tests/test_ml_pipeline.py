import pytest
from datetime import datetime
from fastapi.testclient import TestClient
from app.main import app
from app.ml.schemas import TaskFeaturesInput
from app.ml.feature_extractor import extract_features, FEATURE_NAMES
from app.ml.model_pipeline import (
    generate_benchmark_fixtures,
    temporal_train_test_split,
    train_and_evaluate_pipeline
)

client = TestClient(app)

def test_feature_extraction_no_data_leakage():
    task = TaskFeaturesInput(
        task_id=10,
        task_name="Backend API",
        project_id=1,
        estimated_duration=5,
        progress=40,
        priority="HIGH",
        is_critical=True,
        slack_working_days=0,
        predecessor_count=2,
        successor_count=1,
        has_resource_conflict=True,
        worker_active_workload=3,
        reported_delays_count=1,
        vendor_involved=False
    )
    
    vec = extract_features(task)
    assert len(vec) == len(FEATURE_NAMES)
    assert len(vec) == 12
    # estimated_duration = 5
    assert vec[0] == 5.0
    # remaining_duration = 5 * (1 - 0.4) = 3.0
    assert vec[1] == 3.0
    # progress = 40.0
    assert vec[2] == 40.0
    # priority HIGH = 3.0
    assert vec[3] == 3.0
    # is_critical = 1.0
    assert vec[4] == 1.0
    # slack = 0.0
    assert vec[5] == 0.0
    # has_conflict = 1.0
    assert vec[8] == 1.0

def test_temporal_train_test_split():
    X, y, timestamps = generate_benchmark_fixtures(n_samples=100, random_seed=42)
    assert len(X) == 100
    assert len(y) == 100
    assert len(timestamps) == 100
    
    X_train, y_train, X_test, y_test = temporal_train_test_split(X, y, timestamps, test_ratio=0.25)
    assert len(X_train) == 75
    assert len(X_test) == 25
    assert len(y_train) == 75
    assert len(y_test) == 25

def test_pipeline_training_and_evaluation():
    X, y, timestamps = generate_benchmark_fixtures(n_samples=120, random_seed=42)
    X_train, y_train, X_test, y_test = temporal_train_test_split(X, y, timestamps, test_ratio=0.25)
    
    pipeline, metrics = train_and_evaluate_pipeline(X_train, y_train, X_test, y_test)
    assert metrics.sample_size == 120
    assert metrics.train_size == 90
    assert metrics.test_size == 30
    assert metrics.model_accuracy > 0.5
    assert metrics.f1_score >= 0.0
    assert "tp" in metrics.confusion_matrix
    assert metrics.is_production_ready == False # Governance gate enforced

def test_ml_status_endpoint():
    response = client.get("/ml/status")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "NOT_READY"
    assert data["min_samples_required"] == 100
    assert "message" in data

def test_predict_delay_risk_endpoint():
    payload = {
        "task_id": 1,
        "task_name": "Requirements",
        "project_id": 1,
        "estimated_duration": 4,
        "progress": 50,
        "priority": "HIGH",
        "is_critical": True,
        "slack_working_days": 0,
        "predecessor_count": 0,
        "successor_count": 2,
        "has_resource_conflict": False,
        "worker_active_workload": 1,
        "reported_delays_count": 0,
        "vendor_involved": False
    }
    response = client.post("/ml/predict-delay-risk", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert data["target"] == "TASK_DELAY_RISK"
    assert data["task_id"] == 1
    assert data["status"] == "NOT_READY"
    assert data["risk_level"] == "UNASSESSED"
    assert data["delay_risk_score"] is None # Governance: Not fabricated
    assert len(data["contributing_factors"]) > 0

def test_evaluate_pipeline_endpoint():
    response = client.post("/ml/evaluate-pipeline")
    assert response.status_code == 200
    data = response.json()
    assert data["sample_size"] == 200
    assert data["model_accuracy"] > 0.0
    assert "confusion_matrix" in data
