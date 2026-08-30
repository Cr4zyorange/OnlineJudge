package com.onlinejudge.courseservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.courseservice.config.CourseRabbitProperties;
import com.onlinejudge.courseservice.security.TestJwtFactory;
import com.onlinejudge.courseservice.service.CourseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import java.security.KeyPair;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * v2 boundary regression tests.  They deliberately exercise bearer authentication rather than
 * gateway-owned X-User-* headers, and verify that Course writes its durable producer facts before
 * any RabbitMQ or Learning availability can affect the command result.
 */
@SpringBootTest(classes = CourseServiceApplication.class)
@AutoConfigureMockMvc
class CourseServiceContractTest {
    private static final KeyPair KEY_PAIR = TestJwtFactory.rsaKeyPair();
    private static final String BOOTSTRAP_JWKS = TestJwtFactory.jwks("course-test-kid", KEY_PAIR);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CourseService courseService;

    @Autowired
    private CourseRabbitProperties rabbit;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("course.identity.jwks-trust-bundle", () -> BOOTSTRAP_JWKS);
        registry.add("course.identity.jwks-uri", () -> "http://127.0.0.1:1/identity/jwks.json");
        registry.add("course.identity.refresh-enabled", () -> false);
    }

    @BeforeEach
    void clearCourseFacts() {
        jdbcTemplate.update("DELETE FROM course_event_outbox");
        jdbcTemplate.update("DELETE FROM course_membership_reconciliation_checkpoint");
        jdbcTemplate.update("DELETE FROM crs_course_member");
        jdbcTemplate.update("DELETE FROM crs_announcement");
        jdbcTemplate.update("DELETE FROM crs_resource");
        jdbcTemplate.update("DELETE FROM crs_chapter");
        jdbcTemplate.update("DELETE FROM crs_course");
    }

    @Test
    void bearerTeacherCreatesCourseAndTransactionalMembershipFactsWhileLearningIsUnavailable() throws Exception {
        String response = mockMvc.perform(post("/api/v1/courses")
                        .header("Authorization", userToken("101", List.of("TEACHER")))
                        .header("X-Request-Id", "0b277609-059f-4bd2-b26d-f54341003ecc")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Course independently owns this fact","enrollmentMode":"PUBLIC"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.teacherId").value("101"))
                .andReturn().getResponse().getContentAsString();

        String courseId = objectMapper.readTree(response).at("/data/id").asText();
        List<String> eventTypes = jdbcTemplate.queryForList(
                "SELECT event_type FROM course_event_outbox WHERE aggregate_id IN (?, ?)",
                String.class, courseId + ":101", courseId);
        List<String> routingKeys = jdbcTemplate.queryForList(
                "SELECT routing_key FROM course_event_outbox WHERE aggregate_id IN (?, ?) ORDER BY routing_key",
                String.class, courseId + ":101", courseId);

        assertThat(eventTypes).contains("course.member.changed.v2", "course.membership.snapshot.v2");
        assertThat(rabbit.getExchange()).isEqualTo("onlinejudge.events.v2");
        assertThat(routingKeys).containsExactly("onlinejudge.course.member.changed.v2", "onlinejudge.course.membership.snapshot.v2");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM crs_course", Integer.class)).isEqualTo(1);
    }

    @Test
    void forgedGatewayHeadersCannotCreateCourseAndCachedTrustBundleKeepsExistingSessionOffline() throws Exception {
        mockMvc.perform(post("/api/v1/courses")
                        .header("X-User-Id", "9001")
                        .header("X-User-Role", "TEACHER")
                        .header("X-Request-Id", "cf18df93-a629-451c-a952-b63682e696a8")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"name\":\"forged headers\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/courses")
                        .header("Authorization", userToken("9001", List.of("TEACHER")))
                        .header("X-Request-Id", "4e626b04-e5c4-42bc-9c83-5cf1b8d9bcf2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"name\":\"cached key survives identity outage\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void internalV2AuthorizationUsesAudienceBoundServiceJwtAndReturnsCanonicalPage() throws Exception {
        String courseResponse = mockMvc.perform(post("/api/v1/courses")
                        .header("Authorization", userToken("501", List.of("TEACHER")))
                        .header("X-Request-Id", "dad372a9-7103-4dda-bdf8-797402d287b9")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"name\":\"internal contract course\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String courseId = objectMapper.readTree(courseResponse).at("/data/id").asText();

        mockMvc.perform(get("/internal/v2/courses/{courseId}/authorizations/{userId}?action=MANAGE", courseId, "501")
                        .header("X-Request-Id", "54ea41ef-2d1a-4e8d-8422-7cf4d2b502c0"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SERVICE_IDENTITY_INVALID"));

        mockMvc.perform(get("/internal/v2/courses/{courseId}/members?page=0&size=20", courseId)
                        .header("X-Request-Id", "c2d35e3b-9b08-4857-9fd9-969d5976e0f4")
                        .header("X-OnlineJudge-Service-Authorization",
                                serviceToken("assessment-api", "course", List.of("course.members.read"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].userId").value("501"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void membershipChangePublishesPerMemberAndCompleteRosterWatermarkInTheSameCourseTransaction() throws Exception {
        String teacher = userToken("701", List.of("TEACHER"));
        String courseId = createdCourse(teacher, "outbox roster sequencing");

        mockMvc.perform(post("/api/v1/courses/{courseId}/join", courseId)
                        .header("Authorization", userToken("702", List.of("STUDENT")))
                        .header("X-Request-Id", "93079906-fab5-4e43-9d23-871403d27764"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/courses/{courseId}/members/{userId}", courseId, "702")
                        .header("Authorization", teacher)
                        .header("X-Request-Id", "271e8d2a-1351-4e2c-9ef8-c3524ced8c96")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ASSISTANT\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ASSISTANT"));

        List<String> memberVersions = jdbcTemplate.queryForList("""
                SELECT payload_json FROM course_event_outbox
                 WHERE event_type = 'course.member.changed.v2' AND aggregate_id = ?
                 ORDER BY aggregate_version
                """, String.class, courseId + ":702");
        List<String> rosterVersions = jdbcTemplate.queryForList("""
                SELECT payload_json FROM course_event_outbox
                 WHERE event_type = 'course.membership.snapshot.v2' AND aggregate_id = ?
                 ORDER BY aggregate_version
                """, String.class, courseId);

        assertThat(memberVersions).hasSize(2);
        assertThat(objectMapper.readTree(memberVersions.get(0)).at("/memberVersion").asLong()).isEqualTo(1);
        assertThat(objectMapper.readTree(memberVersions.get(1)).at("/memberVersion").asLong()).isEqualTo(2);
        assertThat(rosterVersions).hasSize(3);
        assertThat(objectMapper.readTree(rosterVersions.get(2)).at("/rosterVersion").asLong()).isEqualTo(3);
        assertThat(objectMapper.readTree(rosterVersions.get(2)).at("/members/1/userId").asText()).isEqualTo("702");
    }

    @Test
    void reconciliationPublishesOneStableSnapshotForPreexistingCourseWithoutLearningAvailability() {
        jdbcTemplate.update("INSERT INTO crs_course (id, course_name, teacher_id, enrollment_mode, status, roster_version) VALUES (812, 'preexisting course', 801, 'PUBLIC', 'ACTIVE', 7)");
        jdbcTemplate.update("INSERT INTO crs_course_member (course_id, user_id, role, join_method, join_status, member_version) VALUES (812, 801, 'TEACHER', 'CREATED', 'ACTIVE', 3)");

        courseService.publishBootstrapSnapshots();
        courseService.publishBootstrapSnapshots();

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM course_event_outbox WHERE event_type = 'course.membership.snapshot.v2' AND aggregate_id = '812'", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT aggregate_version FROM course_event_outbox WHERE event_type = 'course.membership.snapshot.v2' AND aggregate_id = '812'", Long.class)).isEqualTo(7);
        assertThat(jdbcTemplate.queryForObject("SELECT roster_version FROM crs_course WHERE id = 812", Long.class)).isEqualTo(7);
    }

    @Test
    void reconciliationNormalizesLegacyZeroRosterToTheFirstCanonicalWatermarkOnlyOnce() {
        jdbcTemplate.update("INSERT INTO crs_course (id, course_name, teacher_id, enrollment_mode, status, roster_version) VALUES (813, 'legacy roster', 801, 'PUBLIC', 'ACTIVE', 0)");
        jdbcTemplate.update("INSERT INTO crs_course_member (course_id, user_id, role, join_method, join_status, member_version) VALUES (813, 801, 'TEACHER', 'CREATED', 'ACTIVE', 1)");

        courseService.publishBootstrapSnapshots();
        courseService.publishBootstrapSnapshots();

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM course_event_outbox WHERE event_type = 'course.membership.snapshot.v2' AND aggregate_id = '813'", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT aggregate_version FROM course_event_outbox WHERE event_type = 'course.membership.snapshot.v2' AND aggregate_id = '813'", Long.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT roster_version FROM crs_course WHERE id = 813", Long.class)).isEqualTo(1);
    }

    @Test
    void publishedRosterReconciliationEmitsExactlyOneNewSnapshotWithoutLearningCallback() throws Exception {
        String courseId = createdCourse(userToken("841", List.of("TEACHER")), "durable roster repair");
        jdbcTemplate.update("UPDATE course_event_outbox SET delivery_status = 'PUBLISHED' WHERE event_type = 'course.membership.snapshot.v2' AND aggregate_id = ?", courseId);
        jdbcTemplate.update("UPDATE course_membership_reconciliation_checkpoint SET next_reconcile_at = CURRENT_TIMESTAMP WHERE course_id = ?", Long.parseLong(courseId));

        assertThat(courseService.reconcilePublishedRosters()).isEqualTo(1);
        assertThat(courseService.reconcilePublishedRosters()).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM course_event_outbox WHERE event_type = 'course.membership.snapshot.v2' AND aggregate_id = ?", Integer.class, courseId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT roster_version FROM crs_course WHERE id = ?", Long.class, Long.parseLong(courseId))).isEqualTo(2);
    }

    @Test
    void inviteEnrollmentLeaveHiddenChaptersAndAnnouncementsFollowCoursePermissions() throws Exception {
        String teacher = userToken("871", List.of("TEACHER"));
        String student = userToken("872", List.of("STUDENT"));
        String create = mockMvc.perform(post("/api/v1/courses")
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"invite lifecycle\",\"enrollmentMode\":\"INVITE\",\"inviteCode\":\"join-871\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String courseId = objectMapper.readTree(create).at("/data/id").asText();

        mockMvc.perform(post("/api/v1/courses/{courseId}/join", courseId)
                        .header("Authorization", student).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"inviteCode\":\"wrong\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INVITE_CODE"));
        mockMvc.perform(post("/api/v1/courses/{courseId}/join", courseId)
                        .header("Authorization", student).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"inviteCode\":\"join-871\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(post("/api/v1/courses/{courseId}/chapters", courseId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"teacher notes\",\"visible\":false,\"sortOrder\":9}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/courses/{courseId}/chapters", courseId)
                        .header("Authorization", student).header("X-Request-Id", requestId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        mockMvc.perform(get("/api/v1/courses?keyword=invite&page=0&size=1")
                        .header("Authorization", teacher).header("X-Request-Id", requestId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));

        String announcementRequestId = "9dbbca57-4a53-4557-bb47-293f438fb3ec";
        String announcement = mockMvc.perform(post("/api/v1/courses/{courseId}/announcements", courseId)
                        .header("Authorization", teacher).header("X-Request-Id", announcementRequestId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"welcome\",\"content\":\"course starts now\",\"top\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.top").value(true))
                .andReturn().getResponse().getContentAsString();
        String announcementId = objectMapper.readTree(announcement).at("/data/id").asText();
        var announcementFact = jdbcTemplate.queryForMap("""
                SELECT aggregate_type, aggregate_id, aggregate_version, correlation_id, routing_key, payload_json
                  FROM course_event_outbox WHERE event_type = 'course.announcement.published.v2'
                """);
        assertThat(announcementFact).containsEntry("aggregate_type", "course-announcement")
                .containsEntry("aggregate_id", announcementId)
                .containsEntry("aggregate_version", 1L)
                .containsEntry("correlation_id", announcementRequestId)
                .containsEntry("routing_key", "onlinejudge.course.announcement.published.v2");
        JsonNode announcementPayload = objectMapper.readTree((String) announcementFact.get("payload_json"));
        assertThat(announcementPayload.path("courseId").asText()).isEqualTo(courseId);
        assertThat(announcementPayload.path("announcementId").asText()).isEqualTo(announcementId);
        assertThat(announcementPayload.path("publishedAt").asText()).isNotBlank();
        assertThat(java.time.Instant.parse(announcementPayload.path("publishedAt").asText())).isNotNull();
        mockMvc.perform(get("/api/v1/courses/{courseId}/announcements", courseId)
                        .header("Authorization", student).header("X-Request-Id", requestId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].title").value("welcome"));
        mockMvc.perform(put("/api/v1/courses/{courseId}/announcements/{announcementId}", courseId, announcementId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"course starts tomorrow\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").value("course starts tomorrow"));
        mockMvc.perform(delete("/api/v1/courses/{courseId}/announcements/{announcementId}", courseId, announcementId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/courses/{courseId}/leave", courseId)
                        .header("Authorization", student).header("X-Request-Id", requestId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REMOVED"));
        mockMvc.perform(get("/api/v1/courses/{courseId}/announcements", courseId)
                        .header("Authorization", student).header("X-Request-Id", requestId()))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidApiPayloadReturnsClientErrorInsteadOfGenericInternalError() throws Exception {
        mockMvc.perform(post("/api/v1/courses")
                        .header("Authorization", userToken("881", List.of("TEACHER"))).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COURSE_REQUEST_INVALID"));
    }

    @Test
    void courseChapterAndResourceMutationApisRequireCourseManagerAndArchiveClosesEnrollment() throws Exception {
        String teacher = userToken("951", List.of("TEACHER"));
        String student = userToken("952", List.of("STUDENT"));
        String courseId = createdCourse(teacher, "full course API boundary");
        mockMvc.perform(post("/api/v1/courses/{courseId}/join", courseId)
                        .header("Authorization", student).header("X-Request-Id", requestId()))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/courses/{courseId}", courseId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"renamed course\",\"description\":\"owned by Course\",\"enrollmentMode\":\"INVITE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("renamed course"))
                .andExpect(jsonPath("$.data.description").value("owned by Course"));
        mockMvc.perform(put("/api/v1/courses/{courseId}", courseId)
                        .header("Authorization", student).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"forbidden\"}"))
                .andExpect(status().isForbidden());

        String chapter = mockMvc.perform(post("/api/v1/courses/{courseId}/chapters", courseId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"week one\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String chapterId = objectMapper.readTree(chapter).at("/data/id").asText();
        mockMvc.perform(put("/api/v1/courses/{courseId}/chapters/{chapterId}", courseId, chapterId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"week one revised\",\"sortOrder\":3}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("week one revised"));
        mockMvc.perform(put("/api/v1/courses/{courseId}/chapters/{chapterId}", courseId, chapterId)
                        .header("Authorization", student).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"forbidden\"}"))
                .andExpect(status().isForbidden());

        String resource = mockMvc.perform(post("/api/v1/courses/{courseId}/resources", courseId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"slides\",\"url\":\"https://example.test/slides.pdf\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String resourceId = objectMapper.readTree(resource).at("/data/id").asText();
        mockMvc.perform(put("/api/v1/courses/{courseId}/resources/{resourceId}", courseId, resourceId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"revised slides\",\"url\":\"https://example.test/revised.pdf\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("revised slides"));
        mockMvc.perform(get("/api/v1/courses/{courseId}/resources/{resourceId}/download", courseId, resourceId)
                        .header("Authorization", student).header("X-Request-Id", requestId()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(delete("/api/v1/courses/{courseId}/resources/{resourceId}", courseId, resourceId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId()))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/courses/{courseId}/chapters/{chapterId}", courseId, chapterId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId()))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/courses/{courseId}", courseId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ARCHIVED"));
        mockMvc.perform(post("/api/v1/courses/{courseId}/join", courseId)
                        .header("Authorization", userToken("953", List.of("STUDENT"))).header("X-Request-Id", requestId()))
                .andExpect(status().isConflict());
    }

    @Test
    void assistantCanManageVersionedFileResourceWhileMemberDownloadsAndLogicalDeleteHidesIt() throws Exception {
        String teacher = userToken("961", List.of("TEACHER"));
        String assistant = userToken("962", List.of("TEACHER"));
        String student = userToken("963", List.of("STUDENT"));
        String courseId = createdCourse(teacher, "file resource permission");
        mockMvc.perform(post("/api/v1/courses/{courseId}/join", courseId)
                        .header("Authorization", assistant).header("X-Request-Id", requestId()))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/courses/{courseId}/members/{userId}", courseId, "962")
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ASSISTANT\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/courses/{courseId}/join", courseId)
                        .header("Authorization", student).header("X-Request-Id", requestId()))
                .andExpect(status().isOk());

        String response = mockMvc.perform(multipart("/api/v1/courses/{courseId}/resources", courseId)
                        .file(new MockMultipartFile("file", "notes.txt", "text/plain", "v1 notes".getBytes()))
                        .param("name", "notes.txt").param("resourceType", "DOCUMENT").param("visibility", "STUDENT")
                        .header("Authorization", assistant).header("X-Request-Id", requestId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(1))
                .andReturn().getResponse().getContentAsString();
        String resourceId = objectMapper.readTree(response).at("/data/id").asText();

        mockMvc.perform(get("/api/v1/courses/{courseId}/resources/{resourceId}/download", courseId, resourceId)
                        .header("Authorization", student).header("X-Request-Id", requestId()))
                .andExpect(status().isOk()).andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEqualTo("v1 notes".getBytes()));
        mockMvc.perform(put("/api/v1/courses/{courseId}/resources/{resourceId}", courseId, resourceId)
                        .header("Authorization", assistant).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"notes revised\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.version").value(2));
        mockMvc.perform(delete("/api/v1/courses/{courseId}/resources/{resourceId}", courseId, resourceId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/courses/{courseId}/resources", courseId)
                        .header("Authorization", student).header("X-Request-Id", requestId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
    }

    private String createdCourse(String teacherToken, String name) throws Exception {
        String response = mockMvc.perform(post("/api/v1/courses")
                        .header("Authorization", teacherToken)
                        .header("X-Request-Id", "e752f260-d5d6-43f9-933a-fd9244a629df")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/id").asText();
    }

    private String userToken(String userId, List<String> roles) {
        return TestJwtFactory.userToken(KEY_PAIR, "course-test-kid", userId, roles, List.of("course:manage"));
    }

    private String serviceToken(String subject, String audience, List<String> scopes) {
        return TestJwtFactory.serviceToken(KEY_PAIR, "course-test-kid", subject, audience, scopes);
    }

    private String requestId() { return java.util.UUID.randomUUID().toString(); }
}
