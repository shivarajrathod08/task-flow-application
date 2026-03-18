package taskflowai.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import taskflowai.dto.AiExtractionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.*;

/**
 * AGENT 1: Task Extraction Agent
 *
 * Responsibility: Parse meeting transcripts using AI (Gemini/OpenAI)
 * and extract structured data: tasks, assignees, deadlines, priorities.
 *
 * This agent does NOT store anything — it only returns extracted data.
 * The Planning Agent decides what to do with the extracted tasks.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class TaskExtractionAgent {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${ai.provider:gemini}")
    private String aiProvider;

    @Value("${ai.openai.api-key:}")
    private String openAiKey;

    @Value("${ai.openai.model:gpt-4o-mini}")
    private String openAiModel;

    @Value("${ai.gemini.api-key:}")
    private String geminiKey;

    @Value("${ai.gemini.model:gemini-1.5-flash}")
    private String geminiModel;

    @Value("${meetflow.demo-mode:true}")
    private boolean demoMode;

    // ---------------------------------------------------------------
    //  Main entry point
    // ---------------------------------------------------------------

    public AiExtractionResult extract(String transcript) {
        log.info("[TaskExtractionAgent] Starting extraction. Provider: {}, Demo: {}",
                aiProvider, demoMode);

        if (demoMode || "demo".equals(aiProvider)) {
            log.info("[TaskExtractionAgent] Using demo mode response");
            return buildDemoResponse();
        }

        String prompt = buildPrompt(transcript);
        String rawJson;

        try {
            rawJson = "openai".equalsIgnoreCase(aiProvider)
                    ? callOpenAi(prompt)
                    : callGemini(prompt);

            return parseAiResponse(rawJson);

        } catch (Exception e) {
            log.error("[TaskExtractionAgent] AI call failed: {}", e.getMessage(), e);
            log.warn("[TaskExtractionAgent] Falling back to basic extraction");
            return buildFallbackResponse(transcript);
        }
    }

    // ---------------------------------------------------------------
    //  Prompt Engineering
    // ---------------------------------------------------------------

    /**
     * THE CORE PROMPT — carefully engineered to extract structured data.
     *
     * Key design choices:
     * 1. JSON-only output (no markdown fences)
     * 2. Explicit field list with types
     * 3. Priority inference rules
     * 4. Deadline normalization instructions
     * 5. Few-shot examples not needed due to explicit schema
     */
    private String buildPrompt(String transcript) {
        return """
            You are an expert meeting analyst and task extractor for enterprise workflows.
            
            Analyze the following meeting transcript and extract ALL action items.
            
            INSTRUCTIONS:
            1. Identify every action item, task, or commitment made
            2. Extract who is responsible (look for "X will", "X should", "assign X to", "X is responsible for")
            3. Find deadlines (look for dates, "by EOD", "next week", "by Friday", etc.)
            4. Infer priority based on urgency language:
               - CRITICAL: "urgent", "ASAP", "immediately", "blocker", "P0"
               - HIGH: "important", "priority", "soon", "this week", "P1"
               - MEDIUM: "need to", "should", "plan to" (default)
               - LOW: "eventually", "nice to have", "when possible"
            5. Write a 2-3 sentence executive summary
            6. List key decisions made (not tasks, but decisions)
            
            RESPONSE FORMAT: Return ONLY valid JSON, no other text or markdown:
            {
              "summary": "Brief 2-3 sentence summary of the meeting",
              "keyDecisions": ["decision 1", "decision 2"],
              "tasks": [
                {
                  "title": "Short task title (max 100 chars)",
                  "description": "Detailed description of what needs to be done",
                  "assignedTo": "person name or email, empty string if unclear",
                  "deadline": "YYYY-MM-DD format if specific date mentioned, empty string if unclear",
                  "priority": "CRITICAL|HIGH|MEDIUM|LOW"
                }
              ]
            }
            
            MEETING TRANSCRIPT:
            ---
            %s
            ---
            
            Remember: Return ONLY the JSON object, no markdown, no explanation.
            """.formatted(transcript);
    }

    // ---------------------------------------------------------------
    //  OpenAI API Call
    // ---------------------------------------------------------------

    private String callOpenAi(String prompt) {
        log.info("[TaskExtractionAgent] Calling OpenAI API model={}", openAiModel);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", openAiModel);
        requestBody.put("temperature", 0.1);  // Low temp for consistent extraction
        requestBody.put("max_tokens", 2000);

        Map<String, String> systemMsg = new HashMap<>();
        systemMsg.put("role", "system");
        systemMsg.put("content", "You are a precise task extractor. Always respond with valid JSON only.");

        Map<String, String> userMsg = new HashMap<>();
        userMsg.put("role", "user");
        userMsg.put("content", prompt);

        requestBody.put("messages", List.of(systemMsg, userMsg));

        Map<?, ?> response = webClient.post()
                .uri("https://api.openai.com/v1/chat/completions")
                .header("Authorization", "Bearer " + openAiKey)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        return extractTextFromOpenAiResponse(response);
    }

    @SuppressWarnings("unchecked")
    private String extractTextFromOpenAiResponse(Map<?, ?> response) {
        var choices = (List<Map<String, Object>>) response.get("choices");
        var message = (Map<String, Object>) choices.get(0).get("message");
        return (String) message.get("content");
    }

    // ---------------------------------------------------------------
    //  Gemini API Call
    // ---------------------------------------------------------------

    private String callGemini(String prompt) {
        log.info("[TaskExtractionAgent] Calling Gemini API model={}", geminiModel);

        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + geminiModel + ":generateContent?key=" + geminiKey;

        Map<String, Object> part = Map.of("text", prompt);
        Map<String, Object> content = Map.of("parts", List.of(part));
        Map<String, Object> requestBody = Map.of("contents", List.of(content));

        Map<?, ?> response = webClient.post()
                .uri(url)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        return extractTextFromGeminiResponse(response);
    }

    @SuppressWarnings("unchecked")
    private String extractTextFromGeminiResponse(Map<?, ?> response) {
        var candidates = (List<Map<String, Object>>) response.get("candidates");
        var content = (Map<String, Object>) candidates.get(0).get("content");
        var parts = (List<Map<String, Object>>) content.get("parts");
        String text = (String) parts.get(0).get("text");

        // Strip markdown code fences if Gemini adds them
        return text.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
    }

    // ---------------------------------------------------------------
    //  Parse AI JSON response
    // ---------------------------------------------------------------

    private AiExtractionResult parseAiResponse(String rawJson) {
        try {
            // Strip any accidental markdown fences
            String clean = rawJson.replaceAll("```json\\s*", "")
                    .replaceAll("```\\s*", "").trim();
            return objectMapper.readValue(clean, AiExtractionResult.class);
        } catch (JsonProcessingException e) {
            log.error("[TaskExtractionAgent] Failed to parse AI JSON: {}", e.getMessage());
            log.debug("[TaskExtractionAgent] Raw JSON was: {}", rawJson);
            throw new RuntimeException("AI returned invalid JSON: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------
    //  Demo / Fallback responses
    // ---------------------------------------------------------------

    /** Rich demo response for hackathon demo without real API keys */
    private AiExtractionResult buildDemoResponse() {
        return AiExtractionResult.builder()
                .summary("The team held a sprint planning meeting to discuss Q2 objectives. " +
                        "Key decisions were made around product roadmap prioritization and team assignments. " +
                        "Five action items were identified with clear owners and deadlines.")
                .keyDecisions(List.of(
                        "Mobile app will be the primary focus for Q2",
                        "Budget of $50K approved for cloud infrastructure",
                        "Weekly sync meetings scheduled every Monday 10 AM"
                ))
                .tasks(List.of(
                        AiExtractionResult.ExtractedTask.builder()
                                .title("Design mobile app UI mockups")
                                .description("Create high-fidelity mockups for the new mobile app onboarding flow. Include 5 screens: splash, login, signup, home, and profile.")
                                .assignedTo("sarah@company.com")
                                .deadline("2025-04-15")
                                .priority("HIGH")
                                .build(),
                        AiExtractionResult.ExtractedTask.builder()
                                .title("Set up cloud infrastructure on AWS")
                                .description("Provision EC2 instances, RDS database, and configure auto-scaling groups for the new production environment.")
                                .assignedTo("devops@company.com")
                                .deadline("2025-04-10")
                                .priority("CRITICAL")
                                .build(),
                        AiExtractionResult.ExtractedTask.builder()
                                .title("Write Q2 product requirements document")
                                .description("Document all feature requirements for Q2 including acceptance criteria, dependencies, and success metrics.")
                                .assignedTo("pm@company.com")
                                .deadline("2025-04-05")
                                .priority("HIGH")
                                .build(),
                        AiExtractionResult.ExtractedTask.builder()
                                .title("Conduct user interviews for feature validation")
                                .description("Schedule and conduct 10 user interviews to validate the proposed mobile features before development begins.")
                                .assignedTo("ux@company.com")
                                .deadline("2025-04-20")
                                .priority("MEDIUM")
                                .build(),
                        AiExtractionResult.ExtractedTask.builder()
                                .title("Update team on new sprint process")
                                .description("Send email to all team members explaining the new 2-week sprint cycle, standups at 9 AM, and retro process.")
                                .assignedTo("scrum@company.com")
                                .deadline("2025-04-01")
                                .priority("LOW")
                                .build()
                ))
                .build();
    }

    /** Very basic fallback if AI fails — creates a single catch-all task */
    private AiExtractionResult buildFallbackResponse(String transcript) {
        return AiExtractionResult.builder()
                .summary("AI extraction failed. Manual review of meeting transcript required.")
                .keyDecisions(Collections.emptyList())
                .tasks(List.of(
                        AiExtractionResult.ExtractedTask.builder()
                                .title("Review meeting transcript manually")
                                .description("AI extraction failed. Please review the transcript: "
                                        + transcript.substring(0, Math.min(200, transcript.length())) + "...")
                                .assignedTo("")
                                .deadline("")
                                .priority("MEDIUM")
                                .build()
                ))
                .build();
    }
}