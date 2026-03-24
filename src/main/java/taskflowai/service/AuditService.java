package taskflowai.service;



import taskflowai.entity.AuditLog;
import taskflowai.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import java.util.List;

/**
 * AuditService — write-only audit trail.
 *
 * Call from any agent or service to record a decision.
 * Every call is a fire-and-forget INSERT; errors are caught and logged
 * so an audit failure never breaks the main workflow.
 *
 * Usage examples:
 *
 *   auditService.log(AuditLog.TASK_CREATED, "Task 'Design UI' created", taskId, meetingId, wfId);
 *   auditService.logFallback("Assignee missing, defaulted to team@company.com", taskId, "PlanningAgent");
 *   auditService.logStateChange(taskId, "PENDING", "IN_PROGRESS", "TrackingAgent");
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    // ── Core log method ────────────────────────────────────────────────────

    public void log(String eventType, String description,
                    Long taskId, Long meetingId, Long workflowExecutionId) {
        try {
            AuditLog entry = AuditLog.builder()
                    .eventType(eventType)
                    .description(description)
                    .taskId(taskId)
                    .meetingId(meetingId)
                    .workflowExecutionId(workflowExecutionId)
                    .build();
            auditLogRepository.save(entry);
            log.debug("[AUDIT] {} | task={} meeting={} | {}",
                    eventType, taskId, meetingId, description);
        } catch (Exception e) {
            // Audit failure must NEVER break the main workflow
            log.error("[AUDIT] Failed to write audit log: {}", e.getMessage());
        }
    }

    // ── Convenience overloads (fewer params needed in common cases) ─────────

    public void log(String eventType, String description, Long taskId, Long meetingId) {
        log(eventType, description, taskId, meetingId, null);
    }

    public void log(String eventType, String description, Long taskId) {
        log(eventType, description, taskId, null, null);
    }

    // ── Typed helpers — reduce magic strings at call sites ─────────────────

    /** A fallback/self-healing decision was applied. */
    public void logFallback(String description, Long taskId, String actor) {
        try {
            AuditLog entry = AuditLog.builder()
                    .eventType(AuditLog.FALLBACK_USED)
                    .description(description)
                    .taskId(taskId)
                    .actor(actor)
                    .build();
            auditLogRepository.save(entry);
            log.warn("[AUDIT][FALLBACK] task={} actor={} | {}", taskId, actor, description);
        } catch (Exception e) {
            log.error("[AUDIT] Failed to write fallback log: {}", e.getMessage());
        }
    }

    /** A task or entity changed state. */
    public void logStateChange(Long taskId, String oldValue, String newValue, String actor) {
        try {
            AuditLog entry = AuditLog.builder()
                    .eventType(AuditLog.STATUS_CHANGED)
                    .description("Status changed from " + oldValue + " to " + newValue)
                    .taskId(taskId)
                    .actor(actor)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .build();
            auditLogRepository.save(entry);
            log.info("[AUDIT][STATUS] task={} {} → {} by {}", taskId, oldValue, newValue, actor);
        } catch (Exception e) {
            log.error("[AUDIT] Failed to write state-change log: {}", e.getMessage());
        }
    }

    /** A health / bottleneck warning was detected. */
    public void logHealthWarning(String description) {
        log(AuditLog.HEALTH_WARNING, description, null, null, null);
        log.warn("[AUDIT][HEALTH] {}", description);
    }

    /** Notification was sent or failed. */
    public void logNotification(Long taskId, boolean success, String detail, int attemptNumber) {
        String eventType = success ? AuditLog.NOTIFICATION_SENT : AuditLog.NOTIFICATION_FAILED;
        String desc = (success ? "Notification sent" : "Notification FAILED")
                + " (attempt " + attemptNumber + "): " + detail;
        log(eventType, desc, taskId);
    }

    // ── Query helpers ──────────────────────────────────────────────────────

    public List<AuditLog> getAuditTrailForTask(Long taskId) {
        return auditLogRepository.findByTaskIdOrderByCreatedAtDesc(taskId);
    }

    public List<AuditLog> getRecentEvents(int limit) {
        return auditLogRepository.findRecentEvents(PageRequest.of(0, limit));
    }
}