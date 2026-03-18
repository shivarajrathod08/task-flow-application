package taskflowai.agent;

import taskflowai.entity.Notification;
import taskflowai.entity.Task;
import taskflowai.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * AGENT 4: Notification Agent (DEMO MODE)
 *
 * No real email sending — only logs notifications
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationAgent {

    private final NotificationRepository notificationRepository;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");
    private static final String MANAGER_EMAIL = "manager@company.com";

    // ---------------------------------------------------------------
    //  Task Assigned Notification
    // ---------------------------------------------------------------

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

    // ---------------------------------------------------------------
    //  Reminder Notification
    // ---------------------------------------------------------------

    public void sendReminder(Task task) {
        if (task.getReminderSentCount() >= 3) {
            log.info("Max reminders reached for task {}", task.getId());
            return;
        }

        String subject = "[MeetFlow] Reminder: " + task.getTitle();

        sendAndLog(task, Notification.NotificationType.REMINDER,
                task.getAssignedTo(), subject, "Reminder for task");

        task.setReminderSentCount(task.getReminderSentCount() + 1);
    }

    // ---------------------------------------------------------------
    //  Escalation Notification
    // ---------------------------------------------------------------

    public void sendEscalationAlert(Task task, long hoursOverdue) {
        String subject = "[MeetFlow] ESCALATION: " + task.getTitle();

        sendAndLog(task, Notification.NotificationType.ESCALATION,
                task.getAssignedTo(), subject, "Escalation alert");

        sendAndLog(task, Notification.NotificationType.ESCALATION,
                MANAGER_EMAIL, subject, "Manager escalation");
    }

    // ---------------------------------------------------------------
    //  Completion Notification
    // ---------------------------------------------------------------

    public void sendCompletionNotice(Task task) {
        String subject = "[MeetFlow] Task Completed: " + task.getTitle();

        sendAndLog(task, Notification.NotificationType.COMPLETION,
                task.getAssignedTo(), subject, "Task completed");
    }

    // ---------------------------------------------------------------
    //  Pending reminders
    // ---------------------------------------------------------------

    public int sendPendingReminders() {
        List<Task> tasks = notificationRepository.findBySentFalse()
                .stream()
                .map(Notification::getTask)
                .distinct()
                .toList();

        log.info("Pending notifications: {}", tasks.size());
        return tasks.size();
    }

    // ---------------------------------------------------------------
    //  CORE METHOD (DEMO MODE)
    // ---------------------------------------------------------------

    private void sendAndLog(Task task,
                            Notification.NotificationType type,
                            String recipient,
                            String subject,
                            String body) {

        Notification notification = Notification.builder()
                .task(task)
                .type(type)
                .recipientEmail(recipient)
                .message(body)
                .build();

        //  DEMO MODE (no email)
        log.info("[DEMO] Email -> {} | Subject: {}", recipient, subject);

        notification.setSent(true);
        notification.setSentAt(LocalDateTime.now());

        notificationRepository.save(notification);
    }

    private String formatName(String email) {
        if (email == null) return "Team";
        if (email.contains("@")) return email.split("@")[0];
        return email;
    }
}