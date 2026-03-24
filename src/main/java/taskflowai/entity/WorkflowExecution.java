package taskflowai.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;

/**
 * Tracks one complete execution of the 5-step workflow pipeline.
 *
 * One WorkflowExecution is created per processed meeting.
 * Each step updates the currentStep, status, and stepLogs fields.
 *
 * Steps:
 *   1 – TRANSCRIPT_INPUT
 *   2 – AI_EXTRACTION
 *   3 – TASK_PLANNING
 *   4 – TASK_TRACKING
 *   5 – NOTIFICATION
 */
@Entity
@Table(name = "workflow_executions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WorkflowExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "meeting_id")
    private Long meetingId;

    @Column(name = "meeting_title")
    private String meetingTitle;

    // Which step we are currently on (1–5)
    @Column(name = "current_step")
    @Builder.Default
    private int currentStep = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private WorkflowStatus status = WorkflowStatus.RUNNING;

    // Cumulative log of all step transitions — appended as we progress
    @Column(name = "step_logs", columnDefinition = "TEXT")
    @Builder.Default
    private String stepLogs = "";

    // Number of tasks extracted in step 2
    @Column(name = "tasks_extracted")
    @Builder.Default
    private int tasksExtracted = 0;

    // Whether fallback logic fired in any step
    @Column(name = "fallback_used")
    @Builder.Default
    private boolean fallbackUsed = false;

    // Error message if status = FAILED
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at")
    @CreationTimestamp
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // ── Convenience helpers ────────────────────────────────────────────────

    /** Append a timestamped line to the step log. */
    public void appendLog(String message) {
        String line = "[" + LocalDateTime.now().toString() + "] " + message + "\n";
        this.stepLogs = (this.stepLogs == null ? "" : this.stepLogs) + line;
    }

    /** Advance to the next step and log the transition. */
    public void advanceStep(String stepName) {
        this.currentStep++;
        appendLog("→ STEP " + this.currentStep + ": " + stepName);
    }

    public enum WorkflowStatus {
        RUNNING, COMPLETED, FAILED, PARTIALLY_COMPLETED
    }
}