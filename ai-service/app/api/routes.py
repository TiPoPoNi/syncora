from fastapi import APIRouter, HTTPException
from ..schemas.assistant import AssistantChatRequest, AssistantChatResponse
from ..schemas.scenario import ScenarioExplanationRequest, ScenarioExplanationResponse
from ..services.assistant_service import AssistantService
from ..config import settings

router = APIRouter()
assistant_service = AssistantService()

@router.get("/health")
async def health_check():
    return {
        "status": "healthy",
        "service": settings.service_name,
        "version": settings.version,
        "provider": settings.llm_provider
    }

@router.get("/ready")
async def readiness_check():
    return {
        "ready": True,
        "provider": settings.llm_provider
    }

@router.post("/api/assistant/chat", response_model=AssistantChatResponse)
async def chat_with_assistant(request: AssistantChatRequest):
    if not request.userMessage.strip():
        raise HTTPException(status_code=400, detail="User message cannot be empty")
    return await assistant_service.chat(request)

@router.post("/api/assistant/explain-scenario", response_model=ScenarioExplanationResponse)
async def explain_scenario(request: ScenarioExplanationRequest):
    return await assistant_service.explain_scenario(request)
