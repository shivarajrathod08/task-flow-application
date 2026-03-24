package taskflowai.repository;



import taskflowai.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByTaskIdOrderByCreatedAtDesc(Long taskId);

    List<AuditLog> findByMeetingIdOrderByCreatedAtDesc(Long meetingId);

    List<AuditLog> findByEventTypeOrderByCreatedAtDesc(String eventType);

    @Query("SELECT a FROM AuditLog a ORDER BY a.createdAt DESC")
    List<AuditLog> findRecentEvents(org.springframework.data.domain.Pageable pageable);

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.eventType = :type AND a.taskId = :taskId")
    long countByEventTypeAndTaskId(@Param("type") String type, @Param("taskId") Long taskId);
}