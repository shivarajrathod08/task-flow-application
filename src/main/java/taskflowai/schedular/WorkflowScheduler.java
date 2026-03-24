package taskflowai.schedular;

import taskflowai.agent.HealthMonitoringAgent;
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

@Component
@Slf4j
@RequiredArgsConstructor
public class WorkflowScheduler {

    private final TaskRepository taskRepository;
    private final NotificationAgent notificationAgent;
    private final TrackingAgent trackingAgent;

    // ✅ FIX: properly injected
    private final HealthMonitoringAgent healthMonitoringAgent;

    @Value("${meetflow.escalation.delay-threshold-hours:24}")
    private long escalationThresholdHours;

    // ---------------------------------------------------------------
    //  Daily Reminder Job (09:00)
    // ---------------------------------------------------------------

    @Scheduled(cron = "${scheduler.reminder.cron:0 0 9 * * ?}")
    public void dailyReminderJob() {
        log.info("====== [SCHEDULER] Daily Reminder Job started ======");

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime in48Hours = now.plusHours(48);

        List<Task> upcoming = taskRepository.findTasksWithUpcomingDeadline(now, in48Hours);
        log.info("[SCHEDULER] Found {} upcoming tasks", upcoming.size());

        for (Task task : upcoming) {
            notificationAgent.sendReminder(task);
            taskRepository.save(task);
        }

        List<Task> overdue = taskRepository.findOverdueTasks(now);
        log.info("[SCHEDULER] Found {} overdue tasks", overdue.size());

        for (Task task : overdue) {
            notificationAgent.sendReminder(task);
            taskRepository.save(task);
        }

        log.info("====== [SCHEDULER] Reminder Job complete. Sent {} reminders ======",
                upcoming.size() + overdue.size());
    }

    // ---------------------------------------------------------------
    //  Daily Escalation Job (10:00)
    // ---------------------------------------------------------------

    @Scheduled(cron = "${scheduler.escalation.cron:0 0 10 * * ?}")
    public void dailyEscalationJob() {
        log.info("====== [SCHEDULER] Daily Escalation Job started ======");
        int count = trackingAgent.checkAndEscalate(escalationThresholdHours);
        log.info("====== [SCHEDULER] Escalation complete. {} tasks escalated ======", count);
    }

    // ---------------------------------------------------------------
    //  Daily Health Check (08:00)
    // ---------------------------------------------------------------

    @Scheduled(cron = "0 0 8 * * ?")
    public void dailyHealthCheckJob() {
        log.info("====== [SCHEDULER] Daily Health Check Job started ======");

        HealthMonitoringAgent.HealthReport report = healthMonitoringAgent.runHealthCheck();

        log.info("====== [SCHEDULER] Health Check complete: healthy={} overdue={} overloaded={} ======",
                report.isHealthy(),
                report.getOverdueTasks(),
                report.getOverloadedUsers());
    }

    // ---------------------------------------------------------------
    //  Health Ping (every 5 min)
    // ---------------------------------------------------------------

    @Scheduled(fixedRate = 300_000)
    public void healthPing() {
        var summary = trackingAgent.getStatusSummary();

        log.debug("[SCHEDULER] Health: total={}, pending={}, inProgress={}, completed={}, escalated={}",
                summary.getTotalTasks(),
                summary.getPending(),
                summary.getInProgress(),
                summary.getCompleted(),
                summary.getEscalated());
    }
}