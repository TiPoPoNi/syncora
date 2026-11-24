from fastapi import APIRouter, HTTPException
from ..ml.schemas import TaskFeaturesInput, TaskDelayRiskPrediction, PipelineEvaluationMetrics
from ..ml.ml_service import ml_service

router = APIRouter(prefix="/ml", tags=["Machine Learning"])

@router.get("/status")
async def get_ml_status():
    """Returns the data readiness and operational status of the ML delay-risk prediction service."""
    return ml_service.get_status()

@router.post("/predict-delay-risk", response_model=TaskDelayRiskPrediction)
async def predict_task_delay_risk(task: TaskFeaturesInput):
    """
    Evaluates delay risk for a specific task based on validated feature inputs.
    Strictly distinguishes deterministic status from ML estimates.
    """
    return ml_service.predict_delay_risk(task)

@router.post("/evaluate-pipeline", response_model=PipelineEvaluationMetrics)
async def evaluate_pipeline():
    """
    Runs reproducible evaluation on documented benchmark fixtures to verify pipeline functionality.
    """
    return ml_service.run_benchmark()
