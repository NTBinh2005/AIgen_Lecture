package com.example.demo.backend3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.demo.entity.Lecture;
import com.example.demo.entity.VideoStatus;
import com.example.demo.repository.LectureRepository;
import com.example.demo.service.serviceImpl.VideoRenderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class VideoRenderServiceTest {

    @Test
    void requestsRenderThenPersistsCompletedVideoUrl() {
        LectureRepository repository = mock(LectureRepository.class);
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        VideoRenderService service = new VideoRenderService(
                repository, restTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(service, "videoEnabled", true);
        ReflectionTestUtils.setField(service, "videoServiceUrl", "http://video-service:3001");

        Lecture lecture = new Lecture();
        lecture.setLectureId(7L);
        lecture.setVideoStatus(VideoStatus.PENDING);
        when(repository.findById(7L)).thenReturn(Optional.of(lecture));
        when(repository.save(any(Lecture.class))).thenAnswer(invocation -> invocation.getArgument(0));

        server.expect(requestTo("http://video-service:3001/generate-video"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(
                        "{\"jobId\":\"job-7\",\"status\":\"pending\"}",
                        MediaType.APPLICATION_JSON));

        service.requestRender(7L, """
                [{"title":"Intro","bulletPoints":["One"],"narrationText":"Hello"}]
                """);

        assertThat(lecture.getVideoJobId()).isEqualTo("job-7");
        assertThat(lecture.getVideoStatus()).isEqualTo(VideoStatus.PROCESSING);
        server.verify();
        server.reset();

        when(repository.findByVideoStatusIn(List.of(VideoStatus.PROCESSING)))
                .thenReturn(List.of(lecture));
        server.expect(requestTo("http://video-service:3001/video-status/job-7"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"jobId\":\"job-7\",\"lectureId\":\"7\","
                                + "\"status\":\"done\","
                                + "\"videoUrl\":\"https://cdn.example/video.mp4\"}",
                        MediaType.APPLICATION_JSON));

        service.pollActiveRenders();

        assertThat(lecture.getVideoStatus()).isEqualTo(VideoStatus.DONE);
        assertThat(lecture.getVideoUrl()).isEqualTo("https://cdn.example/video.mp4");
        assertThat(lecture.getVideoErrorMessage()).isNull();
        server.verify();
    }
}
