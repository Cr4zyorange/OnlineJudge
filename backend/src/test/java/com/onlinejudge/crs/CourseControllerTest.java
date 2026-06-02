package com.onlinejudge.crs;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CourseControllerTest {
    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void teacherCreatesCourseAndBecomesCourseTeacher() throws Exception {
        String response = mockMvc.perform(post("/api/v1/courses")
                        .header("X-User-Id", "101")
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "软件工程基础",
                                  "description": "课程创建与管理主流程",
                                  "semester": "2026春",
                                  "category": "软件工程",
                                  "status": "ACTIVE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("软件工程基础")))
                .andExpect(jsonPath("$.data.teacherId", is(101)))
                .andExpect(jsonPath("$.data.manageable", is(true)))
                .andReturn().getResponse().getContentAsString();

        String courseId = response.replaceAll("(?s).*\"id\":(\\d+).*", "$1");
        mockMvc.perform(get("/api/v1/courses/" + courseId + "/permissions/101")
                        .header("X-User-Id", "101")
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.member", is(true)))
                .andExpect(jsonPath("$.data.teacher", is(true)))
                .andExpect(jsonPath("$.data.role", is("TEACHER")));
    }

    @Test
    void studentCannotCreateOrEditCourse() throws Exception {
        mockMvc.perform(post("/api/v1/courses")
                        .header("X-User-Id", "201")
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"越权课程\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", is("无权限访问")));
    }

    @Test
    void courseMemberCanReadDetailAfterJoining() throws Exception {
        String response = mockMvc.perform(post("/api/v1/courses")
                        .header("X-User-Id", "301")
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"公开课程\",\"enrollmentMode\":\"PUBLIC\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String courseId = response.replaceAll("(?s).*\"id\":(\\d+).*", "$1");

        mockMvc.perform(get("/api/v1/courses/" + courseId)
                        .header("X-User-Id", "302")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/courses/" + courseId + "/join")
                        .header("X-User-Id", "302")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.member", is(true)));

        mockMvc.perform(get("/api/v1/courses/" + courseId)
                        .header("X-User-Id", "302")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("公开课程")));
    }

    @Test
    void studentJoinsInviteCourseWithValidCodeAndCanAccessResources() throws Exception {
        String response = mockMvc.perform(post("/api/v1/courses")
                        .header("X-User-Id", "351")
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"invite-course-" + System.nanoTime() + "\",\"enrollmentMode\":\"INVITE\",\"inviteCode\":\"JOIN-351\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String courseId = response.replaceAll("(?s).*\"id\":(\\d+).*", "$1");

        mockMvc.perform(post("/api/v1/courses/" + courseId + "/join")
                        .header("X-User-Id", "352")
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\":\"WRONG\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("INVALID_INVITE_CODE")));

        mockMvc.perform(get("/api/v1/courses/" + courseId)
                        .header("X-User-Id", "352")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/courses/" + courseId + "/join")
                        .header("X-User-Id", "352")
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\":\"JOIN-351\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.member", is(true)))
                .andExpect(jsonPath("$.data.status", is("ACTIVE")))
                .andExpect(jsonPath("$.data.role", is("STUDENT")));

        mockMvc.perform(get("/api/v1/courses/" + courseId + "/resources")
                        .header("X-User-Id", "352")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk());
    }

    @Test
    void inviteCourseGeneratesVisibleInviteCodeForTeacherOnly() throws Exception {
        String response = mockMvc.perform(post("/api/v1/courses")
                        .header("X-User-Id", "356")
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"generated-invite-" + System.nanoTime() + "\",\"enrollmentMode\":\"INVITE\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inviteCode", not(emptyOrNullString())))
                .andReturn().getResponse().getContentAsString();
        String courseId = response.replaceAll("(?s).*\"id\":(\\d+).*", "$1");
        String inviteCode = response.replaceAll("(?s).*\"inviteCode\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/v1/courses/" + courseId)
                        .header("X-User-Id", "356")
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inviteCode", is(inviteCode)));

        mockMvc.perform(post("/api/v1/courses/" + courseId + "/join")
                        .header("X-User-Id", "357")
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\":\"" + inviteCode + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/courses/" + courseId)
                        .header("X-User-Id", "357")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inviteCode").doesNotExist());
    }

    @Test
    void reviewCourseCreatesPendingMembershipAndDuplicateJoinReturnsPendingStatus() throws Exception {
        String response = mockMvc.perform(post("/api/v1/courses")
                        .header("X-User-Id", "361")
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"review-course-" + System.nanoTime() + "\",\"enrollmentMode\":\"REVIEW\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String courseId = response.replaceAll("(?s).*\"id\":(\\d+).*", "$1");

        mockMvc.perform(post("/api/v1/courses/" + courseId + "/join")
                        .header("X-User-Id", "362")
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applyReason\":\"please approve\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.member", is(false)))
                .andExpect(jsonPath("$.data.status", is("PENDING")))
                .andExpect(jsonPath("$.data.role", is("STUDENT")));

        mockMvc.perform(get("/api/v1/courses/" + courseId)
                        .header("X-User-Id", "362")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/courses/" + courseId + "/join")
                        .header("X-User-Id", "362")
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applyReason\":\"again\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("JOIN_PENDING")));

        mockMvc.perform(get("/api/v1/courses/" + courseId + "/members?status=PENDING")
                        .header("X-User-Id", "361")
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(1)))
                .andExpect(jsonPath("$.data[0].userId", is(362)))
                .andExpect(jsonPath("$.data[0].status", is("PENDING")));

        mockMvc.perform(put("/api/v1/courses/" + courseId + "/members/362")
                        .header("X-User-Id", "361")
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("ACTIVE")))
                .andExpect(jsonPath("$.data.approvedBy", is(361)));

        mockMvc.perform(get("/api/v1/courses/" + courseId)
                        .header("X-User-Id", "362")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk());
    }

    @Test
    void teacherRejectsPendingJoinRequestAndStudentCanApplyAgain() throws Exception {
        String response = mockMvc.perform(post("/api/v1/courses")
                        .header("X-User-Id", "366")
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"reject-review-" + System.nanoTime() + "\",\"enrollmentMode\":\"REVIEW\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String courseId = response.replaceAll("(?s).*\"id\":(\\d+).*", "$1");

        mockMvc.perform(post("/api/v1/courses/" + courseId + "/join")
                        .header("X-User-Id", "367")
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applyReason\":\"please approve\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PENDING")));

        mockMvc.perform(put("/api/v1/courses/" + courseId + "/members/367")
                        .header("X-User-Id", "366")
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("REJECTED")));

        mockMvc.perform(get("/api/v1/courses/" + courseId)
                        .header("X-User-Id", "367")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/courses/" + courseId + "/members?status=PENDING")
                        .header("X-User-Id", "366")
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(0)));

        mockMvc.perform(post("/api/v1/courses/" + courseId + "/join")
                        .header("X-User-Id", "367")
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applyReason\":\"try again\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PENDING")));
    }

    @Test
    void duplicateActiveJoinClosedCourseAndRemovedMemberAreHandledExplicitly() throws Exception {
        String response = mockMvc.perform(post("/api/v1/courses")
                        .header("X-User-Id", "371")
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"duplicate-course-" + System.nanoTime() + "\",\"enrollmentMode\":\"PUBLIC\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String courseId = response.replaceAll("(?s).*\"id\":(\\d+).*", "$1");

        mockMvc.perform(post("/api/v1/courses/" + courseId + "/join")
                        .header("X-User-Id", "372")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/courses/" + courseId + "/join")
                        .header("X-User-Id", "372")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("ALREADY_JOINED")));

        mockMvc.perform(put("/api/v1/courses/" + courseId)
                        .header("X-User-Id", "371")
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"closed-course\",\"enrollmentMode\":\"PUBLIC\",\"status\":\"CLOSED\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/courses/" + courseId + "/join")
                        .header("X-User-Id", "373")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("COURSE_CLOSED")));

        mockMvc.perform(put("/api/v1/courses/" + courseId)
                        .header("X-User-Id", "371")
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"reopened-course\",\"enrollmentMode\":\"PUBLIC\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/courses/" + courseId + "/join")
                        .header("X-User-Id", "374")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk());

        jdbcTemplate.update("""
                UPDATE crs_course_member
                   SET join_status = 'REMOVED', left_at = CURRENT_TIMESTAMP
                 WHERE course_id = ? AND user_id = ?
                """, Long.parseLong(courseId), 374L);

        mockMvc.perform(get("/api/v1/courses/" + courseId)
                        .header("X-User-Id", "374")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/courses/" + courseId + "/join")
                        .header("X-User-Id", "374")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.member", is(true)))
                .andExpect(jsonPath("$.data.status", is("ACTIVE")));
    }

    @Test
    void fullCourseRejectsAdditionalActiveStudents() throws Exception {
        String response = mockMvc.perform(post("/api/v1/courses")
                        .header("X-User-Id", "381")
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"full-course-" + System.nanoTime() + "\",\"enrollmentMode\":\"PUBLIC\",\"maxStudents\":1,\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String courseId = response.replaceAll("(?s).*\"id\":(\\d+).*", "$1");

        mockMvc.perform(post("/api/v1/courses/" + courseId + "/join")
                        .header("X-User-Id", "382")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/courses/" + courseId + "/join")
                        .header("X-User-Id", "383")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("COURSE_FULL")));
    }

    @Test
    void courseMembersCanBeListedAndOnlyTeacherCanUpdateOrRemoveMembers() throws Exception {
        String response = mockMvc.perform(post("/api/v1/courses")
                        .header("X-User-Id", "391")
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"member-management-" + System.nanoTime() + "\",\"enrollmentMode\":\"PUBLIC\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String courseId = response.replaceAll("(?s).*\"id\":(\\d+).*", "$1");

        mockMvc.perform(post("/api/v1/courses/" + courseId + "/join")
                        .header("X-User-Id", "392")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/courses/" + courseId + "/members")
                        .header("X-User-Id", "392")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(2)))
                .andExpect(jsonPath("$.data[0].userId", is(392)))
                .andExpect(jsonPath("$.data[0].status", is("ACTIVE")));

        mockMvc.perform(put("/api/v1/courses/" + courseId + "/members/391")
                        .header("X-User-Id", "392")
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ASSISTANT\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/courses/" + courseId + "/members/392")
                        .header("X-User-Id", "391")
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ASSISTANT\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role", is("ASSISTANT")))
                .andExpect(jsonPath("$.data.status", is("ACTIVE")));

        mockMvc.perform(put("/api/v1/courses/" + courseId + "/members/392")
                        .header("X-User-Id", "391")
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ASSISTANT\",\"status\":\"PENDING\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("INVALID_MEMBER_STATUS_TRANSITION")));

        mockMvc.perform(delete("/api/v1/courses/" + courseId + "/members/392")
                        .header("X-User-Id", "392")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/courses/" + courseId + "/members/392")
                        .header("X-User-Id", "391")
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/courses/" + courseId + "/permissions/392")
                        .header("X-User-Id", "391")
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.member", is(false)))
                .andExpect(jsonPath("$.data.teacher", is(false)))
                .andExpect(jsonPath("$.data.role", is("ASSISTANT")))
                .andExpect(jsonPath("$.data.status", is("REMOVED")));

        mockMvc.perform(get("/api/v1/courses/" + courseId)
                        .header("X-User-Id", "392")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherCanListOnlyActiveStudentIdsForCourse() throws Exception {
        String response = mockMvc.perform(post("/api/v1/courses")
                        .header("X-User-Id", "395")
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"student-roster-" + System.nanoTime() + "\",\"enrollmentMode\":\"PUBLIC\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String courseId = response.replaceAll("(?s).*\"id\":(\\d+).*", "$1");

        mockMvc.perform(post("/api/v1/courses/" + courseId + "/join")
                        .header("X-User-Id", "396")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/courses/" + courseId + "/join")
                        .header("X-User-Id", "397")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/courses/" + courseId + "/members/397")
                        .header("X-User-Id", "395")
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ASSISTANT\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/courses/" + courseId + "/join")
                        .header("X-User-Id", "398")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/courses/" + courseId + "/members/398")
                        .header("X-User-Id", "395")
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/courses/" + courseId + "/students")
                        .header("X-User-Id", "396")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/courses/" + courseId + "/students")
                        .header("X-User-Id", "399")
                        .header("X-User-Role", "GRD")
                        .header("X-Permissions", "course:students:read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(1)))
                .andExpect(jsonPath("$.data[0]", is(396)));

        mockMvc.perform(get("/api/v1/courses/" + courseId + "/students")
                        .header("X-User-Id", "395")
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(1)))
                .andExpect(jsonPath("$.data[0]", is(396)));
    }

    @Test
    void archivedCourseAppearsInArchivedList() throws Exception {
        String suffix = "401-" + System.nanoTime();
        String currentName = "current-" + suffix;
        String historyName = "history-" + suffix;

        mockMvc.perform(post("/api/v1/courses")
                        .header("X-User-Id", "401")
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + currentName + "\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());

        String response = mockMvc.perform(post("/api/v1/courses")
                        .header("X-User-Id", "401")
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + historyName + "\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String courseId = response.replaceAll("(?s).*\"id\":(\\d+).*", "$1");

        mockMvc.perform(delete("/api/v1/courses/" + courseId)
                        .header("X-User-Id", "401")
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/courses?scope=archived&keyword=" + suffix)
                        .header("X-User-Id", "401")
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", is(1)))
                .andExpect(jsonPath("$.data.list[0].name", is(historyName)))
                .andExpect(jsonPath("$.data.list[0].status", is("ARCHIVED")));

        mockMvc.perform(get("/api/v1/courses?scope=all&keyword=" + suffix)
                        .header("X-User-Id", "401")
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", is(1)))
                .andExpect(jsonPath("$.data.list[0].name", is(currentName)))
                .andExpect(jsonPath("$.data.list[0].status", is("ACTIVE")));
    }

    @Test
    void mineScopeShowsTeacherCreatedCoursesAndStudentJoinedCourses() throws Exception {
        String suffix = "mine-" + System.nanoTime();
        String teacherMine = "teacher-" + suffix;
        String otherTeacher = "other-" + suffix;
        String studentJoined = "student-" + suffix;

        mockMvc.perform(post("/api/v1/courses")
                        .header("X-User-Id", "421")
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + teacherMine + "\",\"enrollmentMode\":\"PUBLIC\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/courses")
                        .header("X-User-Id", "422")
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + otherTeacher + "\",\"enrollmentMode\":\"PUBLIC\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());

        String joinedResponse = mockMvc.perform(post("/api/v1/courses")
                        .header("X-User-Id", "423")
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + studentJoined + "\",\"enrollmentMode\":\"PUBLIC\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String joinedCourseId = joinedResponse.replaceAll("(?s).*\"id\":(\\d+).*", "$1");

        mockMvc.perform(post("/api/v1/courses/" + joinedCourseId + "/join")
                        .header("X-User-Id", "424")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/courses?scope=mine&keyword=" + suffix)
                        .header("X-User-Id", "421")
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", is(1)))
                .andExpect(jsonPath("$.data.list[0].name", is(teacherMine)));

        mockMvc.perform(get("/api/v1/courses?scope=mine&keyword=" + suffix)
                        .header("X-User-Id", "424")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total", is(1)))
                .andExpect(jsonPath("$.data.list[0].name", is(studentJoined)));
    }

    @Test
    void teacherManagesNestedChapterTreeAndStudentCanOnlyReadIt() throws Exception {
        String response = mockMvc.perform(post("/api/v1/courses")
                        .header("X-User-Id", "701")
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"chapter-course-" + System.nanoTime() + "\",\"enrollmentMode\":\"PUBLIC\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String courseId = response.replaceAll("(?s).*\"id\":(\\d+).*", "$1");

        String firstChapter = mockMvc.perform(post("/api/v1/courses/" + courseId + "/chapters")
                        .header("X-User-Id", "701")
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chapterName\":\"课程导论\",\"objective\":\"学习目标\",\"sortOrder\":2,\"visibleStatus\":1,\"chapterType\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.chapterName", is("课程导论")))
                .andExpect(jsonPath("$.data.sortOrder", is(1)))
                .andExpect(jsonPath("$.data.objective", is("学习目标")))
                .andExpect(jsonPath("$.data.visibleStatus", is(1)))
                .andExpect(jsonPath("$.data.chapterType", is(1)))
                .andReturn().getResponse().getContentAsString();
        String chapterId = firstChapter.replaceAll("(?s).*\"id\":(\\d+).*", "$1");

        String secondChapter = mockMvc.perform(post("/api/v1/courses/" + courseId + "/chapters")
                        .header("X-User-Id", "701")
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chapterName\":\"实践准备\",\"sortOrder\":1,\"visibleStatus\":1,\"chapterType\":1}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secondChapterId = secondChapter.replaceAll("(?s).*\"id\":(\\d+).*", "$1");

        mockMvc.perform(post("/api/v1/courses/" + courseId + "/chapters")
                        .header("X-User-Id", "701")
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chapterName\":\"环境安装\",\"parentId\":" + chapterId + ",\"sortOrder\":1,\"visibleStatus\":1,\"chapterType\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.parentId", is(Integer.parseInt(chapterId))));

        mockMvc.perform(post("/api/v1/courses/" + courseId + "/join")
                        .header("X-User-Id", "702")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/courses/" + courseId + "/chapters")
                        .header("X-User-Id", "702")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].chapterName", is("实践准备")))
                .andExpect(jsonPath("$.data[0].sortOrder", is(1)))
                .andExpect(jsonPath("$.data[1].chapterName", is("课程导论")))
                .andExpect(jsonPath("$.data[1].sortOrder", is(2)))
                .andExpect(jsonPath("$.data[1].children[0].chapterName", is("环境安装")));

        mockMvc.perform(put("/api/v1/chapters/" + secondChapterId)
                        .header("X-User-Id", "702")
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chapterName\":\"越权修改\",\"sortOrder\":1,\"visibleStatus\":1,\"chapterType\":1}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/chapters/" + secondChapterId)
                        .header("X-User-Id", "701")
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chapterName\":\"实践准备\",\"sortOrder\":2,\"visibleStatus\":0,\"chapterType\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sortOrder", is(2)))
                .andExpect(jsonPath("$.data.visibleStatus", is(0)))
                .andExpect(jsonPath("$.data.chapterType", is(2)));

        mockMvc.perform(get("/api/v1/courses/" + courseId + "/chapters")
                        .header("X-User-Id", "701")
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(2)))
                .andExpect(jsonPath("$.data[1].chapterName", is("实践准备")))
                .andExpect(jsonPath("$.data[1].visibleStatus", is(0)));

        mockMvc.perform(get("/api/v1/courses/" + courseId + "/chapters")
                        .header("X-User-Id", "702")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(1)))
                .andExpect(jsonPath("$.data[0].chapterName", is("课程导论")))
                .andExpect(jsonPath("$.data[0].sortOrder", is(1)));

        mockMvc.perform(delete("/api/v1/chapters/" + chapterId)
                        .header("X-User-Id", "701")
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/courses/" + courseId + "/chapters")
                        .header("X-User-Id", "702")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(0)));
    }

    @Test
    void teacherManagesResourcesAndOnlyCourseMembersCanListOrDownload() throws Exception {
        String response = mockMvc.perform(post("/api/v1/courses")
                        .header("X-User-Id", "801")
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"resource-course-" + System.nanoTime() + "\",\"enrollmentMode\":\"PUBLIC\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String courseId = response.replaceAll("(?s).*\"id\":(\\d+).*", "$1");

        String chapterResponse = mockMvc.perform(post("/api/v1/courses/" + courseId + "/chapters")
                        .header("X-User-Id", "801")
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chapterName\":\"Resource Chapter\",\"sortOrder\":1,\"visibleStatus\":1,\"chapterType\":1}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String chapterId = chapterResponse.replaceAll("(?s).*\"id\":(\\d+).*", "$1");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "lesson.pdf",
                "application/pdf",
                "course material".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );

        String uploadResponse = mockMvc.perform(multipart("/api/v1/courses/" + courseId + "/resources")
                        .file(file)
                        .param("name", "Lesson PDF")
                        .param("chapterId", chapterId)
                        .param("resourceType", "DOCUMENT")
                        .param("visibility", "STUDENT")
                        .header("X-User-Id", "801")
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("Lesson PDF")))
                .andExpect(jsonPath("$.data.chapterId", is(Integer.parseInt(chapterId))))
                .andExpect(jsonPath("$.data.resourceType", is("DOCUMENT")))
                .andReturn().getResponse().getContentAsString();
        String resourceId = uploadResponse.replaceAll("(?s).*\"id\":(\\d+).*", "$1");

        MockMultipartFile teacherOnlyFile = new MockMultipartFile(
                "file",
                "teacher-note.pdf",
                "application/pdf",
                "teacher material".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        String teacherOnlyUploadResponse = mockMvc.perform(multipart("/api/v1/courses/" + courseId + "/resources")
                        .file(teacherOnlyFile)
                        .param("name", "Teacher Note")
                        .param("chapterId", chapterId)
                        .param("resourceType", "DOCUMENT")
                        .param("visibility", "TEACHER")
                        .header("X-User-Id", "801")
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String teacherOnlyResourceId = teacherOnlyUploadResponse.replaceAll("(?s).*\"id\":(\\d+).*", "$1");

        mockMvc.perform(get("/api/v1/courses/" + courseId + "/resources")
                        .header("X-User-Id", "802")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/courses/" + courseId + "/join")
                        .header("X-User-Id", "802")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/courses/" + courseId + "/resources")
                        .header("X-User-Id", "802")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(1)))
                .andExpect(jsonPath("$.data[0].name", is("Lesson PDF")))
                .andExpect(jsonPath("$.data[0].downloadUrl", is("/api/v1/courses/" + courseId + "/resources/" + resourceId + "/download")));

        mockMvc.perform(get("/api/v1/courses/" + courseId + "/resources/" + resourceId + "/download")
                        .header("X-User-Id", "802")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(content().bytes("course material".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        mockMvc.perform(get("/api/v1/courses/" + courseId + "/resources/" + teacherOnlyResourceId + "/download")
                        .header("X-User-Id", "802")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/courses/" + courseId + "/resources/" + resourceId)
                        .header("X-User-Id", "802")
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"student edit\",\"resourceType\":\"DOCUMENT\",\"visibility\":\"STUDENT\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/courses/" + courseId + "/resources/" + resourceId)
                        .header("X-User-Id", "801")
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Updated PDF\",\"chapterId\":" + chapterId + ",\"resourceType\":\"COURSEWARE\",\"visibility\":\"STUDENT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is("Updated PDF")))
                .andExpect(jsonPath("$.data.resourceType", is("COURSEWARE")));

        mockMvc.perform(delete("/api/v1/courses/" + courseId + "/resources/" + resourceId)
                        .header("X-User-Id", "801")
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/courses/" + courseId + "/resources")
                        .header("X-User-Id", "802")
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()", is(0)));
    }

    @Test
    void resourceUploadRejectsUnsupportedTypeAndOversizedFile() throws Exception {
        String response = mockMvc.perform(post("/api/v1/courses")
                        .header("X-User-Id", "811")
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"resource-validation-" + System.nanoTime() + "\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String courseId = response.replaceAll("(?s).*\"id\":(\\d+).*", "$1");

        MockMultipartFile script = new MockMultipartFile(
                "file",
                "run.exe",
                "application/octet-stream",
                "bad".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/v1/courses/" + courseId + "/resources")
                        .file(script)
                        .param("resourceType", "DOCUMENT")
                        .param("visibility", "STUDENT")
                        .header("X-User-Id", "811")
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isBadRequest());

        MockMultipartFile huge = new MockMultipartFile(
                "file",
                "huge.pdf",
                "application/pdf",
                new byte[51 * 1024 * 1024]
        );
        mockMvc.perform(multipart("/api/v1/courses/" + courseId + "/resources")
                        .file(huge)
                        .param("resourceType", "DOCUMENT")
                        .param("visibility", "STUDENT")
                        .header("X-User-Id", "811")
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isBadRequest());
    }
}
