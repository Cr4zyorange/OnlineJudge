import type { APIRequestContext, APIResponse, TestInfo } from '@playwright/test';
import { expect, test } from '@playwright/test';
import { timingSafeEqual } from 'node:crypto';
import { readFileSync, realpathSync, statSync } from 'node:fs';
import { basename, dirname, isAbsolute, relative } from 'node:path';
import { verifyThreeServiceDisposableProof } from '../three-service-disposable-proof';

type ApiEnvelope<T> = {
  code: string;
  message: string;
  data: T;
};

type AuthSession = {
  token: string;
  user: { id: number; username: string };
};

type GradeRecord = {
  id: number;
  rawScore: number | null;
  gradeStatus: string;
  publishStatus: string;
};

type GradeRow = {
  studentId: number;
  summary: {
    id: number;
    finalScore: number | null;
    finalStatus: string;
    publishStatus: string;
  };
  records: GradeRecord[];
};

const DEMO_STUDENT = {
  account: process.env.E2E_STUDENT_ACCOUNT?.trim() || 'student001',
  password: process.env.E2E_STUDENT_PASSWORD || 'Student001@pass'
};

const DEMO_TEACHER = {
  account: process.env.E2E_TEACHER_ACCOUNT?.trim() || 'teacher001',
  password: process.env.E2E_TEACHER_PASSWORD || 'Teacher001@pass'
};

const hasDisposableProof = verifyDisposableProof() || verifyThreeServiceDisposableProof();

