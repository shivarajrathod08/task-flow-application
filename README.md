# TaskFlowApplication 🤖
### Agentic AI for Autonomous Enterprise Workflows

## The Problem
After every meeting, someone manually reads through notes, extracts action items, emails people, and hopes tasks get done. This is slow, error-prone, and creates accountability gaps.

## The Solution
MeetFlow deploys **4 specialized AI agents** that work together to automate the entire meeting-to-execution pipeline:

```
Meeting Transcript
       │
       ▼
┌─────────────────────┐
│  Agent 1: Extract   │  ← AI parses transcript, finds tasks
│  (TaskExtractionAgent) │
└────────┬────────────┘
         │ Structured tasks
         ▼
┌─────────────────────┐
│  Agent 2: Plan      │  ← Assigns owners, normalizes deadlines
│  (PlanningAgent)    │
└────────┬────────────┘
         │ Persisted tasks + emails sent
         ▼
┌─────────────────────┐
│  Agent 3: Track     │  ← Monitors deadlines, detects delays
│  (TrackingAgent)    │
└────────┬────────────┘
         │ Overdue detected
         ▼
┌─────────────────────┐
│  Agent 4: Notify    │  ← Sends reminders + escalation alerts
│  (NotificationAgent)│
└─────────────────────┘
```

---

## Features

- **AI-Powered Extraction** — Paste any meeting transcript; AI extracts tasks, owners, deadlines, and priorities
- **Auto-Assignment** — Tasks assigned to team members with immediate email notifications  
- **Priority Inference** — AI detects urgency from language ("ASAP", "blocker", "by EOD")
- **Deadline Normalization** — Converts "next Friday", "EOW", "ASAP" into real dates
- **Automated Tracking** — Daily scheduler checks for overdue tasks
- **Smart Escalation** — Tasks overdue by 24h+ automatically escalated to manager
- **Reminder Limits** — Max 3 reminders per task to avoid notification fatigue
- **Dashboard API** — Real-time stats: pending/in-progress/completed/escalated counts

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 17, Spring Boot 3.2 |
| Database | MySQL 8 (H2 for dev/demo) |
| AI | Google Gemini 1.5 Flash / OpenAI GPT-4o-mini |
| HTTP Client | Spring WebFlux (WebClient) |
| Scheduler | Spring @Scheduled |
| Email | Spring Mail (SMTP) |
| API Docs | Springdoc OpenAPI / Swagger UI |

---

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.8+
- (Optional) MySQL 8, Gemini/OpenAI API key

### Run in Demo Mode (No API key needed)
```bash
git clone https://github.com/yourname/meetflow.git
cd meetflow
mvn spring-boot:run
```

The app starts with:
- **H2 in-memory database** (no MySQL required)
- **Demo mode AI** (returns rich mock data)
- **Sample meeting** auto-loaded on startup

Open: http://localhost:8080/swagger-ui.html

### Run with Real AI (Gemini)
```bash
export GEMINI_API_KEY=your_key_here
# In application.yml: set ai.provider=gemini and meetflow.demo-mode=false
mvn spring-boot:run
```

### Run with MySQL
Update `application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/meetflow
    username: root
    password: yourpassword
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
```

---

## API Reference

### Core Flow

**1. Process a meeting transcript**
```http
POST /api/meetings/process
Content-Type: application/json

{
  "title": "Q2 Sprint Planning",
  "meetingDate": "2025-03-17 10:00",
  "transcript": "Sarah will design mockups by April 15 (HIGH priority).\nJohn needs AWS setup ASAP by April 10 — CRITICAL.\nPriya writes PRD by April 5."
}
```

Response:
```json
{
  "meetingId": 1,
  "meetingTitle": "Q2 Sprint Planning",
  "aiSummary": "Sprint planning meeting with 3 action items...",
  "tasksExtracted": 3,
  "tasks": [
    {
      "id": 1,
      "title": "Design mobile UI mockups",
      "assignedTo": "sarah@company.com",
      "deadline": "2025-04-15 17:00",
      "priority": "HIGH",
      "status": "PENDING",
      "overdue": false
    }
  ]
}
```

**2. Update task status**
```http
PATCH /api/tasks/1
{ "status": "IN_PROGRESS" }
```

**3. Get dashboard**
```http
GET /api/tasks/dashboard
```
```json
{
  "totalTasks": 10,
  "pending": 4,
  "inProgress": 3,
  "completed": 2,
  "escalated": 1,
  "overdue": 2,
  "completionRate": 20.0
}
```

**4. Trigger escalation manually**
```http
POST /api/tasks/trigger/escalation
```

