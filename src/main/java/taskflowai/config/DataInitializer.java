package taskflowai.config;

;

import taskflowai.dto.MeetingRequest;
import taskflowai.service.MeetingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.LocalDateTime;

/**
 * Loads sample data on startup so the demo is immediately usable.
 * Disable by removing the @Bean annotation in production.
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class DataInitializer {

    @Bean
    CommandLineRunner loadDemoData(MeetingService meetingService) {
        return args -> {
            log.info("[DataInitializer] Loading demo meeting data...");

            MeetingRequest req = MeetingRequest.builder()
                    .title("Q2 Product Planning Sprint")
                    .meetingDate(LocalDateTime.now().minusHours(2))
                    .transcript("""
                        Attendees: Sarah (Design), John (DevOps), Priya (PM), Alex (UX), Mike (Scrum Master)
                        
                        Priya: Welcome everyone. Main agenda today is Q2 planning.
                        
                        First decision: we've agreed the mobile app is our top priority for Q2.
                        Budget of $50K has been approved for cloud infrastructure upgrades.
                        
                        Action items:
                        
                        Sarah, please design the mobile app UI mockups by April 15th.
                        Make sure to include 5 screens: splash, login, signup, home, and profile.
                        This is HIGH priority.
                        
                        John, we need you to set up AWS infrastructure ASAP — definitely by April 10th.
                        This is blocking everything else so treat it as CRITICAL.
                        
                        Priya will write the Q2 product requirements document by April 5th.
                        Mark that as HIGH priority.
                        
                        Alex, can you schedule 10 user interviews to validate features before dev starts?
                        Deadline April 20th, MEDIUM priority.
                        
                        Mike, please send a team update email about the new sprint process by April 1st.
                        LOW priority but needs to go out before we kick off the sprint.
                        
                        We'll do weekly syncs every Monday at 10 AM starting next week.
                        """)
                    .build();

            var result = meetingService.processMeeting(req);
            log.info("[DataInitializer] Demo data loaded: Meeting id={}, {} tasks created",
                    result.getMeetingId(), result.getTasksExtracted());
        };
    }
}
