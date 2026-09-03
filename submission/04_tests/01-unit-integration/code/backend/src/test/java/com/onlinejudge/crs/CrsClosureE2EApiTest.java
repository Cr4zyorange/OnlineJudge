package com.onlinejudge.crs;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CRS 主流程闭环验收：教师建课 -> 章节 -> 资源 -> 公告；
 * 学生分别以公开/邀请码/审批三种模式加入；审批前后权限变化；
 * 非法邀请码、满员、重复加入与资源失败分支。
 * 该测试从 API 入口完整走通 CRS 业务闭环，作为共享 E2E（#267）的接口级可执行入口之一。
 */
@SpringBootTest
@AutoConfigureMockMvc
class CrsClosureE2EApiTest {

    private static final long TEACHER = 8801L;
    private static final long STUDENT_PUBLIC = 8802L;
    private static final long STUDENT_INVITE = 8803L;
    private static final long STUDENT_REVIEW = 8804L;
    private static final long STUDENT_FULL_A = 8805L;
    private static final long STUDENT_FULL_B = 8806L;

    @Autowired
    MockMvc mockMvc;

    @Test
    void crsClosureTeacherBuildsContentThenStudentsJoinWithThreeModesAndPermissionsChange() throws Exception {
        // 1) 教师建课（公开课）
        String publicCourseId = createCourse(
                "闭环公开课-" + System.nanoTime(),
                "{\"enrollmentMode\":\"PUBLIC\",\"status\":\"ACTIVE\"}",
                TEACHER);

        // 2) 章节：教师创建父章节与子章节，学生加入前无权限读取
        String parentChapterId = createChapter(publicCourseId, "第一章 绪论", null, TEACHER);
        createChapter(publicCourseId, "1.1 课程目标", parentChapterId, TEACHER);
        mockMvc.perform(get("/api/v1/courses/{courseId}/chapters", publicCourseId)
                        .header("X-User-Id", String.valueOf(STUDENT_PUBLIC))
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isForbidden());

        // 3) 资源：教师上传 PDF，学生加入后可见并可下载
        String resourceId = uploadResource(publicCourseId, "讲义.pdf", "application/pdf", TEACHER);
        mockMvc.perform(get("/api/v1/courses/{courseId}/resources/{resourceId}/download", publicCourseId, resourceId)
                        .header("X-User-Id", String.valueOf(STUDENT_PUBLIC))
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isForbidden());

        // 4) 公开加入：学生加入后获得课程访问、章节树与资源下载权限
        mockMvc.perform(post("/api/v1/courses/{courseId}/join", publicCourseId)
                        .header("X-User-Id", String.valueOf(STUDENT_PUBLIC))
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.member", is(true)))
                .andExpect(jsonPath("$.data.role", is("STUDENT")))
                .andExpect(jsonPath("$.data.status", is("ACTIVE")));

        mockMvc.perform(get("/api/v1/courses/{courseId}", publicCourseId)
                        .header("X-User-Id", String.valueOf(STUDENT_PUBLIC))
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name", is(not(nullValue()))));
        mockMvc.perform(get("/api/v1/courses/{courseId}/chapters", publicCourseId)
                        .header("X-User-Id", String.valueOf(STUDENT_PUBLIC))
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].children", hasSize(1)));
        mockMvc.perform(get("/api/v1/courses/{courseId}/resources/{resourceId}/download", publicCourseId, resourceId)
                        .header("X-User-Id", String.valueOf(STUDENT_PUBLIC))
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/courses/{courseId}/join", publicCourseId)
                        .header("X-User-Id", String.valueOf(STUDENT_PUBLIC))
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("ALREADY_JOINED")));

        // 5) 邀请码加入：错误邀请码被拒，正确邀请码成功，重复加入被拒
        String inviteCourseId = createCourse(
                "闭环邀请码课-" + System.nanoTime(),
                "{\"enrollmentMode\":\"INVITE\",\"inviteCode\":\"INV-8803\",\"status\":\"ACTIVE\"}",
                TEACHER);
        mockMvc.perform(post("/api/v1/courses/{courseId}/join", inviteCourseId)
                        .header("X-User-Id", String.valueOf(STUDENT_INVITE))
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\":\"WRONG\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("INVALID_INVITE_CODE")));
        mockMvc.perform(post("/api/v1/courses/{courseId}/join", inviteCourseId)
                        .header("X-User-Id", String.valueOf(STUDENT_INVITE))
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\":\"INV-8803\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("ACTIVE")));
        mockMvc.perform(post("/api/v1/courses/{courseId}/join", inviteCourseId)
                        .header("X-User-Id", String.valueOf(STUDENT_INVITE))
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inviteCode\":\"INV-8803\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("ALREADY_JOINED")));

        // 6) 审批加入：PENDING 阶段无课程访问权限，审批通过后权限立即开放
        String reviewCourseId = createCourse(
                "闭环审批课-" + System.nanoTime(),
                "{\"enrollmentMode\":\"REVIEW\",\"status\":\"ACTIVE\"}",
                TEACHER);
        mockMvc.perform(post("/api/v1/courses/{courseId}/join", reviewCourseId)
                        .header("X-User-Id", String.valueOf(STUDENT_REVIEW))
                        .header("X-User-Role", "STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"applyReason\":\"希望加入课程学习\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.member", is(false)))
                .andExpect(jsonPath("$.data.status", is("PENDING")));
        mockMvc.perform(get("/api/v1/courses/{courseId}", reviewCourseId)
                        .header("X-User-Id", String.valueOf(STUDENT_REVIEW))
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/courses/{courseId}/join", reviewCourseId)
                        .header("X-User-Id", String.valueOf(STUDENT_REVIEW))
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("JOIN_PENDING")));
        mockMvc.perform(get("/api/v1/courses/{courseId}/members?status=PENDING", reviewCourseId)
                        .header("X-User-Id", String.valueOf(TEACHER))
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].userId", is(8804)))
                .andExpect(jsonPath("$.data[0].status", is("PENDING")));
        mockMvc.perform(put("/api/v1/courses/{courseId}/members/{userId}", reviewCourseId, STUDENT_REVIEW)
                        .header("X-User-Id", String.valueOf(TEACHER))
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"STUDENT\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("ACTIVE")));
        mockMvc.perform(get("/api/v1/courses/{courseId}", reviewCourseId)
                        .header("X-User-Id", String.valueOf(STUDENT_REVIEW))
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/courses/{courseId}/permissions/{userId}", reviewCourseId, STUDENT_REVIEW)
                        .header("X-User-Id", String.valueOf(TEACHER))
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.member", is(true)))
                .andExpect(jsonPath("$.data.teacher", is(false)));

        // 7) 满员：课程满员后其他学生加入被拒
        String fullCourseId = createCourse(
                "闭环满员课-" + System.nanoTime(),
                "{\"enrollmentMode\":\"PUBLIC\",\"maxStudents\":1,\"status\":\"ACTIVE\"}",
                TEACHER);
        mockMvc.perform(post("/api/v1/courses/{courseId}/join", fullCourseId)
                        .header("X-User-Id", String.valueOf(STUDENT_FULL_A))
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/courses/{courseId}/join", fullCourseId)
                        .header("X-User-Id", String.valueOf(STUDENT_FULL_B))
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", is("COURSE_FULL")));

        // 8) 资源失败分支：不支持类型被拒且不产生资源记录
        mockMvc.perform(multipart("/api/v1/courses/{courseId}/resources", publicCourseId)
                        .file(new MockMultipartFile("file", "恶意程序.exe", "application/octet-stream", new byte[]{1, 2, 3}))
                        .header("X-User-Id", String.valueOf(TEACHER))
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", is("不支持的文件类型")));
        mockMvc.perform(get("/api/v1/courses/{courseId}/resources", publicCourseId)
                        .header("X-User-Id", String.valueOf(STUDENT_PUBLIC))
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        // 9) 公告：教师发布并置顶，学生按置顶优先查看；首页摘要聚合课程/公告/最近任务
        createAnnouncement(publicCourseId, "置顶公告", "请先阅读置顶公告", true, TEACHER);
        createAnnouncement(publicCourseId, "普通公告", "普通公告内容", false, TEACHER);
        mockMvc.perform(get("/api/v1/courses/{courseId}/announcements", publicCourseId)
                        .header("X-User-Id", String.valueOf(STUDENT_PUBLIC))
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].top", is(true)))
                .andExpect(jsonPath("$.data[1].top", is(false)));
        mockMvc.perform(get("/api/v1/courses/{courseId}/home-summary", publicCourseId)
                        .header("X-User-Id", String.valueOf(STUDENT_PUBLIC))
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.course.id", is(not(nullValue()))))
                .andExpect(jsonPath("$.data.announcements", hasSize(2)))
                .andExpect(jsonPath("$.data.recentTasks", not(nullValue())));

        // 10) 非成员访问资源与课程仍被拒绝
        mockMvc.perform(get("/api/v1/courses/{courseId}/resources/{resourceId}/download", publicCourseId, resourceId)
                        .header("X-User-Id", String.valueOf(STUDENT_FULL_B))
                        .header("X-User-Role", "STUDENT"))
                .andExpect(status().isForbidden());
    }

    private String createCourse(String name, String extraJson, long teacherId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/courses")
                        .header("X-User-Id", String.valueOf(teacherId))
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"," + extraJson.substring(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.manageable", is(true)))
                .andReturn();
        return extractId(result);
    }

    private String createChapter(String courseId, String chapterName, String parentId, long teacherId) throws Exception {
        String parentField = parentId == null ? "null" : parentId;
        MvcResult result = mockMvc.perform(post("/api/v1/courses/{courseId}/chapters", courseId)
                        .header("X-User-Id", String.valueOf(teacherId))
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chapterName\":\"" + chapterName + "\",\"parentId\":" + parentField + "}"))
                .andExpect(status().isOk())
                .andReturn();
        return extractId(result);
    }

    private String uploadResource(String courseId, String filename, String contentType, long teacherId) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/v1/courses/{courseId}/resources", courseId)
                        .file(new MockMultipartFile("file", filename, contentType, new byte[]{37, 80, 68, 70, 10}))
                        .header("X-User-Id", String.valueOf(teacherId))
                        .header("X-User-Role", "TEACHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalFilename", is(filename)))
                .andReturn();
        return extractId(result);
    }

    private void createAnnouncement(String courseId, String title, String content, boolean top, long teacherId) throws Exception {
        mockMvc.perform(post("/api/v1/courses/{courseId}/announcements", courseId)
                        .header("X-User-Id", String.valueOf(teacherId))
                        .header("X-User-Role", "TEACHER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"content\":\"" + content + "\",\"isTop\":" + top + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.top", is(top)));
    }

    private String extractId(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        return body.replaceAll("(?s).*\"id\":(\\d+).*", "$1");
    }
}