### All Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/meetings/process` | **Core** — Process transcript |
| GET | `/api/meetings` | List all meetings |
| GET | `/api/meetings/{id}` | Get meeting details |
| GET | `/api/tasks` | All tasks |
| GET | `/api/tasks/{id}` | Task details |
| PATCH | `/api/tasks/{id}` | Update task |
| GET | `/api/tasks/meeting/{id}` | Tasks by meeting |
| GET | `/api/tasks/assignee/{email}` | Tasks by person |
| GET | `/api/tasks/overdue` | All overdue tasks |
| GET | `/api/tasks/dashboard` | Statistics |
| POST | `/api/tasks/trigger/reminders` | Send reminders now |
| POST | `/api/tasks/trigger/escalation` | Escalate overdue tasks |

---

## Agent Architecture

### Agent 1 — Task Extraction Agent
**File:** `TaskExtractionAgent.java`  
**Input:** Raw meeting transcript (string)  
**Output:** `AiExtractionResult` (structured JSON)  
**How it works:** Sends a carefully engineered prompt to Gemini/OpenAI requesting JSON-only output with tasks, deadlines, priorities, and key decisions.

### Agent 2 — Planning/Assignment Agent
**File:** `PlanningAssignmentAgent.java`  
**Input:** `AiExtractionResult` + `Meeting` entity  
**Output:** List of persisted `Task` entities  
**How it works:** Normalizes deadlines (handles "ASAP", "next Friday", ISO dates), maps priority strings to enums, checks assignee workload, persists to DB, triggers notification.

### Agent 3 — Tracking Agent
**File:** `TrackingAgent.java`  
**Input:** Called by Scheduler (or manual API trigger)  
**Output:** Escalation count, status summary  
**How it works:** Queries DB for tasks where `deadline < NOW()` and status is not COMPLETED. Escalates by updating status and incrementing counter.

### Agent 4 — Notification Agent
**File:** `NotificationAgent.java`  
**Input:** Task entity + notification type  
**Output:** Email sent + `Notification` record in DB  
**How it works:** Composes contextual email templates for assignment/reminder/escalation/completion. Falls back to console logging when SMTP isn't configured (demo mode).

---

## Project Structure
```
TaskFlowAI/
├── pom.xml
├── src/main/
│   ├── java/taskflowai/
│   │   ├── TaskFlowApplication.java
│   │   ├── agent/
│   │   │   ├── TaskExtractionAgent.java    ← Agent 1
│   │   │   ├── PlanningAssignmentAgent.java← Agent 2
│   │   │   ├── TrackingAgent.java          ← Agent 3
│   │   │   └── NotificationAgent.java      ← Agent 4
│   │   ├── controller/
│   │   │   ├── MeetingController.java
│   │   │   └── TaskController.java
│   │   ├── service/
│   │   │   ├── MeetingService.java         ← Orchestrator
│   │   │   └── TaskService.java
│   │   ├── entity/
│   │   │   ├── Meeting.java
│   │   │   ├── Task.java
│   │   │   └── Notification.java
│   │   ├── repository/
│   │   │   ├── MeetingRepository.java
│   │   │   ├── TaskRepository.java
│   │   │   └── NotificationRepository.java
│   │   ├── dto/
│   │   │   ├── MeetingRequest.java
│   │   │   ├── MeetingResponse.java
│   │   │   ├── TaskResponse.java
│   │   │   ├── TaskUpdateRequest.java
│   │   │   ├── ProcessingResult.java
│   │   │   └── AiExtractionResult.java
│   │   ├── scheduler/
│   │   │   └── WorkflowScheduler.java      ← Daily jobs
│   │   └── config/
│   │       ├── AppConfig.java
│   │       ├── DataInitializer.java
│   │       └── GlobalExceptionHandler.java
│   └── resources/
│       └── application.yml
└── docs/
    └── TaskFlowAI.postman_collection.json
```

---

## Impact Model

| Metric | Before MeetFlow | After MeetFlow | Savings |
|--------|----------------|----------------|---------|
| Time to extract tasks from 1hr meeting | 30 min manual | 10 seconds (AI) | **99.4%** |
| Tasks falling through cracks | ~20% per meeting | ~2% (auto-tracked) | **90%** |
| Reminder sending time | 5 min/task/day | 0 (automated) | **100%** |
| Time to escalate overdue tasks | 1-2 days (manual) | 24 hours (auto) | **50%** |
| PM admin overhead per week | ~4 hours | ~20 minutes | **92%** |

**For a 50-person team with 10 meetings/week:**
- Saves ~40 hours/week in meeting admin
- At $50/hr average → **$2,000/week = $104,000/year** in productivity gains

---

## License
MIT — Built for ET GENAI Hackathon 2026
