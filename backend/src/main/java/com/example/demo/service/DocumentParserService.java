package com.example.demo.service;

import org.springframework.web.multipart.MultipartFile;

public interface DocumentParserService {
    String parseDocument(MultipartFile file);
}
