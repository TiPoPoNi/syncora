from typing import List, Optional, Dict, Any
from pydantic import BaseModel, Field
from .assistant import ProjectContext

class ScenarioParameters(BaseModel):
    scenarioType: str # "TASK_DELAY", "WORKER_REASSIGNMENT", "VENDOR_SUBSTITUTION"
    taskId: int
    taskName: str
    additionalDelayDays: Optional[int] = None
    delayReason: Optional[str] = None
    newWorkerId: Optional[int] = None
    newWorkerName: Optional[str] = None
    newVendorId: Optional[int] = None
    newVendorName: Optional[str] = None

class AffectedTaskPreview(BaseModel):
    taskId: int
    taskName: str
    previousPlannedFinish: str
    newPlannedFinish: str
    workingDaysShift: int
    isCritical: Optional[bool] = False

class ScenarioSimulationResult(BaseModel):
    originalForecast: str
    simulatedForecast: str
    workingDaysDifference: int # e.g. +3 (worse) or -2 (better)
    originalDeadlineStatus: str
    simulatedDeadlineStatus: str
    clientDeadline: Optional[str] = None
    affectedTasks: List[AffectedTaskPreview] = Field(default_factory=list)
    conflictsResolvedCount: int = 0
    newConflictsCount: int = 0
    unaffectedTasksCount: int = 0
    isHypothetical: bool = True

class ScenarioExplanationRequest(BaseModel):
    projectContext: ProjectContext
    scenarioParameters: ScenarioParameters
    simulationResult: ScenarioSimulationResult

class ScenarioExplanationResponse(BaseModel):
    explanation: str
    impactSummary: str
    risksAndAssumptions: List[str] = Field(default_factory=list)
    recommendedNextStep: str
    citations: List[str] = Field(default_factory=list)
    status: str = "SUCCESS"
