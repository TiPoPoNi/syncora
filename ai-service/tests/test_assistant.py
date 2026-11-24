import pytest
from fastapi.testclient import TestClient
from app.main import app

client = TestClient(app)

SAMPLE_PROJECT_CONTEXT = {
    "projectId": 1,
    "projectName": "E-Commerce Website Launch",
    "startDate": "2026-10-19",
    "deadline": "2026-11-20",
    "currentForecastCompletion": "2026-11-06",
    "approvedBaselineCompletion": "2026-11-06",
    "deadlineStatus": "DELAYED",
    "workingDaysVariance": 3,
    "criticalPathSequence": ["Requirements", "UI Design", "Frontend Development", "Integration", "Testing", "Deployment"],
    "minimumProjectDurationDays": 16,
    "tasks": [
        {"id": 1, "name": "Requirements", "durationDays": 2, "isCritical": True, "totalSlack": 0},
        {"id": 3, "name": "Backend Development", "durationDays": 5, "isCritical": False, "totalSlack": 2}
    ],
    "reportedDelays": [
        {
            "taskId": 1,
            "taskName": "Requirements",
            "delayDurationDays": 3,
            "delayReason": "Client scope refinement",
            "reportedDate": "2026-10-20"
        }
    ],
    "resourceConflicts": [
        {
            "resourceName": "Rahul Kumar",
            "conflictingTask1": "API Integration",
            "conflictingTask2": "Backend Development",
            "overlapStart": "2026-10-21",
            "overlapEnd": "2026-10-22"
        }
    ],
    "activeRecommendations": [
        {
            "id": 1,
            "summary": "Reassign 'Backend Development' to Vikram Mehta",
            "category": "WORKER_REASSIGNMENT",
            "status": "PROPOSED",
            "explanation": "Resolves overlap on Rahul Kumar",
            "estimatedImpact": "Conflicts resolved: 1"
        }
    ],
    "snapshotTimestamp": "2026-10-20T10:00:00"
}

def test_chat_delay_explanation():
    payload = {
        "projectContext": SAMPLE_PROJECT_CONTEXT,
        "userMessage": "Why is this project delayed?",
        "conversationHistory": []
    }
    response = client.post("/api/assistant/chat", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert "Requirements" in data["answer"]
    assert "Client scope refinement" in data["answer"]
    assert "3 working days" in data["answer"]
    assert len(data["citations"]) > 0
    assert 1 in data["relevantTaskIds"]

def test_chat_critical_path():
    payload = {
        "projectContext": SAMPLE_PROJECT_CONTEXT,
        "userMessage": "What is the critical path?",
        "conversationHistory": []
    }
    response = client.post("/api/assistant/chat", json=payload)
    assert response.status_code == 200
    data = response.json()
    assert "Requirements" in data["answer"]
    assert "16 working days" in data["answer"]
    assert "zero scheduling slack" in data["answer"]

def test_chat_empty_message_validation():
    payload = {
        "projectContext": SAMPLE_PROJECT_CONTEXT,
        "userMessage": "   ",
        "conversationHistory": []
    }
    response = client.post("/api/assistant/chat", json=payload)
    assert response.status_code == 400
    assert "cannot be empty" in response.json()["detail"]
