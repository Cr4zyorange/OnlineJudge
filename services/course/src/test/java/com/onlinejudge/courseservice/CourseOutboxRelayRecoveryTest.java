package com.onlinejudge.courseservice;

import com.onlinejudge.courseservice.config.CourseRabbitProperties;
import com.onlinejudge.courseservice.persistence.CourseOutboxRepository;
import com.onlinejudge.courseservice.service.CourseOutboxRelay;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Requires the disposable RabbitMQ mapped to 127.0.0.1:33327 by the runtime acceptance command. */
@SpringBootTest(classes = CourseServiceApplication.class)
@EnabledIfSystemProperty(named = "course.test.rabbit", matches = "true")
class CourseOutboxRelayRecoveryTest {
    @Autowired private CourseOutboxRepository outbox;
    @Autowired private CourseOutboxRelay relay;
    @Autowired private CourseRabbitProperties rabbit;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void before() {
        rabbit.setEnabled(false);
        jdbcTemplate.update("DELETE FROM course_event_outbox");
    }

    @AfterEach
    void after() {
        rabbit.setEnabled(false);
    }

    @Test
    void unavailableBrokerLeavesCourseFactPendingAndRecoveryPublishesTheSameDurableEvent() {
        outbox.append("course.member.changed.v2", "course-member", "42:99", 1,
                "be087a98-88e6-4dac-9486-5c50b4231b4d",
                Map.of("courseId", "42", "userId", "99", "membershipStatus", "ACTIVE", "memberVersion", 1));
        rabbit.setEnabled(true);
        rabbit.setHost("127.0.0.1");
        rabbit.setPort(1);
        relay.relay();

        assertThat(jdbcTemplate.queryForObject("SELECT delivery_status FROM course_event_outbox", String.class)).isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject("SELECT attempt_count FROM course_event_outbox", Integer.class)).isEqualTo(1);

        jdbcTemplate.update("UPDATE course_event_outbox SET next_attempt_at = CURRENT_TIMESTAMP");
        rabbit.setPort(33327);
        relay.relay();

        assertThat(jdbcTemplate.queryForObject("SELECT delivery_status FROM course_event_outbox", String.class)).isEqualTo("PUBLISHED");
        assertThat(jdbcTemplate.queryForObject("SELECT attempt_count FROM course_event_outbox", Integer.class)).isEqualTo(1);
    }
}
