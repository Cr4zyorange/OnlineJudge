package com.onlinejudge.lrn.service;

import com.onlinejudge.lrn.repository.JdbcLearningRecordRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CompletableFuture;

@Service
public class LearningRecordAsyncWriter {
    private final JdbcLearningRecordRepository recordRepository;

    public LearningRecordAsyncWriter(JdbcLearningRecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    @Async("learningRecordExecutor")
    @Transactional
    public CompletableFuture<Void> saveAsync(long userId, LearningRecordCommand command) {
        recordRepository.save(userId, command);
        return CompletableFuture.completedFuture(null);
    }
}
