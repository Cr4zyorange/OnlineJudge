package com.onlinejudge.integration.grade;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

@Primary
@Component
public class DefaultSourceGradeClient implements SourceGradeClient {
    private final List<SourceGradeProvider> providers;
    private final DemoSourceGradeClient fallbackSourceGradeClient;

    public DefaultSourceGradeClient(
            List<SourceGradeProvider> providers,
            DemoSourceGradeClient fallbackSourceGradeClient
    ) {
        this.providers = providers;
        this.fallbackSourceGradeClient = fallbackSourceGradeClient;
    }

    @Override
    public List<SourceGradeDTO> findSourceGrades(long courseId, SourceGradeType sourceType, long sourceId) {
        for (SourceGradeProvider provider : providers) {
            if (!provider.supports(sourceType)) {
                continue;
            }
            var sourceGrades = provider.findSourceGrades(courseId, sourceId);
            if (sourceGrades.isPresent()) {
                return sourceGrades.get();
            }
        }
        if (sourceType != SourceGradeType.LAB) {
            return List.of();
        }
        return fallbackSourceGradeClient.findSourceGrades(courseId, sourceType, sourceId);
    }
}
