package com.example.demo.service;

import com.example.demo.dto.response.LectureGenerateResponse;

public interface LlmService {
    LectureGenerateResponse generateLectureScript(String documentText);
}
