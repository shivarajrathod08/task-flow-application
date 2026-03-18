package taskflowai.service;

import taskflowai.agent.PlanningAssignmentAgent;
import taskflowai.agent.TaskExtractionAgent;
import taskflowai.dto.MeetingRequest;
import taskflowai.dto.MeetingResponse;
import taskflowai.dto.ProcessingResult;
import taskflowai.dto.TaskResponse;
import taskflowai.entity.Meeting;
import taskflowai.entity.Task;
import taskflowai.repository.MeetingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * MeetingService — Orchestration Layer
 *
 * This is the "conductor" that coordinates Agent 1 (Extraction) and Agent 2 (Planning).
 * Sequence:
 *   1. Save raw meeting to DB
 *   2. Call TaskExtractionAgent → get structured tasks
 *   3. Call PlanningAssignmentAgent → persist + notify
 *   4. Update meeting as processed
 *   5. Return full ProcessingResult
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final TaskExtractionAgent extractionAgent;
    private final PlanningAssignmentAgent planningAgent;

    // ---------------------------------------------------------------
    //  Process a new meeting transcript
    // ---------------------------------------------------------------

    @Transactional
    public ProcessingResult processMeeting(MeetingRequest request) {
        log.info("[MeetingService] Processing new meeting: {}", request.getTitle());

        // 1. Persist the meeting
        Meeting meeting = Meeting.builder()
                .title(request.getTitle())
                .transcript(request.getTranscript())
                .meetingDate(request.getMeetingDate() != null
                        ? request.getMeetingDate()
                        : java.time.LocalDateTime.now())
                .build();
        meeting = meetingRepository.save(meeting);

        // 2. Extract tasks using AI (Agent 1)
        var extraction = extractionAgent.extract(request.getTranscript());

        // 3. Plan and assign (Agent 2) — persists tasks and sends notifications
        List<Task> tasks = planningAgent.planAndAssign(extraction, meeting);

        // 4. Update meeting with AI summary
        meeting.setProcessedByAi(true);
        meeting.setAiSummary(extraction.getSummary());
        meetingRepository.save(meeting);

        // 5. Build response
        List<TaskResponse> taskResponses = tasks.stream()
                .map(this::toTaskResponse)
                .collect(Collectors.toList());

        log.info("[MeetingService] Completed processing meeting id={} | {} tasks created",
                meeting.getId(), tasks.size());

        return ProcessingResult.builder()
                .meetingId(meeting.getId())
                .meetingTitle(meeting.getTitle())
                .aiSummary(extraction.getSummary())
                .tasksExtracted(tasks.size())
                .tasks(taskResponses)
                .message("Meeting processed successfully. " + tasks.size() + " tasks created and assigned.")
                .build();
    }

    // ---------------------------------------------------------------
    //  Get all meetings
    // ---------------------------------------------------------------

    public List<MeetingResponse> getAllMeetings() {
        return meetingRepository.findAllOrderByCreatedAtDesc()
                .stream()
                .map(this::toMeetingResponse)
                .collect(Collectors.toList());
    }

    public MeetingResponse getMeetingById(Long id) {
        Meeting m = meetingRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Meeting not found: " + id));
        return toMeetingResponse(m);
    }

    // ---------------------------------------------------------------
    //  Mappers
    // ---------------------------------------------------------------

    private MeetingResponse toMeetingResponse(Meeting m) {
        return MeetingResponse.builder()
                .id(m.getId())
                .title(m.getTitle())
                .meetingDate(m.getMeetingDate())
                .processedByAi(m.isProcessedByAi())
                .aiSummary(m.getAiSummary())
                .taskCount(m.getTasks().size())
                .createdAt(m.getCreatedAt())
                .build();
    }

    TaskResponse toTaskResponse(Task t) {
        return TaskResponse.builder()
                .id(t.getId())
                .title(t.getTitle())
                .description(t.getDescription())
                .status(t.getStatus())
                .priority(t.getPriority())
                .assignedTo(t.getAssignedTo())
                .deadline(t.getDeadline())
                .overdue(t.isOverdue())
                .escalated(t.isEscalated())
                .escalationCount(t.getEscalationCount())
                .meetingId(t.getMeeting() != null ? t.getMeeting().getId() : null)
                .meetingTitle(t.getMeeting() != null ? t.getMeeting().getTitle() : null)
                .createdAt(t.getCreatedAt())
                .build();
    }
}