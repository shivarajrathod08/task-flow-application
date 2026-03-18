package taskflowai.repository;



import taskflowai.entity.Meeting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface MeetingRepository extends JpaRepository<Meeting, Long> {

    List<Meeting> findByProcessedByAiFalse();

    @Query("SELECT m FROM Meeting m ORDER BY m.createdAt DESC")
    List<Meeting> findAllOrderByCreatedAtDesc();
}
