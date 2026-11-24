from typing import List
from .base import BaseLLMProvider
from ..schemas.assistant import AssistantChatRequest, AssistantChatResponse
from ..schemas.scenario import ScenarioExplanationRequest, ScenarioExplanationResponse

class MockLLMProvider(BaseLLMProvider):
    """
    Deterministic grounded assistant provider for development, testing, and offline modes.
    Answers are synthesized directly and exclusively from the authorized ProjectContext facts.
    """

    async def generate_chat_response(self, request: AssistantChatRequest) -> AssistantChatResponse:
        ctx = request.projectContext
        q = request.userMessage.lower().strip()
        citations = []
        relevant_task_ids = []

        # 1. Why is this project delayed / at risk?
        if any(w in q for w in ["why", "reason", "cause"]) and any(w in q for w in ["delay", "at risk", "late", "behind"]):
            citations.append(f"Project Status: {ctx.deadlineStatus}")
            if ctx.reportedDelays:
                delay_texts = []
                for d in ctx.reportedDelays:
                    delay_texts.append(f"• Task '{d.taskName}': {d.delayDurationDays} working days delay reported on {d.reportedDate} due to '{d.delayReason}'")
                    citations.append(f"Delay Record #{d.taskId} ({d.taskName})")
                    relevant_task_ids.append(d.taskId)
                
                answer = (
                    f"**{ctx.projectName}** is currently **{ctx.deadlineStatus}**.\n\n"
                    f"• **Current Forecast Completion**: {ctx.currentForecastCompletion}\n"
                    f"• **Project Deadline**: {ctx.deadline or 'Not set'}\n"
                    f"• **Variance**: {ctx.workingDaysVariance} working day(s) against target.\n\n"
                    f"**Identified Root Causes from Project Records**:\n" + "\n".join(delay_texts) + "\n\n"
                    f"Because these tasks sit on or feed into downstream dependencies, the delays propagated through the schedule calculation. "
                    f"If you wish to mitigate this overrun, consult the **Schedule Optimization** recommendations to evaluate resource substitutions or slack adjustments."
                )
            else:
                answer = (
                    f"**{ctx.projectName}** has status **{ctx.deadlineStatus}** with forecast completion on **{ctx.currentForecastCompletion}** "
                    f"versus deadline **{ctx.deadline}**. No specific task delays have been logged in the delay register yet."
                )

        # 2. Critical path query
        elif "critical path" in q or "longest chain" in q:
            citations.append("Authoritative CPM Engine (CriticalPathService)")
            cp_tasks = ctx.criticalPathSequence if ctx.criticalPathSequence else [t.name for t in ctx.tasks if t.isCritical]
            for t in ctx.tasks:
                if t.isCritical:
                    relevant_task_ids.append(t.id)

            seq_str = " ➔ ".join(cp_tasks) if cp_tasks else "None identified"
            answer = (
                f"**Current Critical Path for {ctx.projectName}**:\n\n"
                f"**Sequence**: {seq_str}\n\n"
                f"• **Minimum Project Duration**: {ctx.minimumProjectDurationDays} working days\n"
                f"• **Earliest Projected Completion**: {ctx.currentForecastCompletion}\n\n"
                f"All tasks on this sequence have **zero scheduling slack** ($LS - ES = 0$). Any delay to any of these critical tasks "
                f"directly postpones the overall project delivery date."
            )

        # 3. Scheduling slack / float query
        elif "slack" in q or "float" in q:
            citations.append("CPM Total Float Calculation")
            sorted_tasks = sorted(ctx.tasks, key=lambda t: t.totalSlack if t.totalSlack is not None else 999)
            lines = []
            for t in sorted_tasks:
                relevant_task_ids.append(t.id)
                tag = "CRITICAL (0 days slack)" if t.isCritical else f"{t.totalSlack} working day(s) slack"
                lines.append(f"• **{t.name}**: {tag} (Window: {t.plannedStartDate} → {t.plannedFinishDate})")
            
            answer = (
                f"**Task Slack & Buffer Analysis for {ctx.projectName}**:\n\n"
                + "\n".join(lines[:8]) +
                "\n\nTasks with positive slack can absorb non-critical delays without pushing the final project completion."
            )

        # 4. Resource recommendation / optimizer query
        elif "optimizer" in q or "recommend" in q or "vikram" in q or "alternative" in q:
            citations.append("ScheduleOptimizationService & ConflictDetector")
            if ctx.activeRecommendations:
                top_rec = ctx.activeRecommendations[0]
                answer = (
                    f"**Latest Optimization Recommendation (Status: {top_rec.status})**:\n\n"
                    f"• **Proposed Action**: {top_rec.summary}\n"
                    f"• **Category**: {top_rec.category}\n"
                    f"• **Algorithmic Rationale**: {top_rec.explanation}\n"
                    f"• **Projected Impact**: {top_rec.estimatedImpact}\n\n"
                    f"The deterministic optimizer evaluated qualified workers against mandatory skill requirements, "
                    f"verified zero overlapping assignments during the active window, and confirmed that applying this change eliminates team bottlenecks."
                )
            else:
                answer = (
                    f"No active recommendations are currently pending for {ctx.projectName}. "
                    f"Run the deterministic schedule optimizer from the Optimization tab to generate ranked alternatives."
                )

        # 5. Resource conflicts query
        elif "conflict" in q or "overlap" in q or "double book" in q:
            citations.append("ConflictDetector Overlap Engine")
            if ctx.resourceConflicts:
                conflict_lines = []
                for c in ctx.resourceConflicts:
                    conflict_lines.append(f"• **{c.resourceName}**: Overlapping simultaneous tasks **'{c.conflictingTask1}'** and **'{c.conflictingTask2}'** between {c.overlapStart} and {c.overlapEnd}")
                    citations.append(f"Resource Conflict ({c.resourceName})")
                
                answer = (
                    f"**Detected Resource Overlaps for {ctx.projectName}** ({len(ctx.resourceConflicts)} active):\n\n"
                    + "\n".join(conflict_lines) +
                    "\n\nThese concurrent allocations exceed daily worker capacity. Use the **Schedule Optimization** dashboard to reassign or shift the conflicting tasks."
                )
            else:
                answer = f"No resource allocation conflicts are currently detected for **{ctx.projectName}**. All assigned workers and vendors have non-overlapping capacity."

        # 6. Baseline comparison query
        elif "baseline" in q or "compare" in q or "plan" in q:
            citations.append("ProjectBaselineService (Approved Snapshots)")
            base_comp = ctx.approvedBaselineCompletion or "No approved baseline recorded"
            answer = (
                f"**Baseline vs. Forecast Comparison for {ctx.projectName}**:\n\n"
                f"• **Approved Baseline Completion**: {base_comp}\n"
                f"• **Current Forecast Completion**: {ctx.currentForecastCompletion}\n"
                f"• **Client Deadline**: {ctx.deadline or 'N/A'}\n"
                f"• **Working-Day Variance**: {ctx.workingDaysVariance} day(s)\n"
                f"• **Current Status**: **{ctx.deadlineStatus}**\n\n"
                f"The baseline represents the approved master schedule. The forecast reflects the live CPM recalculation considering reported execution delays and dependencies."
            )

        # 7. General fallback grounded in factual context
        else:
            citations.append(f"Project Metadata: {ctx.projectName} (ID {ctx.projectId})")
            answer = (
                f"Here is the verified status for **{ctx.projectName}** (ID #{ctx.projectId}):\n\n"
                f"• **Status**: **{ctx.deadlineStatus}**\n"
                f"• **Forecast Completion**: {ctx.currentForecastCompletion}\n"
                f"• **Client Deadline**: {ctx.deadline or 'None'}\n"
                f"• **Total Tasks**: {len(ctx.tasks)} tasks ({sum(1 for t in ctx.tasks if t.isCritical)} critical)\n"
                f"• **Active Conflicts**: {len(ctx.resourceConflicts)}\n"
                f"• **Logged Delays**: {len(ctx.reportedDelays)}\n\n"
                f"You can ask me to explain delays, analyze critical path tasks, review worker suitability, or simulate hypothetical delay scenarios."
            )

        return AssistantChatResponse(
            answer=answer,
            citations=citations,
            relevantTaskIds=relevant_task_ids,
            hypothetical=False,
            dataSnapshotVersion=ctx.snapshotTimestamp or "v1.0",
            status="SUCCESS"
        )

    async def generate_scenario_explanation(self, request: ScenarioExplanationRequest) -> ScenarioExplanationResponse:
        params = request.scenarioParameters
        sim = request.simulationResult
        ctx = request.projectContext

        diff = sim.workingDaysDifference
        if diff > 0:
            diff_text = f"+{diff} working day(s) later (DELAYED)"
        elif diff < 0:
            diff_text = f"{abs(diff)} working day(s) earlier (IMPROVED)"
        else:
            diff_text = "0 working days change (ABSORBED BY SLACK)"

        explanation = (
            f"**Hypothetical Simulation Analysis**: Simulating {params.scenarioType} on **'{params.taskName}'** "
            f"({params.additionalDelayDays or 0} working days shift).\n\n"
            f"• **Original Completion**: {sim.originalForecast} ({sim.originalDeadlineStatus})\n"
            f"• **Simulated Completion**: {sim.simulatedForecast} ({sim.simulatedDeadlineStatus})\n"
            f"• **Net Schedule Impact**: **{diff_text}**\n"
            f"• **Affected Downstream Tasks**: {len(sim.affectedTasks)} task(s) shifted\n"
            f"• **Conflicts Resolved**: {sim.conflictsResolvedCount} | **New Conflicts**: {sim.newConflictsCount}"
        )

        impact_summary = (
            f"The Java scheduling engine recalculated the entire dependency chain without modifying active project data. "
            f"Deadline status transitioned from {sim.originalDeadlineStatus} to {sim.simulatedDeadlineStatus}."
        )

        risks = [
            "This simulation is strictly hypothetical; no project records or task dates were modified.",
            "Downstream dependencies and vendor lead times must be confirmed before enacting changes.",
            "Resource availability during the shifted timeframe should be verified with the assigned team."
        ]

        next_step = (
            "If you want to apply this schedule adjustment, navigate to the Delay Reporting or Task Management interface "
            "to formally submit the change through the approval workflow."
        )

        citations = [
            f"Hypothetical Simulation: {params.taskName}",
            f"SchedulingEngine Calculation ({sim.originalForecast} → {sim.simulatedForecast})",
            f"Client Deadline: {sim.clientDeadline or 'N/A'}"
        ]

        return ScenarioExplanationResponse(
            explanation=explanation,
            impactSummary=impact_summary,
            risksAndAssumptions=risks,
            recommendedNextStep=next_step,
            citations=citations,
            status="SUCCESS"
        )
