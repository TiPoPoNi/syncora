from abc import ABC, abstractmethod
from ..schemas.assistant import AssistantChatRequest, AssistantChatResponse
from ..schemas.scenario import ScenarioExplanationRequest, ScenarioExplanationResponse

class BaseLLMProvider(ABC):
    
    @abstractmethod
    async def generate_chat_response(self, request: AssistantChatRequest) -> AssistantChatResponse:
        pass

    @abstractmethod
    async def generate_scenario_explanation(self, request: ScenarioExplanationRequest) -> ScenarioExplanationResponse:
        pass
