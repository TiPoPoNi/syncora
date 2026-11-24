# Phase 11 — Analytics & KPI Metrics Reference

This document provides formal definitions, mathematical formulas, inclusion/exclusion criteria, edge-case treatment, and interpretation guidelines for all Business Intelligence (BI) and Portfolio Analytics metrics in the **Project Resource Allocation & Scheduling System (PRS)**.

---

## 1. Portfolio Governance & Executive Overview Metrics

### 1.1 Total Projects
* **Definition**: The total count of all project records visible to the requesting security principal within the selected filter criteria.
* **Formula**: $\sum 1$ for all scoped projects.
* **Inclusions**: All projects matching filter parameters (Date Range, Status, Priority, Client, Project Manager).
* **Exclusions**: Projects belonging to other client organizations when requested by a `CLIENT` role user.
* **Interpretation**: Represents the aggregate scope of governance responsibility.

### 1.2 Active Projects
* **Definition**: Projects currently undergoing planning, resource assignment, or active execution.
* **Formula**: $\sum 1 \text{ where } \text{status} \in \{\text{PLANNED}, \text{IN\_PROGRESS}\}$.
* **Inclusions**: Projects with status `PLANNED` or `IN_PROGRESS` (including unassigned statuses defaulted to planned).
* **Exclusions**: Projects marked as `ON_HOLD`, `COMPLETED`, or `CANCELLED`.
* **Interpretation**: Represents the active operational workload requiring continuous scheduling, CPM tracking, and conflict monitoring.

### 1.3 Completed Projects
* **Definition**: Projects that have completed all scheduled tasks and finalized deliverables.
* **Formula**: $\sum 1 \text{ where } \text{status} = \text{COMPLETED}$.
* **Inclusions**: Projects marked with terminal status `COMPLETED`.
* **Exclusions**: Active, on-hold, or cancelled projects.
* **Interpretation**: Denominator baseline for historical performance evaluations (e.g., historical on-time delivery rate).

### 1.4 Projects Forecast to Miss Deadlines
* **Definition**: Active projects whose current deterministic CPM schedule forecast indicates an expected finish date strictly later than the approved hard deadline.
* **Formula**: 
  $$\sum 1 \text{ where status } \ne \text{COMPLETED} \land \text{expectedCompletionDate} > \text{deadline}$$
* **Inclusions**: Active projects having both a defined `deadline` and a computed `expectedCompletionDate`.
* **Exclusions**: Completed projects (which have historical actual outcomes, not forecasts), cancelled projects, and projects without a hard deadline.
* **Distinction**: Clearly separated from historical delivery failure; this is a forward-looking predictive indicator.

### 1.5 Historical On-Time Delivery Rate
* **Definition**: The percentage of completed projects that finished on or before their planned baseline completion date.
* **Formula**:
  $$\text{On-Time Delivery Rate (\%)} = \left( \frac{\text{Completed Projects Delivered on or before Baseline Finish}}{\text{Total Completed Projects with Baseline Records}} \right) \times 100$$
* **Numerator**: Completed projects where $\text{actualCompletionDate} \le \text{plannedFinishDate}$ (or $\text{deadline}$).
* **Denominator**: Total completed projects with valid recorded completion and baseline finish dates.
* **Zero Denominator Safeguard**: When completed projects $= 0$, the metric explicitly yields **`null` (displayed as `N/A`)**, preventing misleading $0\%$ ratings.
* **Exclusions**: Active projects (forecasts are not actual results), cancelled projects.

### 1.6 Average Schedule Variance for Completed Projects
* **Definition**: The average difference in calendar days between actual project completion and planned baseline finish across all completed projects.
* **Formula**:
  $$\overline{\Delta \text{Days}} = \frac{1}{N_{\text{completed}}} \sum_{i=1}^{N_{\text{completed}}} (\text{actualCompletionDate}_i - \text{plannedFinishDate}_i)$$
* **Interpretation**:
  * $\le 0 \text{ days}$: Project portfolio delivered on time or ahead of schedule (favorable).
  * $> 0 \text{ days}$: Average schedule slippage experienced across completed initiatives.
* **Missing Data / Edge Cases**: Yields `null` (`N/A`) when $N_{\text{completed}} = 0$.

### 1.7 Total Overdue Tasks
* **Definition**: Open, unfinished tasks whose scheduled end date is prior to the current system date ($T_{\text{now}}$).
* **Formula**:
  $$\sum 1 \text{ where status } \ne \text{COMPLETED} \land \text{endDate} < T_{\text{now}}$$
* **Inclusions**: Unfinished tasks across all active projects within the user's authorization boundary.

---

## 2. Resource Utilization Analytics

### 2.1 Working-Day Capacity Basis
* **Standard Capacity**: 8 assigned hours per working day (excluding weekends and statutory holidays).
* **Available Working Days**: Calculated using the deterministic `WorkingDayCalculator` between the evaluation window start and finish dates:
  $$\text{Available Hours} = \text{WorkingDaysInWindow} \times 8 \text{ hours/day}$$

