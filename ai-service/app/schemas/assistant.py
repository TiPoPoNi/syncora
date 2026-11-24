from typing import List, Optional, Dict, Any
from pydantic import BaseModel, Field

class TaskContext(BaseModel):
    id: int
    name: str
    durationDays: Optional[int] = 1
    plannedStartDate: Optional[str] = None
    plannedFinishDate: Optional[str] = None
    status: Optional[str] = "TODO"
    priority: Optional[str] = "MEDIUM"
    requiredSkills: Optional[str] = None
    assignedResource: Optional[str] = None
    assignedResourceType: Optional[str] = None
    isCritical: Optional[bool] = False
    totalSlack: Optional[int] = 0
    predecessors: List[str] = Field(default_factory=list)
    successors: List[str] = Field(default_factory=list)

class DelayContext(BaseModel):
    taskId: int
    taskName: str
    delayDurationDays: int
    delayReason: str
    reportedDate: str
    notes: Optional[str] = None

class ConflictContext(BaseModel):
    resourceName: str
    conflictingTask1: str
    conflictingTask2: str
    overlapStart: str
    overlapEnd: str

class RecommendationContext(BaseModel):
    id: Optional[int] = None
    summary: str
    category: str
    status: str
    explanation: Optional[str] = None
    estimatedImpact: Optional[str] = None

class ProjectContext(BaseModel):
    projectId: int
    projectName: str
    startDate: Optional[str] = None
    deadline: Optional[str] = None
    currentForecastCompletion: Optional[str] = None
    approvedBaselineCompletion: Optional[str] = None
    deadlineStatus: str = "ON_TRACK"
    workingDaysVariance: Optional[int] = 0
    criticalPathSequence: List[str] = Field(default_factory=list)
    minimumProjectDurationDays: Optional[int] = 0
    tasks: List[TaskContext] = Field(default_factory=list)
    reportedDelays: List[DelayContext] = Field(default_factory=list)
    resourceConflicts: List[ConflictContext] = Field(default_factory=list)
    activeRecommendations: List[RecommendationContext] = Field(default_factory=list)
    snapshotTimestamp: Optional[str] = None

class ChatMessage(BaseModel):
    role: str # "user" or "assistant"
    content: str

class AssistantChatRequest(BaseModel):
    projectContext: ProjectContext
    userMessage: str
    conversationHistory: List[ChatMessage] = Field(default_factory=list)

class AssistantChatResponse(BaseModel):
    answer: str
    citations: List[str] = Field(default_factory=list)
    relevantTaskIds: List[int] = Field(default_factory=list)
    hypothetical: bool = False
    dataSnapshotVersion: Optional[str] = None
    status: str = "SUCCESS"
