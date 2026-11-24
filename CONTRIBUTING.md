# Contributing Guidelines

Thank you for contributing to the **Project Resource Allocation & Scheduling System (PRS)**!

---

## 1. Code Standards & Architecture Guidelines

1. **Deterministic Core Invariant**:
   - All schedule calculations, date projections, CPM forward/backward passes, and float arithmetic must remain in the Java backend (`com.prs.scheduler`, `com.prs.cpm`).
   - Do not allow AI services or frontend components to compute or guess schedule dates independently.
2. **Hard Constraints**:
   - Any new optimization heuristic or candidate generator must validate the non-negotiable hard constraints via `CandidateValidator`:
     - Topological cycle prevention (Kahn's algorithm)
     - Working-day precedence constraints (skipping weekends)
     - Skill prerequisites matching
     - Worker availability status (`AVAILABLE`)
     - Completed task immutability
3. **AI Security Boundary**:
   - The Python AI service (`ai-service/`) must never be granted direct database connection strings or credentials.
   - Spring Boot is the single source of truth for authorization and data serialization.
4. **Error Handling**:
   - Do not leak internal stack traces to REST API clients. Use `GlobalExceptionHandler` to produce sanitized, structured error payloads.

---

## 2. Local Development Setup

### Backend (Java 21)
```powershell
cd backend
$env:JAVA_HOME = "$pwd\..\jdk21\jdk-21.0.2+13"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
..\apache-maven-3.9.6\bin\mvn.cmd spring-boot:run
```

### AI Service (Python 3.12)
```powershell
cd ai-service
$env:PYTHONPATH = "$pwd"
uvicorn app.main:app --port 8000
```

### Frontend (React / Vite)
```powershell
cd frontend
npm install
npm run dev
```

---

## 3. Running Automated Tests

Before submitting a Pull Request, verify that all test suites pass with zero failures:

```powershell
# 1. Java Backend Tests (44 tests)
cd backend
..\apache-maven-3.9.6\bin\mvn.cmd test

# 2. Python AI Service Tests (6 tests)
cd ../ai-service
python -m pytest tests

# 3. Frontend Build & Type Check
cd ../frontend
npm run build
```

---

## 4. Database Migrations (Flyway)

- Database schema changes must be added as sequential Flyway migration scripts in `backend/src/main/resources/db/migration/V{version}__{description}.sql`.
- Do not edit previously merged migration files.
- Always include `IF NOT EXISTS` guards where supported and verify foreign key index coverage.
