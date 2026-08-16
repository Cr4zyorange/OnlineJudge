import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as labApi from '../../../src/api/lab/labs';
import LabStudentListView from '../../../src/views/lab/LabStudentListView.vue';

vi.mock('../../../src/api/lab/labs');

describe('LabStudentListView visual task-list contract', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.setSystemTime(new Date('2026-08-15T09:00:00+08:00'));
  });

  it('adapts domain data into searchable task cards without exposing raw enums or role selectors', async () => {
    vi.mocked(labApi.listLabs).mockResolvedValue([
      lab(950211, '容器 I/O 实验', 'PUBLISHED', '2026-08-16T20:00:00'),
      lab(950201, '链表基础实验', 'SCORE_PUBLISHED', '2026-08-12T20:00:00'),
      lab(950299, '未发布草稿', 'DRAFT', '2026-08-20T20:00:00')
    ]);
    vi.mocked(labApi.listLabSubmissions)
      .mockResolvedValueOnce([{
        submissionId: 82,
        labId: 950211,
        studentId: 7,
        language: 'java',
        submitStatus: 'SUBMITTED',
        evaluationStatus: 'ACCEPTED',
        autoScore: 87,
        finalScore: 88,
        version: 1,
        submittedAt: '2026-08-15T10:00:00',
        isLatest: true,
        isFinal: true,
        isScoringBasis: true,
        hasFile: false
      }])
      .mockResolvedValueOnce([{
        submissionId: 81,
        labId: 950201,
        studentId: 7,
        language: 'java',
        submitStatus: 'SUBMITTED',
        evaluationStatus: 'ACCEPTED',
        autoScore: 92,
        finalScore: 95,
        version: 2,
        submittedAt: '2026-08-12T10:00:00',
        isLatest: true,
        isFinal: true,
        isScoringBasis: true,
        hasFile: false
      }]);

    const wrapper = mount(LabStudentListView, { props: { courseId: 9501 } });
    await flushPromises();

    expect(wrapper.text()).toContain('2 个可进入实验');
    expect(wrapper.text()).toContain('进行中');
    expect(wrapper.text()).toContain('成绩已发布');
    expect(wrapper.text()).toContain('最终成绩 95 分');
    expect(wrapper.text()).not.toContain('最终成绩 88 分');
    expect(wrapper.text()).not.toContain('PUBLISHED');
    expect(wrapper.text()).not.toContain('SCORE_PUBLISHED');
    expect(wrapper.text()).not.toContain('DOCKER_IO');
    expect(wrapper.get('[data-testid="open-lab-950211"]').attributes('href')).toBe('/courses/9501/labs/950211');

    await wrapper.get('[data-testid="lab-keyword-filter"]').setValue('链表');
    expect(wrapper.text()).toContain('链表基础实验');
    expect(wrapper.text()).not.toContain('容器 I/O 实验');
  });

  it('shows a retryable failure state', async () => {
    vi.mocked(labApi.listLabs)
      .mockRejectedValueOnce(new Error('网络暂时不可用'))
      .mockResolvedValueOnce([]);

    const wrapper = mount(LabStudentListView, { props: { courseId: 9501 } });
    await flushPromises();

    expect(wrapper.text()).toContain('实验列表加载失败');
    expect(wrapper.text()).toContain('网络暂时不可用');
    await wrapper.get('[data-testid="retry-lab-list"]').trigger('click');
    await flushPromises();
    expect(labApi.listLabs).toHaveBeenCalledTimes(2);
    expect(wrapper.text()).toContain('当前筛选下没有实验');
  });
});

function lab(id: number, title: string, status: 'PUBLISHED' | 'SCORE_PUBLISHED' | 'DRAFT', deadline: string) {
  return {
    id,
    courseId: 9501,
    title,
    status,
    deadline,
    maxScore: 100,
    evaluationMode: 'DOCKER_IO' as const,
    autoEvaluate: true,
    reportRequired: id === 950211,
    deleted: false
  };
}
