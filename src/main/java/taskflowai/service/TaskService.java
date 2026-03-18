package taskflowai.service;

import taskflowai.agent.NotificationAgent;
import taskflowai.agent.TrackingAgent;
import taskflowai.dto.TaskResponse;
import taskflowai.dto.TaskUpdateRequest;
import taskflowai.entity.Task;
import taskflowai.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final NotificationAgent notificationAgent;
    private final TrackingAgent trackingAgent;
    private final MeetingService meetingService;

    // ---------------------------------------------------------------
    //  CRUD
    // ---------------------------------------------------------------

    public List<TaskResponse> getAllTasks() {
        return taskRepository.findAll().stream()
                .map(meetingService::toTaskResponse)
                .collect(Collectors.toList());
    }

    public TaskResponse getTaskById(Long id) {
        Task task = findById(id);
        return meetingService.toTaskResponse(task);
    }

    public List<TaskResponse> getTasksByMeeting(Long meetingId) {
        return taskRepository.findByMeetingId(meetingId).stream()
                .map(meetingService::toTaskResponse)
                .collect(Collectors.toList());
    }

    public List<TaskResponse> getTasksByAssignee(String email) {
        return taskRepository.findByAssignedTo(email).stream()
                .map(meetingService::toTaskResponse)
                .collect(Collectors.toList());
    }

    public List<TaskResponse> getOverdueTasks() {
        return trackingAgent.getOverdueTasks().stream()
                .map(meetingService::toTaskResponse)
                .collect(Collectors.toList());
    }

    public TrackingAgent.TrackingSummary getDashboard() {
        return trackingAgent.getStatusSummary();
    }

    // ---------------------------------------------------------------
    //  Update task status / assignment
    // ---------------------------------------------------------------

    @Transactional
    public TaskResponse updateTask(Long id, TaskUpdateRequest req) {
        Task task = findById(id);
        boolean wasCompleted = task.getStatus() == Task.TaskStatus.COMPLETED;

        if (req.getStatus() != null)     task.setStatus(req.getStatus());
        if (req.getAssignedTo() != null) task.setAssignedTo(req.getAssignedTo());
        if (req.getDeadline() != null)   task.setDeadline(req.getDeadline());
        if (req.getPriority() != null)   task.setPriority(req.getPriority());

        // Mark completion timestamp
        if (req.getStatus() == Task.TaskStatus.COMPLETED && !wasCompleted) {
            task.setCompletedAt(LocalDateTime.now());
            notificationAgent.sendCompletionNotice(task);
            log.info("[TaskService] Task {} marked complete by {}", task.getId(), task.getAssignedTo());
        }

        Task saved = taskRepository.save(task);
        return meetingService.toTaskResponse(saved);
    }

    // ---------------------------------------------------------------
    //  Manual trigger for reminders/escalation (useful for demo)
    // ---------------------------------------------------------------

    @Transactional
    public String triggerReminders() {
        List<Task> tasks = taskRepository.findTasksNeedingReminder();
        int sent = 0;
        for (Task task : tasks) {
            notificationAgent.sendReminder(task);
            taskRepository.save(task);
            sent++;
        }
        return "Sent " + sent + " reminders";
    }

    @Transactional
    public String triggerEscalation() {
        int escalated = trackingAgent.checkAndEscalate(24);
        return "Escalated " + escalated + " overdue tasks";
    }

    // ---------------------------------------------------------------
    //  Helper
    // ---------------------------------------------------------------

    private Task findById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Task not found: " + id));
    }
}