import logging
from ..schemas.assistant import AssistantChatRequest, AssistantChatResponse
from ..schemas.scenario import ScenarioExplanationRequest, ScenarioExplanationResponse
from ..providers.openai_provider import get_llm_provider

logger = logging.getLogger(__name__)

class AssistantService:
    def __init__(self):
        self.provider = get_llm_provider()

    async def chat(self, request: AssistantChatRequest) -> AssistantChatResponse:
        logger.info(f"Processing chat request for project {request.projectContext.projectId}: '{request.userMessage}'")
        try:
            return await self.provider.generate_chat_response(request)
        except Exception as e:
            logger.error(f"Error in chat generation: {e}")
            return AssistantChatResponse(
                answer="An error occurred while generating the assistant response. Factual project data remains safely available.",
                citations=[],
                relevantTaskIds=[],
                hypothetical=False,
                status=f"ERROR: {str(e)}"
            )

    async def explain_scenario(self, request: ScenarioExplanationRequest) -> ScenarioExplanationResponse:
        logger.info(f"Processing scenario explanation for project {request.projectContext.projectId}, task {request.scenarioParameters.taskId}")
        try:
            return await self.provider.generate_scenario_explanation(request)
        except Exception as e:
            logger.error(f"Error in scenario explanation: {e}")
            return ScenarioExplanationResponse(
                explanation="Scenario calculation was performed, but explanation generation encountered an issue.",
                impactSummary="Check Java backend simulation results.",
                risksAndAssumptions=[],
                recommendedNextStep="Review task dates manually.",
                citations=[],
                status=f"ERROR: {str(e)}"
            )