test.describe('@grd GRD real source lifecycle', () => {
  test.skip(
    !hasDisposableProof,
    'Mutating GRD lifecycle must run through npm run test:e2e:grd:disposable'
  );

  test('@grd-main @grd-alternative @grd-exception runs LAB/HWK -> GRD -> LRN with real APIs', async ({ request }, testInfo) => {
    test.setTimeout(120_000);

    const marker = `grd266-${Date.now()}-${testInfo.workerIndex}`;
    const teacher = await login(request, DEMO_TEACHER.account, DEMO_TEACHER.password);
    const student = await login(request, DEMO_STUDENT.account, DEMO_STUDENT.password);
    const missingStudent = await registerStudent(request, marker);
    const teacherHeaders = bearer(teacher.token);
    const studentHeaders = bearer(student.token);
    const missingStudentHeaders = bearer(missingStudent.token);
    const deadline = localDateTimeAfterDays(7);

    const course = await ok<{ id: number }>(await request.post('/api/v1/courses', {
      headers: teacherHeaders,
      data: {
        name: `GRD E2E ${marker}`,
        description: 'Issue #266 isolated real LAB/HWK source lifecycle',
        semester: '2026-D2',
        category: 'E2E',
        enrollmentMode: 'PUBLIC',
        maxStudents: 10,
        status: 'ACTIVE'
      }
    }), 'create isolated course');
    const courseId = course.id;

    await ok(await request.post(`/api/v1/courses/${courseId}/join`, {
      headers: studentHeaders,
      data: {}
    }), 'join primary student');
    await ok(await request.post(`/api/v1/courses/${courseId}/join`, {
      headers: missingStudentHeaders,
      data: {}
    }), 'join missing-score student');

    const lab = await ok<{ id: number }>(await request.post(`/api/v1/courses/${courseId}/labs`, {
      headers: teacherHeaders,
      data: {
        title: `LAB source ${marker}`,
        description: 'Manual lab score consumed by GRD',
        deadline,
        maxScore: 100,
        attachmentIds: [],
        allowedLanguages: 'python',
        evaluationMode: 'MANUAL',
        autoEvaluate: false,
        reportRequired: false,
        timeLimitMs: 1_000,
        memoryLimitKb: 65_536,
        testcases: []
      }
    }), 'create LAB source task');
    const labId = lab.id;
    await ok(await request.post(`/api/v1/labs/${labId}/publish`, { headers: teacherHeaders }), 'publish LAB task');

    const labSubmission = await ok<{ submissionId: number }>(await request.post(`/api/v1/labs/${labId}/submissions`, {
      headers: studentHeaders,
      multipart: {
        code: 'print("GRD E2E")',
        language: 'python'
      }
    }), 'submit LAB through real endpoint');
    await ok(await request.post(`/api/v1/labs/${labId}/submissions/${labSubmission.submissionId}/score`, {
      headers: teacherHeaders,
      data: {
        manualScore: 91,
        finalScore: 91,
        comment: 'GRD E2E lab score',
        changeReason: 'Issue #266 real source fixture'
      }
    }), 'score LAB submission');
    await ok(await request.put(`/api/v1/labs/${labId}/release-scores`, { headers: teacherHeaders }), 'release LAB scores');

    const homework = await ok<{ id: number }>(await request.post('/api/v1/homeworks', {
      headers: teacherHeaders,
      data: {
        courseId,
        title: `HWK source ${marker}`,
        description: 'Text homework score consumed by GRD',
        type: 'TEXT',
        deadline,
        totalScore: 100,
        allowResubmit: true,
        allowLateSubmit: false,
        showEvaluationBeforePublish: false,
        questions: [],
        testCases: []
      }
    }), 'create HWK source task');
    const homeworkId = homework.id;
    await ok(await request.put(`/api/v1/homeworks/${homeworkId}/publish`, { headers: teacherHeaders }), 'publish HWK task');

    const homeworkSubmission = await ok<{ submissionId: number }>(await request.post(`/api/v1/homeworks/${homeworkId}/submissions`, {
      headers: studentHeaders,
      data: { answerText: 'GRD E2E real homework submission' }
    }), 'submit HWK through real endpoint');
    await ok(await request.put(`/api/v1/submissions/${homeworkSubmission.submissionId}/review`, {
      headers: teacherHeaders,
      data: {
        manualScore: 84,
        finalScore: 84,
        comment: 'GRD E2E homework score'
      }
    }), 'review HWK submission');
    await ok(await request.put(`/api/v1/homeworks/${homeworkId}/scores/publish`, { headers: teacherHeaders }), 'release HWK scores');

    await ok(await request.post(`/api/v1/courses/${courseId}/grade-items`, {
      headers: teacherHeaders,
      data: {
        name: `LAB grade ${marker}`,
        sourceType: 'LAB',
        sourceId: labId,
        fullScore: 100,
        weight: 0.5,
        includedInFinal: true,
        sortOrder: 1
      }
    }), 'create LAB grade item');
    await ok(await request.post(`/api/v1/courses/${courseId}/grade-items`, {
      headers: teacherHeaders,
      data: {
        name: `HWK grade ${marker}`,
        sourceType: 'HWK',
        sourceId: homeworkId,
        fullScore: 100,
        weight: 0.5,
        includedInFinal: true,
        sortOrder: 2
      }
    }), 'create HWK grade item');

    const firstSync = await ok<{
      affectedItemCount: number;
      affectedStudentCount: number;
      syncedCount: number;
      missingCount: number;
      ungradedCount: number;
    }>(await request.post(`/api/v1/courses/${courseId}/grades/sync`, { headers: teacherHeaders }), 'sync real sources');
    expect(firstSync).toMatchObject({
      affectedItemCount: 2,
      affectedStudentCount: 2,
      syncedCount: 2,
      missingCount: 2,
      ungradedCount: 0
    });

    const secondSync = await ok<typeof firstSync>(
      await request.post(`/api/v1/courses/${courseId}/grades/sync`, { headers: teacherHeaders }),
      'repeat source sync'
    );
    expect(secondSync).toMatchObject({ syncedCount: 2, missingCount: 2, ungradedCount: 0 });

    const gradeTable = await ok<{ records: GradeRow[]; total: number }>(await request.get(`/api/v1/courses/${courseId}/grades`, {
      headers: teacherHeaders
    }), 'query teacher grade table');
    expect(gradeTable.total).toBe(2);
    const completedRow = gradeTable.records.find((row) => row.studentId === student.user.id);
    const missingRow = gradeTable.records.find((row) => row.studentId === missingStudent.user.id);
    expect(completedRow?.summary).toMatchObject({ finalScore: 87.5, finalStatus: 'CALCULATED' });
    expect(completedRow?.records.map((record) => record.rawScore).sort((left, right) => Number(left) - Number(right))).toEqual([84, 91]);
    expect(missingRow?.summary).toMatchObject({ finalScore: null, finalStatus: 'INCOMPLETE' });
    expect(missingRow?.records.every((record) => record.gradeStatus === 'MISSING')).toBe(true);

    const analysis = await ok<{
      totalStudentCount: number;
      completedCount: number;
      missingCount: number;
      averageScore: number;
      sourceDataTime: string;
    }>(await request.get(`/api/v1/courses/${courseId}/grade-analysis`, { headers: teacherHeaders }), 'query course analysis');
    expect(analysis).toMatchObject({ totalStudentCount: 2, completedCount: 1, missingCount: 1, averageScore: 87.5 });
    expect(analysis.sourceDataTime).toBeTruthy();

    const fullPublish = await request.post(`/api/v1/courses/${courseId}/grades/publish`, {
      headers: teacherHeaders,
      data: { publishScope: 'COURSE', studentIds: [], gradeItemIds: [] }
    });
    await error(fullPublish, 400, 'ERR-GRD-04', 'reject full publish with partial missing grades');

    const publish = await ok<{ publishId: number; publishedCount: number; notificationStatus: string }>(
      await request.post(`/api/v1/courses/${courseId}/grades/publish`, {
        headers: teacherHeaders,
        data: { publishScope: 'PARTIAL_STUDENTS', studentIds: [student.user.id], gradeItemIds: [] }
      }),
      'publish complete student only'
    );
    expect(publish).toMatchObject({ publishedCount: 1, notificationStatus: 'SENT' });

    const repeatedPublish = await ok<typeof publish>(await request.post(`/api/v1/courses/${courseId}/grades/publish`, {
      headers: teacherHeaders,
      data: { publishScope: 'PARTIAL_STUDENTS', studentIds: [student.user.id], gradeItemIds: [] }
    }), 'repeat idempotent publish');
    expect(repeatedPublish.publishId).toBe(publish.publishId);

    const myGrades = await ok<GradeRow>(await request.get(`/api/v1/courses/${courseId}/my-grades`, {
      headers: studentHeaders
    }), 'student queries published grades');
    expect(myGrades.studentId).toBe(student.user.id);
    expect(myGrades.summary).toMatchObject({ finalScore: 87.5, publishStatus: 'PUBLISHED' });
    expect(myGrades.records).toHaveLength(2);
    expect(myGrades.records.every((record) => record.publishStatus === 'PUBLISHED')).toBe(true);

    const hiddenGrades = await request.get(`/api/v1/courses/${courseId}/my-grades`, { headers: missingStudentHeaders });
    await error(hiddenGrades, 400, 'ERR-GRD-04', 'keep incomplete student grades unpublished');

    const review = await ok<{ requestId: number; status: string }>(await request.post(`/api/v1/courses/${courseId}/grade-review-requests`, {
      headers: studentHeaders,
      data: { targetType: 'FINAL_SCORE', reason: `Review ${marker}` }
    }), 'submit grade review');
    expect(review.status).toBe('PENDING');

    const duplicateReview = await request.post(`/api/v1/courses/${courseId}/grade-review-requests`, {
      headers: studentHeaders,
      data: { targetType: 'FINAL_SCORE', reason: `Duplicate ${marker}` }
    });
    await error(duplicateReview, 400, 'ERR-GRD-08', 'reject duplicate pending review');

    const processedReview = await ok<{ requestId: number; status: string }>(await request.put(`/api/v1/grade-review-requests/${review.requestId}/process`, {
      headers: teacherHeaders,
      data: { action: 'REJECT', responseComment: 'Scores match the real LAB/HWK sources' }
    }), 'process grade review');
    expect(processedReview).toMatchObject({ requestId: review.requestId, status: 'REJECTED' });

    await expect.poll(async () => {
      const notifications = await ok<{
        records: Array<{ title: string; sourceModule: string; sourceId: number }>;
      }>(
        await request.get('/api/v1/notifications?type=GRADE&size=100', { headers: studentHeaders }),
        'query student grade notifications'
      );
      return {
        publicationVisible: notifications.records.some((notification) =>
          notification.title === '成绩已发布'
          && notification.sourceModule === 'GRD'
          && notification.sourceId === publish.publishId),
        reviewVisible: notifications.records.some((notification) =>
          notification.title === '成绩复核已处理'
          && notification.sourceModule === 'GRD'
          && notification.sourceId === review.requestId)
      };
    }, {
      message: 'wait for asynchronous grade publication and review notifications',
      timeout: 10_000,
      intervals: [100, 250, 500, 1_000]
    }).toEqual({ publicationVisible: true, reviewVisible: true });

    const invalidAnalysis = await request.get(`/api/v1/courses/${courseId}/grade-analysis?targetType=INVALID`, {
      headers: teacherHeaders
    });
    await error(invalidAnalysis, 400, 'ERR-GRD-04', 'reject invalid analysis target');

    const studentTeacherApi = await request.get(`/api/v1/courses/${courseId}/grades`, { headers: studentHeaders });
    expect(studentTeacherApi.status(), await responseLabel(studentTeacherApi, 'reject student teacher-grade access')).toBe(403);

    const anonymousGrades = await request.get(`/api/v1/courses/${courseId}/my-grades`);
    expect(anonymousGrades.status(), await responseLabel(anonymousGrades, 'reject anonymous grade access')).toBe(401);
  });
});

