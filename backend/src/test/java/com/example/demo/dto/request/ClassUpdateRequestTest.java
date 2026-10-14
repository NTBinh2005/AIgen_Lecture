package com.example.demo.dto.request;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ClassUpdateRequestTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void distinguishesOmittedDescriptionFromExplicitNull() throws Exception {
        ClassUpdateRequest omitted = objectMapper.readValue(
                "{\"className\":\"Updated\"}", ClassUpdateRequest.class);
        ClassUpdateRequest cleared = objectMapper.readValue(
                "{\"description\":null}", ClassUpdateRequest.class);

        assertThat(omitted.descriptionSpecified()).isFalse();
        assertThat(cleared.descriptionSpecified()).isTrue();
        assertThat(cleared.description()).isNull();
    }
}
