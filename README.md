# TaskFlow AI — Agentic AI Workflow System

> **GENAI HACKATHON 2026**  
> An autonomous, multi-agent AI system that converts meeting transcripts into structured, assigned, tracked, and escalated tasks — with zero manual effort.

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [System Architecture](#system-architecture)
3. [Features](#features)
4. [API Usage](#api-usage)
5. [Database Schema](#database-schema)
6. [Error Handling & Self-Healing](#error-handling--self-healing)
7. [How to Run](#how-to-run)
8. [Output Screenshots](#output-screenshots)
9. [Tech Stack](#tech-stack)
10. [Future Enhancements](#future-enhancements)

---

## Project Overview

### What It Does

TaskFlow AI is an **Agentic AI Workflow System** built with Java Spring Boot. It processes raw meeting transcripts and autonomously:

- Extracts action items using **Google Gemini AI**
- Assigns tasks to team members with normalized deadlines
- Tracks task status in **MySQL** database
- Sends email notifications automatically
- Escalates overdue tasks to the manager
- Logs every decision to a complete audit trail

### Problem It Solves

After every team meeting, project managers must:
- Manually read notes and extract tasks
- Email each team member their assignments
- Follow up daily on pending tasks
- Escalate overdue tasks manually

**This costs 4+ hours per week per project manager.**

TaskFlow AI eliminates all of this with a single API call.

---

## System Architecture

```
POST /api/meetings/process
          │
          ▼
┌─────────────────────────────────────────────────────────┐
│              MeetingService — Orchestrator               │
└──────┬─────────────┬─────────────┬──────────────────────┘
       │             │             │
       ▼             ▼             ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│  Agent 1     │ │  Agent 2     │ │  Agent 3     │ │  Agent 4     │
│ Extraction   │ │ Planning &   │ │  Tracking    │ │ Notification │
│ Agent        │ │ Assignment   │ │  Agent       │ │ Agent        │
│              │ │ Agent        │ │              │ │              │
│ Gemini API   │ │ Normalise    │ │ Daily scan   │ │ Email +      │
│ JSON extract │ │ dates + assign│ │ Overdue flag │ │ Retry logic  │
└──────┬───────┘ └──────┬───────┘ └──────┬───────┘ └──────┬───────┘
       └─────────────────┴───────────────┴─────────────────┘
                                    │
                                    ▼
                         ┌──────────────────┐
                         │  MySQL Database   │
                         │  meetings         │
                         │  tasks            │
                         │  audit_logs       │
                         │  notifications    │
                         └──────────────────┘
```

### Components

| Component | Class | Responsibility |
|-----------|-------|---------------|
| REST Controller | `MeetingController` | Accepts API requests, returns responses |
| Orchestrator | `MeetingService` | Coordinates all 4 agents in sequence |
| Agent 1 | `TaskExtractionAgent` | Calls Gemini API, returns structured task JSON |
| Agent 2 | `PlanningAssignmentAgent` | Normalises deadlines, assigns owners, saves to DB |
| Agent 3 | `TrackingAgent` | Monitors deadlines, triggers escalation |
| Agent 4 | `NotificationAgent` | Sends emails with retry logic |
| Health Monitor | `HealthMonitoringAgent` | Detects overloaded users and overdue tasks |
| Audit | `AuditService` | Persists every system decision to `audit_logs` |
| Scheduler | `WorkflowScheduler` | Daily reminder and escalation jobs at 9 AM / 10 AM |

---

## Features

### 1. AI Task Extraction
- Meeting transcript submitted as plain text
- Google Gemini 1.5 Flash parses natural language
- Returns tasks with title, description, assignee, deadline, priority
- **Fallback**: if AI fails, creates a catch-all task for manual review

### 2. Automatic Task Assignment
- Converts person names to email format (`ravi` → `ravi@company.com`)
- Detects overloaded users (5+ tasks) and logs a warning
- Auto-reassigns to `team@company.com` if user has 8+ active tasks
- **Fallback**: if no assignee found → routed to `team@company.com`

### 3. Smart Deadline Normalisation
- Understands: `"by March 25"`, `"ASAP"`, `"next Friday"`, `"EOD"`, `"next week"`
- Converts to ISO 8601 timestamp: `2024-03-25 17:00`
- **Fallback**: if no deadline detected → auto-assigns `+3 days`

### 4. Automated Notifications
- Email sent immediately on task assignment
- Daily reminder emails via Spring `@Scheduled` at 9 AM
- **Retry logic**: up to 2 retries with 500ms delay on email failure
- All notifications logged to `audit_logs` table

### 5. Deadline Tracking & Escalation
- Scheduler runs at 10 AM daily
- Overdue tasks updated to status `ESCALATED`
- Email sent to both assignee and `manager@company.com`
- Escalation count tracked in `tasks.escalation_count`

### 6. Audit Logging
- Every action persisted to `audit_logs` table
- Event types: `TASK_CREATED`, `TASK_ASSIGNED`, `NOTIFICATION_SENT`, `FALLBACK_USED`, `ESCALATION_TRIGGERED`, `HEALTH_WARNING`
- Fully queryable: `SELECT * FROM audit_logs;` → 43+ rows

### 7. System Health Monitoring
- Detects tasks overdue by more than 24 hours
- Flags users with too many open tasks
- Identifies CRITICAL tasks with no assignee
- Exposed via `GET /api/tasks/health`

---

## API Usage

### Core Endpoint

#### `POST /api/meetings/process`

Submits a meeting transcript and triggers the full 5-step pipeline.

**Request:**
```json
{
  "title": "Test Meeting",
  "transcript": "Ravi will complete report by March 25. Anjali will review it."
}
```

**Response (201 Created):**
```json
{
  "meetingId": 13,
  "meetingTitle": "Test Meeting",
  "aiSummary": "The meeting outlined the process for a specific report. Ravi is tasked with completing the report by March 25th, which will then be handed over to Anjali for review.",
  "tasksExtracted": 2,
  "tasks": [
    {
      "id": 46,
      "title": "Complete report",
      "description": "Ravi is responsible for completing the report.",
      "status": "PENDING",
      "priority": "MEDIUM",
      "assignedTo": "ravi@company.com",
      "deadline": "2024-03-25 17:00",
      "overdue": true,
      "escalated": false,
      "escalationCount": 0,
      "meetingId": 13,
      "meetingTitle": "Test Meeting",
      "createdAt": "2026-03-24 10:34"
    },
    {
      "id": 47,
      "title": "Review completed report",
      "description": "Anjali will review the report once it has been completed by Ravi.",
      "status": "PENDING",
      "priority": "MEDIUM",
      "assignedTo": "anjali@company.com",
      "deadline": "2026-03-27 10:34",
      "overdue": false,
      "escalated": false,
      "escalationCount": 0
    }
  ],
  "message": "Meeting processed successfully. 2 tasks created and assigned."
}
```

### All Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/meetings/process` | **Core** — Process transcript |
| `GET` | `/api/meetings` | List all meetings |
| `GET` | `/api/meetings/{id}` | Get meeting details |
| `GET` | `/api/tasks` | All tasks with status |
| `GET` | `/api/tasks/{id}` | Task details |
| `PATCH` | `/api/tasks/{id}` | Update task status |
| `GET` | `/api/tasks/overdue` | All overdue tasks |
| `GET` | `/api/tasks/dashboard` | Statistics (total/pending/escalated) |
| `POST` | `/api/tasks/trigger/reminders` | Manually send reminders |
| `POST` | `/api/tasks/trigger/escalation` | Manually trigger escalation |
| `GET` | `/api/tasks/health` | System health report |
| `GET` | `/api/audit/task/{id}` | Full audit trail for one task |
| `GET` | `/api/audit/recent` | Last 20 audit events |

### Swagger UI

All endpoints are documented and testable at:
```
http://localhost:8080/swagger-ui.html
```

---

## Database Schema

### `meetings` table
```sql
CREATE TABLE meetings (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    title           VARCHAR(255) NOT NULL,
    transcript      LONGTEXT NOT NULL,
    meeting_date    DATETIME,
    processed_by_ai BOOLEAN DEFAULT FALSE,
    ai_summary      TEXT,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP
);
```

### `tasks` table
```sql
CREATE TABLE tasks (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    meeting_id          BIGINT,
    title               VARCHAR(255) NOT NULL,
    description         TEXT,
    status              ENUM('PENDING','IN_PROGRESS','COMPLETED','ESCALATED','CANCELLED') DEFAULT 'PENDING',
    priority            ENUM('LOW','MEDIUM','HIGH','CRITICAL') DEFAULT 'MEDIUM',
    assigned_to         VARCHAR(255),
    deadline            DATETIME,
    completed_at        DATETIME,
    escalated           BOOLEAN DEFAULT FALSE,
    escalation_count    INT DEFAULT 0,
    reminder_sent_count INT DEFAULT 0,
    created_at          DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (meeting_id) REFERENCES meetings(id)
);
```

### `audit_logs` table
```sql
CREATE TABLE audit_logs (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type            VARCHAR(50) NOT NULL,
    description           TEXT NOT NULL,
    task_id               BIGINT,
    meeting_id            BIGINT,
    workflow_execution_id BIGINT,
    actor                 VARCHAR(100),
    old_value             VARCHAR(255),
    new_value             VARCHAR(255),
    created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

**Sample query:**
```sql
SELECT * FROM audit_logs;
-- Returns 43+ rows showing every NOTIFICATION_SENT, FALLBACK_USED, ESCALATION events
```

---

## Error Handling & Self-Healing

| Scenario | Detection | Recovery |
|----------|-----------|----------|
| AI extraction fails | Exception caught in `TaskExtractionAgent` | Falls back to rule-based catch-all task |
| Missing deadline | `deadline == null` in `PlanningAssignmentAgent` | Auto-assigns `+3 days`, logs `FALLBACK_USED` |
| Missing assignee | `assignedTo == null or blank` | Routes to `team@company.com`, logs `ASSIGNEE_DEFAULTED` |
| Notification failure | SMTP exception in `NotificationAgent` | Retries ×2 with 500ms delay, logs `NOTIFICATION_FAILED` |
| User overloaded (5+ tasks) | Checked before assignment | Logs `HEALTH_WARNING` to console |
| User critically overloaded (8+) | Checked before assignment | Auto-reassigns to `team@company.com` |
| Task overdue >24h | `TrackingAgent` daily scan | Status → `ESCALATED`, email to assignee + manager |

---

## How to Run

### Prerequisites
- Java 17+
- Maven 3.8+
- MySQL 8 (or use H2 in-memory for demo)

### Option 1 — Demo Mode (No API key, No MySQL)
```bash
git clone https://github.com/yourname/taskflow-ai.git
cd taskflow-ai
# application.yml has demo-mode: true and H2 by default
mvn spring-boot:run
```
Open: `http://localhost:8080/swagger-ui.html`

### Option 2 — Full Mode with Real AI
```bash
# Set your Gemini API key
export GEMINI_API_KEY=your_key_here

# In application.yml, set:
# ai.provider: gemini
# meetflow.demo-mode: false

mvn spring-boot:run
```

### Option 3 — With MySQL
Update `src/main/resources/application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/taskflow
    username: root
    password: yourpassword
  jpa:
    hibernate:
      ddl-auto: update
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
```

Then run the schema:
```bash
mysql -u root -p taskflow < docs/schema.sql
mvn spring-boot:run
```

### Test the Core API
```bash
curl -X POST http://localhost:8080/api/meetings/process \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Sprint Planning",
    "transcript": "Ravi will complete the report by March 25. Anjali will review it."
  }'
```

---

## Output Screenshots

### 1. Swagger UI — API Request
The POST `/api/meetings/process` endpoint with a sample meeting transcript.

![Swagger Request](https://github.com/shivarajrathod08/task-flow-application/main/swagger%20api%20request.png)

### 2. API Response — 201 Created
Full JSON response showing `meetingId: 13`, `tasksExtracted: 2`, both tasks assigned with deadlines.

![API Response](docs/screenshots/api_response.png)

**Key fields to note:**
- `tasksExtracted: 2` — Gemini AI found 2 tasks
- `assignedTo: ravi@company.com` — auto-assigned from name
- `deadline: 2024-03-25 17:00` — parsed from "by March 25"
- `overdue: true` — system detected this is past deadline

### 3. Application Logs — Agents in Action
Real logs showing `PlanningAssignmentAgent`, `NotificationAgent`, `AuditService`, and the self-healing fallback.

![Logs](docs/screenshots/logs.png)

**Key events visible:**
- `FALLBACK_USED` — deadline was missing, auto-assigned +3 days
- `WORKLOAD WARNING` — anjali@company.com had 5 active tasks
- `[DEMO] Email → anjali@company.com` — notification sent

### 4. MySQL — meetings table
10 meetings processed and stored with timestamps.

![Meetings Table](docs/screenshots/meeting_table.png)

### 5. MySQL — tasks table
43 tasks showing real `ESCALATED`, `PENDING`, and `COMPLETED` statuses.

![Tasks Table](docs/screenshots/tasks_table.png)

### 6. MySQL — audit_logs table
43 events logged: `NOTIFICATION_SENT`, `FALLBACK_USED`, escalations — all with timestamps.

![Audit Logs](docs/screenshots/escalation.png)

### 7. Workflow Logs — Full Pipeline
Complete application logs showing the 5-step pipeline executing from transcript input to task creation.

![Workflow Logs](docs/screenshots/Screenshot_2026-03-24_105258.png)

---

## Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Java | 17 LTS |
| Framework | Spring Boot | 3.2 |
| AI Engine | Google Gemini | 2.5 Flash |
| Database | MySQL / H2 | 8.0 / In-memory |
| HTTP Client | Spring WebFlux | 3.2 |
| Scheduling | Spring `@Scheduled` | Built-in |
| Email | Spring Mail (SMTP) | Built-in |
| API Docs | Springdoc OpenAPI | 2.3 |
| Build | Maven | 3.8+ |

---

## Future Enhancements

### Phase 2 — Integrations
- Slack and Microsoft Teams integration — extract tasks from chat
- Jira / Asana direct push for task creation
- Enterprise SSO and role-based access control
- Voice transcript support (Zoom, Google Meet recordings)

### Phase 3 — Intelligence
- Fine-tuned LLM for domain-specific extraction accuracy
- ML workload predictor for auto-rebalancing assignments
- Kafka event streaming for real-time multi-tenant processing
- Anomaly detection on missed deadline patterns

### Phase 4 — Product
- React frontend dashboard with D3 analytics charts
- iOS and Android push notifications
- Kubernetes autoscaling based on transcript volume
- Multi-language transcript support

---

## Project Structure

```
taskflow-ai/
├── pom.xml
├── src/main/
│   ├── java/taskflowai/
│   │   ├── MeetFlowApplication.java
│   │   ├── agent/
│   │   │   ├── TaskExtractionAgent.java      ← Agent 1: AI extraction
│   │   │   ├── PlanningAssignmentAgent.java  ← Agent 2: Planning + self-healing
│   │   │   ├── TrackingAgent.java            ← Agent 3: Deadline monitoring
│   │   │   ├── NotificationAgent.java        ← Agent 4: Email + retry
│   │   │   └── HealthMonitoringAgent.java    ← Bottleneck detection
│   │   ├── controller/
│   │   │   ├── MeetingController.java
│   │   │   ├── TaskController.java
│   │   │   └── WorkflowController.java       ← Health + audit endpoints
│   │   ├── service/
│   │   │   ├── MeetingService.java           ← Orchestrator
│   │   │   ├── TaskService.java
│   │   │   ├── AuditService.java             ← Audit trail
│   │   │   └── WorkflowOrchestrationService.java ← 5-step logging
│   │   ├── entity/
│   │   │   ├── Meeting.java
│   │   │   ├── Task.java
│   │   │   ├── Notification.java
│   │   │   ├── AuditLog.java                 ← Audit entity
│   │   │   └── WorkflowExecution.java        ← Step tracking entity
│   │   ├── repository/
│   │   │   ├── MeetingRepository.java
│   │   │   ├── TaskRepository.java
│   │   │   ├── NotificationRepository.java
│   │   │   ├── AuditLogRepository.java
│   │   │   └── WorkflowExecutionRepository.java
│   │   ├── dto/                              ← Request/Response DTOs
│   │   └── scheduler/
│   │       └── WorkflowScheduler.java        ← Daily jobs
│   └── resources/
│       └── application.yml
└── docs/
    ├── schema.sql
    └── screenshots/
```

---

## License

MIT — Built as a Final Year Engineering Project, 2025–2026.
