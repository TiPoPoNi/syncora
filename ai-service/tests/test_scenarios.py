import pytest
from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)

SAMPLE_SCENARIO_PAYLOAD = {
    "projectContext": {
        "projectId": 1,
        "projectName": "E-Commerce Website Launch",
        "deadline": "2026-11-20",
        "currentForecastCompletion": "2026-11-06",
        "deadlineStatus": "ON_TRACK",
        "workingDaysVariance": 0,
        "tasks": []
    },
    "scenarioParameters": {
        "scenarioType": "TASK_DELAY",
        "taskId": 1,
        "taskName": "Requirements",
        "additionalDelayDays": 3,
        "delayReason": "Client design overhaul"
    },
    "simulationResult": {
        "originalForecast": "2026-11-06",
        "simulatedForecast": "2026-11-11",
        "workingDaysDifference": 3,
        "originalDeadlineStatus": "ON_TRACK",
        "simulatedDeadlineStatus": "AT_RISK",
        "clientDeadline": "2026-11-20",
        "affectedTasks": [
            {
                "taskId": 2,
                "taskName": "UI Design",
                "previousPlannedFinish": "2026-10-23",
                "newPlannedFinish": "2026-10-28",
                "workingDaysShift": 3,
                "isCritical": True
            }
        ],
        "conflictsResolvedCount": 0,
        "newConflictsCount": 0,
        "unaffectedTasksCount": 5,
        "isHypothetical": True
    }
}

def test_explain_scenario():
    response = client.post("/api/assistant/explain-scenario", json=SAMPLE_SCENARIO_PAYLOAD)
    assert response.status_code == 200
    data = response.json()
    assert "Requirements" in data["explanation"]
    assert "3 working day(s) later" in data["explanation"]
    assert "strictly hypothetical" in data["risksAndAssumptions"][0]
    assert data["status"] == "SUCCESS"
