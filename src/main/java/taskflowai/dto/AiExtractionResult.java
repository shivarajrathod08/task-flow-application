package taskflowai.dto;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiExtractionResult {
    private String summary;
    private List<ExtractedTask> tasks;
    private List<String> keyDecisions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtractedTask {
        private String title;
        private String description;
        private String assignedTo;
        private String deadline;    // Raw string from AI (e.g. "2025-04-01" or "next Friday")
        private String priority;    // LOW | MEDIUM | HIGH | CRITICAL
    }
}
