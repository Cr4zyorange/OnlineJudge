package com.onlinejudge.hwk.domain;

import java.util.List;
import java.util.Optional;

public interface HomeworkRepository {
    Homework save(Homework homework);

    Homework update(Homework homework);

    Optional<Homework> findById(long id);

    List<Homework> findByCourseId(long courseId);
}
