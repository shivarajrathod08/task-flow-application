package taskflowai.schedular;

import taskflowai.agent.NotificationAgent;
import taskflowai.agent.TrackingAgent;
import taskflowai.entity.Task;
import taskflowai.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.List;

/**
 * WorkflowScheduler — The Heartbeat of MeetFlow
 *
 * Runs background jobs that coordinate the Tracking Agent and Notification Agent:
 *
 *   - Daily at 9 AM : Send reminders for pending/overdue tasks
 *   - Daily at 10 AM: Run escalation check for significantly delayed tasks
 *
 * In demo mode these can be triggered manually via API.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WorkflowScheduler {

    private final TaskRepository taskRepository;
    private final NotificationAgent notificationAgent;
    private final TrackingAgent trackingAgent;

    @Value("${meetflow.escalation.delay-threshold-hours:24}")
    private long escalationThresholdHours;

    // ---------------------------------------------------------------
    //  Daily Reminder Job  (09:00 every day)
    // ---------------------------------------------------------------

    @Scheduled(cron = "${scheduler.reminder.cron:0 0 9 * * ?}")
    public void dailyReminderJob() {
        log.info("====== [SCHEDULER] Daily Reminder Job started ======");

        // 1. Tasks approaching deadline (next 48 hours)
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime in48Hours = now.plusHours(48);
        List<Task> upcoming = taskRepository.findTasksWithUpcomingDeadline(now, in48Hours);
        log.info("[SCHEDULER] Found {} tasks with upcoming deadlines (48h window)", upcoming.size());

        for (Task task : upcoming) {
            notificationAgent.sendReminder(task);
            taskRepository.save(task);
        }

        // 2. Tasks that are already overdue
        List<Task> overdue = taskRepository.findOverdueTasks(now);
        log.info("[SCHEDULER] Found {} overdue tasks", overdue.size());

        for (Task task : overdue) {
            notificationAgent.sendReminder(task);
            taskRepository.save(task);
        }

        log.info("====== [SCHEDULER] Daily Reminder Job complete. Sent {} reminders ======",
                upcoming.size() + overdue.size());
    }

    // ---------------------------------------------------------------
    //  Daily Escalation Job  (10:00 every day)
    // ---------------------------------------------------------------

    @Scheduled(cron = "${scheduler.escalation.cron:0 0 10 * * ?}")
    public void dailyEscalationJob() {
        log.info("====== [SCHEDULER] Daily Escalation Job started ======");
        int count = trackingAgent.checkAndEscalate(escalationThresholdHours);
        log.info("====== [SCHEDULER] Escalation Job complete. {} tasks escalated ======", count);
    }

    // ---------------------------------------------------------------
    //  Health ping (every 5 minutes — shows system is alive in demo)
    // ---------------------------------------------------------------

    @Scheduled(fixedRate = 300_000)
    public void healthPing() {
        var summary = trackingAgent.getStatusSummary();
        log.debug("[SCHEDULER] Health: total={}, pending={}, inProgress={}, completed={}, escalated={}",
                summary.getTotalTasks(), summary.getPending(),
                summary.getInProgress(), summary.getCompleted(), summary.getEscalated());
    }
}
