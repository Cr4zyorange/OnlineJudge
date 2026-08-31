package com.onlinejudge.assessmentservice.controller;

import com.onlinejudge.assessmentservice.persistence.SourceGradeRepository;
import com.onlinejudge.assessmentservice.security.AssessmentServiceIdentityAuthentication;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/** Read-only rebuild input for Grade; it never exposes Assessment's schema directly. */
@RestController
@RequestMapping("/internal/v2/source-grades")
public class AssessmentSourceGradeController {
    private final SourceGradeRepository grades; private final AssessmentServiceIdentityAuthentication serviceIdentity;
    public AssessmentSourceGradeController(SourceGradeRepository grades, AssessmentServiceIdentityAuthentication serviceIdentity) { this.grades = grades; this.serviceIdentity = serviceIdentity; }
    @GetMapping
    @Transactional(readOnly = true)
    public Map<String, Object> list(@RequestParam String courseId, @RequestParam String sourceType, @RequestParam String sourceId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size, HttpServletRequest request) {
        if (request.getHeader("X-Request-Id") == null || request.getHeader("X-Request-Id").isBlank()) throw new SourceGradeRequestException("X-Request-Id is required");
        if (courseId.isBlank() || sourceId.isBlank() || !("LAB".equals(sourceType) || "HWK".equals(sourceType)) || page < 0 || size < 1 || size > 100) throw new SourceGradeRequestException("invalid source-grade filter");
        serviceIdentity.requireGradesRead(request);
        long snapshotVersion = grades.snapshotVersion(courseId, sourceType, sourceId);
        long total = grades.count(courseId, sourceType, sourceId, snapshotVersion);
        int offset;
        try { offset = Math.multiplyExact(page, size); }
        catch (ArithmeticException overflow) { throw new SourceGradeRequestException("invalid source-grade filter"); }
        List<Map<String, Object>> items = grades.page(courseId, sourceType, sourceId, snapshotVersion, offset, size)
                .stream().map(this::response).toList();
        return Map.of("items", items, "page", page, "size", size, "total", total, "sourceSnapshotVersion", snapshotVersion);
    }

    private Map<String, Object> response(SourceGradeRepository.SourceGrade grade) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("courseId", grade.courseId());
        response.put("sourceType", grade.sourceType());
        response.put("sourceId", grade.sourceId());
        response.put("studentId", grade.studentId());
        response.put("score", grade.score());
        response.put("fullScore", grade.fullScore());
        response.put("status", grade.status());
        response.put("sourceVersion", grade.sourceVersion());
        response.put("updatedAt", grade.updatedAt().toString());
        return response;
    }
}
