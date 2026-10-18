package com.example.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.demo.common.exception.BadRequestException;
import com.example.demo.dto.request.PresentationSlideRequest;
import com.example.demo.dto.response.PresentationSlideResponse;
import com.example.demo.service.serviceImpl.SlideAiDeckMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SlideAiDeckMapperTest {
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String DECK = """
            {"meta": {"title": "Nhập môn học máy", "theme": "academic"},
             "slides": [
              {"layout": "TITLE", "title": "Nhập môn **học máy**", "subtitle": "Chương 1", "author": "GV A"},
              {"layout": "COMPARE", "title": "Có giám sát và không giám sát", "notes": "So sánh hai cách học",
               "left": {"heading": "Có giám sát", "items": [{"label": "Dữ liệu", "text": "Có nhãn"}],
                        "steps": [{"label": "Thu thập"}, {"label": "Huấn luyện"}]},
               "right": {"heading": "Không giám sát", "body": "Tìm **cấu trúc** ẩn"},
               "message": "Chọn theo dữ liệu"},
              {"layout": "TABLE", "title": "Thuật toán", "columns": ["Tên", "Loại"],
               "rows": [["KNN", "Phân loại"], ["K-means", "Phân cụm"]]},
              {"layout": "STAT_SPLIT", "title": "Số liệu",
               "stats": [{"value": "90%", "label": "Độ chính xác", "body": ""}],
               "steps": {"heading": "", "items": ["Bước 1", "Bước 2"]}},
              {"layout": "CLOSING", "title": "Cảm ơn", "subtitle": "Hỏi đáp", "contact": ""}
             ]}
            """;

    @Test
    void mapsDeckToSlidesWithPlainTextAndOriginalData() throws Exception {
        List<PresentationSlideRequest> slides = SlideAiDeckMapper.toSlideRequests(mapper.readTree(DECK));

        assertThat(slides).hasSize(5);
        assertThat(slides).extracting(PresentationSlideRequest::order).containsExactly(0, 1, 2, 3, 4);
        assertThat(slides.get(0).title()).isEqualTo("Nhập môn học máy");
        assertThat(slides.get(0).contentBlocks()).containsExactly("Chương 1", "GV A");
        assertThat(slides.get(1).layout()).isEqualTo("COMPARE");
        assertThat(slides.get(1).speakerNotes()).isEqualTo("So sánh hai cách học");
        assertThat(slides.get(1).contentBlocks()).containsExactly(
                "Có giám sát", "Dữ liệu: Có nhãn", "Thu thập → Huấn luyện",
                "Không giám sát", "Tìm cấu trúc ẩn", "Chọn theo dữ liệu");
        assertThat(slides.get(2).contentBlocks()).containsExactly("Tên | Loại", "KNN | Phân loại", "K-means | Phân cụm");
        assertThat(slides.get(3).contentBlocks()).containsExactly("90% Độ chính xác", "Bước 1 → Bước 2");
        assertThat(slides.get(4).contentBlocks()).containsExactly("Hỏi đáp");
        // data giữ nguyên slide gốc (kể cả dấu ** để HTML in đậm)
        assertThat(slides.get(0).data()).containsEntry("title", "Nhập môn **học máy**");
        assertThat(slides).allSatisfy(s -> assertThat(s.contentBlocks()).allSatisfy(b -> assertThat(b).isNotBlank()));
    }

    @Test
    void buildsRenderDeckKeepingDataAndConvertingPlainSlides() throws Exception {
        JsonNode compare = mapper.readTree(DECK).path("slides").get(1);
        List<PresentationSlideResponse> slides = List.of(
                new PresentationSlideResponse("Mở đầu", List.of("Giới thiệu môn học"), null, "TITLE_AND_CONTENT", 0),
                new PresentationSlideResponse("So sánh", List.of("x"), null, "COMPARE", 1,
                        mapper.convertValue(compare, new TypeReference<Map<String, Object>>() {})),
                new PresentationSlideResponse("Ý chính", List.of("Một", "  ", "Hai"), "Ghi chú", "TITLE_AND_CONTENT", 2));

        JsonNode deck = SlideAiDeckMapper.toDeck("Bài 1", slides, "fresh");

        assertThat(deck.path("meta").path("theme").asText()).isEqualTo("fresh");
        JsonNode out = deck.path("slides");
        assertThat(out.get(0).path("layout").asText()).isEqualTo("TITLE");
        assertThat(out.get(0).path("subtitle").asText()).isEqualTo("Giới thiệu môn học");
        assertThat(out.get(1)).isEqualTo(compare);
        assertThat(out.get(2).path("layout").asText()).isEqualTo("BULLETS");
        assertThat(out.get(2).path("bullets")).hasSize(2);
        assertThat(out.get(2).path("notes").asText()).isEqualTo("Ghi chú");
    }

    @Test
    void rejectsDecksTooShortToRender() {
        List<PresentationSlideResponse> slides = List.of(
                new PresentationSlideResponse("A", List.of(), null, "TITLE_AND_CONTENT", 0));
        assertThatThrownBy(() -> SlideAiDeckMapper.toDeck("x", slides, "modern-blue"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void mapsTemplatesToThemes() {
        assertThat(SlideAiDeckMapper.themeFor("standard")).isEqualTo("modern-blue");
        assertThat(SlideAiDeckMapper.themeFor("Academic")).isEqualTo("academic");
        assertThat(SlideAiDeckMapper.themeFor("minimal")).isEqualTo("fresh");
        assertThat(SlideAiDeckMapper.themeFor("unknown")).isEqualTo("modern-blue");
    }

    @Test
    void storedSlidesJsonStaysCompatible() throws Exception {
        // slides_json cũ (không có data) vẫn đọc được, và slide không có data không ghi thêm trường.
        String legacy = "[{\"title\":\"A\",\"contentBlocks\":[\"x\"],\"speakerNotes\":null,"
                + "\"layout\":\"TITLE_AND_CONTENT\",\"order\":0}]";
        List<PresentationSlideResponse> read = mapper.readValue(legacy, new TypeReference<>() {});
        assertThat(read.get(0).data()).isNull();
        assertThat(mapper.writeValueAsString(read)).doesNotContain("\"data\"");

        PresentationSlideResponse withData = new PresentationSlideResponse(
                "B", List.of(), null, "TABLE", 1, Map.of("layout", "TABLE"));
        PresentationSlideResponse back = mapper.readValue(
                mapper.writeValueAsString(withData), PresentationSlideResponse.class);
        assertThat(back.data()).containsEntry("layout", "TABLE");
    }

    @Test
    void httpJacksonThreeReadsAndWritesSlideData() {
        // Spring Boot 4 dùng Jackson 3 (tools.jackson) cho request/response HTTP.
        tools.jackson.databind.json.JsonMapper http = tools.jackson.databind.json.JsonMapper.builder().build();
        String body = "{\"title\":\"A\",\"contentBlocks\":[],\"speakerNotes\":null,\"layout\":\"TABLE\","
                + "\"order\":0,\"data\":{\"layout\":\"TABLE\",\"columns\":[\"x\",\"y\"]}}";
        PresentationSlideRequest request = http.readValue(body, PresentationSlideRequest.class);
        assertThat(request.data()).containsEntry("layout", "TABLE");

        String json = http.writeValueAsString(new PresentationSlideResponse(
                "A", List.of(), null, "TABLE", 0, request.data()));
        assertThat(json).contains("\"data\":{\"layout\":\"TABLE\",\"columns\":[\"x\",\"y\"]}");
        assertThat(http.writeValueAsString(new PresentationSlideResponse("A", List.of(), null, "X", 0)))
                .doesNotContain("\"data\"");
    }
}
