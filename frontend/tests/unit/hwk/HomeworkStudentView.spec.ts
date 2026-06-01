import { flushPromises, mount } from '@vue/test-utils';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import HomeworkStudentView from '../../../src/views/hwk/HomeworkStudentView.vue';
import * as homeworkApi from '../../../src/api/hwk/homeworks';
import * as learningProgressApi from '../../../src/api/lrn/learningProgress';

vi.mock('../../../src/api/hwk/homeworks');
vi.mock('../../../src/api/lrn/learningProgress');

describe('HomeworkStudentView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    window.history.replaceState({}, '', '/courses/101/homeworks/501?role=student');
    vi.mocked(learningProgressApi.saveLearningProgress).mockResolvedValue({
      progressId: 1,
      courseId: 101,
      courseName: '软件工程基础',
      chapterId: 1001,
      chapterName: '课程导论',
      sourceModule: 'HWK',
      sourceId: 501,
      progressPercent: 20,
      lastPosition: 'homeworkId=501',
      status: 'IN_PROGRESS',
      continueUrl: '/courses/101/homeworks/501?role=student',
      updatedAt: '2026-06-01 10:00:00'
    });
  });

  it('records homework progress when a student opens and completes homework', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail());

    const wrapper = mount(HomeworkStudentView, {
      props: {
        courseId: 101,
        homeworkId: 501
      }
    });
    await flushPromises();

    expect(wrapper.text()).toContain('作业一');
    expect(learningProgressApi.saveLearningProgress).toHaveBeenCalledWith({
      courseId: 101,
      chapterId: 1001,
      sourceModule: 'HWK',
      sourceId: 501,
      progressPercent: 20,
      lastPosition: 'homeworkId=501'
    });

    await wrapper.get('[data-testid="complete-homework"]').trigger('click');
    await flushPromises();

    expect(learningProgressApi.saveLearningProgress).toHaveBeenLastCalledWith({
      courseId: 101,
      chapterId: 1001,
      sourceModule: 'HWK',
      sourceId: 501,
      progressPercent: 100,
      lastPosition: 'homeworkId=501;completed=true'
    });
    expect(wrapper.text()).toContain('已记录完成进度');
  });

  it('shows the restored homework breakpoint from the resume query', async () => {
    window.history.replaceState({}, '', '/courses/101/homeworks/501?role=student&resume=question%3D2');
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(homeworkDetail());

    const wrapper = mount(HomeworkStudentView, {
      props: {
        courseId: 101,
        homeworkId: 501
      }
    });
    await flushPromises();

    expect(wrapper.text()).toContain('已恢复上次断点：question=2');
  });
});

function homeworkDetail() {
  return {
    id: 501,
    courseId: 101,
    chapterId: 1001,
    title: '作业一',
    description: '完成第一章练习',
    type: 'OBJECTIVE' as const,
    status: 'PUBLISHED' as const,
    totalScore: 100,
    deadline: '2026-06-30T23:59:59',
    allowResubmit: true,
    allowLateSubmit: false,
    showEvaluationBeforePublish: true,
    deleted: false,
    judgeConfigId: null,
    createdBy: 501,
    publishedAt: '2026-06-01T09:00:00',
    createdAt: '2026-06-01T08:00:00',
    updatedAt: '2026-06-01T09:00:00',
    questions: [
      {
        id: 1,
        homeworkId: 501,
        questionType: 'SINGLE_CHOICE',
        stem: '1 + 1 = ?',
        optionsJson: '["1","2"]',
        score: 100,
        sortOrder: 1
      }
    ],
    testCases: []
  };
}
