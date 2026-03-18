package taskflowai.controller;

import taskflowai.dto.MeetingRequest;
import taskflowai.dto.MeetingResponse;
import taskflowai.dto.ProcessingResult;
import taskflowai.service.MeetingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/meetings")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Meetings", description = "Submit meeting transcripts for AI processing")
public class MeetingController {

    private final MeetingService meetingService;

    /**
     * POST /api/meetings/process
     * Core endpoint: submit a meeting transcript and get tasks extracted
     */
    @PostMapping("/process")
    @Operation(summary = "Process meeting transcript",
            description = "Submits a meeting transcript to the AI extraction agent, " +
                    "creates tasks, assigns them, and sends notifications.")
    public ResponseEntity<ProcessingResult> processMeeting(@RequestBody MeetingRequest request) {
        log.info("POST /api/meetings/process - title: {}", request.getTitle());
        ProcessingResult result = meetingService.processMeeting(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /**
     * GET /api/meetings
     */
    @GetMapping
    @Operation(summary = "List all meetings")
    public ResponseEntity<List<MeetingResponse>> getAllMeetings() {
        return ResponseEntity.ok(meetingService.getAllMeetings());
    }

    /**
     * GET /api/meetings/{id}
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get meeting by ID")
    public ResponseEntity<MeetingResponse> getMeetingById(@PathVariable Long id) {
        return ResponseEntity.ok(meetingService.getMeetingById(id));
    }
}
