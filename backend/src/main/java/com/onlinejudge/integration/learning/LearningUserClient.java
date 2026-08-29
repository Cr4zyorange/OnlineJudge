package com.onlinejudge.integration.learning;

import java.util.Collection;
import java.util.Map;
import java.util.OptionalLong;

public interface LearningUserClient {
    Map<Long, String> findDisplayNames(Collection<Long> userIds);
    OptionalLong findUserIdByUsername(String username);
}
