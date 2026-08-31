package com.onlinejudge.courseservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlinejudge.courseservice.config.CourseRabbitProperties;
import com.onlinejudge.courseservice.security.TestJwtFactory;
import com.onlinejudge.courseservice.service.CourseService;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.security.auth.x500.X500Principal;
import org.junit.jupiter.api.AfterAll;
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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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
    private static final String EMPTY_TASK_PAGE = """
            {"items":[],"page":0,"size":5,"total":0}
            """;
    private static final Path TLS_DIR;
    private static final Path STORAGE_ROOT;
    private static HttpsServer learningServer;
    private static final AtomicReference<LearningStubResponse> LEARNING_RESPONSE =
            new AtomicReference<>(new LearningStubResponse(200, EMPTY_TASK_PAGE));

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

    static {
        try {
            TLS_DIR = Files.createTempDirectory("course-mtls-test");
            STORAGE_ROOT = Files.createTempDirectory("course-storage-test");
            generateMtlsKeystores(TLS_DIR);
            // Bind explicitly to the IPv4 loopback: the mTLS client connects to
            // https://127.0.0.1:port, so an OS-preferring IPv6 loopback address
            // would make the same suite fail only on some runners (CI).
            learningServer = HttpsServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
            learningServer.setHttpsConfigurator(new HttpsConfigurator(serverSslContext(TLS_DIR)) {
                @Override
                public void configure(HttpsParameters parameters) {
                    // The proof is the renewable mTLS path: the Course client
                    // must present its workload certificate on every call.
                    // setNeedClientAuth alone is not reliably propagated to the
                    // SSLEngine by jdk.httpserver on every JDK; installing the
                    // full SSLParameters is the documented, portable form.
                    SSLParameters sslParameters = serverSslContext(TLS_DIR).getDefaultSSLParameters();
                    sslParameters.setNeedClientAuth(true);
                    parameters.setSSLParameters(sslParameters);
                }
            });
            learningServer.createContext("/internal/v2/learning/tasks/recent", exchange -> {
                String peerSubject;
                try {
                    peerSubject = peerSubject((com.sun.net.httpserver.HttpsExchange) exchange);
                } catch (Exception unverified) {
                    peerSubject = null;
                }
                if (!"CN=course-service".equals(peerSubject)) {
                    byte[] body = "{\"code\":\"SERVICE_IDENTITY_INVALID\",\"message\":\"mTLS workload identity is invalid\",\"requestId\":\"\",\"retryable\":false}"
                            .getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(401, body.length);
                    try (OutputStream stream = exchange.getResponseBody()) {
                        stream.write(body);
                    }
                    return;
                }
                LearningStubResponse response = LEARNING_RESPONSE.get();
                byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(response.status(), body.length);
                try (OutputStream stream = exchange.getResponseBody()) {
                    stream.write(body);
                }
            });
            learningServer.start();
        } catch (Exception failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("course.identity.jwks-trust-bundle", () -> BOOTSTRAP_JWKS);
        registry.add("course.identity.jwks-uri", () -> "http://127.0.0.1:1/identity/jwks.json");
        registry.add("course.identity.refresh-enabled", () -> false);
        registry.add("course.identity.mtls-service-subjects", () -> "CN=course-service");
        registry.add("course.learning.base-url", () -> "https://127.0.0.1:" + learningServer.getAddress().getPort());
        registry.add("course.learning.timeout-ms", () -> 2000L);
        registry.add("course.learning.service-token", () -> "");
        registry.add("course.learning.mtls-enabled", () -> true);
        registry.add("course.learning.mtls-keystore-path", () -> TLS_DIR.resolve("client.p12").toString());
        registry.add("course.learning.mtls-keystore-password", () -> "changeit");
        registry.add("course.learning.mtls-keystore-type", () -> "PKCS12");
        registry.add("course.learning.mtls-truststore-path", () -> TLS_DIR.resolve("client-trust.p12").toString());
        registry.add("course.learning.mtls-truststore-password", () -> "changeit");
        registry.add("course.learning.mtls-truststore-type", () -> "PKCS12");
        registry.add("course.storage.root", () -> STORAGE_ROOT.toString());
    }

    @AfterAll
    static void stopLearningStub() {
        learningServer.stop(0);
    }

    @BeforeEach
    void clearCourseFacts() {
        LEARNING_RESPONSE.set(new LearningStubResponse(200, EMPTY_TASK_PAGE));
        jdbcTemplate.update("DELETE FROM course_file_delete_journal");
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
    void expiredBearerTokenFailsClosedAsSessionExpired() throws Exception {
        mockMvc.perform(get("/api/v1/courses")
                        .header("Authorization", TestJwtFactory.expiredUserToken(KEY_PAIR, "course-test-kid", "9011", List.of("TEACHER")))
                        .header("X-Request-Id", "a99f25d6-5ec1-4b2a-91c2-26e0d6a763c1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void unknownCourseDetailReturnsCourseNotFoundForAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/v1/courses/424242")
                        .header("Authorization", userToken("9012", List.of("STUDENT")))
                        .header("X-Request-Id", "0de25f9e-6d8c-493a-a538-109cdc0b18c2"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
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
        // A fixed past due-gate keeps the claim deterministic: comparing a DB
        // CURRENT_TIMESTAMP against Instant.now() is wall-clock sensitive and
        // intermittently leaves the row not-yet-due on slower CI runners.
        jdbcTemplate.update("UPDATE course_membership_reconciliation_checkpoint SET next_reconcile_at = '2020-01-01 00:00:00' WHERE course_id = ?", Long.parseLong(courseId));

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

    @Test
    void resourceDownloadGetIsReadOnlyAcrossBrowserPrefetchRetries() throws Exception {
        String teacher = userToken("971", List.of("TEACHER"));
        String student = userToken("972", List.of("STUDENT"));
        String courseId = createdCourse(teacher, "read only resource download");
        mockMvc.perform(post("/api/v1/courses/{courseId}/join", courseId)
                        .header("Authorization", student).header("X-Request-Id", requestId()))
                .andExpect(status().isOk());
        String resourceResponse = mockMvc.perform(post("/api/v1/courses/{courseId}/resources", courseId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"browser prefetch target\",\"url\":\"https://example.test/prefetch.pdf\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long resourceId = objectMapper.readTree(resourceResponse).at("/data/id").asLong();
        var resourceBefore = jdbcTemplate.queryForMap("SELECT download_count, version, updated_at FROM crs_resource WHERE id = ?", resourceId);
        int outboxBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM course_event_outbox", Integer.class);
        int memberBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM crs_course_member", Integer.class);

        for (int retry = 0; retry < 2; retry++) {
            mockMvc.perform(get("/api/v1/courses/{courseId}/resources/{resourceId}/download", courseId, resourceId)
                            .header("Authorization", student).header("X-Request-Id", requestId()))
                    .andExpect(status().is3xxRedirection());
        }

        assertThat(jdbcTemplate.queryForMap("SELECT download_count, version, updated_at FROM crs_resource WHERE id = ?", resourceId))
                .isEqualTo(resourceBefore);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM course_event_outbox", Integer.class)).isEqualTo(outboxBefore);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM crs_course_member", Integer.class)).isEqualTo(memberBefore);
    }

    @Test
    void chapterCreationReturnsItsGeneratedRowInsteadOfTheLastSortedChapter() throws Exception {
        String teacher = userToken("981", List.of("TEACHER"));
        String courseId = createdCourse(teacher, "generated chapter key");
        String existing = mockMvc.perform(post("/api/v1/courses/{courseId}/chapters", courseId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"existing last sorted row\",\"sortOrder\":99}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long existingId = objectMapper.readTree(existing).at("/data/id").asLong();

        String created = mockMvc.perform(post("/api/v1/courses/{courseId}/chapters", courseId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"new lower sort row\",\"sortOrder\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("new lower sort row"))
                .andReturn().getResponse().getContentAsString();
        long createdId = objectMapper.readTree(created).at("/data/id").asLong();

        assertThat(createdId).isNotEqualTo(existingId);
        assertThat(jdbcTemplate.queryForObject("SELECT chapter_name FROM crs_chapter WHERE id = ?", String.class, createdId))
                .isEqualTo("new lower sort row");
    }

    @Test
    void memberHomeSummaryReturnsCourseAnnouncementsAndRealRecentTasksFromLearning() throws Exception {
        String teacher = userToken("921", List.of("TEACHER"));
        String student = userToken("922", List.of("STUDENT"));
        String courseId = createdCourse(teacher, "home summary course");
        mockMvc.perform(post("/api/v1/courses/{courseId}/announcements", courseId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"pinned welcome\",\"content\":\"first\",\"top\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/courses/{courseId}/announcements", courseId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"plain update\",\"content\":\"second\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/courses/{courseId}/join", courseId)
                        .header("Authorization", student).header("X-Request-Id", requestId()))
                .andExpect(status().isOk());

        LEARNING_RESPONSE.set(new LearningStubResponse(200, """
                {"items":[
                  {"taskId":77,"taskType":"HOMEWORK","title":"Submit homework 1","courseId":%s,"courseName":"home summary course","deadline":"2026-09-03 10:00:00","progress":20,"status":"IN_PROGRESS","actionUrl":"/courses/%s/homeworks/77"},
                  {"taskId":88,"taskType":"EXPERIMENT","title":"Lab 1","courseId":%s,"courseName":"home summary course","deadline":"2026-09-05 10:00:00","progress":0,"status":"NOT_STARTED","actionUrl":"/courses/%s/labs/88"}
                ],"page":0,"size":5,"total":2}
                """.formatted(courseId, courseId, courseId, courseId)));

        mockMvc.perform(get("/api/v1/courses/{courseId}/home-summary", courseId)
                        .header("Authorization", student).header("X-Request-Id", requestId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.course.name").value("home summary course"))
                .andExpect(jsonPath("$.data.announcements.length()").value(2))
                .andExpect(jsonPath("$.data.announcements[0].title").value("pinned welcome"))
                .andExpect(jsonPath("$.data.announcements[0].top").value(true))
                .andExpect(jsonPath("$.data.recentTasks.length()").value(2))
                .andExpect(jsonPath("$.data.recentTasks[0].title").value("Submit homework 1"))
                .andExpect(jsonPath("$.data.recentTasks[0].taskType").value("HOMEWORK"))
                .andExpect(jsonPath("$.data.recentTasks[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.recentTasks[0].deadline").value("2026-09-03 10:00:00"))
                .andExpect(jsonPath("$.data.recentTasks[1].title").value("Lab 1"));
    }

    @Test
    void memberHomeSummaryShowsEmptyTaskSectionWhenLearningReturnsNoTasks() throws Exception {
        String teacher = userToken("925", List.of("TEACHER"));
        String student = userToken("926", List.of("STUDENT"));
        String courseId = createdCourse(teacher, "no task home summary");
        mockMvc.perform(post("/api/v1/courses/{courseId}/join", courseId)
                        .header("Authorization", student).header("X-Request-Id", requestId()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/courses/{courseId}/home-summary", courseId)
                        .header("Authorization", student).header("X-Request-Id", requestId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.course.name").value("no task home summary"))
                .andExpect(jsonPath("$.data.recentTasks").isEmpty());
    }

    @Test
    void memberHomeSummaryFailsClosedWhenLearningTaskSummaryIsUnavailable() throws Exception {
        String teacher = userToken("927", List.of("TEACHER"));
        String student = userToken("928", List.of("STUDENT"));
        String courseId = createdCourse(teacher, "unavailable task home summary");
        mockMvc.perform(post("/api/v1/courses/{courseId}/join", courseId)
                        .header("Authorization", student).header("X-Request-Id", requestId()))
                .andExpect(status().isOk());

        LEARNING_RESPONSE.set(new LearningStubResponse(503,
                "{\"code\":\"LEARNING_TASKS_UNAVAILABLE\",\"message\":\"learning unavailable\",\"requestId\":\"00000000-0000-0000-0000-000000000000\",\"retryable\":true}"));

        mockMvc.perform(get("/api/v1/courses/{courseId}/home-summary", courseId)
                        .header("Authorization", student).header("X-Request-Id", requestId()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("LEARNING_TASKS_UNAVAILABLE"))
                .andExpect(jsonPath("$.retryable").value(true));
    }

    @Test
    void nonMemberHomeSummaryIsDenied() throws Exception {
        String teacher = userToken("931", List.of("TEACHER"));
        String courseId = createdCourse(teacher, "member only home summary");
        mockMvc.perform(get("/api/v1/courses/{courseId}/home-summary", courseId)
                        .header("Authorization", userToken("932", List.of("STUDENT")))
                        .header("X-Request-Id", requestId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COURSE_ACCESS_FORBIDDEN"));
    }

    @Test
    void archivedAndClosedCoursesRejectContentMutationsWhileStayingReadable() throws Exception {
        String teacher = userToken("941", List.of("TEACHER"));
        String courseId = createdCourse(teacher, "read only after archive");
        mockMvc.perform(delete("/api/v1/courses/{courseId}", courseId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ARCHIVED"));

        mockMvc.perform(post("/api/v1/courses/{courseId}/chapters", courseId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"forbidden chapter\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COURSE_READ_ONLY"));
        mockMvc.perform(post("/api/v1/courses/{courseId}/resources", courseId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"forbidden resource\",\"url\":\"https://example.test/x.pdf\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COURSE_READ_ONLY"));
        mockMvc.perform(post("/api/v1/courses/{courseId}/announcements", courseId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"forbidden\",\"content\":\"x\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COURSE_READ_ONLY"));
        mockMvc.perform(put("/api/v1/courses/{courseId}", courseId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"renamed after archive\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COURSE_READ_ONLY"));
        mockMvc.perform(delete("/api/v1/courses/{courseId}", courseId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COURSE_READ_ONLY"));

        mockMvc.perform(get("/api/v1/courses/{courseId}/announcements", courseId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        mockMvc.perform(get("/api/v1/courses/{courseId}/chapters", courseId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());

        String closedId = createdCourse(teacher, "closed by status");
        mockMvc.perform(put("/api/v1/courses/{courseId}", closedId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CLOSED"));
        mockMvc.perform(post("/api/v1/courses/{courseId}/announcements", closedId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"closed\",\"content\":\"x\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COURSE_READ_ONLY"));
    }

    @Test
    void archivedAndClosedCoursesRejectMemberMutationsAndLeaveWhileStayingReadable() throws Exception {
        String teacher = userToken("944", List.of("TEACHER"));
        String student = userToken("945", List.of("STUDENT"));
        String archivedId = createdCourse(teacher, "member read only after archive");
        mockMvc.perform(post("/api/v1/courses/{courseId}/join", archivedId)
                        .header("Authorization", student).header("X-Request-Id", requestId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACTIVE"));
        mockMvc.perform(delete("/api/v1/courses/{courseId}", archivedId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ARCHIVED"));

        mockMvc.perform(put("/api/v1/courses/{courseId}/members/{userId}", archivedId, "945")
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ASSISTANT\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COURSE_READ_ONLY"));
        mockMvc.perform(delete("/api/v1/courses/{courseId}/members/{userId}", archivedId, "945")
                        .header("Authorization", teacher).header("X-Request-Id", requestId()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COURSE_READ_ONLY"));
        mockMvc.perform(post("/api/v1/courses/{courseId}/leave", archivedId)
                        .header("Authorization", student).header("X-Request-Id", requestId()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COURSE_READ_ONLY"));
        assertThat(jdbcTemplate.queryForMap(
                "SELECT role, join_status, member_version FROM crs_course_member WHERE course_id = ? AND user_id = ?",
                Long.parseLong(archivedId), 945L))
                .containsEntry("role", "STUDENT").containsEntry("join_status", "ACTIVE").containsEntry("member_version", 1L);

        String closedId = createdCourse(teacher, "member read only when closed");
        String closedStudent = userToken("946", List.of("STUDENT"));
        mockMvc.perform(post("/api/v1/courses/{courseId}/join", closedId)
                        .header("Authorization", closedStudent).header("X-Request-Id", requestId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACTIVE"));
        mockMvc.perform(put("/api/v1/courses/{courseId}", closedId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CLOSED"));
        mockMvc.perform(post("/api/v1/courses/{courseId}/leave", closedId)
                        .header("Authorization", closedStudent).header("X-Request-Id", requestId()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COURSE_READ_ONLY"));
        mockMvc.perform(put("/api/v1/courses/{courseId}/members/{userId}", closedId, "946")
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"STUDENT\",\"status\":\"REMOVED\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COURSE_READ_ONLY"));
    }

    @Test
    void memberStatusChangesFollowEnrollmentStateMachineAndRejectDirectActivation() throws Exception {
        String teacher = userToken("751", List.of("TEACHER"));
        String student = userToken("752", List.of("STUDENT"));
        String create = mockMvc.perform(post("/api/v1/courses")
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"member state machine\",\"enrollmentMode\":\"REVIEW\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String courseId = objectMapper.readTree(create).at("/data/id").asText();

        mockMvc.perform(post("/api/v1/courses/{courseId}/join", courseId)
                        .header("Authorization", student).header("X-Request-Id", requestId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING"));

        mockMvc.perform(put("/api/v1/courses/{courseId}/members/{userId}", courseId, "752")
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"STUDENT\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACTIVE"));
        mockMvc.perform(put("/api/v1/courses/{courseId}/members/{userId}", courseId, "752")
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"PENDING\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_MEMBER_STATUS_TRANSITION"));
        mockMvc.perform(put("/api/v1/courses/{courseId}/members/{userId}", courseId, "752")
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_MEMBER_STATUS_TRANSITION"));
        mockMvc.perform(put("/api/v1/courses/{courseId}/members/{userId}", courseId, "752")
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REMOVED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REMOVED"));

        int outboxBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM course_event_outbox", Integer.class);
        mockMvc.perform(put("/api/v1/courses/{courseId}/members/{userId}", courseId, "752")
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"STUDENT\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_MEMBER_STATUS_TRANSITION"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM course_event_outbox", Integer.class))
                .isEqualTo(outboxBefore);

        mockMvc.perform(post("/api/v1/courses/{courseId}/join", courseId)
                        .header("Authorization", student).header("X-Request-Id", requestId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING"));
        mockMvc.perform(put("/api/v1/courses/{courseId}/members/{userId}", courseId, "752")
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"STUDENT\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACTIVE"));

        String secondStudent = userToken("753", List.of("STUDENT"));
        mockMvc.perform(post("/api/v1/courses/{courseId}/join", courseId)
                        .header("Authorization", secondStudent).header("X-Request-Id", requestId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING"));
        mockMvc.perform(put("/api/v1/courses/{courseId}/members/{userId}", courseId, "753")
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REJECTED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REJECTED"));
        mockMvc.perform(put("/api/v1/courses/{courseId}/members/{userId}", courseId, "753")
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"STUDENT\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_MEMBER_STATUS_TRANSITION"));
        mockMvc.perform(post("/api/v1/courses/{courseId}/join", courseId)
                        .header("Authorization", secondStudent).header("X-Request-Id", requestId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING"));
        mockMvc.perform(put("/api/v1/courses/{courseId}/members/{userId}", courseId, "753")
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"STUDENT\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void courseOwnerCannotChangeOwnTeacherIdentityInMultiTeacherCourse() throws Exception {
        String owner = userToken("761", List.of("TEACHER"));
        String secondTeacher = userToken("762", List.of("TEACHER"));
        String courseId = createdCourse(owner, "owner teacher identity lock");
        mockMvc.perform(post("/api/v1/courses/{courseId}/join", courseId)
                        .header("Authorization", secondTeacher).header("X-Request-Id", requestId()))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/courses/{courseId}/members/{userId}", courseId, "762")
                        .header("Authorization", owner).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"TEACHER\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.role").value("TEACHER"));

        mockMvc.perform(put("/api/v1/courses/{courseId}/members/{userId}", courseId, "761")
                        .header("Authorization", owner).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ASSISTANT\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CANNOT_CHANGE_SELF_TEACHER"));
        mockMvc.perform(put("/api/v1/courses/{courseId}/members/{userId}", courseId, "761")
                        .header("Authorization", owner).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"TEACHER\",\"status\":\"REMOVED\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CANNOT_CHANGE_SELF_TEACHER"));
        mockMvc.perform(delete("/api/v1/courses/{courseId}/members/{userId}", courseId, "761")
                        .header("Authorization", owner).header("X-Request-Id", requestId()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CANNOT_CHANGE_SELF_TEACHER"));

        assertThat(jdbcTemplate.queryForMap(
                "SELECT role, join_status FROM crs_course_member WHERE course_id = ? AND user_id = ?",
                Long.parseLong(courseId), 761L))
                .containsEntry("role", "TEACHER").containsEntry("join_status", "ACTIVE");
    }

    @Test
    void resourceUploadRejectsDisguisedExecutablesAndUnmatchedContentWithoutLeavingRecords() throws Exception {
        String teacher = userToken("771", List.of("TEACHER"));
        String courseId = createdCourse(teacher, "resource content safety");

        byte[] pe = new byte[]{'M', 'Z', 0, 0, 0, 0, (byte) 0x90, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        mockMvc.perform(multipart("/api/v1/courses/{courseId}/resources", courseId)
                        .file(new MockMultipartFile("file", "slides.pdf", "application/pdf", pe))
                        .param("name", "slides.pdf").param("resourceType", "DOCUMENT").param("visibility", "STUDENT")
                        .header("Authorization", teacher).header("X-Request-Id", requestId()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RESOURCE_INVALID"));

        byte[] elf = new byte[]{0x7F, 'E', 'L', 'F', 2, 1, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0};
        mockMvc.perform(multipart("/api/v1/courses/{courseId}/resources", courseId)
                        .file(new MockMultipartFile("file", "notes.pdf", "application/pdf", elf))
                        .param("name", "notes.pdf")
                        .header("Authorization", teacher).header("X-Request-Id", requestId()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RESOURCE_INVALID"));

        mockMvc.perform(multipart("/api/v1/courses/{courseId}/resources", courseId)
                        .file(new MockMultipartFile("file", "run.txt", "text/plain",
                                "#!/bin/sh\nrm -rf /tmp/pwn\n".getBytes(StandardCharsets.UTF_8)))
                        .param("name", "run.txt")
                        .header("Authorization", teacher).header("X-Request-Id", requestId()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RESOURCE_INVALID"));

        mockMvc.perform(multipart("/api/v1/courses/{courseId}/resources", courseId)
                        .file(new MockMultipartFile("file", "notes.pdf", "application/pdf",
                                "plain text is not a pdf".getBytes(StandardCharsets.UTF_8)))
                        .param("name", "notes.pdf")
                        .header("Authorization", teacher).header("X-Request-Id", requestId()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("RESOURCE_INVALID"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM crs_resource WHERE course_id = ?", Integer.class, Long.parseLong(courseId))).isZero();

        mockMvc.perform(multipart("/api/v1/courses/{courseId}/resources", courseId)
                        .file(new MockMultipartFile("file", "slides.pdf", "application/pdf",
                                "%PDF-1.4\n1 0 obj\n%%EOF\n".getBytes(StandardCharsets.UTF_8)))
                        .param("name", "slides.pdf").param("resourceType", "DOCUMENT").param("visibility", "STUDENT")
                        .header("Authorization", teacher).header("X-Request-Id", requestId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("slides.pdf"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM crs_resource WHERE course_id = ?", Integer.class, Long.parseLong(courseId))).isEqualTo(1);
    }

    @Test
    void siblingChapterOrderConflictIsRejectedWith409ForCreateUpdateAndDifferentParents() throws Exception {
        String teacher = userToken("601", List.of("TEACHER"));
        String courseId = createdCourse(teacher, "chapter order conflict");

        String first = mockMvc.perform(post("/api/v1/courses/{courseId}/chapters", courseId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"first root\",\"sortOrder\":5}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String firstId = objectMapper.readTree(first).at("/data/id").asText();

        mockMvc.perform(post("/api/v1/courses/{courseId}/chapters", courseId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"duplicate root order\",\"sortOrder\":5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHAPTER_ORDER_CONFLICT"));

        String second = mockMvc.perform(post("/api/v1/courses/{courseId}/chapters", courseId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"second root\",\"sortOrder\":6}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String secondId = objectMapper.readTree(second).at("/data/id").asText();

        // A different parent is a different ordering group, so the order may repeat.
        mockMvc.perform(post("/api/v1/courses/{courseId}/chapters", courseId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"child of first\",\"parentId\":\"" + firstId + "\",\"sortOrder\":5}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/courses/{courseId}/chapters/{chapterId}", courseId, secondId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sortOrder\":5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHAPTER_ORDER_CONFLICT"));

        // Keeping an existing order on update stays valid (self is excluded).
        mockMvc.perform(put("/api/v1/courses/{courseId}/chapters/{chapterId}", courseId, firstId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sortOrder\":5}"))
                .andExpect(status().isOk());
    }

    @Test
    void loweringCourseCapacityBelowActiveRosterIsRejectedWhileTheCourseRowLockGuardsIt() throws Exception {
        String teacher = userToken("611", List.of("TEACHER"));
        String create = mockMvc.perform(post("/api/v1/courses")
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"capacity guard\",\"maxStudents\":2}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String courseId = objectMapper.readTree(create).at("/data/id").asText();

        mockMvc.perform(post("/api/v1/courses/{courseId}/join", courseId)
                        .header("Authorization", userToken("612", List.of("STUDENT"))).header("X-Request-Id", requestId()))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/courses/{courseId}", courseId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"maxStudents\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COURSE_CAPACITY_BELOW_ROSTER"));

        mockMvc.perform(put("/api/v1/courses/{courseId}", courseId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"maxStudents\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.maxStudents").value(2));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT max_students FROM crs_course WHERE id = ?", Integer.class, Long.parseLong(courseId))).isEqualTo(2);
    }

    @Test
    void rejoiningRemovedAssistantResetsToStudentAndNeverRestoresManagement() throws Exception {
        String teacher = userToken("621", List.of("TEACHER"));
        String assistant = userToken("622", List.of("TEACHER"));
        String courseId = createdCourse(teacher, "rejoin resets role");
        mockMvc.perform(post("/api/v1/courses/{courseId}/join", courseId)
                        .header("Authorization", assistant).header("X-Request-Id", requestId()))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/courses/{courseId}/members/{userId}", courseId, "622")
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ASSISTANT\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/v1/courses/{courseId}/members/{userId}", courseId, "622")
                        .header("Authorization", teacher).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ASSISTANT\",\"status\":\"REMOVED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REMOVED"));

        mockMvc.perform(post("/api/v1/courses/{courseId}/join", courseId)
                        .header("Authorization", assistant).header("X-Request-Id", requestId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        mockMvc.perform(post("/api/v1/courses/{courseId}/chapters", courseId)
                        .header("Authorization", assistant).header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"not allowed\"}"))
                .andExpect(status().isForbidden());

        assertThat(jdbcTemplate.queryForMap(
                "SELECT role, join_status FROM crs_course_member WHERE course_id = ? AND user_id = ?",
                Long.parseLong(courseId), 622L))
                .containsEntry("role", "STUDENT").containsEntry("join_status", "ACTIVE");
    }

    @Test
    void deletingUploadedResourceRemovesItsPhysicalObjectAndCompletesTheJournal() throws Exception {
        String teacher = userToken("631", List.of("TEACHER"));
        String courseId = createdCourse(teacher, "physical file delete");
        String response = mockMvc.perform(multipart("/api/v1/courses/{courseId}/resources", courseId)
                        .file(new MockMultipartFile("file", "notes.txt", "text/plain", "delete me".getBytes(StandardCharsets.UTF_8)))
                        .param("name", "notes.txt").param("resourceType", "DOCUMENT").param("visibility", "STUDENT")
                        .header("Authorization", teacher).header("X-Request-Id", requestId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String resourceId = objectMapper.readTree(response).at("/data/id").asText();
        String storageKey = jdbcTemplate.queryForObject(
                "SELECT storage_key FROM crs_resource WHERE id = ?", String.class, Long.parseLong(resourceId));
        Path stored = STORAGE_ROOT.resolve(storageKey);
        assertThat(stored).exists();

        mockMvc.perform(delete("/api/v1/courses/{courseId}/resources/{resourceId}", courseId, resourceId)
                        .header("Authorization", teacher).header("X-Request-Id", requestId()))
                .andExpect(status().isOk());

        assertThat(stored).doesNotExist();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM course_file_delete_journal WHERE resource_id = ?", String.class, Long.parseLong(resourceId)))
                .isEqualTo("COMPLETED");
    }

    @Test
    void pendingFileDeleteJournalEntriesAreRecoveredByTheSweep() throws Exception {
        Path orphan = STORAGE_ROOT.resolve("orphan-recovery.txt");
        Files.writeString(orphan, "orphan");
        jdbcTemplate.update("""
                INSERT INTO course_file_delete_journal (course_id, resource_id, storage_key, status)
                VALUES (1, 424242, ?, 'PENDING')
                """, "orphan-recovery.txt");

        assertThat(courseService.recoverPendingFileDeletions()).isEqualTo(1);
        assertThat(orphan).doesNotExist();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM course_file_delete_journal WHERE resource_id = 424242", String.class))
                .isEqualTo("COMPLETED");
    }

    @Test
    void internalV2EndpointsAcceptTrustedMtlsWorkloadCertificateWithoutServiceJwt() throws Exception {
        String courseResponse = mockMvc.perform(post("/api/v1/courses")
                        .header("Authorization", userToken("641", List.of("TEACHER")))
                        .header("X-Request-Id", requestId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"mtls internal contract\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String courseId = objectMapper.readTree(courseResponse).at("/data/id").asText();

        mockMvc.perform(get("/internal/v2/courses/{courseId}/members?page=0&size=20", courseId)
                        .header("X-Request-Id", requestId())
                        .requestAttr("jakarta.servlet.request.X509Certificate",
                                new X509Certificate[]{certificateWithSubject("CN=course-service")}))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].userId").value("641"));

        mockMvc.perform(get("/internal/v2/courses/{courseId}/members?page=0&size=20", courseId)
                        .header("X-Request-Id", requestId())
                        .requestAttr("jakarta.servlet.request.X509Certificate",
                                new X509Certificate[]{certificateWithSubject("CN=unknown-workload")}))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SERVICE_IDENTITY_FORBIDDEN"));
    }

    private static void generateMtlsKeystores(Path dir) throws Exception {
        keytool("-genkeypair", "-alias", "server", "-keyalg", "RSA", "-keysize", "2048",
                "-dname", "CN=backend", "-ext", "SAN=ip:127.0.0.1", "-validity", "3650",
                "-storetype", "PKCS12", "-keystore", dir.resolve("server.p12").toString(),
                "-storepass", "changeit", "-keypass", "changeit");
        keytool("-genkeypair", "-alias", "client", "-keyalg", "RSA", "-keysize", "2048",
                "-dname", "CN=course-service", "-validity", "3650",
                "-storetype", "PKCS12", "-keystore", dir.resolve("client.p12").toString(),
                "-storepass", "changeit", "-keypass", "changeit");
        keytool("-exportcert", "-alias", "server", "-file", dir.resolve("server.cer").toString(),
                "-keystore", dir.resolve("server.p12").toString(), "-storepass", "changeit");
        keytool("-exportcert", "-alias", "client", "-file", dir.resolve("client.cer").toString(),
                "-keystore", dir.resolve("client.p12").toString(), "-storepass", "changeit");
        keytool("-importcert", "-noprompt", "-alias", "server", "-file", dir.resolve("server.cer").toString(),
                "-storetype", "PKCS12", "-keystore", dir.resolve("client-trust.p12").toString(), "-storepass", "changeit");
        keytool("-importcert", "-noprompt", "-alias", "client", "-file", dir.resolve("client.cer").toString(),
                "-storetype", "PKCS12", "-keystore", dir.resolve("server-trust.p12").toString(), "-storepass", "changeit");
    }

    private static void keytool(String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "keytool").toString());
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            throw new IllegalStateException("keytool failed: " + output);
        }
    }

    private static SSLContext serverSslContext(Path dir) throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(dir.resolve("server.p12"))) {
            keyStore.load(in, "changeit".toCharArray());
        }
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keyStore, "changeit".toCharArray());
        KeyStore trustStore = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(dir.resolve("server-trust.p12"))) {
            trustStore.load(in, "changeit".toCharArray());
        }
        TrustManagerFactory trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagers.init(trustStore);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers.getKeyManagers(), trustManagers.getTrustManagers(), null);
        return context;
    }

    private static String peerSubject(com.sun.net.httpserver.HttpsExchange exchange) throws IOException {
        try {
            X509Certificate peer = (X509Certificate) exchange.getSSLSession().getPeerCertificates()[0];
            return peer.getSubjectX500Principal().getName();
        } catch (Exception failure) {
            throw new IOException(failure);
        }
    }

    private static X509Certificate certificateWithSubject(String subject) {
        return new X509Certificate() {
            @Override
            public X500Principal getSubjectX500Principal() {
                return new X500Principal(subject);
            }
            @Override public java.security.Principal getIssuerDN() { throw new UnsupportedOperationException(); }
            @Override public java.security.Principal getSubjectDN() { throw new UnsupportedOperationException(); }
            @Override public java.util.Date getNotAfter() { throw new UnsupportedOperationException(); }
            @Override public java.util.Date getNotBefore() { throw new UnsupportedOperationException(); }
            @Override public java.math.BigInteger getSerialNumber() { throw new UnsupportedOperationException(); }
            @Override public String getSigAlgName() { throw new UnsupportedOperationException(); }
            @Override public String getSigAlgOID() { throw new UnsupportedOperationException(); }
            @Override public byte[] getSigAlgParams() { throw new UnsupportedOperationException(); }
            @Override public int getVersion() { throw new UnsupportedOperationException(); }
            @Override public byte[] getTBSCertificate() { throw new UnsupportedOperationException(); }
            @Override public byte[] getSignature() { throw new UnsupportedOperationException(); }
            @Override public boolean[] getSubjectUniqueID() { throw new UnsupportedOperationException(); }
            @Override public boolean[] getIssuerUniqueID() { throw new UnsupportedOperationException(); }
            @Override public boolean[] getKeyUsage() { throw new UnsupportedOperationException(); }
            @Override public List<String> getExtendedKeyUsage() { throw new UnsupportedOperationException(); }
            @Override public int getBasicConstraints() { throw new UnsupportedOperationException(); }
            @Override public java.util.Collection<List<?>> getSubjectAlternativeNames() { throw new UnsupportedOperationException(); }
            @Override public java.util.Collection<List<?>> getIssuerAlternativeNames() { throw new UnsupportedOperationException(); }
            @Override public void checkValidity() { throw new UnsupportedOperationException(); }
            @Override public void checkValidity(java.util.Date date) { throw new UnsupportedOperationException(); }
            @Override public void verify(java.security.PublicKey key) { throw new UnsupportedOperationException(); }
            @Override public void verify(java.security.PublicKey key, String sigProvider) { throw new UnsupportedOperationException(); }
            @Override public byte[] getEncoded() { throw new UnsupportedOperationException(); }
            @Override public java.security.PublicKey getPublicKey() { throw new UnsupportedOperationException(); }
            @Override public String toString() { throw new UnsupportedOperationException(); }
            @Override public boolean hasUnsupportedCriticalExtension() { throw new UnsupportedOperationException(); }
            @Override public java.util.Set<String> getCriticalExtensionOIDs() { throw new UnsupportedOperationException(); }
            @Override public java.util.Set<String> getNonCriticalExtensionOIDs() { throw new UnsupportedOperationException(); }
            @Override public byte[] getExtensionValue(String oid) { throw new UnsupportedOperationException(); }
        };
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

    private record LearningStubResponse(int status, String body) {
    }
}
