package com.onlinejudge.crs.config;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

@Configuration
@ConditionalOnProperty(name = "onlinejudge.course.schema-initializer.enabled", havingValue = "true", matchIfMissing = true)
public class CourseSchemaInitializer {
    private final DataSource dataSource;

    public CourseSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void initialize() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(false, false, "UTF-8", new ClassPathResource("schema.sql"));
        populator.execute(dataSource);
    }
}
