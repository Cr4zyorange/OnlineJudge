package com.onlinejudge.crs;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CourseControllerTest {
    @Autowired
    MockMvc mockMvc;

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
        mockMvc.perform(get("/api/v1/courses/" + courseId + "/permissions/101"))
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
}
