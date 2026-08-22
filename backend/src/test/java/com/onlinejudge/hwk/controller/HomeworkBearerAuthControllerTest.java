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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Map;

import static java.util.Map.entry;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:homework_bearer_auth;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
        "onlinejudge.auth.allow-header-auth=false"
})
@AutoConfigureMockMvc
@Sql(
        statements = {
                "DELETE FROM t_hwk_review_log",
                "DELETE FROM t_hwk_evaluation",
                "DELETE FROM t_hwk_submission",
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

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    @Test
    void crsHomeworkFlowUsesRealCourseMembershipForVisibilitySubmissionAndReviewPermission() throws Exception {
        long teacherId = createUserWithRole("teacher-hwk-int", "TEACHER");
        long enrolledStudentId = createUserWithRole("student-hwk-int", "STUDENT");
        long nonMemberStudentId = createUserWithRole("outsider-hwk-int", "STUDENT");
        long otherTeacherId = createUserWithRole("other-teacher-hwk-int", "TEACHER");
        String teacherToken = sessionTokenService.createSession(teacherId).token();
        String enrolledStudentToken = sessionTokenService.createSession(enrolledStudentId).token();
        String nonMemberStudentToken = sessionTokenService.createSession(nonMemberStudentId).token();
        String otherTeacherToken = sessionTokenService.createSession(otherTeacherId).token();
        long courseId = courseRepository.insert(new CourseCreateRequest(
                "CRS HWK integration",
                "Course membership drives homework access.",
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
        courseRepository.insertMember(courseId, enrolledStudentId, CourseMemberRole.STUDENT, CourseMemberStatus.ACTIVE, "JOINED", teacherId);

        String homeworkBody = mockMvc.perform(post("/api/v1/homeworks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(textPayload(courseId))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long homeworkId = objectMapper.readTree(homeworkBody).path("data").path("id").asLong();

        mockMvc.perform(put("/api/v1/homeworks/{homeworkId}/publish", homeworkId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        mockMvc.perform(get("/api/v1/homeworks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + enrolledStudentToken)
                        .param("courseId", Long.toString(courseId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list", hasSize(1)))
                .andExpect(jsonPath("$.data.list[0].id").value(homeworkId));

        String submissionBody = mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + enrolledStudentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("answerText", "submitted through real CRS membership"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.studentId").value(enrolledStudentId))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long submissionId = objectMapper.readTree(submissionBody).path("data").path("submissionId").asLong();

        mockMvc.perform(post("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + nonMemberStudentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("answerText", "not a member"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HWK_4031"));

        mockMvc.perform(put("/api/v1/submissions/{submissionId}/review", submissionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherTeacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "manualScore", 80,
                                "finalScore", 82,
                                "comment", "wrong course"
                        ))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HWK_4031"));

        mockMvc.perform(put("/api/v1/submissions/{submissionId}/review", submissionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "manualScore", 90,
                                "finalScore", 92,
                                "comment", "reviewed by course teacher"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("REVIEWED"))
                .andExpect(jsonPath("$.data.finalScore").value(92))
                .andExpect(jsonPath("$.data.comment").value("reviewed by course teacher"));
    }

    @Test
    void statisticsAndAttentionUseOnlyActiveCrsStudentMemberships() throws Exception {
        long teacherId = createUserWithRole("teacher-hwk-roster", "TEACHER");
        long activeStudentId = createUserWithRole("active-hwk-roster", "STUDENT");
        long pendingStudentId = createUserWithRole("pending-hwk-roster", "STUDENT");
        long rejectedStudentId = createUserWithRole("rejected-hwk-roster", "STUDENT");
        long removedStudentId = createUserWithRole("removed-hwk-roster", "STUDENT");
        long deletedStudentId = createUserWithRole("deleted-hwk-roster", "STUDENT");
        String teacherToken = sessionTokenService.createSession(teacherId).token();
        long courseId = courseRepository.insert(new CourseCreateRequest(
                "CRS statistics roster",
                "Only active students define HWK statistics and attention queues.",
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
        courseRepository.insertMember(courseId, teacherId, CourseMemberRole.TEACHER, CourseMemberStatus.ACTIVE,
                "CREATED", teacherId);
        courseRepository.insertMember(courseId, activeStudentId, CourseMemberRole.STUDENT, CourseMemberStatus.ACTIVE,
                "JOINED", teacherId);
        courseRepository.insertMember(courseId, pendingStudentId, CourseMemberRole.STUDENT, CourseMemberStatus.PENDING,
                "REQUEST", null);
        courseRepository.insertMember(courseId, rejectedStudentId, CourseMemberRole.STUDENT, CourseMemberStatus.REJECTED,
                "REQUEST", teacherId);
        courseRepository.insertMember(courseId, removedStudentId, CourseMemberRole.STUDENT, CourseMemberStatus.REMOVED,
                "JOINED", teacherId);
        courseRepository.insertMember(courseId, deletedStudentId, CourseMemberRole.STUDENT, CourseMemberStatus.ACTIVE,
                "JOINED", teacherId);
        jdbcTemplate.update(
                "UPDATE crs_course_member SET is_deleted = TRUE WHERE course_id = ? AND user_id = ?",
                courseId,
                deletedStudentId
        );

        String homeworkBody = mockMvc.perform(post("/api/v1/homeworks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(textPayload(courseId))))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        long homeworkId = objectMapper.readTree(homeworkBody).path("data").path("id").asLong();
        for (long studentId : java.util.List.of(
                activeStudentId,
                pendingStudentId,
                rejectedStudentId,
                removedStudentId,
                deletedStudentId
        )) {
            insertPendingCodeSubmission(homeworkId, studentId);
        }

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}/statistics", homeworkId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalStudentCount").value(1))
                .andExpect(jsonPath("$.data.submittedCount").value(1))
                .andExpect(jsonPath("$.data.autoEvaluableCount").value(1))
                .andExpect(jsonPath("$.data.pendingEvaluationCount").value(1));

        mockMvc.perform(get("/api/v1/homeworks/{homeworkId}/submissions", homeworkId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + teacherToken)
                        .param("attention", "EVALUATION_PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list", hasSize(1)))
                .andExpect(jsonPath("$.data.list[0].studentId").value(activeStudentId));
    }

    private long createUserWithRole(String username, String roleCode) {
        long userId = authRepository.createUser(
                username,
                roleCode,
                username,
                null,
                username + "@example.com",
                null,
                "hash",
                "salt"
        );
        authRepository.assignRole(userId, roleCode, null);
        return userId;
    }

    private void insertPendingCodeSubmission(long homeworkId, long studentId) {
        jdbcTemplate.update("""
                INSERT INTO t_hwk_submission
                (homework_id, student_id, submit_type, answer_text, answer_json, file_url, language,
                 submit_status, evaluation_status, review_status, auto_score, manual_score, final_score,
                 comment, version, is_final, submitted_at, reviewed_by, reviewed_at, created_at, updated_at,
                 is_deleted)
                VALUES (?, ?, 'CODE', NULL, NULL, NULL, 'java', 'SUBMITTED', 'PENDING', 'NEED_REVIEW',
                        NULL, NULL, NULL, NULL, 1, TRUE, CURRENT_TIMESTAMP, NULL, NULL,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, FALSE)
                """, homeworkId, studentId);
    }

    private Map<String, Object> objectivePayload(long courseId) {
        return Map.ofEntries(
                entry("courseId", courseId),
                entry("chapterId", 11),
                entry("title", "HWK01 bearer draft"),
                entry("description", "Answer the basics."),
                entry("type", "OBJECTIVE"),
                entry("deadline", futureDeadline()),
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

    private Map<String, Object> textPayload(long courseId) {
        return Map.ofEntries(
                entry("courseId", courseId),
                entry("chapterId", 11),
                entry("title", "HWK01 CRS integration text"),
                entry("description", "Submit a short answer."),
                entry("type", "TEXT"),
                entry("deadline", futureDeadline()),
                entry("totalScore", 100),
                entry("allowResubmit", true),
                entry("allowLateSubmit", false),
                entry("showEvaluationBeforePublish", true)
        );
    }

    private String futureDeadline() {
        return LocalDateTime.now().plusDays(30).withNano(0).toString();
    }
}
