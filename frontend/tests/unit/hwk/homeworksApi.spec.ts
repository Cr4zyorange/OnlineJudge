import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  closeHomework,
  createHomework,
  getHomeworkDetail,
  getHomeworkSubmission,
  listHomeworks,
  listHomeworkSubmissions,
  listMyHomeworkSubmissions,
  publishHomework,
  saveHomeworkQuestions,
  saveHomeworkTestCases,
  submitHomework,
  updateHomework
} from '../../../src/api/hwk/homeworks';

describe('homeworks api', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
    installLocalStorageMock();
    window.localStorage.setItem('onlinejudge.authToken', 'token-1');
    window.localStorage.setItem('onlinejudge.userId', '501');
    window.localStorage.setItem('onlinejudge.userRole', 'TEACHER');
    window.localStorage.setItem('onlinejudge.courseIds', '101');
    window.localStorage.setItem('onlinejudge.manageableCourseIds', '101');
  });

  it('builds documented HWK01 routes and unwraps ApiResponse data', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch');
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ list: [], page: 1, size: 20, total: 0 }))
      .mockResolvedValueOnce(jsonResponse(homeworkDetail({ id: 11 })))
      .mockResolvedValueOnce(jsonResponse(homeworkDetail({ id: 11, title: 'updated' })))
      .mockResolvedValueOnce(jsonResponse(homeworkDetail({ id: 11, questions: [{ ...questionPayload(), id: 7, homeworkId: 11 }] })))
      .mockResolvedValueOnce(jsonResponse(homeworkDetail({ id: 11, testCases: [{ ...testCasePayload(), id: 9, homeworkId: 11 }] })))
      .mockResolvedValueOnce(jsonResponse(homeworkDetail({ id: 11 })))
      .mockResolvedValueOnce(jsonResponse(homeworkDetail({ id: 11, status: 'PUBLISHED' })))
      .mockResolvedValueOnce(jsonResponse(homeworkDetail({ id: 11, status: 'CLOSED' })));

    await expect(listHomeworks({ courseId: 101, status: 'DRAFT', keyword: 'array' }))
      .resolves.toEqual({ list: [], page: 1, size: 20, total: 0 });
    await createHomework(homeworkPayload());
    await updateHomework(11, { ...homeworkPayload(), title: 'updated' });
    await saveHomeworkQuestions(11, [questionPayload()]);
    await saveHomeworkTestCases(11, [testCasePayload()]);
    await getHomeworkDetail(11);
    await publishHomework(11);
    await closeHomework(11);

    expect(fetchMock.mock.calls.map((call) => [call[0], (call[1] as RequestInit).method])).toEqual([
      ['/api/v1/homeworks?courseId=101&page=1&size=20&status=DRAFT&keyword=array', 'GET'],
      ['/api/v1/homeworks', 'POST'],
      ['/api/v1/homeworks/11', 'PUT'],
      ['/api/v1/homeworks/11/questions', 'PUT'],
      ['/api/v1/homeworks/11/test-cases', 'PUT'],
      ['/api/v1/homeworks/11', 'GET'],
      ['/api/v1/homeworks/11/publish', 'PUT'],
      ['/api/v1/homeworks/11/close', 'PUT']
    ]);
  });

  it('posts documented HWK02 student submissions and returns the receipt', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse({
        submissionId: 91,
        homeworkId: 11,
        studentId: 601,
        submitStatus: 'SUBMITTED',
        evaluationStatus: 'NONE',
        version: 1,
        final: true,
        submittedAt: '2026-06-01T10:00:00'
      }));

    await expect(submitHomework(11, {
      answerText: 'Use dynamic programming.',
      answerJson: '{"q1":"B"}',
      fileIds: ['file-1'],
      codeText: '',
      language: ''
    })).resolves.toEqual(expect.objectContaining({
      submissionId: 91,
      submitStatus: 'SUBMITTED',
      evaluationStatus: 'NONE'
    }));

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/homeworks/11/submissions', expect.objectContaining({
      method: 'POST',
      body: JSON.stringify({
        answerText: 'Use dynamic programming.',
        answerJson: '{"q1":"B"}',
        fileIds: ['file-1'],
        codeText: '',
        language: ''
      })
    }));
  });

  it('builds documented HWK03 submission history and detail routes', async () => {
    const submission = {
      submissionId: 91,
      homeworkId: 11,
      studentId: 601,
      submitType: 'TEXT',
      answerText: 'Use dynamic programming.',
      submitStatus: 'SUBMITTED',
      evaluationStatus: 'NONE',
      reviewStatus: 'UNREVIEWED',
      version: 2,
      final: true,
      submittedAt: '2026-06-01T10:00:00'
    };
    const fetchMock = vi.spyOn(globalThis, 'fetch');
    fetchMock
      .mockResolvedValueOnce(jsonResponse([submission]))
      .mockResolvedValueOnce(jsonResponse({ list: [submission], page: 2, size: 5, total: 6 }))
      .mockResolvedValueOnce(jsonResponse({ ...submission, answerJson: '{"q1":"B"}' }));

    await expect(listMyHomeworkSubmissions(11)).resolves.toEqual([submission]);
    await expect(listHomeworkSubmissions(11, {
      page: 2,
      size: 5,
      studentKeyword: '602',
      submitStatus: 'LATE',
      evaluationStatus: 'PENDING',
      reviewStatus: 'NEED_REVIEW'
    })).resolves.toEqual({
      list: [submission],
      page: 2,
      size: 5,
      total: 6
    });
    await expect(getHomeworkSubmission(91)).resolves.toEqual(expect.objectContaining({
      submissionId: 91,
      answerJson: '{"q1":"B"}'
    }));

    expect(fetchMock.mock.calls.map((call) => [call[0], (call[1] as RequestInit).method])).toEqual([
      ['/api/v1/homeworks/11/my-submissions', 'GET'],
      [
        '/api/v1/homeworks/11/submissions?page=2&size=5&studentKeyword=602&submitStatus=LATE&evaluationStatus=PENDING&reviewStatus=NEED_REVIEW',
        'GET'
      ],
      ['/api/v1/submissions/91', 'GET']
    ]);
  });
});

