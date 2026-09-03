import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import GradeItemConfigView from '../../../src/views/grd/GradeItemConfigView.vue';
import * as gradeItemApi from '../../../src/api/grd/gradeItems';
import * as labApi from '../../../src/api/lab/labs';
import * as homeworkApi from '../../../src/api/hwk/homeworks';
import {
  changeTypeLabel,
  finalStatusLabel,
  gradeSourceLabel,
  gradeStatusLabel,
  notificationStatusLabel,
  publishScopeLabel,
  publishStatusLabel,
  reviewStatusLabel
} from '../../../src/views/grd/grdDisplay';

vi.mock('../../../src/api/grd/gradeItems');
vi.mock('../../../src/api/lab/labs');
vi.mock('../../../src/api/hwk/homeworks');

describe('GradeItemConfigView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.mocked(labApi.listLabs).mockResolvedValue([
      {
        id: 301,
        courseId: 101,
        title: '数据结构实验',
        status: 'PUBLISHED',
        deadline: '2026-06-10T23:59:00',
        maxScore: 100,
        evaluationMode: 'MANUAL',
        autoEvaluate: false,
        reportRequired: true,
        deleted: false
      }
    ]);
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValue({
      list: [
        {
          id: 401,
          courseId: 101,
          title: '单元测试作业',
          description: '完成单元测试',
          type: 'TEXT',
          status: 'PUBLISHED',
          totalScore: 100,
          deadline: '2026-06-12T23:59:00',
          allowResubmit: true,
          allowLateSubmit: false,
          showEvaluationBeforePublish: false,
          deleted: false
        }
      ],
      total: 1,
      page: 1,
      size: 100
    });
  });

  it('creates a LAB grade item and refreshes the visible item list', async () => {
    vi.mocked(gradeItemApi.listGradeItems).mockResolvedValueOnce([]);
    vi.mocked(gradeItemApi.validateGradeRules).mockResolvedValue({
      valid: true,
      totalIncludedWeight: '0.40',
      errors: []
    });
    vi.mocked(gradeItemApi.createGradeItem).mockResolvedValueOnce({
      id: 1,
      courseId: 101,
      name: '实验一',
      sourceType: 'LAB',
      sourceId: 301,
      fullScore: '100.00',
      weight: '0.40',
      includedInFinal: true,
      enabled: true,
      sortOrder: 1
    });
    vi.mocked(gradeItemApi.listGradeItems).mockResolvedValueOnce([
      {
        id: 1,
        courseId: 101,
        name: '实验一',
        sourceType: 'LAB',
        sourceId: 301,
        fullScore: '100.00',
        weight: '0.40',
        includedInFinal: true,
        enabled: true,
        sortOrder: 1
      }
    ]);

    const wrapper = mount(GradeItemConfigView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    expect(wrapper.text()).toContain('暂无成绩项');

    await wrapper.get('[name="name"]').setValue('实验一');
    await wrapper.get('[name="sourceType"]').setValue('LAB');
    const taskSelector = wrapper.get('[name="sourceId"]');
    expect(taskSelector.element.tagName).toBe('SELECT');
    expect(taskSelector.text()).toContain('数据结构实验');
    await taskSelector.setValue('301');
    await wrapper.get('[name="fullScore"]').setValue('100.00');
    await wrapper.get('[name="weight"]').setValue('0.40');
    await wrapper.get('[name="includedInFinal"]').setValue(true);
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(gradeItemApi.createGradeItem).toHaveBeenCalledWith(101, {
      name: '实验一',
      sourceType: 'LAB',
      sourceId: 301,
      fullScore: '100.00',
      weight: '0.40',
      includedInFinal: true,
      sortOrder: 1
    });
    expect(wrapper.text()).toContain('保存成功');
    expect(wrapper.text()).toContain('实验一');
    expect(wrapper.text()).toContain('实验任务');
    expect(wrapper.text()).toContain('作业任务');
    expect(wrapper.text()).toContain('课程内其他成绩项');
    expect(wrapper.text()).toContain('关联任务');
    expect(wrapper.text()).toContain('数据结构实验');
    expect(wrapper.text()).not.toContain('关联任务编号');
    expect(wrapper.text()).not.toContain('301');
    expect(wrapper.text()).not.toContain('LAB');
    expect(wrapper.text()).not.toContain('HWK');
    expect(wrapper.text()).not.toContain('OTHER_COURSE_ITEM');
  });

  it('keeps invalid score rules on the page and shows validation feedback', async () => {
    vi.mocked(gradeItemApi.listGradeItems).mockResolvedValueOnce([]);

    const wrapper = mount(GradeItemConfigView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    await wrapper.get('[name="fullScore"]').setValue('0');
    await wrapper.get('[name="weight"]').setValue('1.20');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(gradeItemApi.createGradeItem).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('成绩项名称不能为空');
    expect(wrapper.text()).toContain('满分值必须大于 0');
    expect(wrapper.text()).toContain('权重必须在 0 到 1 之间');
  });

  it('requires a real LAB or HWK task selection instead of a numeric source id', async () => {
    vi.mocked(gradeItemApi.listGradeItems).mockResolvedValueOnce([]);

    const wrapper = mount(GradeItemConfigView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    await wrapper.get('[name="name"]').setValue('实验一');
    await wrapper.get('[name="sourceType"]').setValue('LAB');
    await wrapper.get('[name="fullScore"]').setValue('100.00');
    await wrapper.get('[name="weight"]').setValue('0.40');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(gradeItemApi.createGradeItem).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('请选择关联实验任务');
    expect(wrapper.find('input[name="sourceId"]').exists()).toBe(false);
  });

  it('keeps OTHER_COURSE_ITEM source nullable and explains that no task is required', async () => {
    vi.mocked(gradeItemApi.listGradeItems).mockResolvedValueOnce([]).mockResolvedValueOnce([]);
    vi.mocked(gradeItemApi.createGradeItem).mockResolvedValueOnce({
      id: 2,
      courseId: 101,
      name: '课堂表现',
      sourceType: 'OTHER_COURSE_ITEM',
      sourceId: null,
      fullScore: '20.00',
      weight: '0.10',
      includedInFinal: true,
      enabled: true,
      sortOrder: 1
    });

    const wrapper = mount(GradeItemConfigView, { props: { courseId: 101 } });
    await flushPromises();

    await wrapper.get('[name="name"]').setValue('课堂表现');
    await wrapper.get('[name="sourceType"]').setValue('OTHER_COURSE_ITEM');
    await wrapper.get('[name="fullScore"]').setValue('20');
    await wrapper.get('[name="weight"]').setValue('0.1');

    expect(wrapper.find('[name="sourceId"]').exists()).toBe(false);
    expect(wrapper.text()).toContain('无需关联实验或作业任务');

    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(gradeItemApi.createGradeItem).toHaveBeenCalledWith(101, expect.objectContaining({
      sourceType: 'OTHER_COURSE_ITEM',
      sourceId: null
    }));
  });

  it('uses PageState for source-list failures and retries the complete page data load', async () => {
    vi.mocked(gradeItemApi.listGradeItems).mockResolvedValue([]);
    vi.mocked(labApi.listLabs)
      .mockRejectedValueOnce(new Error('实验任务加载失败'))
      .mockResolvedValueOnce([]);

    const wrapper = mount(GradeItemConfigView, { props: { courseId: 101 } });
    await flushPromises();

    expect(wrapper.text()).toContain('成绩项数据加载失败');
    expect(wrapper.text()).toContain('实验任务加载失败');

    await wrapper.get('[data-testid="page-state-retry"]').trigger('click');
    await flushPromises();

    expect(labApi.listLabs).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain('暂无成绩项');
  });

  it('uses documented APIs to update, delete, and validate grade rules', async () => {
    const item = {
      id: 7,
      courseId: 101,
      name: '作业一',
      sourceType: 'HWK' as const,
      sourceId: 401,
      fullScore: '100.00',
      weight: '0.50',
      includedInFinal: true,
      enabled: true,
      sortOrder: 1
    };

    vi.mocked(gradeItemApi.listGradeItems).mockResolvedValueOnce([item]);
    vi.mocked(gradeItemApi.updateGradeItem).mockResolvedValueOnce({
      ...item,
      name: '作业一-修订',
      weight: '0.60'
    });
    vi.mocked(gradeItemApi.listGradeItems).mockResolvedValueOnce([
      {
        ...item,
        name: '作业一-修订',
        weight: '0.60'
      }
    ]);
    vi.mocked(gradeItemApi.validateGradeRules).mockResolvedValueOnce({
      valid: true,
      totalIncludedWeight: '0.60',
      errors: []
    });
    vi.mocked(gradeItemApi.deleteGradeItem).mockResolvedValueOnce({
      ...item,
      enabled: false
    });
    vi.mocked(gradeItemApi.listGradeItems).mockResolvedValueOnce([]);

    const wrapper = mount(GradeItemConfigView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    await wrapper.get('tbody button').trigger('click');
    await wrapper.get('[name="name"]').setValue('作业一-修订');
    await wrapper.get('[name="weight"]').setValue('0.60');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(gradeItemApi.updateGradeItem).toHaveBeenCalledWith(7, {
      name: '作业一-修订',
      sourceType: 'HWK',
      sourceId: 401,
      fullScore: '100.00',
      weight: '0.60',
      includedInFinal: true,
      sortOrder: 1,
      enabled: true
    });
    expect(wrapper.text()).toContain('更新成功');

    await wrapper.findAll('button').find((button) => button.text() === '校验规则')?.trigger('click');
    await flushPromises();
    expect(gradeItemApi.validateGradeRules).toHaveBeenCalledWith(101);
    expect(wrapper.text()).toContain('规则校验通过');

    await wrapper.findAll('button').find((button) => button.text() === '停用')?.trigger('click');
    await flushPromises();
    expect(gradeItemApi.deleteGradeItem).toHaveBeenCalledWith(7);
    expect(wrapper.text()).toContain('已停用成绩项');
  });

  it('maps every visible GRD enum family and keeps unknown values user friendly', () => {
    expect(gradeSourceLabel('LAB')).toBe('实验任务');
    expect(finalStatusLabel('INCOMPLETE')).toBe('待补全');
    expect(publishStatusLabel('PUBLISHED')).toBe('已发布');
    expect(gradeStatusLabel('UNGRADED')).toBe('待评分');
    expect(reviewStatusLabel('APPROVED')).toBe('已同意');
    expect(changeTypeLabel('FINAL_ADJUST')).toBe('课程总评调整');
    expect(publishScopeLabel('PARTIAL_STUDENTS')).toBe('指定学生');
    expect(notificationStatusLabel('SENT')).toBe('已发送');

    expect(gradeSourceLabel('NEW_SOURCE')).toBe('未知成绩来源');
    expect(finalStatusLabel('NEW_STATUS')).toBe('未知总评状态');
    expect(publishStatusLabel('NEW_STATUS')).toBe('未知发布状态');
    expect(gradeStatusLabel('NEW_STATUS')).toBe('未知成绩状态');
    expect(reviewStatusLabel('NEW_STATUS')).toBe('未知复核状态');
    expect(changeTypeLabel('NEW_TYPE')).toBe('未知变更类型');
    expect(publishScopeLabel('NEW_SCOPE')).toBe('未知发布范围');
    expect(notificationStatusLabel('NEW_STATUS')).toBe('未知通知状态');
  });
});
