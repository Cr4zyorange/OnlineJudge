package com.onlinejudge.hwk.service;

import com.onlinejudge.hwk.domain.CreateHomeworkSubmissionCommand;
import com.onlinejudge.hwk.domain.Homework;
import com.onlinejudge.hwk.domain.HomeworkEvaluation;
import com.onlinejudge.hwk.domain.HomeworkEvaluationRepository;
import com.onlinejudge.hwk.domain.HomeworkQuestion;
import com.onlinejudge.hwk.domain.HomeworkRepository;
import com.onlinejudge.hwk.domain.HomeworkReviewLog;
import com.onlinejudge.hwk.domain.HomeworkReviewLogRepository;
import com.onlinejudge.hwk.domain.HomeworkStatus;
import com.onlinejudge.hwk.domain.HomeworkSubmission;
import com.onlinejudge.hwk.domain.HomeworkSubmissionRepository;
import com.onlinejudge.hwk.domain.HomeworkSubmissionSearchCriteria;
import com.onlinejudge.hwk.domain.HomeworkTestCase;
import com.onlinejudge.hwk.domain.HomeworkType;
import com.onlinejudge.common.web.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HomeworkSubmissionServiceTest {
    @Test
    void submitReturnsControlledConflictWhenSubmissionVersionIsAlreadyUsed() {
        HomeworkSubmissionService service = new HomeworkSubmissionService(
                new SingleHomeworkRepository(publishedTextHomework()),
                new DuplicateVersionSubmissionRepository(),
                new UnusedHomeworkEvaluationRepository(),
                new UnusedHomeworkReviewLogRepository(),
                (courseId, userId) -> true,
                task -> {
                    throw new UnsupportedOperationException();
                }
        );

        assertThatThrownBy(() -> service.submit(
                11,
                601,
                new CreateHomeworkSubmissionCommand("answer", null, null, null, null)
        ))
                .isInstanceOf(HomeworkApiException.class)
                .extracting("code")
                .isEqualTo("HWK_4006");
    }

    private Homework publishedTextHomework() {
        LocalDateTime now = LocalDateTime.now();
        return new Homework(
                11,
                101,
                null,
                "HWK03 text homework",
                "Explain your solution.",
                HomeworkType.TEXT,
                HomeworkStatus.PUBLISHED,
                100,
                now.plusDays(7),
                true,
                false,
                true,
                null,
                501,
                now,
                false,
                now,
                now,
                List.of(),
                List.of(),
                null
        );
    }

    private record SingleHomeworkRepository(Homework homework) implements HomeworkRepository {
        @Override
        public Homework save(Homework homework) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Homework> update(Homework homework) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Homework> replaceQuestions(long homeworkId, List<HomeworkQuestion> questions) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Homework> replaceTestCases(long homeworkId, List<HomeworkTestCase> testCases) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean softDeleteDraft(long homeworkId, LocalDateTime deletedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<Homework> findById(long homeworkId) {
            return homework.id() == homeworkId ? Optional.of(homework) : Optional.empty();
        }

        @Override
        public Optional<Homework> findByIdForUpdate(long homeworkId) {
            return findById(homeworkId);
        }

        @Override
        public List<Homework> findByCourseId(long courseId, HomeworkStatus status, String keyword, int page, int size) {
            return List.of();
        }

        @Override
        public long countByCourseId(long courseId, HomeworkStatus status, String keyword) {
            return 0;
        }

        @Override
        public List<Homework> findByCourseIdAndStatuses(
                long courseId,
                List<HomeworkStatus> statuses,
                String keyword,
                int page,
                int size
        ) {
            return List.of();
        }

        @Override
        public long countByCourseIdAndStatuses(long courseId, List<HomeworkStatus> statuses, String keyword) {
            return 0;
        }
    }

    private static final class DuplicateVersionSubmissionRepository implements HomeworkSubmissionRepository {
        @Override
        public HomeworkSubmission save(HomeworkSubmission submission) {
            throw new DuplicateKeyException("duplicate homework submission version");
        }

        @Override
        public HomeworkSubmission update(HomeworkSubmission submission) {
            return submission;
        }

        @Override
        public Optional<HomeworkSubmission> findById(long submissionId) {
            return Optional.empty();
        }

        @Override
        public Optional<HomeworkSubmission> findLatestFinalByHomeworkIdAndStudentId(long homeworkId, long studentId) {
            return Optional.empty();
        }

        @Override
        public List<HomeworkSubmission> findFinalByHomeworkId(long homeworkId) {
            return List.of();
        }

        @Override
        public List<HomeworkSubmission> findByHomeworkIdAndStudentId(long homeworkId, long studentId) {
            return List.of();
        }

        @Override
        public PageResponse<HomeworkSubmission> findByHomeworkId(
                long homeworkId,
                HomeworkSubmissionSearchCriteria criteria,
                int page,
                int size
        ) {
            return new PageResponse<>(List.of(), 0, page, size);
        }
    }

    private static final class UnusedHomeworkEvaluationRepository implements HomeworkEvaluationRepository {
        @Override
        public HomeworkEvaluation save(HomeworkEvaluation evaluation) {
            throw new UnsupportedOperationException();
        }

        @Override
        public HomeworkEvaluation update(HomeworkEvaluation evaluation) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<HomeworkEvaluation> findById(long id) {
            return Optional.empty();
        }

        @Override
        public Optional<HomeworkEvaluation> findLatestBySubmissionId(long submissionId) {
            return Optional.empty();
        }
    }

    private static final class UnusedHomeworkReviewLogRepository implements HomeworkReviewLogRepository {
        @Override
        public HomeworkReviewLog save(HomeworkReviewLog reviewLog) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<HomeworkReviewLog> findBySubmissionId(long submissionId) {
            throw new UnsupportedOperationException();
        }
    }
}
