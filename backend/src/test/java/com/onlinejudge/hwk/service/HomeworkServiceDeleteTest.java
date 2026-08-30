package com.onlinejudge.hwk.service;

import com.onlinejudge.common.event.NotificationEventPublisher;
import com.onlinejudge.hwk.domain.Homework;
import com.onlinejudge.hwk.domain.HomeworkQuestion;
import com.onlinejudge.hwk.domain.HomeworkRepository;
import com.onlinejudge.hwk.domain.HomeworkReviewLogRepository;
import com.onlinejudge.hwk.domain.HomeworkStatus;
import com.onlinejudge.hwk.domain.HomeworkSubmissionRepository;
import com.onlinejudge.hwk.domain.HomeworkTestCase;
import com.onlinejudge.hwk.domain.HomeworkType;
import com.onlinejudge.hwk.repository.AssessmentEventOutboxRepository;
import com.onlinejudge.integration.course.CoursePermissionClient;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class HomeworkServiceDeleteTest {
    @Test
    void deleteReturnsTheCurrentRowWhenAnEditCommitsBeforeTheDelete() {
        Homework initial = draft("删除前标题");
        Homework concurrentEdit = draft("并发编辑后的标题");
        DeleteRaceRepository repository = new DeleteRaceRepository(initial, concurrentEdit.softDelete(LocalDateTime.now()));
        HomeworkService service = service(repository);

        Homework deleted = service.deleteDraft(initial.id(), 501);

        assertThat(deleted.deleted()).isTrue();
        assertThat(deleted.title()).isEqualTo("并发编辑后的标题");
        assertThat(repository.currentReadUsed()).isTrue();
    }

    @Test
    void deleteClassifiesAConcurrentDeleteAsNotFound() {
        Homework initial = draft("并发删除");
        DeleteRaceRepository repository = new DeleteRaceRepository(
                initial,
                initial.softDelete(LocalDateTime.now()),
                false
        );

        assertThatThrownBy(() -> service(repository).deleteDraft(initial.id(), 501))
                .isInstanceOf(HomeworkApiException.class)
                .extracting("code")
                .isEqualTo("HWK_4001");
    }

    @Test
    void deleteClassifiesAConcurrentPublishAsLifecycleConflict() {
        Homework initial = draft("并发发布");
        DeleteRaceRepository repository = new DeleteRaceRepository(
                initial,
                initial.publish(LocalDateTime.now()),
                false
        );

        assertThatThrownBy(() -> service(repository).deleteDraft(initial.id(), 501))
                .isInstanceOf(HomeworkApiException.class)
                .extracting("code")
                .isEqualTo("HWK_4095");
    }

    private HomeworkService service(HomeworkRepository repository) {
        CoursePermissionClient permissions = (courseId, userId) -> courseId == 101 && userId == 501;
        return new HomeworkService(
                repository,
                mock(HomeworkSubmissionRepository.class),
                mock(HomeworkReviewLogRepository.class),
                permissions,
                mock(NotificationEventPublisher.class),
                mock(AssessmentEventOutboxRepository.class)
        );
    }

    private Homework draft(String title) {
        LocalDateTime now = LocalDateTime.now();
        return new Homework(
                11,
                101,
                null,
                title,
                "删除并发契约",
                HomeworkType.TEXT,
                HomeworkStatus.DRAFT,
                100,
                now.plusDays(7),
                true,
                false,
                true,
                null,
                501,
                null,
                false,
                now,
                now,
                List.of(),
                List.of(),
                null
        );
    }

    private static final class DeleteRaceRepository implements HomeworkRepository {
        private final Homework initial;
        private final Homework current;
        private final boolean deleteSucceeds;
        private boolean currentReadUsed;

        private DeleteRaceRepository(Homework initial, Homework current) {
            this(initial, current, true);
        }

        private DeleteRaceRepository(Homework initial, Homework current, boolean deleteSucceeds) {
            this.initial = initial;
            this.current = current;
            this.deleteSucceeds = deleteSucceeds;
        }

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
            return deleteSucceeds;
        }

        @Override
        public Optional<Homework> findById(long homeworkId) {
            return homeworkId == initial.id() ? Optional.of(initial) : Optional.empty();
        }

        @Override
        public Optional<Homework> findByIdForUpdate(long homeworkId) {
            currentReadUsed = true;
            return homeworkId == current.id() ? Optional.of(current) : Optional.empty();
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
        public long countByCourseIdAndStatuses(
                long courseId,
                List<HomeworkStatus> statuses,
                String keyword
        ) {
            return 0;
        }

        private boolean currentReadUsed() {
            return currentReadUsed;
        }
    }
}
