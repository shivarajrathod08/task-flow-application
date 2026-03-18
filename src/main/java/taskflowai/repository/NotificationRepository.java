package taskflowai.repository;


import taskflowai.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findBySentFalse();
    List<Notification> findByTaskId(Long taskId);
    List<Notification> findByRecipientEmail(String email);
}