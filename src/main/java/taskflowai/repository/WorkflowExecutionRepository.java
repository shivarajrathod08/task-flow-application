package taskflowai.repository;



import taskflowai.entity.WorkflowExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowExecutionRepository extends JpaRepository<WorkflowExecution, Long> {

    Optional<WorkflowExecution> findByMeetingId(Long meetingId);

    List<WorkflowExecution> findByStatus(WorkflowExecution.WorkflowStatus status);
}