package com.onlinejudge.grd.service;

import com.onlinejudge.grd.domain.CreateGradeItemCommand;
import com.onlinejudge.grd.domain.GradeItem;
import com.onlinejudge.grd.domain.GradeItemRepository;
import com.onlinejudge.grd.domain.SourceType;
import com.onlinejudge.grd.domain.UpdateGradeItemCommand;
import com.onlinejudge.integration.course.CoursePermissionClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GradeItemServiceTest {
    @Test
    void teacherCreatesCourseGradeItemWhenRuleAndCoursePermissionAreValid() {
        GradeItemRepository repository = new InMemoryGradeItemRepository();
        CoursePermissionClient coursePermissionClient = (courseId, userId) -> true;
        GradeItemService service = new GradeItemService(repository, coursePermissionClient);

        GradeItem item = service.createGradeItem(101L, 501L, new CreateGradeItemCommand(
                "实验一",
                SourceType.LAB,
                301L,
                new BigDecimal("100.00"),
                new BigDecimal("0.40"),
                true,
                1
        ));

        assertThat(item.id()).isPositive();
        assertThat(item.courseId()).isEqualTo(101L);
        assertThat(item.name()).isEqualTo("实验一");
        assertThat(item.sourceType()).isEqualTo(SourceType.LAB);
        assertThat(item.sourceId()).isEqualTo(301L);
        assertThat(item.fullScore()).isEqualByComparingTo("100.00");
        assertThat(item.weight()).isEqualByComparingTo("0.40");
        assertThat(item.includedInFinal()).isTrue();
        assertThat(item.enabled()).isTrue();
        assertThat(item.sortOrder()).isEqualTo(1);
        assertThat(item.createdBy()).isEqualTo(501L);
        assertThat(service.listGradeItems(101L, 501L)).containsExactly(item);
    }

    @Test
    void teacherCannotCreateGradeItemWhenCoursePermissionIsMissing() {
        GradeItemService service = new GradeItemService(new InMemoryGradeItemRepository(), (courseId, userId) -> false);

        assertThatThrownBy(() -> service.createGradeItem(101L, 501L, new CreateGradeItemCommand(
                "作业一",
                SourceType.HWK,
                401L,
                new BigDecimal("100.00"),
                new BigDecimal("0.50"),
                true,
                1
        ))).isInstanceOf(GradeItemPermissionException.class)
                .hasMessageContaining("教师无课程成绩管理权限");
    }

    @Test
    void teacherCannotCreateGradeItemWhenRuleIsInvalid() {
        GradeItemService service = new GradeItemService(new InMemoryGradeItemRepository(), (courseId, userId) -> true);

        assertThatThrownBy(() -> service.createGradeItem(101L, 501L, new CreateGradeItemCommand(
                " ",
                SourceType.LAB,
                301L,
                new BigDecimal("0.00"),
                new BigDecimal("1.20"),
                true,
                1
        ))).isInstanceOf(InvalidGradeRuleException.class)
                .hasMessageContaining("成绩项名称不能为空")
                .hasMessageContaining("满分值必须大于 0")
                .hasMessageContaining("权重必须在 0 到 1 之间");
    }

    @Test
    void teacherCannotCreateLabOrHomeworkGradeItemWhenSourceIdIsNotPositive() {
        GradeItemService service = new GradeItemService(new InMemoryGradeItemRepository(), (courseId, userId) -> true);

        assertThatThrownBy(() -> service.createGradeItem(101L, 501L, new CreateGradeItemCommand(
                "实验一",
                SourceType.LAB,
                0L,
                new BigDecimal("100.00"),
                new BigDecimal("0.40"),
                true,
                1
        ))).isInstanceOf(InvalidGradeRuleException.class)
                .hasMessageContaining("来源任务编号必须大于 0");
    }

    @Test
    void teacherCannotCreateDuplicateActiveGradeItemNameInSameCourse() {
        GradeItemService service = new GradeItemService(new InMemoryGradeItemRepository(), (courseId, userId) -> true);
        service.createGradeItem(101L, 501L, new CreateGradeItemCommand(
                "实验一",
                SourceType.LAB,
                301L,
                new BigDecimal("100.00"),
                new BigDecimal("0.40"),
                true,
                1
        ));

        assertThatThrownBy(() -> service.createGradeItem(101L, 501L, new CreateGradeItemCommand(
                " 实验一 ",
                SourceType.LAB,
                302L,
                new BigDecimal("100.00"),
                new BigDecimal("0.20"),
                true,
                2
        ))).isInstanceOf(InvalidGradeRuleException.class)
                .hasMessageContaining("同一课程下成绩项名称不能重复");
    }

    @Test
    void teacherCannotCreateGradeItemWhenIncludedWeightWouldExceedOne() {
        GradeItemService service = new GradeItemService(new InMemoryGradeItemRepository(), (courseId, userId) -> true);
        service.createGradeItem(101L, 501L, new CreateGradeItemCommand(
                "实验一",
                SourceType.LAB,
                301L,
                new BigDecimal("100.00"),
                new BigDecimal("0.70"),
                true,
                1
        ));

        assertThatThrownBy(() -> service.createGradeItem(101L, 501L, new CreateGradeItemCommand(
                "作业一",
                SourceType.HWK,
                401L,
                new BigDecimal("100.00"),
                new BigDecimal("0.40"),
                true,
                2
        ))).isInstanceOf(InvalidGradeRuleException.class)
                .hasMessageContaining("计入总评的权重之和不能超过 1");
    }

    @Test
    void teacherCannotUpdateGradeItemWhenIncludedWeightWouldExceedOne() {
        GradeItemService service = new GradeItemService(new InMemoryGradeItemRepository(), (courseId, userId) -> true);
        GradeItem first = service.createGradeItem(101L, 501L, new CreateGradeItemCommand(
                "实验一",
                SourceType.LAB,
                301L,
                new BigDecimal("100.00"),
                new BigDecimal("0.50"),
                true,
                1
        ));
        service.createGradeItem(101L, 501L, new CreateGradeItemCommand(
                "作业一",
                SourceType.HWK,
                401L,
                new BigDecimal("100.00"),
                new BigDecimal("0.40"),
                true,
                2
        ));

        assertThatThrownBy(() -> service.updateGradeItem(first.id(), 501L, new UpdateGradeItemCommand(
                "实验一",
                SourceType.LAB,
                301L,
                new BigDecimal("100.00"),
                new BigDecimal("0.70"),
                true,
                1,
                true
        ))).isInstanceOf(InvalidGradeRuleException.class)
                .hasMessageContaining("计入总评的权重之和不能超过 1");
    }

    private static final class InMemoryGradeItemRepository implements GradeItemRepository {
        private long nextId = 1L;
        private final java.util.ArrayList<GradeItem> items = new java.util.ArrayList<>();

        @Override
        public GradeItem save(GradeItem item) {
            GradeItem saved = item.withId(nextId++);
            items.add(saved);
            return saved;
        }

        @Override
        public GradeItem update(GradeItem item) {
            for (int index = 0; index < items.size(); index++) {
                if (items.get(index).id() == item.id()) {
                    items.set(index, item);
                    return item;
                }
            }
            throw new IllegalArgumentException("成绩项不存在");
        }

        @Override
        public Optional<GradeItem> findById(long id) {
            return items.stream()
                    .filter(item -> item.id() == id)
                    .findFirst();
        }

        @Override
        public List<GradeItem> findByCourseId(long courseId) {
            return items.stream()
                    .filter(item -> item.courseId() == courseId && !item.deleted())
                    .toList();
        }
    }
}
