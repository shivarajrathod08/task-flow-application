package taskflowai;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MeetFlow - Agentic AI for Autonomous Enterprise Workflows
 *
 * Multi-Agent Architecture:
 *   1. Task Extraction Agent  - Parses meeting transcripts via AI
 *   2. Planning/Assignment Agent - Assigns tasks to users with priorities
 *   3. Tracking Agent         - Monitors task progress and deadlines
 *   4. Notification Agent     - Sends reminders and escalation alerts
 */
@SpringBootApplication
@EnableScheduling
public class TaskFlowApplication {
    public static void main(String[] args) {
        SpringApplication.run(TaskFlowApplication.class, args);
        System.out.println("""
                
                ╔══════════════════════════════════════╗
                ║   MeetFlow - Agentic AI Workflows    ║
                ║   Swagger UI: /swagger-ui.html       ║
                ║   H2 Console: /h2-console            ║
                ╚══════════════════════════════════════╝
                """);
    }
}
