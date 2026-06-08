package com.onlinejudge.integration.grade;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;

@Primary
@Component
public class DefaultSourceGradeClient implements SourceGradeClient {
    private final List<SourceGradeProvider> providers;

    public DefaultSourceGradeClient(List<SourceGradeProvider> providers) {
        this.providers = providers;
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
        return List.of();
    }
}
