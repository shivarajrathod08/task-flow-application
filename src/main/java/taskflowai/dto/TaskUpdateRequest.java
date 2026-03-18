package taskflowai.dto;


import com.fasterxml.jackson.annotation.JsonFormat;
import taskflowai.entity.Task;
import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskUpdateRequest {
    private Task.TaskStatus status;
    private String assignedTo;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime deadline;

    private Task.Priority priority;
}