function installLocalStorageMock() {
  const values = new Map<string, string>();
  Object.defineProperty(window, 'localStorage', {
    configurable: true,
    value: {
      getItem: vi.fn((key: string) => values.get(key) ?? null),
      setItem: vi.fn((key: string, value: string) => values.set(key, value)),
      removeItem: vi.fn((key: string) => values.delete(key)),
      clear: vi.fn(() => values.clear())
    }
  });
}

function homeworkPayload() {
  return {
    courseId: 101,
    chapterId: null,
    title: 'HWK01 draft',
    description: 'Answer basics.',
    type: 'OBJECTIVE' as const,
    deadline: '2026-06-30T23:59:59',
    totalScore: 100,
    allowResubmit: true,
    allowLateSubmit: false,
    showEvaluationBeforePublish: true,
    questions: [questionPayload()],
    testCases: [],
    languageLimitJson: null,
    timeLimitMs: 1000,
    memoryLimitKb: 65536,
    outputCompareMode: 'EXACT'
  };
}

function questionPayload() {
  return {
    questionType: 'SINGLE_CHOICE',
    stem: '1 + 1 = ?',
    optionsJson: '["1","2"]',
    answerJson: '["2"]',
    score: 100,
    sortOrder: 1
  };
}

function testCasePayload() {
  return {
    inputData: '1 2',
    expectedOutput: '3',
    scoreWeight: 100,
    hidden: false,
    timeLimitMs: 1000,
    memoryLimitKb: 65536,
    sortOrder: 1
  };
}

function homeworkDetail(overrides: Record<string, unknown> = {}) {
  return {
    id: 11,
    courseId: 101,
    chapterId: null,
    title: 'HWK01 draft',
    description: 'Answer basics.',
    type: 'OBJECTIVE',
    status: 'DRAFT',
    deadline: '2026-06-30T23:59:59',
    totalScore: 100,
    allowResubmit: true,
    allowLateSubmit: false,
    showEvaluationBeforePublish: true,
    judgeConfigId: null,
    createdBy: 501,
    publishedAt: null,
    deleted: false,
    createdAt: '2026-05-30T12:00:00',
    updatedAt: '2026-05-30T12:00:00',
    questions: [],
    testCases: [],
    ...overrides
  };
}

function jsonResponse(data: unknown) {
  return new Response(JSON.stringify({ code: '0', message: 'success', data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  });
}
