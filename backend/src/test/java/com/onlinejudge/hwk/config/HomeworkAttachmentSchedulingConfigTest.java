package com.onlinejudge.hwk.config;

import com.onlinejudge.common.storage.FileStorageService;
import com.onlinejudge.hwk.domain.HomeworkSubmissionAttachmentRepository;
import com.onlinejudge.hwk.service.HomeworkAttachmentCleanupService;
import com.onlinejudge.lrn.config.ReminderSchedulingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class HomeworkAttachmentSchedulingConfigTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(
                    ReminderSchedulingConfig.class,
                    HomeworkAttachmentSchedulingConfig.class,
                    HomeworkAttachmentCleanupService.class
            )
            .withBean(HomeworkSubmissionAttachmentRepository.class,
                    () -> mock(HomeworkSubmissionAttachmentRepository.class))
            .withBean(FileStorageService.class, () -> mock(FileStorageService.class))
            .withBean(TransactionTemplate.class, () -> mock(TransactionTemplate.class));

    @Test
    void homeworkCleanupSchedulingStaysEnabledWhenLearningReminderSchedulingIsDisabled() {
        contextRunner.withPropertyValues("onlinejudge.lrn.reminders.scheduling-enabled=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(HomeworkAttachmentSchedulingConfig.class);
                    assertThat(context).hasSingleBean(HomeworkAttachmentCleanupService.class);
                    assertThat(context).hasSingleBean(ScheduledAnnotationBeanPostProcessor.class);
                });
    }

    @Test
    void homeworkCleanupCanBeDisabledWithoutDependingOnLearningReminderConfiguration() {
        contextRunner.withPropertyValues(
                        "onlinejudge.lrn.reminders.scheduling-enabled=true",
                        "onlinejudge.hwk.attachments.cleanup-enabled=false"
                )
                .run(context -> {
                    assertThat(context).doesNotHaveBean(HomeworkAttachmentSchedulingConfig.class);
                    assertThat(context).doesNotHaveBean(HomeworkAttachmentCleanupService.class);
                });
    }
}
