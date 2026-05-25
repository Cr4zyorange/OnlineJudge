import { mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import GradeItemConfigView from '../../../src/views/grd/GradeItemConfigView.vue';
import * as gradeItemApi from '../../../src/api/grd/gradeItems';

vi.mock('../../../src/api/grd/gradeItems');

describe('GradeItemConfigView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
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
    await wrapper.get('[name="sourceId"]').setValue('301');
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
    expect(wrapper.text()).toContain('LAB');
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

  it('keeps invalid LAB or HWK source ids on the page', async () => {
    vi.mocked(gradeItemApi.listGradeItems).mockResolvedValueOnce([]);

    const wrapper = mount(GradeItemConfigView, {
      props: {
        courseId: 101
      }
    });
    await flushPromises();

    await wrapper.get('[name="name"]').setValue('实验一');
    await wrapper.get('[name="sourceType"]').setValue('LAB');
    await wrapper.get('[name="sourceId"]').setValue('0');
    await wrapper.get('[name="fullScore"]').setValue('100.00');
    await wrapper.get('[name="weight"]').setValue('0.40');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(gradeItemApi.createGradeItem).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('来源任务编号必须大于 0');
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
});

async function flushPromises() {
  await Promise.resolve();
  await Promise.resolve();
}
