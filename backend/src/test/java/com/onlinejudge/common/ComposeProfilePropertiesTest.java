package com.onlinejudge.common;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class ComposeProfilePropertiesTest {
    @Test
    void composeProfileUsesMysqlAndLeavesBootstrapToMysqlContainer() throws IOException {
        Properties properties = new Properties();
        try (InputStream inputStream = getClass().getResourceAsStream("/application-compose.properties")) {
            assertThat(inputStream).as("application-compose.properties should exist").isNotNull();
            properties.load(inputStream);
        }

        assertThat(properties.getProperty("spring.datasource.url")).startsWith("jdbc:mysql://");
        assertThat(properties.getProperty("spring.datasource.driver-class-name")).isEqualTo("com.mysql.cj.jdbc.Driver");
        assertThat(properties.getProperty("spring.sql.init.mode")).isEqualTo("never");
        assertThat(properties).doesNotContainKey("spring.sql.init.schema-locations");
        assertThat(properties.getProperty("spring.datasource.hikari.initialization-fail-timeout")).isEqualTo("60000");
        assertThat(properties.getProperty("onlinejudge.course.schema-initializer.enabled")).isEqualTo("false");
        assertThat(properties.getProperty("onlinejudge.storage.local-root")).isEqualTo("/opt/onlinejudge/data/uploads");
    }
}
