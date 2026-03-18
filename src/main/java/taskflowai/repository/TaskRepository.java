package taskflowai.repository;
import taskflowai.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByAssignedTo(String assignedTo);

    List<Task> findByStatus(Task.TaskStatus status);

    List<Task> findByMeetingId(Long meetingId);

    // Tasks that are overdue (past deadline and not done)
    @Query("SELECT t FROM Task t WHERE t.deadline < :now " +
            "AND t.status NOT IN ('COMPLETED', 'CANCELLED', 'ESCALATED')")
    List<Task> findOverdueTasks(@Param("now") LocalDateTime now);

    // Tasks approaching deadline within the next X hours (for reminders)
    @Query("SELECT t FROM Task t WHERE t.deadline BETWEEN :now AND :future " +
            "AND t.status NOT IN ('COMPLETED', 'CANCELLED')")
    List<Task> findTasksWithUpcomingDeadline(
            @Param("now") LocalDateTime now,
            @Param("future") LocalDateTime future);

    // Pending tasks that haven't had a reminder recently
    @Query("SELECT t FROM Task t WHERE t.status IN ('PENDING', 'IN_PROGRESS') " +
            "AND t.reminderSentCount < 3")
    List<Task> findTasksNeedingReminder();

    List<Task> findByEscalatedFalseAndStatusIn(List<Task.TaskStatus> statuses);

    @Query("SELECT t FROM Task t WHERE t.assignedTo = :email " +
            "AND t.status NOT IN ('COMPLETED', 'CANCELLED') " +
            "ORDER BY t.deadline ASC")
    List<Task> findActiveTasks(@Param("email") String email);
}