package com.onlinejudge.lrn.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class LearningTaskDefaultConfigurationTest {
    private static final String LEARNING_TASK_MIGRATION =
            "file:../database/migrations/20260530_01_create_lrn_learning_task.sql";
    private static final String LEARNING_PROGRESS_MIGRATION =
            "file:../database/migrations/20260531_01_create_lrn_learning_progress.sql";
    private static final String LEARNING_RECORD_MIGRATION =
            "file:../database/migrations/20260602_01_create_lrn_learning_record.sql";

    @Test
    void defaultRuntimeSchemaLocationsIncludeLearningTaskMigration() throws IOException {
        Properties runtimeProperties = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of("src/main/resources/application.properties"))) {
            runtimeProperties.load(reader);
        }

        String schemaLocations = runtimeProperties.getProperty("spring.sql.init.schema-locations", "");

        assertThat(Arrays.stream(schemaLocations.split(",")).map(String::trim))
                .contains(LEARNING_TASK_MIGRATION, LEARNING_PROGRESS_MIGRATION, LEARNING_RECORD_MIGRATION);
    }
}
