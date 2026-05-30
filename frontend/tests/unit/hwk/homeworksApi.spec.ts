import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  closeHomework,
  createHomework,
  getHomeworkDetail,
  listMyHomeworkSubmissions,
  listHomeworks,
  publishHomework,
  saveHomeworkQuestions,
  saveHomeworkTestCases,
  submitHomework,
  updateHomework
} from '../../../src/api/hwk/homeworks';

describe('homeworks api', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
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
      .mockResolvedValueOnce(jsonResponse(submission({ id: 31 })))
      .mockResolvedValueOnce(jsonResponse([submission({ id: 31 })]))
      .mockResolvedValueOnce(jsonResponse(homeworkDetail({ id: 11, status: 'PUBLISHED' })))
      .mockResolvedValueOnce(jsonResponse(homeworkDetail({ id: 11, status: 'CLOSED' })));

    await expect(listHomeworks({ courseId: 101, status: 'DRAFT', keyword: 'array' }))
      .resolves.toEqual({ list: [], page: 1, size: 20, total: 0 });
    await createHomework(homeworkPayload());
    await updateHomework(11, { ...homeworkPayload(), title: 'updated' });
    await saveHomeworkQuestions(11, [questionPayload()]);
    await saveHomeworkTestCases(11, [testCasePayload()]);
    await getHomeworkDetail(11);
    await submitHomework(11, { answerText: 'text answer', answerJson: null, fileUrl: null, codeText: null, language: null });
    await listMyHomeworkSubmissions(11);
    await publishHomework(11);
    await closeHomework(11);

    expect(fetchMock.mock.calls.map((call) => [call[0], (call[1] as RequestInit).method])).toEqual([
      ['/api/v1/homeworks?courseId=101&page=1&size=20&status=DRAFT&keyword=array', 'GET'],
      ['/api/v1/homeworks', 'POST'],
      ['/api/v1/homeworks/11', 'PUT'],
      ['/api/v1/homeworks/11/questions', 'PUT'],
      ['/api/v1/homeworks/11/test-cases', 'PUT'],
      ['/api/v1/homeworks/11', 'GET'],
      ['/api/v1/homeworks/11/submissions', 'POST'],
      ['/api/v1/homeworks/11/my-submissions', 'GET'],
      ['/api/v1/homeworks/11/publish', 'PUT'],
      ['/api/v1/homeworks/11/close', 'PUT']
    ]);
  });
});

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

function submission(overrides: Record<string, unknown> = {}) {
  return {
    id: 31,
    homeworkId: 11,
    studentId: 601,
    submitType: 'TEXT',
    answerText: 'text answer',
    answerJson: null,
    fileUrl: null,
    language: null,
    submitStatus: 'SUBMITTED',
    evaluationStatus: 'NOT_REQUIRED',
    reviewStatus: 'UNREVIEWED',
    autoScore: null,
    manualScore: null,
    finalScore: null,
    comment: null,
    final: true,
    submittedAt: '2026-05-30T13:00:00',
    createdAt: '2026-05-30T13:00:00',
    updatedAt: '2026-05-30T13:00:00',
    ...overrides
  };
}

function jsonResponse(data: unknown) {
  return new Response(JSON.stringify({ code: '0', message: 'success', data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  });
}
