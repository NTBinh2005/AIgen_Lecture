package com.example.demo.service.serviceImpl;

import com.example.demo.dto.response.LectureGenerateResponse;
import com.example.demo.service.LlmService;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
public class LlmServiceImpl implements LlmService {
    private static final int DEFAULT_QUESTION_COUNT = 5;
    private static final int MAX_QUESTION_COUNT = 20;
    /**
     * Base retry back-off in milliseconds. Gemini trả 429/500/503 khi quá tải; ta thử lại
     * với thời gian chờ tăng dần (3s, 6s, 12s) trước khi để job FAILED.
     */
    private static final long[] RETRY_BACKOFF_MS = {3_000L, 6_000L, 12_000L};

    @Value("${gemini.api.url}")
    private String geminiApiUrl;

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public LlmServiceImpl() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(120000); // Tăng timeout lên 120s cho Gemini
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public LectureGenerateResponse generateLectureScript(String documentText) {
        return generateLectureScript(documentText, DEFAULT_QUESTION_COUNT);
    }

    @Override
    public LectureGenerateResponse generateLectureScript(String documentText, int questionCount) {
        String apiKey = geminiApiKey;
        if (!StringUtils.hasText(documentText)) {
            throw new IllegalArgumentException("Document text is required");
        }
        if (questionCount < 1 || questionCount > MAX_QUESTION_COUNT) {
            throw new IllegalArgumentException("Question count must be between 1 and " + MAX_QUESTION_COUNT);
        }

        if (!StringUtils.hasText(apiKey)) {
            log.warn("Gemini API key is not configured. Falling back to document text parser.");
            return generateFallbackResponse(documentText, questionCount);
        }

        String prompt = "You are an expert university instructional designer and an engaging teacher, not a slide summarizer. "
                + "Transform the source into a coherent mini lesson plan for a friendly animated 3D female teacher. "
                + "Create 5 to 8 teaching scenes and exactly " + questionCount
                + " multiple-choice quiz questions. Preserve the source language and factual meaning. "
                + "The scene sequence must follow: HOOK (welcome and motivating question), OBJECTIVE (what learners will achieve), "
                + "one or more EXPLAIN scenes (teach concepts step by step), an EXAMPLE scene (worked example/application), "
                + "a CHECK scene (ask learners to think, include a short pause cue in natural speech), and SUMMARY. "
                + "Use 2 to 4 concise board notes in bulletPoints. narrationText must be 18 to 32 words so each scene fits a short animated clip, conversational, "
                + "use transitions, explain why/how, address learners directly, and must NOT merely read bulletPoints verbatim. "
                + "Do not put bracketed stage directions in narrationText. Put physical delivery in teacherAction only. "
                + "lessonPhase must be one of HOOK, OBJECTIVE, EXPLAIN, EXAMPLE, CHECK, SUMMARY. "
                + "teacherAction must be one of WELCOME, EXPLAIN, POINT, EMPHASIZE, QUESTION, SUMMARIZE and should vary naturally. "
                + "teachingGoal is one measurable learner outcome. interactionPrompt is required for CHECK and empty otherwise. "
                + "For imagePrompt, describe a colorful 3D educational visual supporting the explanation, 16:9, no text or watermark. "
                + "The quizzes array must contain exactly " + questionCount
                + " items. Return strict JSON without markdown using this schema: "
                + "{\"slides\":[{\"title\":\"Scene title\",\"lessonPhase\":\"EXPLAIN\","
                + "\"teachingGoal\":\"Learner outcome\",\"teacherAction\":\"POINT\","
                + "\"interactionPrompt\":\"\",\"bulletPoints\":[\"Board note 1\",\"Board note 2\"],"
                + "\"narrationText\":\"Natural teacher explanation\",\"imagePrompt\":\"Educational visual description\"}], "
                + "\"quizzes\":[{\"questionText\":\"Question text?\",\"options\":[\"A. Option 1\",\"B. Option 2\",\"C. Option 3\",\"D. Option 4\"],\"correctAnswer\":\"A\"}]}. "
                + "Source:\n" + documentText;

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
        requestBody.put("generationConfig", Map.of("responseMimeType", "application/json"));

        try {
            String response = postToGemini(requestBody);
            JsonNode root = objectMapper.readTree(response);
            JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
            if (!parts.isArray() || parts.isEmpty()) {
                throw new IllegalStateException("AI provider returned no generated content");
            }
            String aiText = parts.get(0).path("text").asText();
            aiText = aiText.replaceFirst("(?s)^```(?:json)?\\s*", "")
                    .replaceFirst("(?s)```\\s*$", "")
                    .trim();
            LectureGenerateResponse result = objectMapper.readValue(aiText, LectureGenerateResponse.class);
            if (result.getSlides() == null || result.getSlides().isEmpty()) {
                throw new IllegalStateException("AI provider returned no slides");
            }
            normalizeLessonPlan(result.getSlides());
            ensureQuestionCount(result, documentText, questionCount);
            return result;
        } catch (Exception exception) {
            log.warn("Gemini AI generation failed ({}), generating fallback slides from document text.", exception.getMessage());
            return generateFallbackResponse(documentText, questionCount);
        }
    }

