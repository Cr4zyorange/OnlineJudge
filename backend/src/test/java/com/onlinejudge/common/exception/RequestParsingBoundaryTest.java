package com.onlinejudge.common.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:request_parsing_boundary;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"
})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@Import(RequestParsingBoundaryTest.UnexpectedFailureConfig.class)
class RequestParsingBoundaryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @TestConfiguration(proxyBeanMethods = false)
    static class UnexpectedFailureConfig {
        @Bean
        UnexpectedFailureController unexpectedFailureController() {
            return new UnexpectedFailureController();
        }
    }

    @RestController
    static class UnexpectedFailureController {
        @PostMapping("/api/v1/internal-test/failure")
        void fail() {
            throw new IllegalStateException("intentional-test-explosion");
        }
    }

    @Test
    void crsMultipartInvalidVisibilityReturnsBadRequestPointingAtField() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "lesson.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "pdf".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/courses/101/resources")
                        .file(file)
                        .param("resourceType", "DOCUMENT")
                        .param("visibility", "BOGUS")
                        .header("X-User-Id", "501")
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CRS_400"))
                .andExpect(jsonPath("$.message", containsString("visibility")));
    }

    @Test
    void crsMultipartInvalidResourceTypeReturnsBadRequestPointingAtField() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "lesson.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "pdf".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/courses/101/resources")
                        .file(file)
                        .param("resourceType", "BOGUS")
                        .param("visibility", "STUDENT")
                        .header("X-User-Id", "501")
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CRS_400"))
                .andExpect(jsonPath("$.message", containsString("resourceType")));
    }

    @Test
    void grdQueryInvalidGradeStatusReturnsBadRequestPointingAtField() throws Exception {
        mockMvc.perform(get("/api/v1/courses/101/grades")
                        .param("gradeStatus", "BOGUS")
                        .headers(teacherHeaders()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.message", containsString("gradeStatus")));
    }

    @Test
    void grdQueryInvalidPublishStatusReturnsBadRequestPointingAtField() throws Exception {
        mockMvc.perform(get("/api/v1/courses/101/grades")
                        .param("publishStatus", "BOGUS")
                        .headers(teacherHeaders()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.message", containsString("publishStatus")));
    }

    @Test
    void crsJsonInvalidResourceTypeReturnsBadRequestWithoutAuthErrorCode() throws Exception {
        mockMvc.perform(put("/api/v1/courses/101/resources/1")
                        .headers(teacherHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "lesson",
                                  "resourceType": "BOGUS",
                                  "visibility": "STUDENT"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CRS_400"))
                .andExpect(jsonPath("$.code").value(not("AUTH_400")))
                .andExpect(jsonPath("$.message", containsString("resourceType")));
    }

    @Test
    void grdJsonInvalidSourceTypeReturnsBadRequestWithoutAuthErrorCode() throws Exception {
        mockMvc.perform(post("/api/v1/courses/101/grade-items")
                        .headers(teacherHeaders())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "grade item",
                                  "sourceType": "BOGUS",
                                  "sourceId": 301,
                                  "fullScore": "100.00",
                                  "weight": "0.50",
                                  "includedInFinal": true,
                                  "sortOrder": 1
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.code").value(not("AUTH_400")))
                .andExpect(jsonPath("$.message", containsString("sourceType")));
    }

    @Test
    void joinWithoutBodyKeepsOptionalRequestBodySemantics() throws Exception {
        String courseId = createCourse("join-no-body-" + System.nanoTime());

        mockMvc.perform(post("/api/v1/courses/" + courseId + "/join")
                        .header("X-User-Id", "991")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk());
    }

    @Test
    void joinWithEmptyJsonBodyKeepsOptionalRequestBodySemantics() throws Exception {
        String courseId = createCourse("join-empty-json-" + System.nanoTime());

        mockMvc.perform(post("/api/v1/courses/" + courseId + "/join")
                        .header("X-User-Id", "992")
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isOk());
    }

    @Test
    void joinWithFormUrlEncodedContentTypeReturnsUnsupportedMediaTypeInsteadOfServerError() throws Exception {
        mockMvc.perform(post("/api/v1/courses/101/join")
                        .header("X-User-Id", "991")
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("inviteCode=abc"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("415"))
                .andExpect(jsonPath("$.message").value("不支持的媒体类型"));
    }

    @Test
    void joinWithTextPlainContentTypeReturnsUnsupportedMediaTypeInsteadOfServerError() throws Exception {
        mockMvc.perform(post("/api/v1/courses/101/join")
                        .header("X-User-Id", "991")
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("plain text"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("415"))
                .andExpect(jsonPath("$.message").value("不支持的媒体类型"));
    }

    @Test
    void unexpectedServerErrorLogsMethodUriAndStackTraceWithoutSensitiveData(CapturedOutput output) throws Exception {
        String secretHeader = "secret-token-value-9981";
        String secretBody = "secret-password-value-9981";

        mockMvc.perform(post("/api/v1/internal-test/failure")
                        .header("X-User-Id", "501")
                        .header("X-User-Role", "TEACHER")
                        .header("X-Secret-Token", secretHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"" + secretBody + "\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("500"))
                .andExpect(jsonPath("$.message").value("系统错误，请联系管理员"));

        assertThat(output.getAll())
                .contains("Unhandled exception for POST /api/v1/internal-test/failure")
                .contains("IllegalStateException")
                .contains("intentional-test-explosion");
        assertThat(output.getAll())
                .doesNotContain(secretHeader)
                .doesNotContain(secretBody);
    }

    private String createCourse(String name) throws Exception {
        String body = mockMvc.perform(post("/api/v1/courses")
                        .header("X-User-Id", "501")
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asText();
    }

    private HttpHeaders teacherHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-User-Id", "501");
        headers.add("X-User-Role", "TEACHER");
        headers.add("X-Manageable-Course-Ids", "101");
        return headers;
    }
}
