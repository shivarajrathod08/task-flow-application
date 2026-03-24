package taskflowai.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

/**
 * Immutable audit record — one row per meaningful system event.
 *
 * Never updated after creation. This is the "flight recorder" of the system.
 * Used to prove every decision is traceable during hackathon demo.
 *
 * Example events:
 *   TASK_CREATED, TASK_ASSIGNED, STATUS_CHANGED, NOTIFICATION_SENT,
 *   NOTIFICATION_FAILED, ESCALATION_TRIGGERED, FALLBACK_USED,
 *   DEADLINE_DEFAULTED, ASSIGNEE_DEFAULTED, WORKFLOW_STARTED,
 *   WORKFLOW_COMPLETED, HEALTH_WARNING
 */
@Entity
@Table(name = "audit_logs",
        indexes = {
                @Index(name = "idx_audit_task",    columnList = "task_id"),
                @Index(name = "idx_audit_meeting", columnList = "meeting_id"),
                @Index(name = "idx_audit_event",   columnList = "event_type"),
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // What happened
    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    // Free-text description of what the system decided
    @Column(name = "description", columnDefinition = "TEXT", nullable = false)
    private String description;

    // Optional links to related entities
    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "meeting_id")
    private Long meetingId;

    @Column(name = "workflow_execution_id")
    private Long workflowExecutionId;

    // Who or what triggered the event (agent class name)
    @Column(name = "actor", length = 100)
    private String actor;

    // Serialised before/after values for state changes
    @Column(name = "old_value", length = 255)
    private String oldValue;

    @Column(name = "new_value", length = 255)
    private String newValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    // ── Event type constants (avoid magic strings) ─────────────────────────
    public static final String TASK_CREATED           = "TASK_CREATED";
    public static final String TASK_ASSIGNED          = "TASK_ASSIGNED";
    public static final String STATUS_CHANGED         = "STATUS_CHANGED";
    public static final String NOTIFICATION_SENT      = "NOTIFICATION_SENT";
    public static final String NOTIFICATION_FAILED    = "NOTIFICATION_FAILED";
    public static final String NOTIFICATION_RETRIED   = "NOTIFICATION_RETRIED";
    public static final String ESCALATION_TRIGGERED   = "ESCALATION_TRIGGERED";
    public static final String FALLBACK_USED          = "FALLBACK_USED";
    public static final String DEADLINE_DEFAULTED     = "DEADLINE_DEFAULTED";
    public static final String ASSIGNEE_DEFAULTED     = "ASSIGNEE_DEFAULTED";
    public static final String WORKFLOW_STARTED       = "WORKFLOW_STARTED";
    public static final String WORKFLOW_COMPLETED     = "WORKFLOW_COMPLETED";
    public static final String WORKFLOW_FAILED        = "WORKFLOW_FAILED";
    public static final String HEALTH_WARNING         = "HEALTH_WARNING";
}