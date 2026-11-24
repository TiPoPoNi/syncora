import json
import httpx
from .base import BaseLLMProvider
from .mock_provider import MockLLMProvider
from ..schemas.assistant import AssistantChatRequest, AssistantChatResponse
from ..schemas.scenario import ScenarioExplanationRequest, ScenarioExplanationResponse
from ..config import settings

class OpenAIProvider(BaseLLMProvider):
    """
    OpenAI-compatible LLM provider.
    Falls back gracefully to MockLLMProvider if API key is not configured or network error occurs.
    """
    def __init__(self, api_key: str):
        self.api_key = api_key
        self.fallback = MockLLMProvider()

    async def generate_chat_response(self, request: AssistantChatRequest) -> AssistantChatResponse:
        if not self.api_key:
            return await self.fallback.generate_chat_response(request)

        # Grounded system prompt
        ctx_json = request.projectContext.model_dump_json(indent=2)
        system_prompt = (
            "You are the authoritative Project Management AI Assistant for SYNCORA (Project Resource Allocation & Scheduling System). "
            "You MUST ground all your answers strictly in the verified Project Context provided below. "
            "Never invent dates, durations, tasks, workers, or algorithms. "
            "If information is missing, explicitly state what is unavailable.\n\n"
            f"VERIFIED PROJECT CONTEXT:\n{ctx_json}"
        )

        messages = [{"role": "system", "content": system_prompt}]
        for msg in request.conversationHistory[-4:]:
            messages.append({"role": msg.role, "content": msg.content})
        messages.append({"role": "user", "content": request.userMessage})

        try:
            async with httpx.AsyncClient(timeout=settings.request_timeout_seconds) as client:
                res = await client.post(
                    "https://api.openai.com/v1/chat/completions",
                    headers={
                        "Authorization": f"Bearer {self.api_key}",
                        "Content-Type": "application/json"
                    },
                    json={
                        "model": "gpt-4o-mini",
                        "messages": messages,
                        "temperature": 0.2
                    }
                )
                if res.status_code == 200:
                    data = res.json()
                    answer = data["choices"][0]["message"]["content"]
                    return AssistantChatResponse(
                        answer=answer,
                        citations=[f"OpenAI GPT-4o-mini", f"Project Context: {request.projectContext.projectName}"],
                        relevantTaskIds=[],
                        hypothetical=False,
                        dataSnapshotVersion=request.projectContext.snapshotTimestamp or "v1.0",
                        status="SUCCESS"
                    )
                else:
                    # Fallback on non-200
                    return await self.fallback.generate_chat_response(request)
        except Exception:
            return await self.fallback.generate_chat_response(request)

    async def generate_scenario_explanation(self, request: ScenarioExplanationRequest) -> ScenarioExplanationResponse:
        # Simulations are calculated by Java scheduling engine; use deterministic grounded explanation
        return await self.fallback.generate_scenario_explanation(request)

def get_llm_provider() -> BaseLLMProvider:
    provider_name = settings.llm_provider.lower()
    if provider_name == "openai" and settings.openai_api_key:
        return OpenAIProvider(settings.openai_api_key)
    return MockLLMProvider()
