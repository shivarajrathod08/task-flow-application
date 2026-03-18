package taskflowai.agent;


import taskflowai.entity.Task;
import taskflowai.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * AGENT 3: Tracking Agent
 *
 * Responsibility:
 * - Continuously monitors all active tasks
 * - Detects overdue tasks
 * - Triggers escalation when tasks are significantly delayed
 * - Provides status reports and dashboards
 * - Marks tasks as ESCALATED and increments escalation counter
 *
 * This agent is invoked:
 * 1. By the scheduler (periodic checks)
 * 2. On-demand via API (GET /api/tasks/overdue)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TrackingAgent {

    private final TaskRepository taskRepository;
    private final NotificationAgent notificationAgent;

    // ---------------------------------------------------------------
    //  Escalation Check (called by scheduler)
    // ---------------------------------------------------------------

    /**
     * Scans all active tasks and escalates those that are overdue
     * beyond the configured threshold.
     *
     * @param thresholdHours  How many hours overdue before escalating
     * @return                Number of tasks escalated
     */
    public int checkAndEscalate(long thresholdHours) {
        log.info("[TrackingAgent] Running escalation check. Threshold: {} hours", thresholdHours);

        LocalDateTime cutoff = LocalDateTime.now().minusHours(thresholdHours);

        List<Task> candidates = taskRepository.findByEscalatedFalseAndStatusIn(
                List.of(Task.TaskStatus.PENDING, Task.TaskStatus.IN_PROGRESS)
        );

        int escalated = 0;
        for (Task task : candidates) {
            if (task.getDeadline() != null && task.getDeadline().isBefore(cutoff)) {
                escalateTask(task);
                escalated++;
            }
        }

        log.info("[TrackingAgent] Escalation check complete. Escalated: {} tasks", escalated);
        return escalated;
    }

    // ---------------------------------------------------------------
    //  Single task escalation
    // ---------------------------------------------------------------

    public void escalateTask(Task task) {
        long hoursOverdue = ChronoUnit.HOURS.between(task.getDeadline(), LocalDateTime.now());

        log.warn("[TrackingAgent] ESCALATING task id={} '{}' | Assignee: {} | {}h overdue",
                task.getId(), task.getTitle(), task.getAssignedTo(), hoursOverdue);

        task.setEscalated(true);
        task.setStatus(Task.TaskStatus.ESCALATED);
        task.setEscalationCount(task.getEscalationCount() + 1);
        taskRepository.save(task);

        // Notify both assignee and manager
        notificationAgent.sendEscalationAlert(task, hoursOverdue);
    }

    // ---------------------------------------------------------------
    //  Status Summary (for dashboard)
    // ---------------------------------------------------------------

    public TrackingSummary getStatusSummary() {
        long total      = taskRepository.count();
        long pending    = taskRepository.findByStatus(Task.TaskStatus.PENDING).size();
        long inProgress = taskRepository.findByStatus(Task.TaskStatus.IN_PROGRESS).size();
        long completed  = taskRepository.findByStatus(Task.TaskStatus.COMPLETED).size();
        long escalated  = taskRepository.findByStatus(Task.TaskStatus.ESCALATED).size();
        long overdue    = taskRepository.findOverdueTasks(LocalDateTime.now()).size();

        double completionRate = total > 0 ? (double) completed / total * 100 : 0;

        return TrackingSummary.builder()
                .totalTasks(total)
                .pending(pending)
                .inProgress(inProgress)
                .completed(completed)
                .escalated(escalated)
                .overdue(overdue)
                .completionRate(Math.round(completionRate * 100.0) / 100.0)
                .build();
    }

    // ---------------------------------------------------------------
    //  Overdue Task List
    // ---------------------------------------------------------------

    public List<Task> getOverdueTasks() {
        return taskRepository.findOverdueTasks(LocalDateTime.now());
    }

    // ---------------------------------------------------------------
    //  Inner summary DTO
    // ---------------------------------------------------------------

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TrackingSummary {
        private long totalTasks;
        private long pending;
        private long inProgress;
        private long completed;
        private long escalated;
        private long overdue;
        private double completionRate;
    }
}