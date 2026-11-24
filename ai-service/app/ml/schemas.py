from typing import List, Optional, Dict, Any
from pydantic import BaseModel, Field

class TaskFeaturesInput(BaseModel):
    task_id: int
    task_name: str
    project_id: int
    estimated_duration: int = Field(default=1, ge=1)
    progress: int = Field(default=0, ge=0, le=100)
    priority: str = Field(default="MEDIUM") # LOW, MEDIUM, HIGH
    is_critical: bool = False
    slack_working_days: int = Field(default=0, ge=0)
    predecessor_count: int = Field(default=0, ge=0)
    successor_count: int = Field(default=0, ge=0)
    has_resource_conflict: bool = False
    worker_active_workload: int = Field(default=0, ge=0)
    reported_delays_count: int = Field(default=0, ge=0)
    vendor_involved: bool = False

class TaskDelayRiskPrediction(BaseModel):
    target: str = "TASK_DELAY_RISK"
    task_id: int
    task_name: str
    project_id: int
    status: str # NOT_READY, EVALUATED, ENABLED
    delay_risk_score: Optional[float] = None # Probability 0.0 to 1.0 (null if NOT_READY)
    risk_level: str # LOW, MEDIUM, HIGH, UNASSESSED
    model_version: str
    contributing_factors: List[str]
    limitations: str
    prediction_timestamp: str

class PipelineEvaluationMetrics(BaseModel):
    model_type: str
    model_version: str
    sample_size: int
    train_size: int
    test_size: int
    positive_label_count: int
    negative_label_count: int
    baseline_majority_accuracy: float
    model_accuracy: float
    precision: float
    recall: float
    f1_score: float
    roc_auc: float
    confusion_matrix: Dict[str, int] # tp, fp, tn, fn
    is_production_ready: bool
    governance_notes: str