    private LectureGenerateResponse generateFallbackResponse(String documentText, int questionCount) {
        LectureGenerateResponse response = new LectureGenerateResponse();
        List<LectureGenerateResponse.SlideDto> slides = new ArrayList<>();

        List<String> paragraphs = extractParagraphs(documentText);
        if (paragraphs.isEmpty()) paragraphs = List.of("Nội dung bài giảng từ tài liệu đã tải lên");
        boolean vietnamese = documentText.matches("(?s).*[À-ỹĐđ].*");
        String topic = shorten(paragraphs.get(0), 72);
        List<String> keyTopics = paragraphs.stream().limit(3).map(p -> shorten(p, 120)).toList();

        slides.add(createTeachingSlide(
                vietnamese ? "Khởi động: " + topic : "Lesson opener: " + topic,
                List.of(topic, vietnamese ? "Kết nối kiến thức với thực tế" : "Connect the topic to real life"),
                vietnamese
                        ? "Chào các em. Trước khi bắt đầu, hãy thử nghĩ xem nội dung này xuất hiện ở đâu trong học tập hoặc đời sống. Hôm nay cô sẽ không chỉ nêu lại tài liệu, mà sẽ cùng các em bóc tách ý tưởng, xem cách nó vận hành và vì sao nó đáng chú ý."
                        : "Welcome. Before we begin, think about where this topic appears in study or real life. Today we will not simply repeat the document; we will unpack the idea, see how it works, and understand why it matters.",
                "HOOK", vietnamese ? "Khơi gợi sự quan tâm đến chủ đề" : "Build curiosity about the topic",
                "WELCOME", "", topic));

        slides.add(createTeachingSlide(
                vietnamese ? "Mục tiêu bài học" : "Learning objectives",
                keyTopics,
                vietnamese
                        ? "Sau bài học này, các em cần nhận diện được những khái niệm trọng tâm, giải thích được mối liên hệ giữa chúng và biết cách vận dụng vào một tình huống cụ thể. Hãy dùng các mục tiêu này như bản đồ để theo dõi tiến trình học của mình."
                        : "After this lesson, you should be able to identify the central concepts, explain how they connect, and apply them to a concrete situation. Use these objectives as a map for your learning progress.",
                "OBJECTIVE", vietnamese ? "Xác định kết quả học tập cần đạt" : "Identify the expected learning outcomes",
                "POINT", "", topic));

        int contentSceneCount = Math.max(2, Math.min(paragraphs.size(), 4));
        for (int i = 0; i < contentSceneCount; i++) {
            String paragraph = paragraphs.get(i % paragraphs.size());
            boolean exampleScene = i == contentSceneCount - 1;
            String title = shorten(paragraph, 64);
            String narration = vietnamese
                    ? (exampleScene
                            ? "Bây giờ chúng ta thử đặt kiến thức vào một tình huống cụ thể. " + paragraph
                                    + " Hãy chú ý cách xác định dữ kiện, chọn nguyên lý phù hợp rồi kiểm tra kết quả; đó là quy trình các em có thể lặp lại với bài toán tương tự."
                            : "Trước hết, chúng ta phân tích ý này theo từng bước. " + paragraph
                                    + " Điều quan trọng không phải là học thuộc câu chữ, mà là hiểu nguyên nhân, mối liên hệ và điều kiện để ý tưởng này được áp dụng đúng.")
                    : (exampleScene
                            ? "Now let us apply the idea to a concrete situation. " + paragraph
                                    + " Notice how we identify the facts, choose the relevant principle, and check the result; this is a process you can reuse."
                            : "Let us unpack this idea step by step. " + paragraph
                                    + " The goal is not to memorize the wording, but to understand the cause, the connections, and the conditions for correct use.");
            slides.add(createTeachingSlide(
                    (exampleScene ? (vietnamese ? "Ví dụ vận dụng: " : "Worked example: ") : "") + title,
                    List.of(shorten(paragraph, 180), vietnamese ? "Ý nghĩa và cách vận dụng" : "Meaning and application"),
                    narration, exampleScene ? "EXAMPLE" : "EXPLAIN",
                    vietnamese ? "Giải thích và vận dụng " + title : "Explain and apply " + title,
                    exampleScene ? "POINT" : (i % 2 == 0 ? "EXPLAIN" : "EMPHASIZE"), "", title));
        }

        String checkPrompt = vietnamese
                ? "Nếu phải giải thích ý chính bằng một câu, em sẽ nói gì?"
                : "If you had to explain the main idea in one sentence, what would you say?";
        slides.add(createTeachingSlide(
                vietnamese ? "Dừng lại và suy nghĩ" : "Pause and think",
                List.of(checkPrompt, vietnamese ? "Nêu một ví dụ của riêng em" : "Give one example of your own"),
                vietnamese
                        ? "Trước khi kết thúc, cô muốn các em tự kiểm tra mức độ hiểu bài. Nếu phải giải thích ý chính bằng một câu, em sẽ nói gì? Hãy dừng lại vài giây, tự trả lời, rồi thử nêu thêm một ví dụ của riêng mình."
                        : "Before we finish, check your understanding. If you had to explain the main idea in one sentence, what would you say? Pause for a few seconds, answer it yourself, and then create one example of your own.",
                "CHECK", vietnamese ? "Tự kiểm tra khả năng diễn đạt và vận dụng" : "Self-check explanation and application",
                "QUESTION", checkPrompt, topic));

        slides.add(createTeachingSlide(
                vietnamese ? "Tổng kết bài học" : "Lesson summary",
                keyTopics,
                vietnamese
                        ? "Chúng ta vừa đi từ câu hỏi mở đầu đến các khái niệm cốt lõi, cách giải thích và một tình huống vận dụng. Các em hãy nhớ ba việc: hiểu bản chất, nhận ra mối liên hệ và kiểm tra kiến thức bằng ví dụ. Đó là nền tảng để tiếp tục học sâu hơn."
                        : "We moved from an opening question through the core concepts, their explanation, and an application. Remember three things: understand the mechanism, recognize the connections, and test your knowledge with an example. That is the foundation for deeper learning.",
                "SUMMARY", vietnamese ? "Củng cố các ý chính của bài học" : "Consolidate the lesson's key ideas",
                "SUMMARIZE", "", topic));
        List<LectureGenerateResponse.QuizDto> quizzes = new ArrayList<>();
        for (int i = 0; i < questionCount; i++) {
            quizzes.add(createFallbackQuiz(paragraphs, i));
        }
        response.setSlides(slides);
        response.setQuizzes(quizzes);
        return response;
    }

