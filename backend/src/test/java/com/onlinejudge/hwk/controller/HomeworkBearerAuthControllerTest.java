package com.onlinejudge.hwk.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.auth.repository.AuthRepository;
import com.onlinejudge.auth.service.SessionTokenService;
import com.onlinejudge.crs.domain.CourseMemberRole;
import com.onlinejudge.crs.domain.CourseMemberStatus;
import com.onlinejudge.crs.domain.CourseStatus;
import com.onlinejudge.crs.domain.EnrollmentMode;
import com.onlinejudge.crs.domain.dto.CourseCreateRequest;
import com.onlinejudge.crs.mapper.CourseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static java.util.Map.entry;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:homework_bearer_auth;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "onlinejudge.auth.allow-header-auth=false"
})
@AutoConfigureMockMvc
@Sql(
        statements = {
                "DELETE FROM t_hwk_test_case",
                "DELETE FROM t_hwk_question",
                "DELETE FROM t_hwk_judge_config",
                "DELETE FROM t_hwk_homework",
                "DELETE FROM crs_course_member",
                "DELETE FROM crs_course",
                "DELETE FROM t_auth_audit_log",
                "DELETE FROM t_auth_session",
                "DELETE FROM t_auth_user_role",
                "DELETE FROM t_auth_role_permission",
                "DELETE FROM t_auth_permission",
                "DELETE FROM t_auth_role",
                "DELETE FROM t_auth_user"
        },
        executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD
)
class HomeworkBearerAuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthRepository authRepository;

    @Autowired
    private SessionTokenService sessionTokenService;

    @Autowired
    private CourseRepository courseRepository;

    @BeforeEach
    void seedAuthRoles() {
        authRepository.ensureBaseRolesAndPermissions();
    }

    @Test
    void teacherCreatesHomeworkWithBearerSessionAndCrsCourseMembership() throws Exception {
        long teacherId = authRepository.createUser(
                "teacher-hwk-bearer",
                "TEACHER",
                "HWK Bearer Teacher",
                null,
                "teacher-hwk-bearer@example.com",
                null,
                "hash",
                "salt"
        );
        authRepository.assignRole(teacherId, "TEACHER", null);
        String token = sessionTokenService.createSession(teacherId).token();
        long courseId = courseRepository.insert(new CourseCreateRequest(
                "Bearer course",
                "Course for bearer auth homework flow.",
                "2026 Spring",
                "Programming",
                null,
                EnrollmentMode.PUBLIC,
                null,
                null,
                null,
                null,
                CourseStatus.ACTIVE
        ), teacherId).id();
        courseRepository.insertMember(courseId, teacherId, CourseMemberRole.TEACHER, CourseMemberStatus.ACTIVE, "CREATED", teacherId);

        mockMvc.perform(post("/api/v1/homeworks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(objectivePayload(courseId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.courseId").value(courseId))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    private Map<String, Object> objectivePayload(long courseId) {
        return Map.ofEntries(
                entry("courseId", courseId),
                entry("chapterId", 11),
                entry("title", "HWK01 bearer draft"),
                entry("description", "Answer the basics."),
                entry("type", "OBJECTIVE"),
                entry("deadline", "2026-06-30T23:59:59"),
                entry("totalScore", 100),
                entry("allowResubmit", true),
                entry("allowLateSubmit", false),
                entry("showEvaluationBeforePublish", true),
                entry("questions", java.util.List.of(
                        Map.of(
                                "questionType", "SINGLE_CHOICE",
                                "stem", "1 + 1 = ?",
                                "optionsJson", "[\"1\",\"2\"]",
                                "answerJson", "[\"2\"]",
                                "score", 100,
                                "sortOrder", 1
                        )
                ))
        );
    }
}
