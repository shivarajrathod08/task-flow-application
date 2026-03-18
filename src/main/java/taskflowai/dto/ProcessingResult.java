package taskflowai.dto;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingResult {
    private Long meetingId;
    private String meetingTitle;
    private String aiSummary;
    private int tasksExtracted;
    private List<TaskResponse> tasks;
    private String message;
}