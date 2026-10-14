package com.example.demo.dto.request;

import lombok.Data;

@Data
public class AttemptAnswerSubmitRequest {
    /** Set by the controller from the {questionId} path variable — never validated in body. */
    private Long questionId;

    private String response; // null or empty for un-answering

    /** 0 for a brand-new answer; must match current version for updates (optimistic lock). */
    private Long answerVersion;
}
