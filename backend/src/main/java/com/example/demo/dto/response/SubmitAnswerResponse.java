package com.example.demo.dto.response;

import lombok.Data;

/**
 * Response trả về sau khi Student nộp câu trả lời.
 * Backend tự tính isCorrect — không tin client.
 */
@Data
public class SubmitAnswerResponse {

    private Long logId;
    private Long elementId;
    private String submittedAnswer;
    private Boolean isCorrect;
    /** Đáp án đúng — chỉ tiết lộ SAU KHI student đã nộp bài */
    private String correctAnswer;
    private Boolean isFirstAttempt;

    public static SubmitAnswerResponse of(
            Long logId, Long elementId, String submittedAnswer,
            Boolean isCorrect, String correctAnswer, Boolean isFirstAttempt) {
        SubmitAnswerResponse r = new SubmitAnswerResponse();
        r.setLogId(logId);
        r.setElementId(elementId);
        r.setSubmittedAnswer(submittedAnswer);
        r.setIsCorrect(isCorrect);
        r.setCorrectAnswer(correctAnswer);
        r.setIsFirstAttempt(isFirstAttempt);
        return r;
    }
}
