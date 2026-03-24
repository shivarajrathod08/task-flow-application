package taskflowai.controller;


import taskflowai.agent.HealthMonitoringAgent;
import taskflowai.dto.MeetingRequest;
import taskflowai.entity.AuditLog;
import taskflowai.service.AuditService;
import taskflowai.service.WorkflowOrchestrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * WorkflowController
 *
 * New endpoints added for hackathon demo:
 *
 *   POST /api/workflow/run         — Full 5-step pipeline with logging
 *   GET  /api/tasks/health         — System health check
 *   GET  /api/audit/task/{id}      — Full audit trail for a task
 *   GET  /api/audit/recent         — Last N audit events
 */
@RestController
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Workflow & Health", description = "Autonomous workflow orchestration and health monitoring")
public class WorkflowController {

    private final WorkflowOrchestrationService orchestrationService;
    private final HealthMonitoringAgent         healthAgent;
    private final AuditService                  auditService;

    // ── POST /api/workflow/run ─────────────────────────────────────────────
    //    Full 5-step pipeline — USE THIS FOR THE HACKATHON DEMO
    //    Shows structured step-by-step logs in the console

    @PostMapping("/api/workflow/run")
    @Operation(
            summary = "Run the full 5-step autonomous workflow",
            description = "Processes a meeting transcript through all 5 pipeline steps: "
                    + "Transcript Input → AI Extraction → Task Planning → Tracking → Notification. "
                    + "Returns the workflow execution record with step-by-step logs. "
                    + "Use this endpoint for hackathon demo — watch the console for step logging."
    )
    public ResponseEntity<WorkflowOrchestrationService.WorkflowExecutionResult> runWorkflow(
            @RequestBody MeetingRequest request) {

        log.info("[WorkflowController] POST /api/workflow/run — title='{}'", request.getTitle());
        var result = orchestrationService.runWorkflow(request);

        if ("FAILED".equals(result.getStatus())) {
            return ResponseEntity.internalServerError().body(result);
        }
        return ResponseEntity.ok(result);
    }

    // ── GET /api/tasks/health ──────────────────────────────────────────────
    //    System health check — detects overdue, overloaded, unassigned critical

    @GetMapping("/api/tasks/health")
    @Operation(
            summary = "System health check",
            description = "Detects: tasks overdue >24h, users with too many tasks, "
                    + "unassigned CRITICAL tasks. Returns healthy=true/false with warnings list."
    )
    public ResponseEntity<HealthResponse> getHealth() {
        log.info("[WorkflowController] GET /api/tasks/health");
        HealthMonitoringAgent.HealthReport report = healthAgent.runHealthCheck();
        return ResponseEntity.ok(HealthResponse.from(report));
    }

    // ── GET /api/audit/task/{taskId} ───────────────────────────────────────
    //    Full audit trail for a single task

    @GetMapping("/api/audit/task/{taskId}")
    @Operation(
            summary = "Get full audit trail for a task",
            description = "Returns every recorded action on this task: creation, assignment, "
                    + "status changes, notifications sent, fallbacks applied."
    )
    public ResponseEntity<List<AuditLog>> getTaskAuditTrail(@PathVariable Long taskId) {
        return ResponseEntity.ok(auditService.getAuditTrailForTask(taskId));
    }

    // ── GET /api/audit/recent ──────────────────────────────────────────────
    //    Last N system events (default 20)

    @GetMapping("/api/audit/recent")
    @Operation(
            summary = "Recent audit events",
            description = "Returns the last N system events across all tasks and workflows. "
                    + "Shows the system making autonomous decisions in real time."
    )
    public ResponseEntity<List<AuditLog>> getRecentAuditEvents(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(auditService.getRecentEvents(limit));
    }

    // ── Wrapper DTO for health response ───────────────────────────────────

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class HealthResponse {
        private boolean healthy;
        private int overdueTasks;
        private int overloadedUsers;
        private int unassignedCriticalTasks;
        private java.util.List<String> warnings;
        private java.util.List<String> criticals;
        private String timestamp;

        static HealthResponse from(HealthMonitoringAgent.HealthReport r) {
            return HealthResponse.builder()
                    .healthy(r.isHealthy())
                    .overdueTasks(r.getOverdueTasks())
                    .overloadedUsers(r.getOverloadedUsers())
                    .unassignedCriticalTasks(r.getUnassignedCriticalTasks())
                    .warnings(r.getWarnings())
                    .criticals(r.getCriticals())
                    .timestamp(r.getTimestamp().toString())
                    .build();
        }
    }
}