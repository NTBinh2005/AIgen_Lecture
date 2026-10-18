package com.example.demo.service.serviceImpl;

import com.example.demo.common.exception.BadRequestException;
import com.example.demo.dto.request.PresentationSlideRequest;
import com.example.demo.dto.response.PresentationSlideResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Chuyển qua lại giữa deck của slide-ai-service và slide của backend.
 * contentBlocks luôn là bản chữ phẳng của slide để trình soạn thảo và xuất PowerPoint vẫn dùng được.
 */
public final class SlideAiDeckMapper {
    public static final int MIN_RENDER_SLIDES = 3;
    public static final int MAX_RENDER_SLIDES = 30;
    private static final int MAX_BLOCKS = 50;
    private static final int MAX_BLOCK_CHARS = 4_000;
    /** Chỉ để chuyển JsonNode <-> Map (slide data lưu dạng Map để Jackson 3 ở tầng HTTP đọc/ghi được). */
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final Map<String, String> TEMPLATE_THEMES = Map.of(
            "standard", "modern-blue",
            "academic", "academic",
            "minimal", "fresh");

    private SlideAiDeckMapper() {
    }

    public static String themeFor(String templateId) {
        return templateId == null ? "modern-blue"
                : TEMPLATE_THEMES.getOrDefault(templateId.trim().toLowerCase(Locale.ROOT), "modern-blue");
    }

    public static List<PresentationSlideRequest> toSlideRequests(JsonNode deck) {
        List<PresentationSlideRequest> result = new ArrayList<>();
        int order = 0;
        for (JsonNode slide : deck.path("slides")) {
            String title = truncate(plain(slide.path("title").asText("")), 255);
            result.add(new PresentationSlideRequest(
                    title.isBlank() ? "Slide " + (order + 1) : title,
                    contentBlocks(slide),
                    truncate(slide.path("notes").asText(""), 20_000),
                    slide.path("layout").asText("BULLETS"),
                    order++,
                    JSON.convertValue(slide, MAP_TYPE)));
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("slide-ai-service did not return any slides");
        }
        return result;
    }

    /** Deck để dựng HTML: slide có data giữ nguyên bố cục, slide thường thành BULLETS/TITLE. */
    public static ObjectNode toDeck(String title, List<PresentationSlideResponse> slides, String theme) {
        if (slides == null || slides.size() < MIN_RENDER_SLIDES) {
            throw new BadRequestException("Cần ít nhất " + MIN_RENDER_SLIDES + " slide để trình chiếu");
        }
        if (slides.size() > MAX_RENDER_SLIDES) {
            throw new BadRequestException("Chỉ trình chiếu được tối đa " + MAX_RENDER_SLIDES + " slide");
        }
        JsonNodeFactory f = JsonNodeFactory.instance;
        ObjectNode deck = f.objectNode();
        deck.putObject("meta").put("title", title == null ? "" : title).put("theme", theme);
        ArrayNode out = deck.putArray("slides");
        for (PresentationSlideResponse slide : slides) {
            out.add(slide.data() != null ? JSON.valueToTree(slide.data()) : plainSlide(slide));
        }
        return deck;
    }

