package com.onlinejudge.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ApiExceptionHandlerTest {
    @Test
    void multipartParserLimitUsesTheStableHomeworkPayloadTooLargeError() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new UploadFailureController())
                .setControllerAdvice(new ApiExceptionHandler())
                .build();

        mockMvc.perform(post("/api/v1/homeworks/42/attachments").contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("HWK_4131"))
                .andExpect(jsonPath("$.data").doesNotExist());

        mockMvc.perform(post("/api/v1/courses/42/resources").contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("413"))
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not("HWK_4131")));
    }

    @RestController
    private static final class UploadFailureController {
        @PostMapping({
                "/api/v1/homeworks/42/attachments",
                "/api/v1/courses/42/resources"
        })
        void upload() {
            throw new MaxUploadSizeExceededException(10 * 1024 * 1024L);
        }
    }
}
