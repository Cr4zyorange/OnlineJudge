import { mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import HomeworkTeacherView from '../../../src/views/hwk/HomeworkTeacherView.vue';
import * as homeworkApi from '../../../src/api/hwk/homeworks';
import type { HomeworkDetail, HomeworkSummary } from '../../../src/types/hwk';

vi.mock('../../../src/api/hwk/homeworks');

describe('HomeworkTeacherView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
  });

  it('creates an objective homework draft and refreshes the course homework list', async () => {
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValueOnce([]);
    vi.mocked(homeworkApi.createHomework).mockResolvedValueOnce(homeworkDetail({
      id: 17,
      title: '第一次作业'
    }));
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValueOnce([
      homeworkSummary({
        id: 17,
        title: '第一次作业',
        status: 'DRAFT'
      })
    ]);

    const wrapper = mount(HomeworkTeacherView, {
      props: {
        courseId: 202
      }
    });
    await flushPromises();

    expect(wrapper.text()).toContain('暂无作业');

    await wrapper.get('[name="title"]').setValue('第一次作业');
    await wrapper.get('[name="description"]').setValue('完成第一章客观题');
    await wrapper.get('[name="chapterId"]').setValue('9');
    await wrapper.get('[name="totalScore"]').setValue('100');
    await wrapper.get('[name="deadline"]').setValue('2099-07-01T23:59');
    await wrapper.get('[name="questionStem-0"]').setValue('2 + 3 = ?');
    await wrapper.get('[name="questionOptions-0"]').setValue('["3","4","5","6"]');
    await wrapper.get('[name="questionAnswer-0"]').setValue('["5"]');
    await wrapper.get('[name="questionScore-0"]').setValue('100');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(homeworkApi.createHomework).toHaveBeenCalledWith({
      courseId: 202,
      chapterId: 9,
      title: '第一次作业',
      description: '完成第一章客观题',
      type: 'OBJECTIVE',
      totalScore: '100.00',
      deadline: '2099-07-01T23:59:00',
      allowResubmit: true,
      allowLateSubmit: false,
      showEvaluationBeforePublish: false,
      questions: [
        {
          questionType: 'SINGLE_CHOICE',
          stem: '2 + 3 = ?',
          optionsJson: '["3","4","5","6"]',
          answerJson: '["5"]',
          score: '100.00',
          sortOrder: 1
        }
      ],
      testCases: []
    });
    expect(wrapper.text()).toContain('作业草稿已保存');
    expect(wrapper.text()).toContain('第一次作业');
  });

  it('publishes and closes homework through documented teacher actions', async () => {
    vi.mocked(homeworkApi.listHomeworks)
      .mockResolvedValueOnce([
        homeworkSummary({
          id: 17,
          title: '第一次作业',
          status: 'DRAFT'
        })
      ])
      .mockResolvedValueOnce([
        homeworkSummary({
          id: 17,
          title: '第一次作业',
          status: 'PUBLISHED'
        })
      ])
      .mockResolvedValueOnce([
        homeworkSummary({
          id: 17,
          title: '第一次作业',
          status: 'CLOSED'
        })
      ]);
    vi.mocked(homeworkApi.publishHomework).mockResolvedValueOnce(homeworkDetail({
      id: 17,
      status: 'PUBLISHED'
    }));
    vi.mocked(homeworkApi.closeHomework).mockResolvedValueOnce(homeworkDetail({
      id: 17,
      status: 'CLOSED'
    }));

    const wrapper = mount(HomeworkTeacherView, {
      props: {
        courseId: 202
      }
    });
    await flushPromises();

    await wrapper.findAll('button').find((button) => button.text() === '发布')?.trigger('click');
    await flushPromises();

    expect(homeworkApi.publishHomework).toHaveBeenCalledWith(17);
    expect(wrapper.text()).toContain('作业已发布');

    await wrapper.findAll('button').find((button) => button.text() === '关闭')?.trigger('click');
    await flushPromises();

    expect(homeworkApi.closeHomework).toHaveBeenCalledWith(17);
    expect(wrapper.text()).toContain('作业已关闭');
  });

  it('keeps invalid homework input on the page and avoids API writes', async () => {
    vi.mocked(homeworkApi.listHomeworks).mockResolvedValueOnce([]);

    const wrapper = mount(HomeworkTeacherView, {
      props: {
        courseId: 202
      }
    });
    await flushPromises();

    await wrapper.get('[name="totalScore"]').setValue('0');
    await wrapper.get('[name="deadline"]').setValue('2020-01-01T00:00');
    await wrapper.get('form').trigger('submit');
    await flushPromises();

    expect(homeworkApi.createHomework).not.toHaveBeenCalled();
    expect(wrapper.text()).toContain('作业标题不能为空');
    expect(wrapper.text()).toContain('作业说明不能为空');
    expect(wrapper.text()).toContain('满分必须大于 0');
    expect(wrapper.text()).toContain('截止时间必须晚于当前时间');
  });
});

async function flushPromises() {
  await Promise.resolve();
  await Promise.resolve();
}

function homeworkSummary(overrides: Partial<HomeworkSummary> = {}): HomeworkSummary {
  return {
    id: 17,
    courseId: 202,
    chapterId: 9,
    title: '第一次作业',
    description: '完成第一章',
    type: 'OBJECTIVE',
    status: 'DRAFT',
    totalScore: '100.00',
    deadline: '2099-07-01T23:59:00',
    allowResubmit: true,
    allowLateSubmit: false,
    showEvaluationBeforePublish: false,
    createdBy: 501,
    publishedAt: null,
    createdAt: '2026-05-27T10:00:00',
    updatedAt: '2026-05-27T10:00:00',
    ...overrides
  };
}

function homeworkDetail(overrides: Partial<HomeworkDetail> = {}): HomeworkDetail {
  return {
    ...homeworkSummary(overrides),
    questions: [
      {
        id: 1,
        homeworkId: 17,
        questionType: 'SINGLE_CHOICE',
        stem: '2 + 3 = ?',
        optionsJson: '["3","4","5","6"]',
        answerJson: '["5"]',
        score: '100.00',
        sortOrder: 1
      }
    ],
    testCases: []
  };
}