    private static ObjectNode plainSlide(PresentationSlideResponse slide) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        String title = truncate(slide.title(), 70);
        List<String> bullets = slide.contentBlocks() == null ? List.of() : slide.contentBlocks().stream()
                .filter(b -> b != null && !b.isBlank())
                .map(b -> truncate(b.trim(), 120))
                .limit(6)
                .toList();
        if (bullets.size() >= 2) {
            node.put("layout", "BULLETS").put("title", title);
            ArrayNode arr = node.putArray("bullets");
            bullets.forEach(arr::add);
        } else {
            node.put("layout", "TITLE").put("title", title)
                    .put("subtitle", bullets.isEmpty() ? "" : truncate(bullets.get(0), 140));
        }
        if (slide.speakerNotes() != null) {
            node.put("notes", slide.speakerNotes());
        }
        return node;
    }

    private static List<String> contentBlocks(JsonNode s) {
        List<String> blocks = new ArrayList<>();
        switch (s.path("layout").asText("")) {
            case "TITLE" -> {
                add(blocks, s.path("subtitle"));
                add(blocks, s.path("author"));
            }
            case "BULLETS" -> s.path("bullets").forEach(b -> add(blocks, b));
            case "COMPARE" -> {
                side(blocks, s.path("left"));
                side(blocks, s.path("right"));
                add(blocks, s.path("message"));
            }
            case "CARD_GRID" -> s.path("cards").forEach(c -> pair(blocks, c, "heading", "body"));
            case "QUOTE_CARDS" -> {
                JsonNode q = s.path("quote");
                if (!q.path("text").asText("").isBlank()) {
                    String author = q.path("author").asText("");
                    blocks.add("“" + q.path("text").asText() + "”" + (author.isBlank() ? "" : " — " + author));
                }
                add(blocks, q.path("note"));
                s.path("cards").forEach(c -> pair(blocks, c, "heading", "body"));
            }
            case "TEXT_VISUAL" -> {
                s.path("blocks").forEach(b -> pair(blocks, b, "heading", "body"));
                add(blocks, s.path("callout"));
                add(blocks, s.path("visual").path("caption"));
            }
            case "TABLE" -> {
                add(blocks, joined(s.path("columns"), " | "));
                s.path("rows").forEach(r -> add(blocks, joined(r, " | ")));
            }
            case "STAT_SPLIT" -> {
                s.path("stats").forEach(st -> {
                    String head = (st.path("value").asText("") + " " + st.path("label").asText("")).trim();
                    String body = st.path("body").asText("");
                    addText(blocks, body.isBlank() ? head : head + ": " + body);
                });
                JsonNode steps = s.path("steps");
                if (steps.isObject()) {
                    String head = steps.path("heading").asText("");
                    String items = joined(steps.path("items"), " → ");
                    addText(blocks, head.isBlank() ? items : items.isBlank() ? head : head + ": " + items);
                }
            }
            case "CLOSING" -> {
                add(blocks, s.path("subtitle"));
                add(blocks, s.path("contact"));
            }
            default -> {
            }
        }
        return blocks.stream().limit(MAX_BLOCKS).collect(Collectors.toList());
    }

    private static void side(List<String> blocks, JsonNode side) {
        add(blocks, side.path("heading"));
        side.path("items").forEach(i -> pair(blocks, i, "label", "text"));
        add(blocks, side.path("body"));
        pair(blocks, side.path("example"), "heading", "body");
        String steps = StreamSupport.stream(side.path("steps").spliterator(), false)
                .map(st -> st.path("label").asText(""))
                .filter(l -> !l.isBlank())
                .collect(Collectors.joining(" → "));
        addText(blocks, steps);
    }

    private static void pair(List<String> blocks, JsonNode node, String headKey, String bodyKey) {
        String head = node.path(headKey).asText("");
        String body = node.path(bodyKey).asText("");
        addText(blocks, head.isBlank() ? body : body.isBlank() ? head : head + ": " + body);
    }

    private static String joined(JsonNode array, String separator) {
        return StreamSupport.stream(array.spliterator(), false)
                .map(n -> n.asText(""))
                .collect(Collectors.joining(separator));
    }

    private static void add(List<String> blocks, JsonNode node) {
        if (node != null && node.isTextual()) {
            addText(blocks, node.asText());
        }
    }

    private static void add(List<String> blocks, String text) {
        addText(blocks, text);
    }

    private static void addText(List<String> blocks, String text) {
        String clean = plain(text).trim();
        if (!clean.isEmpty()) {
            blocks.add(truncate(clean, MAX_BLOCK_CHARS));
        }
    }

    /** Bỏ dấu **đậm** của slide-ai (PowerPoint và trình soạn thảo hiển thị chữ thường). */
    private static String plain(String text) {
        return text == null ? "" : text.replace("**", "");
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max - 1) + "…";
    }
}
