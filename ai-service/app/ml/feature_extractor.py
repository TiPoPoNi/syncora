import numpy as np
from typing import List, Dict, Any
from .schemas import TaskFeaturesInput

PRIORITY_MAP = {
    "LOW": 1.0,
    "MEDIUM": 2.0,
    "HIGH": 3.0
}

FEATURE_NAMES = [
    "estimated_duration",
    "remaining_duration",
    "progress",
    "priority_numeric",
    "is_critical",
    "slack_working_days",
    "predecessor_count",
    "successor_count",
    "has_resource_conflict",
    "worker_active_workload",
    "reported_delays_count",
    "vendor_involved"
]

def extract_features(task: TaskFeaturesInput) -> np.ndarray:
    """
    Extracts time-consistent feature vector without lookahead bias.
    Guarantees no future completion dates or labels are leaked.
    """
    remaining = float(task.estimated_duration) * (1.0 - float(task.progress) / 100.0)
    prio = PRIORITY_MAP.get(str(task.priority).upper(), 2.0)
    
    vec = [
        float(task.estimated_duration),
        remaining,
        float(task.progress),
        prio,
        1.0 if task.is_critical else 0.0,
        float(task.slack_working_days),
        float(task.predecessor_count),
        float(task.successor_count),
        1.0 if task.has_resource_conflict else 0.0,
        float(task.worker_active_workload),
        float(task.reported_delays_count),
        1.0 if task.vendor_involved else 0.0
    ]
    return np.array(vec, dtype=np.float32)

def extract_contributing_factors(task: TaskFeaturesInput, score: float) -> List[str]:
    """
    Generates transparent human-readable explanations of top risk contributors.
    """
    factors = []
    if task.is_critical:
        factors.append("Task lies on the Critical Path (Zero schedule float)")
    if task.has_resource_conflict:
        factors.append("Active resource assignment conflict / double-booking detected")
    if task.reported_delays_count > 0:
        factors.append(f"{task.reported_delays_count} upstream or supplier delay(s) reported")
    if task.slack_working_days == 0 and not task.is_critical:
        factors.append("Zero buffer remaining before milestone slips")
    if task.worker_active_workload >= 3:
        factors.append(f"Assigned worker has high concurrent task load ({task.worker_active_workload} active tasks)")
    if task.priority.upper() == "HIGH":
        factors.append("High priority task with strict delivery requirements")
    if not factors:
        factors.append("Standard operating baseline parameters")
    return factors
