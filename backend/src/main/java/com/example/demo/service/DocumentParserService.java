package com.example.demo.service;

import org.springframework.web.multipart.MultipartFile;

public interface DocumentParserService {
    String parseDocument(MultipartFile file);

    /** Parses an already validated Asset snapshot in an asynchronous worker. */
    String parseDocument(String originalFilename, byte[] content);
}
