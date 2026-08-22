import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as homeworkApi from '../../../src/api/hwk/homeworks';
import {
  closeHomework,
  createHomework,
  deleteHomework,
  getHomeworkDetail,
  getHomeworkEvaluationLogs,
  getHomeworkSubmissionReviewLogs,
  getHomeworkSubmissionEvaluation,
  getHomeworkStatistics,
  getHomeworkSubmission,
  getHomeworkTestCases,
  listHomeworks,
  listHomeworkSubmissions,
  listMyHomeworkSubmissions,
  publishHomework,
  publishHomeworkScores,
  reevaluateHomeworkSubmission,
  reviewHomeworkSubmission,
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
      .mockResolvedValueOnce(jsonResponse([{ ...testCasePayload(), id: 9, homeworkId: 11 }]))
      .mockResolvedValueOnce(jsonResponse(homeworkDetail({ id: 11 })))
      .mockResolvedValueOnce(jsonResponse(homeworkDetail({ id: 11, status: 'PUBLISHED' })))
      .mockResolvedValueOnce(jsonResponse(homeworkDetail({ id: 11, status: 'CLOSED' })))
      .mockResolvedValueOnce(jsonResponse(homeworkDetail({
        id: 11,
        deleted: true,
        updatedAt: '2026-08-22T12:25:04'
      })));

    await expect(listHomeworks({ courseId: 101, status: 'DRAFT', keyword: 'array' }))
      .resolves.toEqual({ list: [], page: 1, size: 20, total: 0 });
    await createHomework(homeworkPayload());
    await updateHomework(11, { ...homeworkPayload(), title: 'updated' });
    await saveHomeworkQuestions(11, [questionPayload()]);
    await saveHomeworkTestCases(11, [testCasePayload()]);
    await expect(getHomeworkTestCases(11)).resolves.toEqual([{ ...testCasePayload(), id: 9, homeworkId: 11 }]);
    await getHomeworkDetail(11);
    await publishHomework(11);
    await closeHomework(11);
    await expect(deleteHomework(11)).resolves.toEqual(expect.objectContaining({
      deleted: true,
      updatedAt: '2026-08-22T12:25:04'
    }));

    expect(fetchMock.mock.calls.map((call) => [call[0], (call[1] as RequestInit).method])).toEqual([
      ['/api/v1/homeworks?courseId=101&page=1&size=20&status=DRAFT&keyword=array', 'GET'],
      ['/api/v1/homeworks', 'POST'],
      ['/api/v1/homeworks/11', 'PUT'],
      ['/api/v1/homeworks/11/questions', 'PUT'],
      ['/api/v1/homeworks/11/test-cases', 'PUT'],
      ['/api/v1/homeworks/11/test-cases', 'GET'],
      ['/api/v1/homeworks/11', 'GET'],
      ['/api/v1/homeworks/11/publish', 'PUT'],
      ['/api/v1/homeworks/11/close', 'PUT'],
      ['/api/v1/homeworks/11', 'DELETE']
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
      reviewStatus: 'NEED_REVIEW',
      attention: 'EVALUATION_PENDING'
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
        '/api/v1/homeworks/11/submissions?page=2&size=5&studentKeyword=602&submitStatus=LATE&evaluationStatus=PENDING&reviewStatus=NEED_REVIEW&attention=EVALUATION_PENDING',
        'GET'
      ],
      ['/api/v1/submissions/91', 'GET']
    ]);
  });

  it('builds documented HWK04 evaluation result, logs, and reevaluation routes', async () => {
    const evaluation = {
      evaluationId: 501,
      submissionId: 91,
      evaluationStatus: 'ACCEPTED',
      score: 100,
      passedCases: 2,
      totalCases: 2,
      durationMs: 120,
      feedback: 'accepted',
      reevaluation: false,
      startedAt: '2026-06-01T10:00:00',
      finishedAt: '2026-06-01T10:00:01'
    };
    const fetchMock = vi.spyOn(globalThis, 'fetch');
    fetchMock
      .mockResolvedValueOnce(jsonResponse(evaluation))
      .mockResolvedValueOnce(jsonResponse({ ...evaluation, compileLog: 'compile ok', runLog: 'case output' }))
      .mockResolvedValueOnce(jsonResponse({ ...evaluation, evaluationId: 502, reevaluation: true }));

    await expect(getHomeworkSubmissionEvaluation(91)).resolves.toEqual(evaluation);
    await expect(getHomeworkEvaluationLogs(501)).resolves.toEqual(expect.objectContaining({
      compileLog: 'compile ok',
      runLog: 'case output'
    }));
    await expect(reevaluateHomeworkSubmission(91, 'teacher requested a fresh judge')).resolves.toEqual(expect.objectContaining({
      evaluationId: 502,
      reevaluation: true
    }));

    expect(fetchMock.mock.calls.map((call) => [call[0], (call[1] as RequestInit).method])).toEqual([
      ['/api/v1/submissions/91/evaluation', 'GET'],
      ['/api/v1/evaluations/501/logs', 'GET'],
      ['/api/v1/submissions/91/reevaluate', 'POST']
    ]);
    expect(fetchMock.mock.calls[2][1]).toEqual(expect.objectContaining({
      body: JSON.stringify({ reason: 'teacher requested a fresh judge' })
    }));
  });

  it('builds documented HWK05 teacher review and audit log routes', async () => {
    const reviewedSubmission = {
      submissionId: 91,
      homeworkId: 11,
      studentId: 601,
      submitType: 'TEXT',
      answerText: 'Use dynamic programming.',
      submitStatus: 'SUBMITTED',
      evaluationStatus: 'NONE',
      reviewStatus: 'REVIEWED',
      manualScore: 88,
      finalScore: 90,
      comment: 'Clear reasoning.',
      version: 1,
      final: true,
      submittedAt: '2026-06-01T10:00:00'
    };
    const reviewLogs = [{
      id: 701,
      submissionId: 91,
      homeworkId: 11,
      studentId: 601,
      operationType: 'REVIEW',
      oldScore: null,
      newScore: 90,
      comment: 'Clear reasoning.',
      operatorId: 501,
      reason: null,
      createdAt: '2026-06-01T11:00:00'
    }];
    const fetchMock = vi.spyOn(globalThis, 'fetch');
    fetchMock
      .mockResolvedValueOnce(jsonResponse(reviewedSubmission))
      .mockResolvedValueOnce(jsonResponse(reviewLogs));

    await expect(reviewHomeworkSubmission(91, {
      manualScore: 88,
      finalScore: 90,
      comment: 'Clear reasoning.'
    })).resolves.toEqual(expect.objectContaining({
      submissionId: 91,
      reviewStatus: 'REVIEWED',
      finalScore: 90
    }));
    await expect(getHomeworkSubmissionReviewLogs(91)).resolves.toEqual(reviewLogs);

    expect(fetchMock.mock.calls.map((call) => [call[0], (call[1] as RequestInit).method])).toEqual([
      ['/api/v1/submissions/91/review', 'PUT'],
      ['/api/v1/submissions/91/review-logs', 'GET']
    ]);
    expect(fetchMock.mock.calls[0][1]).toEqual(expect.objectContaining({
      body: JSON.stringify({
        manualScore: 88,
        finalScore: 90,
        comment: 'Clear reasoning.'
      })
    }));
  });

  it('builds documented HWK06 score publish and statistics routes', async () => {
    const statistics = {
      homeworkId: 11,
      courseId: 101,
      totalStudentCount: 3,
      submittedCount: 2,
      unsubmittedCount: 1,
      evaluatedCount: 2,
      reviewedCount: 2,
      autoEvaluableCount: 2,
      pendingEvaluationCount: 0,
      pendingReviewCount: 0,
      scoredCount: 2,
      averageScore: 70,
      maxScore: 100,
      minScore: 40,
      scoreDistribution: {
        '0-59': 1,
        '60-69': 0,
        '70-79': 0,
        '80-89': 0,
        '90-100': 1
      },
      generatedAt: '2026-08-22T10:30:00+08:00',
      unsubmittedPage: 2,
      unsubmittedSize: 2,
      unsubmittedTotal: 4,
      unsubmittedStudentIds: [603]
    };
    const fetchMock = vi.spyOn(globalThis, 'fetch');
    fetchMock
      .mockResolvedValueOnce(jsonResponse(homeworkDetail({ id: 11, status: 'SCORE_PUBLISHED' })))
      .mockResolvedValueOnce(jsonResponse(statistics));

    await expect(publishHomeworkScores(11)).resolves.toEqual(expect.objectContaining({
      id: 11,
      status: 'SCORE_PUBLISHED'
    }));
    await expect(getHomeworkStatistics(11, { page: 2, size: 2 })).resolves.toEqual(statistics);

    expect(fetchMock.mock.calls.map((call) => [call[0], (call[1] as RequestInit).method])).toEqual([
      ['/api/v1/homeworks/11/scores/publish', 'PUT'],
      ['/api/v1/homeworks/11/statistics?page=2&size=2', 'GET']
    ]);
  });

  it('uploads one homework attachment as authenticated multipart data', async () => {
    const uploaded = attachmentUpload();
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(jsonResponse(uploaded));
    const file = new File(['private report bytes'], '课程报告.pdf', { type: 'application/pdf' });

    const result = await attachmentApi().uploadHomeworkAttachment(11, file);

    expect(result).toEqual(uploaded);
    expect(result).toMatchObject({
      status: 'UPLOADED',
      uploadedAt: '2026-08-22T10:00:00+08:00'
    });

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/v1/homeworks/11/attachments');
    expect(init.method).toBe('POST');
    expect(init.body).toBeInstanceOf(FormData);
    expect((init.body as FormData).get('file')).toBe(file);
    expect(init.headers).toEqual({ Authorization: 'Bearer token-1' });
  });

  it('preserves the backend HWK error code for deterministic upload failures', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(new Response(JSON.stringify({
      code: 'HWK_4131',
      message: 'payload too large',
      data: null
    }), {
      status: 413,
      headers: { 'Content-Type': 'application/json' }
    }));

    await expect(attachmentApi().uploadHomeworkAttachment(
      11,
      new File(['oversized'], 'large.pdf', { type: 'application/pdf' })
    )).rejects.toMatchObject({
      code: 'HWK_4131',
      message: 'payload too large'
    });
  });

  it('revalidates and removes only the current homework upload by opaque file id', async () => {
    const uploaded = attachmentUpload();
    const fetchMock = vi.spyOn(globalThis, 'fetch')
      .mockResolvedValueOnce(jsonResponse(uploaded))
      .mockResolvedValueOnce(jsonResponse(null));

    await expect(attachmentApi().getHomeworkAttachment(11, uploaded.fileId)).resolves.toEqual(uploaded);
    await expect(attachmentApi().deleteHomeworkAttachment(11, uploaded.fileId)).resolves.toBeNull();

    expect(fetchMock.mock.calls.map((call) => [call[0], (call[1] as RequestInit).method])).toEqual([
      ['/api/v1/homeworks/11/attachments/85c3d5a0-2140-4d80-9000-000000000011', 'GET'],
      ['/api/v1/homeworks/11/attachments/85c3d5a0-2140-4d80-9000-000000000011', 'DELETE']
    ]);
  });

  it('downloads a submitted attachment only through the controlled blob route', async () => {
    const blob = new Blob(['submitted report'], { type: 'application/pdf' });
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce({
      ok: true,
      headers: new Headers({
        'Content-Disposition': "attachment; filename*=UTF-8''submitted-report.pdf"
      }),
      blob: async () => blob
    } as Response);

    await expect(attachmentApi().downloadHomeworkSubmissionAttachment(11, 91)).resolves.toEqual({
      blob,
      filename: 'submitted-report.pdf'
    });
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/homeworks/11/submissions/91/attachment/download',
      expect.objectContaining({ method: 'GET' })
    );
  });
});

function attachmentApi() {
  return homeworkApi as unknown as {
    uploadHomeworkAttachment: (homeworkId: number, file: File) => Promise<ReturnType<typeof attachmentUpload>>;
    getHomeworkAttachment: (homeworkId: number, fileId: string) => Promise<ReturnType<typeof attachmentUpload>>;
    deleteHomeworkAttachment: (homeworkId: number, fileId: string) => Promise<null>;
    downloadHomeworkSubmissionAttachment: (
      homeworkId: number,
      submissionId: number
    ) => Promise<{ blob: Blob; filename?: string }>;
  };
}

function attachmentUpload() {
  return {
    fileId: '85c3d5a0-2140-4d80-9000-000000000011',
    originalFilename: '课程报告.pdf',
    contentType: 'application/pdf',
    fileSize: 20,
    expiresAt: '2026-08-23T10:00:00+08:00',
    status: 'UPLOADED',
    uploadedAt: '2026-08-22T10:00:00+08:00'
  };
}

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
