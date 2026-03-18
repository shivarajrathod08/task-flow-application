package taskflowai.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import taskflowai.entity.Task;
import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskResponse {
    private Long id;
    private String title;
    private String description;
    private Task.TaskStatus status;
    private Task.Priority priority;
    private String assignedTo;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime deadline;

    private boolean overdue;
    private boolean escalated;
    private int escalationCount;
    private Long meetingId;
    private String meetingTitle;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime createdAt;
}