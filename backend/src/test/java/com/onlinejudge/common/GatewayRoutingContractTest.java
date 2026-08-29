package com.onlinejudge.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayRoutingContractTest {
    private static final Path TEMPLATE = Path.of("..", "deploy", "nginx", "gateway.conf.template");

    @Test
    void gatewayRoutesEveryFrozenPublicApiFamilyWithoutCoursePrefixConflicts() throws IOException {
        String template = Files.readString(TEMPLATE);

        assertThat(template).contains(
                "location = /api/v1/courses",
                "location /api/v1/courses/",
                "location = /api/v1/homeworks",
                "location ^~ /api/v1/homeworks/",
                "location = /api/v1/notifications",
                "location ^~ /api/v1/notifications/",
                "location = /api/v1/reminder-rules",
                "location ^~ /api/v1/grade-items/",
                "location ^~ /api/v1/grade-records/",
                "location ^~ /api/v1/course-grade-summaries/",
                "location ^~ /api/v1/grade-review-requests/",
                "/(grades|grade-items|grade-rules|grade-publish-records|grade-change-logs|my-grades|grade-analysis|grade-review-requests|my-grade-review-requests)(/|$)");
        assertThat(template.indexOf("/(labs|homeworks)"))
                .isLessThan(template.indexOf("location /api/v1/courses/"));
        assertThat(template.indexOf("/(grades|grade-items|grade-rules"))
                .isLessThan(template.indexOf("location /api/v1/courses/"));
        assertThat(template).doesNotContain("location ^~ /api/v1/courses/");
    }

    @Test
    void gatewayForwardsBearerButClearsAllBrowserSuppliedIdentityHeaders() throws IOException {
        String template = Files.readString(TEMPLATE);

        assertThat(template).contains(
                "proxy_set_header Authorization $http_authorization;",
                "proxy_set_header X-User-Id \"\";",
                "proxy_set_header X-Username \"\";",
                "proxy_set_header X-User-Role \"\";",
                "proxy_set_header X-Permissions \"\";",
                "proxy_set_header X-Course-Ids \"\";",
                "proxy_set_header X-Manageable-Course-Ids \"\";");
        assertThat(template).doesNotContain("proxy_set_header X-User-Id $http_");
    }

    @Test
    void gatewayDefinesUploadTimeoutFailureAndSpaBoundaries() throws IOException {
        String template = Files.readString(TEMPLATE);

        assertThat(template).contains(
                "client_max_body_size 55m;",
                "proxy_connect_timeout 5s;",
                "proxy_read_timeout 60s;",
                "proxy_send_timeout 60s;",
                "proxy_read_timeout 300s;",
                "proxy_send_timeout 300s;",
                "proxy_next_upstream off;",
                "error_page 502 = @gateway_bad_gateway;",
                "error_page 504 = @gateway_gateway_timeout;",
                "\"code\":\"GATEWAY_502\"",
                "\"code\":\"GATEWAY_504\"",
                "location /api/ { proxy_pass http://backend:8080; }",
                "try_files $uri /index.html;");
    }
}
