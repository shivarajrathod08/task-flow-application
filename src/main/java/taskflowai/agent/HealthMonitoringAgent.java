package taskflowai.agent;


import taskflowai.entity.AuditLog;
import taskflowai.entity.Task;
import taskflowai.repository.TaskRepository;
import taskflowai.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * HealthMonitoringAgent
 *
 * Detects workflow bottlenecks and system health issues:
 *   - Tasks overdue by more than N hours
 *   - Users overloaded with too many open tasks
 *   - Critical tasks with no owner
 *
 * Called by the scheduler daily AND exposed via /api/tasks/health.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class HealthMonitoringAgent {

    private final TaskRepository taskRepository;
    private final AuditService   auditService;

    @Value("${meetflow.health.overdue-threshold-hours:24}")
    private long overdueThresholdHours;

    @Value("${meetflow.health.overload-task-limit:5}")
    private int overloadTaskLimit;

    // ── Main health check ──────────────────────────────────────────────────

    public HealthReport runHealthCheck() {
        log.info("[HealthMonitoringAgent] Running system health check...");

        List<String> warnings  = new ArrayList<>();
        List<String> criticals = new ArrayList<>();

        int overdueCount    = checkOverdueTasks(warnings, criticals);
        int overloadedUsers = checkUserWorkload(warnings);
        int unassigned      = checkUnassignedCritical(warnings, criticals);

        HealthReport report = HealthReport.builder()
                .timestamp(LocalDateTime.now())
                .overdueTasks(overdueCount)
                .overloadedUsers(overloadedUsers)
                .unassignedCriticalTasks(unassigned)
                .warnings(warnings)
                .criticals(criticals)
                .healthy(criticals.isEmpty())
                .build();

        if (!criticals.isEmpty()) {
            log.error("[HealthMonitoringAgent] CRITICAL: {} issues detected", criticals.size());
            criticals.forEach(c -> log.error("  CRITICAL: {}", c));
        }
        if (!warnings.isEmpty()) {
            log.warn("[HealthMonitoringAgent] WARNINGS: {} issues detected", warnings.size());
            warnings.forEach(w -> log.warn("  WARNING: {}", w));
        }
        if (report.isHealthy() && warnings.isEmpty()) {
            log.info("[HealthMonitoringAgent] System is healthy. No issues detected.");
        }

        return report;
    }

    // ── Check 1: Tasks overdue beyond threshold ────────────────────────────

    private int checkOverdueTasks(List<String> warnings, List<String> criticals) {
        List<Task> overdue = taskRepository.findOverdueTasks(LocalDateTime.now());
        int count = 0;

        for (Task task : overdue) {
            long hoursOverdue = ChronoUnit.HOURS.between(task.getDeadline(), LocalDateTime.now());

            if (hoursOverdue >= overdueThresholdHours) {
                count++;
                String message = "Task #" + task.getId() + " '" + task.getTitle()
                        + "' is " + hoursOverdue + "h overdue — assigned to " + task.getAssignedTo();

                if (hoursOverdue >= overdueThresholdHours * 2) {
                    criticals.add(message);
                    auditService.logHealthWarning("CRITICAL: " + message);
                } else {
                    warnings.add(message);
                    auditService.logHealthWarning("WARNING: " + message);
                }
            }
        }

        log.info("[HealthMonitoringAgent] Overdue check: {} tasks past {}h threshold",
                count, overdueThresholdHours);
        return count;
    }

    // ── Check 2: User workload (overloaded assignees) ──────────────────────

    private int checkUserWorkload(List<String> warnings) {
        List<Task> activeTasks = taskRepository.findByStatus(Task.TaskStatus.PENDING);
        activeTasks.addAll(taskRepository.findByStatus(Task.TaskStatus.IN_PROGRESS));

        // Group by assignee, count tasks per person
        Map<String, Long> tasksByUser = activeTasks.stream()
                .filter(t -> t.getAssignedTo() != null)
                .collect(Collectors.groupingBy(Task::getAssignedTo, Collectors.counting()));

        int overloadedCount = 0;
        for (Map.Entry<String, Long> entry : tasksByUser.entrySet()) {
            if (entry.getValue() > overloadTaskLimit) {
                overloadedCount++;
                String msg = "User " + entry.getKey() + " has " + entry.getValue()
                        + " open tasks (limit: " + overloadTaskLimit + "). Consider redistributing.";
                warnings.add(msg);
                auditService.logHealthWarning("OVERLOAD: " + msg);
                log.warn("[HealthMonitoringAgent] OVERLOAD: {}", msg);
            }
        }

        return overloadedCount;
    }

    // ── Check 3: Unassigned CRITICAL tasks ────────────────────────────────

    private int checkUnassignedCritical(List<String> warnings, List<String> criticals) {
        List<Task> pending = taskRepository.findByStatus(Task.TaskStatus.PENDING);
        int count = 0;

        for (Task task : pending) {
            boolean isCritical = task.getPriority() == Task.Priority.CRITICAL;
            boolean isUnassigned = "unassigned@company.com".equals(task.getAssignedTo())
                    || task.getAssignedTo() == null;

            if (isCritical && isUnassigned) {
                count++;
                String msg = "CRITICAL task #" + task.getId() + " '" + task.getTitle()
                        + "' has no owner!";
                criticals.add(msg);
                auditService.logHealthWarning(msg);
            }
        }

        return count;
    }

    // ── Health Report DTO ──────────────────────────────────────────────────

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class HealthReport {
        private LocalDateTime timestamp;
        private int  overdueTasks;
        private int  overloadedUsers;
        private int  unassignedCriticalTasks;
        private List<String> warnings;
        private List<String> criticals;
        private boolean healthy;
    }
}