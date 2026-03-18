package taskflowai.controller;

import taskflowai.agent.TrackingAgent;
import taskflowai.dto.TaskResponse;
import taskflowai.dto.TaskUpdateRequest;
import taskflowai.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Tasks", description = "Task management, tracking, and agent triggers")
public class TaskController {

    private final TaskService taskService;

    // ---------------------------------------------------------------
    //  Read endpoints
    // ---------------------------------------------------------------

    @GetMapping
    @Operation(summary = "Get all tasks")
    public ResponseEntity<List<TaskResponse>> getAllTasks() {
        return ResponseEntity.ok(taskService.getAllTasks());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get task by ID")
    public ResponseEntity<TaskResponse> getTaskById(@PathVariable Long id) {
        return ResponseEntity.ok(taskService.getTaskById(id));
    }

    @GetMapping("/meeting/{meetingId}")
    @Operation(summary = "Get all tasks for a meeting")
    public ResponseEntity<List<TaskResponse>> getTasksByMeeting(@PathVariable Long meetingId) {
        return ResponseEntity.ok(taskService.getTasksByMeeting(meetingId));
    }

    @GetMapping("/assignee/{email}")
    @Operation(summary = "Get tasks assigned to a person")
    public ResponseEntity<List<TaskResponse>> getTasksByAssignee(@PathVariable String email) {
        return ResponseEntity.ok(taskService.getTasksByAssignee(email));
    }

    @GetMapping("/overdue")
    @Operation(summary = "Get all overdue tasks (Tracking Agent)")
    public ResponseEntity<List<TaskResponse>> getOverdueTasks() {
        return ResponseEntity.ok(taskService.getOverdueTasks());
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Get task statistics dashboard")
    public ResponseEntity<TrackingAgent.TrackingSummary> getDashboard() {
        return ResponseEntity.ok(taskService.getDashboard());
    }

    // ---------------------------------------------------------------
    //  Update endpoint
    // ---------------------------------------------------------------

    @PatchMapping("/{id}")
    @Operation(summary = "Update task status, assignee, or deadline")
    public ResponseEntity<TaskResponse> updateTask(@PathVariable Long id,
                                                   @RequestBody TaskUpdateRequest request) {
        log.info("PATCH /api/tasks/{} - status={}", id, request.getStatus());
        return ResponseEntity.ok(taskService.updateTask(id, request));
    }

    // ---------------------------------------------------------------
    //  Agent trigger endpoints (useful for demo)
    // ---------------------------------------------------------------

    @PostMapping("/trigger/reminders")
    @Operation(summary = "Manually trigger reminder notifications (Notification Agent)")
    public ResponseEntity<Map<String, String>> triggerReminders() {
        String result = taskService.triggerReminders();
        return ResponseEntity.ok(Map.of("message", result));
    }

    @PostMapping("/trigger/escalation")
    @Operation(summary = "Manually trigger escalation check (Tracking Agent)")
    public ResponseEntity<Map<String, String>> triggerEscalation() {
        String result = taskService.triggerEscalation();
        return ResponseEntity.ok(Map.of("message", result));
    }
}