function verifyDisposableProof(): boolean {
  const proofFile = process.env.E2E_GRD_DISPOSABLE_PROOF_FILE?.trim();
  const suppliedToken = process.env.E2E_GRD_DISPOSABLE_TOKEN?.trim();
  const baseUrl = process.env.E2E_BASE_URL?.trim();
  if (!proofFile || !suppliedToken || !baseUrl || !/^[0-9a-f]{64}$/.test(suppliedToken)) {
    return false;
  }

  try {
    const proofPath = realpathSync(proofFile);
    const tempRoot = realpathSync(process.env.TMPDIR?.trim() || '/tmp');
    const relativeProof = relative(tempRoot, proofPath);
    if (relativeProof.startsWith('..') || isAbsolute(relativeProof)) {
      return false;
    }
    if (basename(proofPath) !== 'disposable-proof'
      || !basename(dirname(proofPath)).startsWith('onlinejudge-grd-e2e.')) {
      return false;
    }

    const proofStat = statSync(proofPath);
    if ((proofStat.mode & 0o077) !== 0
      || (typeof process.getuid === 'function' && proofStat.uid !== process.getuid())) {
      return false;
    }

    const [storedToken, storedBaseUrl, storedBackendPid] = readFileSync(proofPath, 'utf8').trim().split('\n');
    const suppliedTokenBytes = Buffer.from(suppliedToken, 'utf8');
    const storedTokenBytes = Buffer.from(storedToken || '', 'utf8');
    if (storedTokenBytes.length !== suppliedTokenBytes.length
      || !timingSafeEqual(storedTokenBytes, suppliedTokenBytes)) {
      return false;
    }
    if (storedBaseUrl !== baseUrl || !/^http:\/\/127\.0\.0\.1:\d+$/.test(baseUrl)) {
      return false;
    }

    const backendPid = Number(storedBackendPid);
    if (!Number.isSafeInteger(backendPid) || backendPid <= 0) {
      return false;
    }
    process.kill(backendPid, 0);
    return true;
  } catch {
    return false;
  }
}

