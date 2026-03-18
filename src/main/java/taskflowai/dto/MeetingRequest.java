package taskflowai.dto;



import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetingRequest {
    private String title;
    private String transcript;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm")
    private LocalDateTime meetingDate;
}