### 2.2 Individual Worker Utilization
* **Formula**:
  $$\text{Worker Utilization (\%)} = \left( \frac{\text{Total Assigned Task Hours in Window}}{\text{Worker Available Working Hours}} \right) \times 100$$
* **Utilization Status Classification**:
  * **`OVERALLOCATED`**: Utilization $> 100\%$ (requires immediate workload balancing or task re-assignment).
  * **`OPTIMAL`**: Utilization between $70\%$ and $100\%$ (healthy, productive assignment).
  * **`UNDERUTILIZED`**: Utilization between $1\%$ and $< 70\%$ (available capacity to absorb tasks).
  * **`IDLE`**: Utilization $= 0\%$ while worker is marked `AVAILABLE`.
  * **`UNAVAILABLE`**: Worker status is set to `ON_LEAVE` or `INACTIVE` (distinguished from an available worker who simply has no tasks).

### 2.3 Portfolio Aggregate Utilization
* **Formula**:
  $$\text{Portfolio Utilization (\%)} = \left( \frac{\sum \text{Assigned Hours Across Tracked Workers}}{\sum \text{Available Hours Across Tracked Workers}} \right) \times 100$$
* **Safeguards**: Yields `null` if total available worker hours equals zero.
* **Privacy & Isolation**: When accessed by a `CLIENT` user, internal resource utilization tables return empty sets to protect proprietary staff allocation and billing rates.

---

## 3. Vendor Delivery Reliability Analytics

### 3.1 Vendor Delivery Reliability Rate
* **Definition**: The percentage of completed material/service commitments that arrived on or before the committed delivery date.
* **Formula**:
  $$\text{Vendor Reliability (\%)} = \left( \frac{\text{Completed Deliveries with Actual Date } \le \text{ Committed Date}}{\text{Completed Deliveries with Valid Recorded Dates}} \right) \times 100$$
* **Inclusion / Exclusion Rules**:
  * **Included**: Deliveries with both a recorded committed delivery date and a verified actual delivery date.
  * **Excluded**: Open/in-transit deliveries without actual delivery dates (tracked separately as outstanding commitments).
  * **Excluded**: Records with corrupted, missing, or contradictory dates.
* **Zero Denominator Safeguard**: Returns `null` (`N/A`) if zero completed deliveries exist.

### 3.2 Sample Size Reliability Disclosure
* To prevent inaccurate vendor rankings based on negligible observations, the system discloses the observation count ($N$) and flags:
  * **`Reliable (N ≥ 3)`**: Sufficient historical sample size to derive statistically meaningful reliability rates.
  * **`Low Sample (N < 3)`**: Small observation count; metrics presented with disclosure warning.

### 3.3 Delivery Delay Distribution Buckets
* Categorizes deliveries into deterministic delay intervals:
  1. **On-Time**: Variance $\le 0$ days.
  2. **Minor Delay**: Variance between $1$ and $3$ days.
  3. **Moderate Delay**: Variance between $4$ and $7$ days.
  4. **Severe Delay**: Variance $> 7$ days.

---

## 4. Deadline, Schedule Variance & Risk Management

### 4.1 Forecast Variance vs Historical Variance
* **Active Projects (Forecast Variance)**:
  $$\text{Forecast Variance} = \text{Expected Completion Date (CPM)} - \text{Planned Baseline Finish}$$
* **Completed Projects (Historical Variance)**:
  $$\text{Historical Variance} = \text{Actual Completion Date} - \text{Planned Baseline Finish}$$
* **Reporting Integrity Rule**: Reports and CSV exports explicitly label variance type (`FORECAST_VARIANCE` vs `HISTORICAL_VARIANCE`) to ensure managers never confuse forecasts with actual outcomes.

### 4.2 Management Attention Radar
Identifies projects requiring immediate intervention based on three deterministic risk triggers:
1. **Critical Path Delay**: One or more critical-path tasks has negative float or reported delay.
2. **Active Conflict**: One or more workers assigned to the project is concurrently over-allocated across overlapping dates.
3. **Vendor Bottleneck**: Delayed vendor delivery impacting dependent tasks.

---

## 5. Security, Authorization & Export Protection

### 5.1 Role-Based Scoping
* **`ADMIN`**: Full portfolio visibility across all enterprise projects, workers, vendors, and clients.
* **`PROJECT_MANAGER`**: Full operational metrics for assigned and authorized projects.
* **`CLIENT`**: Strictly isolated to projects belonging to their organization. Worker allocations, internal labor hours, and competitor vendor rankings are withheld.

### 5.2 CSV Formula-Injection Defense
All exported CSV cells are sanitized through prefix inspection. Any field starting with formula execution characters (`=`, `+`, `-`, `@`, `\t`, `\r`) is prefixed with a single quote (`'`) to neutralize spreadsheet code execution in Excel, LibreOffice, and Google Sheets.
