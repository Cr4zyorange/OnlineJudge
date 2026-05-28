import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  configureHomeworkAuthContext,
  createHomework,
  getSubmissionEvaluation,
  getHomeworkSubmission,
  listHomeworkSubmissions,
  listMyHomeworkSubmissions,
  listHomeworks,
  publishHomework,
  reevaluateSubmission,
  submitHomework
} from '../../../src/api/hwk/homeworks';
import type { HomeworkPayload } from '../../../src/types/hwk';

describe('homeworks API client', () => {
  afterEach(() => {
    configureHomeworkAuthContext(null);
    vi.restoreAllMocks();
  });

  it('calls documented HWK-01 endpoints with teacher course management headers', async () => {
    configureHomeworkAuthContext(() => ({
      userId: 501,
      userRole: 'TEACHER',
      manageableCourseIds: [202]
    }));

    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({ id: 17 }))
      .mockResolvedValueOnce(jsonResponse({ id: 17, status: 'PUBLISHED' }))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse({ id: 88 }))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse([]))
      .mockResolvedValueOnce(jsonResponse({ id: 88 }))
      .mockResolvedValueOnce(jsonResponse({ id: 9, status: 'ACCEPTED' }))
      .mockResolvedValueOnce(jsonResponse({ id: 10, status: 'ACCEPTED' }));

    const payload: HomeworkPayload = {
      courseId: 202,
      chapterId: 9,
      title: '第一次作业',
      description: '完成数组练习',
      type: 'CODE',
      totalScore: '100.00',
      deadline: '2099-07-01T23:59:00',
      allowResubmit: true,
      allowLateSubmit: false,
      showEvaluationBeforePublish: false,
      questions: [],
      testCases: [
        {
          inputData: '1 2',
          expectedOutput: '3',
          scoreWeight: '1.00',
          hidden: true,
          timeLimitMs: 1000,
          memoryLimitKb: 262144,
          sortOrder: 1
        }
      ]
    };

    await createHomework(payload);
    await publishHomework(17);
    await listHomeworks(202);
    await submitHomework(17, {
      codeText: 'print(1)',
      language: 'python'
    });
    await listMyHomeworkSubmissions(17);
    await listHomeworkSubmissions(17);
    await getHomeworkSubmission(88);
    await getSubmissionEvaluation(88);
    await reevaluateSubmission(88);

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/v1/homeworks', expect.objectContaining({
      method: 'POST',
      headers: expect.objectContaining({
        'X-User-Id': '501',
        'X-User-Role': 'TEACHER',
        'X-Manageable-Course-Ids': '202'
      }),
      body: JSON.stringify(payload)
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/v1/homeworks/17/publish', expect.objectContaining({
      method: 'PUT'
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(3, '/api/v1/homeworks?courseId=202', expect.objectContaining({
      method: 'GET'
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(4, '/api/v1/homeworks/17/submissions', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        codeText: 'print(1)',
        language: 'python'
      })
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(5, '/api/v1/homeworks/17/my-submissions', expect.objectContaining({
      method: 'GET'
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(6, '/api/v1/homeworks/17/submissions', expect.objectContaining({
      method: 'GET'
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(7, '/api/v1/submissions/88', expect.objectContaining({
      method: 'GET'
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(8, '/api/v1/submissions/88/evaluation', expect.objectContaining({
      method: 'GET'
    }));
    expect(fetchMock).toHaveBeenNthCalledWith(9, '/api/v1/submissions/88/reevaluate', expect.objectContaining({
      method: 'POST'
    }));
  });

  it('fails fast when homework auth context is not available', async () => {
    vi.spyOn(globalThis, 'fetch');

    await expect(listHomeworks(202)).rejects.toThrow('当前登录态缺失');
    expect(globalThis.fetch).not.toHaveBeenCalled();
  });
});

function jsonResponse<T>(data: T) {
  return {
    ok: true,
    json: async () => ({
      code: '0',
      message: 'ok',
      data
    })
  } as Response;
}