    private LectureGenerateResponse.SlideDto createTeachingSlide(
            String title, List<String> bulletPoints, String narration, String lessonPhase,
            String teachingGoal, String teacherAction, String interactionPrompt, String visualTopic) {
        LectureGenerateResponse.SlideDto slide = new LectureGenerateResponse.SlideDto();
        slide.setTitle(title);
        slide.setBulletPoints(bulletPoints);
        slide.setNarrationText(narration);
        slide.setLessonPhase(lessonPhase);
        slide.setTeachingGoal(teachingGoal);
        slide.setTeacherAction(teacherAction);
        slide.setInteractionPrompt(interactionPrompt);
        slide.setImagePrompt("Colorful 3D educational illustration about " + visualTopic
                + ", clear visual metaphor, simple classroom composition, no text, 16:9");
        return slide;
    }

    private String shorten(String value, int maxLength) {
        String normalized = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength
                ? normalized
                : normalized.substring(0, Math.max(1, maxLength - 3)) + "...";
    }

    private void normalizeLessonPlan(List<LectureGenerateResponse.SlideDto> slides) {
        for (int i = 0; i < slides.size(); i++) {
            LectureGenerateResponse.SlideDto slide = slides.get(i);
            String defaultPhase;
            if (i == 0) defaultPhase = "HOOK";
            else if (i == 1) defaultPhase = "OBJECTIVE";
            else if (i == slides.size() - 1) defaultPhase = "SUMMARY";
            else if (i == slides.size() - 2) defaultPhase = "CHECK";
            else if (i == slides.size() - 3) defaultPhase = "EXAMPLE";
            else defaultPhase = "EXPLAIN";

            if (!StringUtils.hasText(slide.getLessonPhase())) slide.setLessonPhase(defaultPhase);
            if (!StringUtils.hasText(slide.getTeachingGoal())) slide.setTeachingGoal(slide.getTitle());
            if (!StringUtils.hasText(slide.getTeacherAction())) {
                slide.setTeacherAction(switch (slide.getLessonPhase().toUpperCase()) {
                    case "HOOK" -> "WELCOME";
                    case "OBJECTIVE", "EXAMPLE" -> "POINT";
                    case "CHECK" -> "QUESTION";
                    case "SUMMARY" -> "SUMMARIZE";
                    default -> "EXPLAIN";
                });
            }
            if (slide.getInteractionPrompt() == null) slide.setInteractionPrompt("");
        }
    }