async function registerStudent(request: APIRequestContext, marker: string): Promise<AuthSession> {
  const username = marker.replaceAll('-', '').slice(0, 40);
  const password = 'Grd266Student@pass';
  await ok(await request.post('/api/v1/auth/register', {
    data: {
      username,
      password,
      userType: 'STUDENT',
      displayName: `GRD E2E missing ${marker}`
    }
  }), 'register missing-score student');
  return login(request, username, password);
}

async function login(request: APIRequestContext, account: string, password: string): Promise<AuthSession> {
  return ok<AuthSession>(await request.post('/api/v1/auth/login', {
    data: { account, password }
  }), `login ${account}`);
}

async function ok<T>(response: APIResponse, label: string): Promise<T> {
  const body = await json<ApiEnvelope<T>>(response);
  expect(response.ok(), `${label}: HTTP ${response.status()} ${JSON.stringify(body)}`).toBe(true);
  expect(body.code, `${label}: ${body.message}`).toBe('0');
  return body.data;
}

async function error(response: APIResponse, status: number, code: string, label: string) {
  const body = await json<ApiEnvelope<unknown>>(response);
  expect(response.status(), `${label}: ${JSON.stringify(body)}`).toBe(status);
  expect(body.code, `${label}: ${body.message}`).toBe(code);
  expect(body.data).toBeNull();
}

async function responseLabel(response: APIResponse, label: string) {
  return `${label}: HTTP ${response.status()} ${JSON.stringify(await json(response))}`;
}

async function json<T = unknown>(response: APIResponse): Promise<T> {
  return response.json() as Promise<T>;
}

function bearer(token: string) {
  return { Authorization: `Bearer ${token}` };
}

function localDateTimeAfterDays(days: number) {
  return new Date(Date.now() + days * 24 * 60 * 60 * 1_000).toISOString().slice(0, 19);
}
