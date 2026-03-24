package taskflowai.agent;

import taskflowai.entity.Notification;
import taskflowai.entity.Task;
import taskflowai.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import taskflowai.service.AuditService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationAgent {

    private final NotificationRepository notificationRepository;
    private final AuditService auditService;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");
    private static final String MANAGER_EMAIL = "manager@company.com";

    private static final int MAX_RETRIES = 2;
    private static final int RETRY_DELAY_MS = 500;

    public void sendTaskAssigned(Task task) {
        String subject = "[MeetFlow] New Task Assigned: " + task.getTitle();
        String body = """
                Hi %s,

                You have been assigned a new task from the meeting: "%s"

                Task Details:
                  Title      : %s
                  Description: %s
                  Priority   : %s
                  Deadline   : %s

                """.formatted(
                formatName(task.getAssignedTo()),
                task.getMeeting() != null ? task.getMeeting().getTitle() : "N/A",
                task.getTitle(),
                task.getDescription(),
                task.getPriority(),
                task.getDeadline() != null ? task.getDeadline().format(FMT) : "Not specified"
        );

        sendAndLog(task, Notification.NotificationType.TASK_ASSIGNED,
                task.getAssignedTo(), subject, body);
    }

    public void sendReminder(Task task) {
        if (task.getReminderSentCount() >= 3) return;

        String subject = "[MeetFlow] Reminder: " + task.getTitle();
        sendAndLog(task, Notification.NotificationType.REMINDER,
                task.getAssignedTo(), subject, "Reminder for task");

        task.setReminderSentCount(task.getReminderSentCount() + 1);
    }



    // ADD this new method here:
    public void sendCompletionNotice(Task task) {
        String subject = "[MeetFlow] Task Completed: " + task.getTitle();
        String body = "The task \"" + task.getTitle() + "\" has been marked as completed.";

        sendAndLog(task, Notification.NotificationType.COMPLETION,
                task.getAssignedTo(), subject, body);
    }
    public void sendEscalationAlert(Task task, long hoursOverdue) {
        String subject = "[MeetFlow] ESCALATION: " + task.getTitle();
        String body = """
            The task "%s" assigned to you is overdue by %d hours.

            Please take immediate action.
            """.formatted(task.getTitle(), hoursOverdue);

        sendAndLog(task, Notification.NotificationType.ESCALATION,
                task.getAssignedTo(), subject, body);

        sendAndLog(task, Notification.NotificationType.ESCALATION,
                MANAGER_EMAIL, subject, "Manager escalation");
    }

    private void sendAndLog(Task task, Notification.NotificationType type,
                            String recipient, String subject, String body) {

        Notification notification = Notification.builder()
                .task(task)
                .type(type)
                .recipientEmail(recipient)
                .message(body)
                .build();

        boolean sent = false;
        int attempt = 0;
        Exception lastException = null;

        while (attempt <= MAX_RETRIES && !sent) {
            attempt++;
            try {
                if (attempt > 1) {
                    log.warn("[NotificationAgent] Retry attempt {}/{} for task #{} to {}",
                            attempt, MAX_RETRIES + 1, task.getId(), recipient);
                    Thread.sleep(RETRY_DELAY_MS * attempt);

                    auditService.logNotification(task.getId(), false,
                            "Retry " + attempt + " for " + recipient, attempt);
                }

                // DEMO mode — replace with actual email sending if needed
                log.info("[DEMO] Email -> {} | Subject: {}", recipient, subject);

                sent = true;
                notification.setSent(true);
                notification.setSentAt(LocalDateTime.now());

                auditService.logNotification(task.getId(), true,
                        type + " to " + recipient, attempt);

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.error("[NotificationAgent] Retry interrupted for task #{}", task.getId());
                break;
            } catch (Exception e) {
                lastException = e;
                if (attempt <= MAX_RETRIES) {
                    log.warn("[NotificationAgent] Attempt {}/{} failed for task #{}: {}",
                            attempt, MAX_RETRIES + 1, task.getId(), e.getMessage());
                }
            }
        }

        if (!sent) {
            log.error("[NotificationAgent] ALL {} attempts FAILED for task #{} to {}: {}",
                    MAX_RETRIES + 1, task.getId(), recipient,
                    lastException != null ? lastException.getMessage() : "unknown");

            notification.setSent(true);
            notification.setSentAt(LocalDateTime.now());

            auditService.log("NOTIFICATION_FAILED",
                    "Notification FAILED after " + (MAX_RETRIES + 1) + " attempts to " + recipient,
                    task.getId());
        }

        notificationRepository.save(notification);
    }

    private String formatName(String email) {
        if (email == null) return "Team";
        if (email.contains("@")) return email.split("@")[0];
        return email;
    }
}