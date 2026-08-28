package com.onlinejudge.authservice;

import com.onlinejudge.auth.repository.AuthRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "onlinejudge.auth.seed-data-enabled=false")
@AutoConfigureMockMvc
class AuthDependencyFailureTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthRepository authRepository;

    @Test
    void loginDependencyFailureDoesNotLeakConnectionDetails() throws Exception {
        when(authRepository.findUserByLoginIdentifier(anyString()))
                .thenThrow(new DataAccessResourceFailureException(
                        "jdbc:mysql://auth-db/onlinejudge_auth password=secret"
                ));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"account":"student01","password":"Student01@pass"}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("500"))
                .andExpect(jsonPath("$.message").value("系统错误，请联系管理员"))
                .andExpect(content().string(not(containsString("jdbc"))))
                .andExpect(content().string(not(containsString("auth-db"))))
                .andExpect(content().string(not(containsString("onlinejudge_auth"))))
                .andExpect(content().string(not(containsString("secret"))));
    }
}
