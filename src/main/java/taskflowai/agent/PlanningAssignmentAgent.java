package taskflowai.agent;

import taskflowai.dto.AiExtractionResult;
import taskflowai.entity.Meeting;
import taskflowai.entity.Task;
import taskflowai.repository.TaskRepository;
import taskflowai.service.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class PlanningAssignmentAgent {

    private final TaskRepository taskRepository;
    private final NotificationAgent notificationAgent;

    // ✅ Self-healing audit
    private final AuditService auditService;

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy")
    };

    // ---------------------------------------------------------------
    //  Main entry point
    // ---------------------------------------------------------------

    public List<Task> planAndAssign(AiExtractionResult extraction, Meeting meeting) {
        log.info("[PlanningAssignmentAgent] Planning {} extracted tasks for meeting: {}",
                extraction.getTasks().size(), meeting.getTitle());

        List<Task> savedTasks = new ArrayList<>();

        for (AiExtractionResult.ExtractedTask et : extraction.getTasks()) {
            try {
                Task task = buildTask(et, meeting);

                // First workload check
                checkWorkload(task);

                Task saved = taskRepository.save(task);
                savedTasks.add(saved);

                // Optional second check after persistence
                checkWorkload(saved);

                // Notify assignee
                if (saved.getAssignedTo() != null && !saved.getAssignedTo().isBlank()) {
                    notificationAgent.sendTaskAssigned(saved);
                }

                log.info("[PlanningAssignmentAgent] Task created: '{}' -> assigned to: {}, deadline: {}, priority: {}",
                        saved.getTitle(), saved.getAssignedTo(), saved.getDeadline(), saved.getPriority());

            } catch (Exception e) {
                log.error("[PlanningAssignmentAgent] Failed to create task '{}': {}",
                        et.getTitle(), e.getMessage());
            }
        }

        log.info("[PlanningAssignmentAgent] Successfully created {}/{} tasks",
                savedTasks.size(), extraction.getTasks().size());

        return savedTasks;
    }

    // ---------------------------------------------------------------
    //  Build Task entity
    // ---------------------------------------------------------------

    private Task buildTask(AiExtractionResult.ExtractedTask et, Meeting meeting) {
        return Task.builder()
                .title(sanitize(et.getTitle()))
                .description(sanitize(et.getDescription()))
                .assignedTo(normalizeAssignee(et.getAssignedTo(), null)) // Task ID can be passed if available
                .deadline(parseDeadline(et.getDeadline(), null))         // Task ID can be passed if available
                .priority(parsePriority(et.getPriority()))
                .status(Task.TaskStatus.PENDING)
                .meeting(meeting)
                .build();
    }

    // ---------------------------------------------------------------
    //  Workload Check
    // ---------------------------------------------------------------

    private void checkWorkload(Task task) {
        if (task.getAssignedTo() == null || task.getAssignedTo().isBlank()) return;

        List<Task> activeTasks = taskRepository.findActiveTasks(task.getAssignedTo());

        if (activeTasks.size() >= 5) {
            log.warn("[PlanningAssignmentAgent] WORKLOAD WARNING: {} already has {} active tasks. Consider redistributing.",
                    task.getAssignedTo(), activeTasks.size());
        }
    }

    // ---------------------------------------------------------------
    //  Self-Healing Helpers
    // ---------------------------------------------------------------

    private String normalizeAssignee(String raw, Long taskId) {
        if (raw == null || raw.isBlank()) {
            log.warn("[PlanningAssignmentAgent] No assignee found for task {} — defaulting to team@company.com", taskId);

            auditService.logFallback(
                    "Assignee missing in AI response — defaulted to team@company.com",
                    taskId,
                    "PlanningAssignmentAgent"
            );

            return "team@company.com";
        }

        if (raw.contains("@")) return raw.trim().toLowerCase();

        return raw.trim().toLowerCase().replace(" ", ".") + "@company.com";
    }

    private LocalDateTime parseDeadline(String deadlineStr, Long taskId) {
        if (deadlineStr == null || deadlineStr.isBlank()) {
            LocalDateTime defaultDeadline = LocalDateTime.now().plusDays(3);
            log.warn("[PlanningAssignmentAgent] No deadline found for task {} — defaulting to +3 days ({})",
                    taskId, defaultDeadline.toLocalDate());

            auditService.logFallback(
                    "Deadline missing — auto-assigned default of +3 days: " + defaultDeadline.toLocalDate(),
                    taskId,
                    "PlanningAssignmentAgent"
            );

            return defaultDeadline;
        }

        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDateTime.parse(deadlineStr, fmt);
            } catch (Exception ignored) {}

            try {
                return LocalDate.parse(deadlineStr, fmt).atTime(17, 0);
            } catch (Exception ignored) {}
        }

        String lower = deadlineStr.toLowerCase();

        if (lower.contains("tomorrow"))   return LocalDateTime.now().plusDays(1).withHour(17);
        if (lower.contains("next week"))  return LocalDateTime.now().plusWeeks(1).withHour(17);
        if (lower.contains("next month")) return LocalDateTime.now().plusMonths(1).withHour(17);
        if (lower.contains("eod") || lower.contains("end of day")) return LocalDateTime.now().withHour(17);
        if (lower.contains("eow") || lower.contains("end of week")) return LocalDateTime.now().plusDays(
                (7 - LocalDateTime.now().getDayOfWeek().getValue()) % 7 + 1).withHour(17);
        if (lower.contains("friday"))     return LocalDateTime.now().plusDays(5).withHour(17);
        if (lower.contains("asap"))       return LocalDateTime.now().plusDays(1).withHour(12);

        log.warn("[PlanningAssignmentAgent] Could not parse deadline '{}' for task {} — defaulting to +3 days",
                deadlineStr, taskId);

        auditService.logFallback(
                "Deadline '" + deadlineStr + "' could not be parsed — defaulted to +3 days",
                taskId,
                "PlanningAssignmentAgent"
        );

        return LocalDateTime.now().plusDays(3);
    }

    private Task.Priority parsePriority(String priority) {
        if (priority == null) return Task.Priority.MEDIUM;

        return switch (priority.toUpperCase().trim()) {
            case "CRITICAL", "P0", "URGENT" -> Task.Priority.CRITICAL;
            case "HIGH", "P1" -> Task.Priority.HIGH;
            case "LOW", "P3" -> Task.Priority.LOW;
            default -> Task.Priority.MEDIUM;
        };
    }

    private String sanitize(String input) {
        if (input == null) return "";
        return input.trim().replaceAll("[\\r\\n]+", " ");
    }
}