    private void ensureQuestionCount(
            LectureGenerateResponse response,
            String documentText,
            int questionCount) {
        List<LectureGenerateResponse.QuizDto> quizzes = response.getQuizzes() == null
                ? new ArrayList<>()
                : new ArrayList<>(response.getQuizzes());
        if (quizzes.size() > questionCount) {
            quizzes = new ArrayList<>(quizzes.subList(0, questionCount));
        }

        List<String> paragraphs = extractParagraphs(documentText);
        while (quizzes.size() < questionCount) {
            quizzes.add(createFallbackQuiz(paragraphs, quizzes.size()));
        }
        response.setQuizzes(quizzes);
    }

    private List<String> extractParagraphs(String documentText) {
        return documentText.lines()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private LectureGenerateResponse.QuizDto createFallbackQuiz(List<String> paragraphs, int index) {
        String paragraph = paragraphs.isEmpty()
                ? "Nội dung bài giảng từ tài liệu được upload"
                : paragraphs.get(index % paragraphs.size());
        String topic = paragraph.length() > 60 ? paragraph.substring(0, 57) + "..." : paragraph;
        String answer = paragraph.length() > 70 ? paragraph.substring(0, 67) + "..." : paragraph;

        LectureGenerateResponse.QuizDto quiz = new LectureGenerateResponse.QuizDto();
        quiz.setQuestionText("Câu " + (index + 1) + ": Nội dung trọng tâm của phần \"" + topic + "\" là gì?");
        quiz.setOptions(List.of(
                "A. " + answer,
                "B. Khái niệm không xuất hiện trong tài liệu",
                "C. Số liệu không liên quan đến bài giảng",
                "D. Nội dung chưa được xác định"
        ));
        quiz.setCorrectAnswer("A");
        return quiz;
    }

    @Override
    public String generateQuizDraft(String documentText, String configPrompt) {
        if (geminiApiKey == null || geminiApiKey.isEmpty()) {
            throw new RuntimeException("Gemini API key is not configured.");
        }

        String prompt = "Bạn là trợ lý giáo dục. Dựa vào nội dung tài liệu sau, hãy tạo ra danh sách các câu hỏi trắc nghiệm.\n"
                + (configPrompt != null ? configPrompt : "Tạo 5 câu hỏi MCQ_SINGLE.") + "\n\n"
                + "Quy tắc CỰC KỲ QUAN TRỌNG: Trả về JSON chuẩn xác là một mảng (Array). KHÔNG trả về object chứa mảng. KHÔNG kèm markdown (không ```json).\n"
                + "Format bắt buộc:\n"
                + "[\n"
                + "  {\n"
                + "    \"questionType\": \"MCQ_SINGLE\",\n"
                + "    \"questionText\": \"Câu hỏi?\",\n"
                + "    \"options\": [\"A. ...\", \"B. ...\", \"C. ...\", \"D. ...\"],\n"
                + "    \"correctAnswer\": \"A\",\n"
                + "    \"points\": 1,\n"
                + "    \"explanation\": \"Giải thích vì sao đúng...\"\n"
                + "  }\n"
                + "]\n\n"
                + "Nội dung tài liệu:\n" + documentText;

        return callGeminiApi(prompt);
    }

    @Override
    public Double aiSuggestScore(String questionText, String studentAnswer, String rubric) {
        if (geminiApiKey == null || geminiApiKey.isEmpty()) {
            return null; // Silent degrade if not configured
        }

        String prompt = "Bạn là người chấm điểm tự luận.\n"
                + "Câu hỏi: " + questionText + "\n"
                + "Hướng dẫn chấm (Rubric): " + (rubric != null ? rubric : "Không có") + "\n"
                + "Bài làm của học sinh (đã loại bỏ thông tin cá nhân): " + studentAnswer + "\n\n"
                + "YÊU CẦU: Dựa trên câu trả lời, hãy đề xuất 1 điểm số. Chỉ trả về một con số thập phân, ví dụ 8.5, tuyệt đối không trả về chữ nào khác.";

        try {
            String responseText = callGeminiApi(prompt);
            return Double.parseDouble(responseText.trim());
        } catch (Exception e) {
            return null; // AI suggestion is not critical
        }
    }

    private String callGeminiApi(String prompt) {
        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> parts = new HashMap<>();
        parts.put("text", prompt);

        Map<String, Object> contents = new HashMap<>();
        contents.put("parts", List.of(parts));
        requestBody.put("contents", List.of(contents));

        Map<String, Object> generationConfig = new HashMap<>();
        generationConfig.put("responseMimeType", "application/json");
        requestBody.put("generationConfig", generationConfig);

        try {
            String response = postToGemini(requestBody);
            JsonNode rootNode = objectMapper.readTree(response);

            JsonNode candidates = rootNode.path("candidates");
            if (candidates.isArray() && candidates.size() > 0) {
                String aiText = candidates.get(0).path("content").path("parts").get(0).path("text").asText();
                return aiText.replaceAll("(?s)^```json\\s*", "").replaceAll("(?s)^```\\s*", "").replaceAll("```$", "").trim();
            } else {
                throw new RuntimeException("Invalid response from Gemini API.");
            }
        } catch (Exception e) {
            throw new RuntimeException("AI processing failed: " + e.getMessage(), e);
        }
    }

    /**
     * Gọi Gemini với API key trong header {@code x-goog-api-key} (không để trong URL để
     * tránh lộ key trong log/thông báo lỗi), và thử lại với back-off khi gặp 429/500/503
     * hoặc timeout tạm thời.
     */
    private String postToGemini(Map<String, Object> requestBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", geminiApiKey);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        int attempt = 0;
        while (true) {
            try {
                return restTemplate.postForObject(geminiApiUrl, entity, String.class);
            } catch (HttpStatusCodeException exception) {
                int status = exception.getStatusCode().value();
                boolean retryable = status == 429 || status == 500 || status == 503;
                if (!retryable || attempt >= RETRY_BACKOFF_MS.length) {
                    throw exception;
                }
                log.warn("Gemini trả {} (lần {}/{}), thử lại sau {}ms",
                        status, attempt + 1, RETRY_BACKOFF_MS.length, RETRY_BACKOFF_MS[attempt]);
                sleep(RETRY_BACKOFF_MS[attempt]);
                attempt++;
            } catch (ResourceAccessException exception) {
                if (attempt >= RETRY_BACKOFF_MS.length) {
                    throw exception;
                }
                log.warn("Gemini timeout/không kết nối được (lần {}/{}), thử lại sau {}ms",
                        attempt + 1, RETRY_BACKOFF_MS.length, RETRY_BACKOFF_MS[attempt]);
                sleep(RETRY_BACKOFF_MS[attempt]);
                attempt++;
            }
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("AI retry interrupted", interrupted);
        }
    }
}
