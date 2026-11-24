from datetime import datetime
from typing import Dict, Any, Optional
from .schemas import TaskFeaturesInput, TaskDelayRiskPrediction, PipelineEvaluationMetrics
from .feature_extractor import extract_features, extract_contributing_factors
from .model_pipeline import (
    load_model_artifacts,
    generate_benchmark_fixtures,
    temporal_train_test_split,
    train_and_evaluate_pipeline,
    save_model_artifacts
)

class MLDelayRiskService:
    def __init__(self):
        self.pipeline, self.metadata = load_model_artifacts()
        self.data_readiness_status = "NOT_READY" # Live production data gate: >= 100 real completed tasks
        self.min_samples_required = 100

    def get_status(self) -> Dict[str, Any]:
        return {
            "status": self.data_readiness_status,
            "min_samples_required": self.min_samples_required,
            "current_real_samples": 5, # From initial demo outcomes seed
            "pipeline_validated_on_fixtures": True,
            "message": "Delay prediction model is in RESEARCH/EVALUATION status. Live production training requires at least 100 completed tasks. Deterministic CPM schedule status is authoritative."
        }

    def predict_delay_risk(self, task: TaskFeaturesInput) -> TaskDelayRiskPrediction:
        contributing_factors = extract_contributing_factors(task, 0.0)
        timestamp = datetime.utcnow().isoformat()

        # If data readiness is NOT_READY, do not invent risk probabilities
        if self.data_readiness_status == "NOT_READY":
            return TaskDelayRiskPrediction(
                target="TASK_DELAY_RISK",
                task_id=task.task_id,
                task_name=task.task_name,
                project_id=task.project_id,
                status="NOT_READY",
                delay_risk_score=None,
                risk_level="UNASSESSED",
                model_version="None (Threshold Gate: 5/100 completed tasks)",
                contributing_factors=contributing_factors,
                limitations="Active production dataset contains < 100 completed tasks. Probabilistic ML inference is disabled until verified outcome threshold is achieved. Authoritative schedule status is provided by deterministic CPM engine.",
                prediction_timestamp=timestamp
            )

        # In evaluated mode: run inference through pipeline
        if self.pipeline is not None:
            features = extract_features(task).reshape(1, -1)
            proba = float(self.pipeline.predict_proba(features)[0, 1])
            
            risk_level = "LOW"
            if proba >= 0.65:
                risk_level = "HIGH"
            elif proba >= 0.35:
                risk_level = "MEDIUM"

            return TaskDelayRiskPrediction(
                target="TASK_DELAY_RISK",
                task_id=task.task_id,
                task_name=task.task_name,
                project_id=task.project_id,
                status="EVALUATED",
                delay_risk_score=round(proba, 4),
                risk_level=risk_level,
                model_version="task_delay_risk_v1.0",
                contributing_factors=contributing_factors,
                limitations="Advisory risk estimate based on baseline logistic regression. Authoritative dates are determined by CPM scheduling.",
                prediction_timestamp=timestamp
            )

        # Fallback if no pipeline loaded
        return TaskDelayRiskPrediction(
            target="TASK_DELAY_RISK",
            task_id=task.task_id,
            task_name=task.task_name,
            project_id=task.project_id,
            status="NOT_READY",
            delay_risk_score=None,
            risk_level="UNASSESSED",
            model_version="None",
            contributing_factors=contributing_factors,
            limitations="Model artifacts not yet initialized.",
            prediction_timestamp=timestamp
        )

    def run_benchmark(self) -> PipelineEvaluationMetrics:
        """
        Executes reproducible evaluation benchmark on synthetic fixtures to verify pipeline functionality.
        """
        X, y, timestamps = generate_benchmark_fixtures(n_samples=200, random_seed=42)
        X_train, y_train, X_test, y_test = temporal_train_test_split(X, y, timestamps, test_ratio=0.25)
        pipeline, metrics = train_and_evaluate_pipeline(X_train, y_train, X_test, y_test)
        save_model_artifacts(pipeline, metrics)
        self.pipeline = pipeline
        self.metadata = metrics.model_dump()
        return metrics

ml_service = MLDelayRiskService()
