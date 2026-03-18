package taskflowai.agent;



import taskflowai.dto.AiExtractionResult;
import taskflowai.entity.Meeting;
import taskflowai.entity.Task;
import taskflowai.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * AGENT 2: Planning / Assignment Agent
 *
 * Responsibility:
 * - Receives extracted tasks from TaskExtractionAgent
 * - Normalizes deadlines, maps priority strings to enums
 * - Persists tasks to database
 * - Triggers Notification Agent to send "task assigned" emails
 * - Applies basic workload balancing (if assignee is overloaded, flags it)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PlanningAssignmentAgent {

    private final TaskRepository taskRepository;
    private final NotificationAgent notificationAgent;

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("MM/dd/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy")
    };

    // ---------------------------------------------------------------
    //  Main entry point
    // ---------------------------------------------------------------

    /**
     * Takes AI-extracted tasks and turns them into persisted Task entities.
     *
     * @param extraction  Result from TaskExtractionAgent
     * @param meeting     The parent meeting entity
     * @return            List of saved Task entities
     */
    public List<Task> planAndAssign(AiExtractionResult extraction, Meeting meeting) {
        log.info("[PlanningAssignmentAgent] Planning {} extracted tasks for meeting: {}",
                extraction.getTasks().size(), meeting.getTitle());

        List<Task> savedTasks = new ArrayList<>();

        for (AiExtractionResult.ExtractedTask et : extraction.getTasks()) {
            try {
                Task task = buildTask(et, meeting);
                checkWorkload(task);        // Warn if assignee is overloaded
                Task saved = taskRepository.save(task);
                savedTasks.add(saved);

                // Notify assignee immediately after assignment
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
    //  Build Task entity from AI-extracted data
    // ---------------------------------------------------------------

    private Task buildTask(AiExtractionResult.ExtractedTask et, Meeting meeting) {
        return Task.builder()
                .title(sanitize(et.getTitle()))
                .description(sanitize(et.getDescription()))
                .assignedTo(normalizeAssignee(et.getAssignedTo()))
                .deadline(parseDeadline(et.getDeadline()))
                .priority(parsePriority(et.getPriority()))
                .status(Task.TaskStatus.PENDING)
                .meeting(meeting)
                .build();
    }

    // ---------------------------------------------------------------
    //  Workload Check
    // ---------------------------------------------------------------

    /**
     * If an assignee already has 5+ open tasks, log a warning.
     * In production this could auto-reassign or flag for manager review.
     */
    private void checkWorkload(Task task) {
        if (task.getAssignedTo() == null || task.getAssignedTo().isBlank()) return;

        List<Task> activeTasks = taskRepository.findActiveTasks(task.getAssignedTo());
        if (activeTasks.size() >= 5) {
            log.warn("[PlanningAssignmentAgent] WORKLOAD WARNING: {} already has {} active tasks. " +
                    "Consider redistributing.", task.getAssignedTo(), activeTasks.size());
        }
    }

    // ---------------------------------------------------------------
    //  Parsing Helpers
    // ---------------------------------------------------------------

    private LocalDateTime parseDeadline(String deadlineStr) {
        if (deadlineStr == null || deadlineStr.isBlank()) {
            // Default: 7 days from now if no deadline specified
            log.debug("[PlanningAssignmentAgent] No deadline found, defaulting to +7 days");
            return LocalDateTime.now().plusDays(7);
        }

        // Try each known format
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDateTime.parse(deadlineStr + " 17:00",
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            } catch (DateTimeParseException ignored) {}
            try {
                return LocalDateTime.parse(deadlineStr, fmt).withHour(17).withMinute(0);
            } catch (Exception ignored) {}
        }

        // Natural language fallback
        String lower = deadlineStr.toLowerCase();
        if (lower.contains("tomorrow"))      return LocalDateTime.now().plusDays(1).withHour(17);
        if (lower.contains("next week"))     return LocalDateTime.now().plusWeeks(1).withHour(17);
        if (lower.contains("next month"))    return LocalDateTime.now().plusMonths(1).withHour(17);
        if (lower.contains("eod") || lower.contains("end of day")) return LocalDateTime.now().withHour(17);
        if (lower.contains("eow") || lower.contains("end of week")) return LocalDateTime.now().plusDays(
                (7 - LocalDateTime.now().getDayOfWeek().getValue()) % 7 + 1).withHour(17);
        if (lower.contains("friday"))        return LocalDateTime.now().plusDays(5).withHour(17);
        if (lower.contains("asap"))          return LocalDateTime.now().plusDays(1).withHour(12);

        log.warn("[PlanningAssignmentAgent] Could not parse deadline '{}', defaulting to +7 days", deadlineStr);
        return LocalDateTime.now().plusDays(7);
    }

    private Task.Priority parsePriority(String priority) {
        if (priority == null) return Task.Priority.MEDIUM;
        return switch (priority.toUpperCase().trim()) {
            case "CRITICAL", "P0", "URGENT" -> Task.Priority.CRITICAL;
            case "HIGH",     "P1"           -> Task.Priority.HIGH;
            case "LOW",      "P3"           -> Task.Priority.LOW;
            default                         -> Task.Priority.MEDIUM;
        };
    }

    private String normalizeAssignee(String raw) {
        if (raw == null || raw.isBlank()) return "unassigned@company.com";
        // If it already looks like an email, keep it
        if (raw.contains("@")) return raw.trim().toLowerCase();
        // Otherwise treat as a name — in prod you'd look up the directory
        return raw.trim().toLowerCase().replace(" ", ".") + "@company.com";
    }

    private String sanitize(String input) {
        if (input == null) return "";
        return input.trim().replaceAll("[\\r\\n]+", " ");
    }
}