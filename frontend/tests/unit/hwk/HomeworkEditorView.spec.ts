import { flushPromises, mount, RouterLinkStub, type VueWrapper } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { matchedRouteKey, routerKey, type Router } from 'vue-router';
import HomeworkEditorView from '../../../src/views/hwk/HomeworkEditorView.vue';
import * as homeworkApi from '../../../src/api/hwk/homeworks';
import { currentUser } from '../../../src/app/runtimeContext';
import type { HomeworkDetail, HomeworkType } from '../../../src/types/hwk';

vi.mock('../../../src/api/hwk/homeworks');

const replace = vi.fn();
const mountedEditors = new Set<VueWrapper>();

describe('HomeworkEditorView', () => {
  beforeEach(() => {
    vi.resetAllMocks();
    window.sessionStorage.clear();
    currentUser.value = null;
    replace.mockResolvedValue(undefined);
    vi.spyOn(Date, 'now').mockReturnValue(new Date('2026-08-20T00:00:00+08:00').getTime());
  });

  afterEach(() => {
    for (const wrapper of mountedEditors) {
      if (!wrapper.vm.$.isUnmounted) wrapper.unmount();
    }
    mountedEditors.clear();
    currentUser.value = null;
    window.sessionStorage.clear();
    vi.useRealTimers();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('presents staged editing for all four homework types without raw JSON fields', async () => {
    const wrapper = mountEditor({ courseId: 101 });

    expect(wrapper.text()).toContain('基础信息');
    expect(wrapper.text()).toContain('作业内容');
    expect(wrapper.text()).toContain('提交与反馈规则');
    expect(wrapper.text()).toContain('发布检查');
    expect(wrapper.get('[name="type"]').findAll('option').map((option) => option.text())).toEqual([
      '文本作业',
      '客观题作业',
      '附件作业',
      '代码作业'
    ]);
    expect(wrapper.find('[name="optionsJson"]').exists()).toBe(false);
    expect(wrapper.find('[name="answerJson"]').exists()).toBe(false);
    expect(wrapper.find('[name="languageLimitJson"]').exists()).toBe(false);

    await wrapper.get('[name="type"]').setValue('FILE');

    expect(wrapper.find('[data-testid="file-contract-notice"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="type-content-check"]').attributes('data-ready')).toBe('true');
    expect(wrapper.get('[data-testid="type-content-check"]').text()).toContain('附件提交契约已就绪');
    expect(wrapper.get('[data-testid="type-content-check"]').text()).not.toContain('#214');
  });

  it('creates a text draft, exposes pending state, then updates the created draft', async () => {
    let resolveCreate!: (value: HomeworkDetail) => void;
    vi.mocked(homeworkApi.createHomework).mockReturnValueOnce(new Promise((resolve) => {
      resolveCreate = resolve;
    }));
    vi.mocked(homeworkApi.updateHomework).mockResolvedValueOnce(detail({ id: 9, title: '文本作业草稿' }));
    const wrapper = mountEditor({ courseId: 101 });
    await fillBasic(wrapper, 'TEXT');

    await wrapper.get('[data-testid="homework-editor-form"]').trigger('submit');

    expect(wrapper.get('[data-testid="save-homework"]').attributes('disabled')).toBeDefined();
    expect(wrapper.get('[data-testid="save-homework"]').text()).toBe('保存中…');
    expect(homeworkApi.createHomework).toHaveBeenCalledWith(expect.objectContaining({
      courseId: 101,
      title: '文本作业草稿',
      type: 'TEXT',
      deadline: new Date('2026-08-25T23:59').toISOString(),
      totalScore: 100,
      questions: [],
      testCases: [],
      languageLimitJson: null
    }));

    resolveCreate(detail({ id: 9, title: '文本作业草稿' }));
    await flushPromises();

    expect(wrapper.get('[role="status"]').text()).toContain('草稿已保存');
    expect(replace).toHaveBeenCalledWith({
      name: 'homework-edit',
      params: { courseId: 101, homeworkId: 9 }
    });

    await wrapper.get('[data-testid="homework-editor-form"]').trigger('submit');
    await flushPromises();

    expect(homeworkApi.createHomework).toHaveBeenCalledTimes(1);
    expect(homeworkApi.updateHomework).toHaveBeenCalledWith(9, expect.objectContaining({
      title: '文本作业草稿',
      type: 'TEXT'
    }));
  });

  it('ignores a save response after the route switches to another homework context', async () => {
    let resolveCreate!: (value: HomeworkDetail) => void;
    vi.mocked(homeworkApi.createHomework).mockReturnValueOnce(new Promise((resolve) => {
      resolveCreate = resolve;
    }));
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(detail({
      id: 22,
      courseId: 202,
      chapterId: 77,
      title: '新上下文作业'
    }));
    vi.mocked(homeworkApi.updateHomework).mockResolvedValueOnce(detail({
      id: 22,
      courseId: 202,
      chapterId: 77,
      title: '新上下文作业'
    }));
    const wrapper = mountEditor({ courseId: 101 });
    await fillBasic(wrapper, 'TEXT');

    await wrapper.get('[data-testid="homework-editor-form"]').trigger('submit');
    await wrapper.setProps({ courseId: 202, homeworkId: 22 });
    await flushPromises();

    expect((wrapper.get('[name="title"]').element as HTMLInputElement).value).toBe('新上下文作业');

    resolveCreate(detail({ id: 9, courseId: 101, title: '旧保存响应' }));
    await flushPromises();

    expect(replace).not.toHaveBeenCalled();
    expect(wrapper.find('[role="status"]').exists()).toBe(false);
    expect((wrapper.get('[name="title"]').element as HTMLInputElement).value).toBe('新上下文作业');

    await wrapper.get('[data-testid="homework-editor-form"]').trigger('submit');
    await flushPromises();

    expect(homeworkApi.updateHomework).toHaveBeenCalledWith(22, expect.objectContaining({
      courseId: 202,
      chapterId: 77,
      title: '新上下文作业'
    }));
  });

  it('serializes structured objective options and answers into the existing DTO', async () => {
    vi.mocked(homeworkApi.createHomework).mockResolvedValueOnce(detail({ id: 10, type: 'OBJECTIVE' }));
    const wrapper = mountEditor({ courseId: 101 });
    await fillBasic(wrapper, 'OBJECTIVE');

    await wrapper.get('[name="question-stem-0"]').setValue('以下哪些是 JVM 语言？');
    await wrapper.get('[name="question-type-0"]').setValue('MULTIPLE_CHOICE');
    await wrapper.get('[name="question-option-0-0"]').setValue('Java');
    await wrapper.get('[name="question-option-0-1"]').setValue('Kotlin');
    await wrapper.get('[data-testid="question-answer-0-1"]').setValue(true);
    await wrapper.get('[data-testid="question-answer-0-0"]').setValue(true);
    await wrapper.get('[name="question-score-0"]').setValue('100');
    await wrapper.get('[data-testid="homework-editor-form"]').trigger('submit');
    await flushPromises();

    expect(homeworkApi.createHomework).toHaveBeenCalledWith(expect.objectContaining({
      type: 'OBJECTIVE',
      questions: [{
        questionType: 'MULTIPLE_CHOICE',
        stem: '以下哪些是 JVM 语言？',
        optionsJson: JSON.stringify(['Java', 'Kotlin']),
        answerJson: JSON.stringify(['Java', 'Kotlin']),
        score: 100,
        sortOrder: 1
      }],
      testCases: []
    }));
  });

  it('serializes selected code languages and structured test cases into the existing DTO', async () => {
    vi.mocked(homeworkApi.createHomework).mockResolvedValueOnce(detail({ id: 11, type: 'CODE' }));
    const wrapper = mountEditor({ courseId: 101 });
    await fillBasic(wrapper, 'CODE');

    await wrapper.get('[data-testid="language-python"]').setValue(true);
    await wrapper.get('[name="testcase-input-0"]').setValue('1 2');
    await wrapper.get('[name="testcase-output-0"]').setValue('3');
    await wrapper.get('[name="testcase-weight-0"]').setValue('100');
    await wrapper.get('[data-testid="homework-editor-form"]').trigger('submit');
    await flushPromises();

    expect(homeworkApi.createHomework).toHaveBeenCalledWith(expect.objectContaining({
      type: 'CODE',
      languageLimitJson: JSON.stringify(['python']),
      timeLimitMs: 1000,
      memoryLimitKb: 65536,
      outputCompareMode: 'TRIM',
      questions: [],
      testCases: [{
        inputData: '1 2',
        expectedOutput: '3',
        scoreWeight: 100,
        hidden: false,
        timeLimitMs: 1000,
        memoryLimitKb: 65536,
        sortOrder: 1
      }]
    }));
    expect(wrapper.find('[data-testid="language-java"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="language-cpp"]').exists()).toBe(false);
    expect(wrapper.find('[data-testid="language-javascript"]').exists()).toBe(false);
    expect(wrapper.find('[name="outputCompareMode"]').exists()).toBe(false);
    expect(wrapper.get('[data-testid="output-compare-notice"]').text()).toContain('忽略首尾空白');
  });

  it('blocks legacy unsupported code languages until the teacher explicitly removes them', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(detail({
      id: 12,
      type: 'CODE',
      languageLimitJson: JSON.stringify(['java', 'cpp', 'javascript']),
      outputCompareMode: 'EXACT',
      testCases: [{
        id: 121,
        homeworkId: 12,
        inputData: '1 2',
        expectedOutput: '3',
        scoreWeight: 100,
        hidden: false,
        timeLimitMs: 1000,
        memoryLimitKb: 65536,
        sortOrder: 1
      }]
    }));
    vi.mocked(homeworkApi.updateHomework).mockResolvedValueOnce(detail({ id: 12, type: 'CODE' }));
    const wrapper = mountEditor({ courseId: 101, homeworkId: 12 });
    await flushPromises();

    expect(wrapper.get('[data-testid="unsupported-language-warning"]').text()).toContain('当前沙箱仅支持 Python');
    expect(wrapper.get('[data-testid="unsupported-language-warning"]').text()).toContain('Java');
    expect(wrapper.get('[data-testid="unsupported-language-warning"]').text()).toContain('C++');
    expect(wrapper.get('[data-testid="unsupported-language-warning"]').text()).toContain('JavaScript');

    await wrapper.get('[data-testid="homework-editor-form"]').trigger('submit');

    expect(homeworkApi.updateHomework).not.toHaveBeenCalled();
    expect(wrapper.get('[data-testid="editor-error"]').text()).toContain('请先显式移除不受支持的语言');

    await wrapper.get('[data-testid="remove-unsupported-language-java"]').trigger('click');
    await wrapper.get('[data-testid="remove-unsupported-language-cpp"]').trigger('click');
    await wrapper.get('[data-testid="remove-unsupported-language-javascript"]').trigger('click');
    await wrapper.get('[data-testid="language-python"]').setValue(true);
    await wrapper.get('[data-testid="homework-editor-form"]').trigger('submit');
    await flushPromises();

    expect(homeworkApi.updateHomework).toHaveBeenCalledWith(12, expect.objectContaining({
      languageLimitJson: JSON.stringify(['python']),
      outputCompareMode: 'TRIM'
    }));
  });

  it('preserves valid empty-output and zero-weight CODE test cases', async () => {
    vi.mocked(homeworkApi.createHomework).mockResolvedValueOnce(detail({ id: 12, type: 'CODE' }));
    const wrapper = mountEditor({ courseId: 101 });
    await fillBasic(wrapper, 'CODE');

    await wrapper.get('[data-testid="language-python"]').setValue(true);
    await wrapper.get('[name="testcase-input-0"]').setValue('heartbeat');
    await wrapper.get('[name="testcase-output-0"]').setValue('');
    await wrapper.get('[name="testcase-weight-0"]').setValue('100');
    const addTestCase = wrapper.findAll('button').find((button) => button.text() === '新增用例');
    expect(addTestCase).toBeDefined();
    await addTestCase!.trigger('click');
    await wrapper.get('[name="testcase-input-1"]').setValue('side effect check');
    await wrapper.get('[name="testcase-output-1"]').setValue('');
    await wrapper.get('[name="testcase-weight-1"]').setValue('0');
    await wrapper.get('[data-testid="homework-editor-form"]').trigger('submit');
    await flushPromises();

    expect(homeworkApi.createHomework).toHaveBeenCalledWith(expect.objectContaining({
      testCases: [
        expect.objectContaining({ expectedOutput: '', scoreWeight: 100 }),
        expect.objectContaining({ expectedOutput: '', scoreWeight: 0 })
      ]
    }));
  });

  it('loads an existing objective draft into structured controls and updates it', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(detail({
      id: 7,
      chapterId: 8,
      type: 'OBJECTIVE',
      questions: [{
        id: 71,
        homeworkId: 7,
        questionType: 'SINGLE_CHOICE',
        stem: '1 + 1 = ?',
        optionsJson: JSON.stringify(['1', '2']),
        answerJson: JSON.stringify(['2']),
        score: 100,
        sortOrder: 1
      }]
    }));
    vi.mocked(homeworkApi.updateHomework).mockResolvedValueOnce(detail({ id: 7, title: '更新后的客观题' }));
    const wrapper = mountEditor({ courseId: 101, homeworkId: 7 });
    await flushPromises();

    expect((wrapper.get('[name="question-option-0-0"]').element as HTMLInputElement).value).toBe('1');
    expect((wrapper.get('[name="question-option-0-1"]').element as HTMLInputElement).value).toBe('2');
    expect((wrapper.get('[data-testid="question-answer-0-1"]').element as HTMLInputElement).checked).toBe(true);

    await wrapper.get('[name="title"]').setValue('更新后的客观题');
    await wrapper.get('[data-testid="homework-editor-form"]').trigger('submit');
    await flushPromises();

    expect(homeworkApi.updateHomework).toHaveBeenCalledWith(7, expect.objectContaining({
      title: '更新后的客观题',
      chapterId: 8,
      questions: [expect.objectContaining({
        optionsJson: JSON.stringify(['1', '2']),
        answerJson: JSON.stringify(['2'])
      })]
    }));
  });

  it('round-trips legacy object-shaped objective options without exposing JSON controls', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(detail({
      id: 8,
      type: 'OBJECTIVE',
      questions: [{
        id: 81,
        homeworkId: 8,
        questionType: 'SINGLE_CHOICE',
        stem: '选择一种 JVM 语言',
        optionsJson: JSON.stringify({ A: 'Java', B: 'Python' }),
        answerJson: JSON.stringify(['A']),
        score: 100,
        sortOrder: 1
      }]
    }));
    vi.mocked(homeworkApi.updateHomework).mockResolvedValueOnce(detail({ id: 8, type: 'OBJECTIVE' }));
    const wrapper = mountEditor({ courseId: 101, homeworkId: 8 });
    await flushPromises();

    expect((wrapper.get('[name="question-option-0-0"]').element as HTMLInputElement).value).toBe('Java');
    expect((wrapper.get('[name="question-option-0-1"]').element as HTMLInputElement).value).toBe('Python');
    expect((wrapper.get('[data-testid="question-answer-0-0"]').element as HTMLInputElement).checked).toBe(true);
    expect(wrapper.find('[name="optionsJson"]').exists()).toBe(false);

    await wrapper.get('[data-testid="homework-editor-form"]').trigger('submit');
    await flushPromises();

    expect(homeworkApi.updateHomework).toHaveBeenCalledWith(8, expect.objectContaining({
      questions: [expect.objectContaining({
        optionsJson: JSON.stringify({ A: 'Java', B: 'Python' }),
        answerJson: JSON.stringify(['A'])
      })]
    }));
  });

  it('hydrates JUDGE and TRUE_FALSE drafts and serializes both with the canonical TRUE_FALSE type', async () => {
    vi.mocked(homeworkApi.getHomeworkDetail).mockResolvedValueOnce(detail({
      id: 13,
      type: 'OBJECTIVE',
      questions: [
        {
          id: 131,
          homeworkId: 13,
          questionType: 'JUDGE',
          stem: 'JavaScript 可以在浏览器运行。',
          optionsJson: JSON.stringify(['正确', '错误']),
          answerJson: JSON.stringify(['正确']),
          score: 50,
          sortOrder: 1
        },
        {
          id: 132,
          homeworkId: 13,
          questionType: 'TRUE_FALSE',
          stem: 'HTTP 是无状态协议。',
          optionsJson: JSON.stringify(['正确', '错误']),
          answerJson: JSON.stringify(['正确']),
          score: 50,
          sortOrder: 2
        }
      ]
    }));
    vi.mocked(homeworkApi.updateHomework).mockResolvedValueOnce(detail({ id: 13, type: 'OBJECTIVE' }));
    const wrapper = mountEditor({ courseId: 101, homeworkId: 13 });
    await flushPromises();

    expect((wrapper.get('[name="question-type-0"]').element as HTMLSelectElement).value).toBe('TRUE_FALSE');
    expect((wrapper.get('[name="question-type-1"]').element as HTMLSelectElement).value).toBe('TRUE_FALSE');

    await wrapper.get('[data-testid="homework-editor-form"]').trigger('submit');
    await flushPromises();

    expect(homeworkApi.updateHomework).toHaveBeenCalledWith(13, expect.objectContaining({
      questions: [
        expect.objectContaining({ questionType: 'TRUE_FALSE', answerJson: JSON.stringify(['正确']) }),
        expect.objectContaining({ questionType: 'TRUE_FALSE', answerJson: JSON.stringify(['正确']) })
      ]
    }));
  });

  it('reports field-level validation errors and keeps invalid input visible', async () => {
    const wrapper = mountEditor({ courseId: 101 });
    await wrapper.get('[name="title"]').setValue('未完成作业');
    await wrapper.get('[name="type"]').setValue('OBJECTIVE');
    await wrapper.get('[name="deadline"]').setValue('2026-08-19T10:00');
    await wrapper.get('[name="totalScore"]').setValue('0');
    await wrapper.get('[data-testid="homework-editor-form"]').trigger('submit');

    expect(homeworkApi.createHomework).not.toHaveBeenCalled();
    expect(wrapper.get('[data-testid="editor-error"]').text()).toContain('作业说明不能为空');
    expect(wrapper.get('[data-testid="editor-error"]').text()).toContain('截止时间必须晚于当前时间');
    expect(wrapper.get('[data-testid="editor-error"]').text()).toContain('满分必须是正整数');
    expect(wrapper.get('[data-testid="editor-error"]').text()).toContain('第 1 题题干不能为空');
    expect(wrapper.get('[data-testid="editor-error"]').text()).toContain('第 1 题的选项不能为空');
    expect(wrapper.get('[data-testid="editor-error"]').text()).toContain('第 1 题必须选择正确答案');
    expect((wrapper.get('[name="title"]').element as HTMLInputElement).value).toBe('未完成作业');
  });

  it('rejects decimal values for integer API fields before sending a request', async () => {
    const wrapper = mountEditor({ courseId: 101 });
    await fillBasic(wrapper, 'TEXT');
    await wrapper.get('[name="totalScore"]').setValue('99.5');
    await wrapper.get('[data-testid="homework-editor-form"]').trigger('submit');

    expect(homeworkApi.createHomework).not.toHaveBeenCalled();
    expect(wrapper.get('[data-testid="editor-error"]').text()).toContain('满分必须是正整数');
    expect((wrapper.get('[name="totalScore"]').element as HTMLInputElement).value).toBe('99.5');
  });

  it('preserves every field when saving fails and restores the save action', async () => {
    vi.mocked(homeworkApi.createHomework).mockRejectedValueOnce(new Error('网络中断，请重试'));
    const wrapper = mountEditor({ courseId: 101 });
    await fillBasic(wrapper, 'FILE');
    await wrapper.get('[data-testid="homework-editor-form"]').trigger('submit');
    await flushPromises();

    expect(wrapper.get('[data-testid="editor-error"]').text()).toContain('网络中断，请重试');
    expect((wrapper.get('[name="title"]').element as HTMLInputElement).value).toBe('文本作业草稿');
    expect((wrapper.get('[name="type"]').element as HTMLSelectElement).value).toBe('FILE');
    expect(wrapper.get('[data-testid="save-homework"]').attributes('disabled')).toBeUndefined();
    expect(wrapper.get('[data-testid="save-homework"]').text()).toBe('保存草稿');
  });

  it('auto-saves unsaved editor input and restores the fresh local draft on re-entry', async () => {
    vi.useFakeTimers();
    const storageKey = 'oj:teacher-homework-draft:v1:anonymous:101:new';
    const wrapper = mountEditor({ courseId: 101 });

    await wrapper.get('[name="title"]').setValue('离开后可恢复的作业');
    await wrapper.get('[name="description"]').setValue('这段未提交到服务器的内容不能丢失。');
    expect(window.sessionStorage.getItem(storageKey)).toBeNull();

    await vi.advanceTimersByTimeAsync(500);

    expect(window.sessionStorage.getItem(storageKey)).toContain('离开后可恢复的作业');
    wrapper.unmount();

    const restored = mountEditor({ courseId: 101 });
    await flushPromises();

    expect((restored.get('[name="title"]').element as HTMLInputElement).value)
      .toBe('离开后可恢复的作业');
    expect((restored.get('[name="description"]').element as HTMLTextAreaElement).value)
      .toBe('这段未提交到服务器的内容不能丢失。');
    expect(restored.get('[data-testid="editor-draft-status"]').text()).toContain('已恢复');
    restored.unmount();
  });

  it('saves locally and asks for confirmation before leaving with server-unsaved changes', async () => {
    const leaveGuards = new Set<(...args: never[]) => unknown>();
    vi.stubGlobal('confirm', vi.fn(() => false));
    const wrapper = mountEditor({ courseId: 101 }, leaveGuards);

    await wrapper.get('[name="title"]').setValue('尚未保存到服务器');
    const guard = [...leaveGuards][0];

    expect(guard).toBeDefined();
    expect(guard!()).toBe(false);
    expect(confirm).toHaveBeenCalledWith(expect.stringContaining('尚未保存到服务器'));
    expect(window.sessionStorage.getItem('oj:teacher-homework-draft:v1:anonymous:101:new'))
      .toContain('尚未保存到服务器');

    const unloadEvent = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(unloadEvent);
    expect(unloadEvent.defaultPrevented).toBe(true);

    wrapper.unmount();
    expect(leaveGuards.size).toBe(0);
  });
});

function mountEditor(
  props: { courseId: number; homeworkId?: number },
  leaveGuards?: Set<(...args: never[]) => unknown>
) {
  const matchedRecord = leaveGuards
    ? { value: { leaveGuards } }
    : undefined;
  const wrapper = mount(HomeworkEditorView, {
    props,
    global: {
      stubs: { RouterLink: RouterLinkStub },
      provide: {
        [routerKey as symbol]: { replace } as unknown as Router,
        ...(matchedRecord ? { [matchedRouteKey as symbol]: matchedRecord } : {})
      }
    }
  });
  mountedEditors.add(wrapper);
  return wrapper;
}

async function fillBasic(wrapper: VueWrapper, type: HomeworkType) {
  await wrapper.get('[name="title"]').setValue('文本作业草稿');
  await wrapper.get('[name="description"]').setValue('完成本周作业并按要求提交。');
  await wrapper.get('[name="type"]').setValue(type);
  await wrapper.get('[name="deadline"]').setValue('2026-08-25T23:59');
  await wrapper.get('[name="totalScore"]').setValue('100');
}

function detail(overrides: Partial<HomeworkDetail> = {}): HomeworkDetail {
  return {
    id: 7,
    courseId: 101,
    chapterId: null,
    title: '原作业',
    description: '完成本周作业并按要求提交。',
    type: 'TEXT',
    status: 'DRAFT',
    deadline: '2026-08-25T23:59:00',
    totalScore: 100,
    allowResubmit: true,
    allowLateSubmit: false,
    showEvaluationBeforePublish: true,
    deleted: false,
    judgeConfigId: null,
    createdBy: 9,
    publishedAt: null,
    createdAt: '2026-08-20T08:00:00',
    updatedAt: '2026-08-20T08:00:00',
    languageLimitJson: null,
    timeLimitMs: 1000,
    memoryLimitKb: 65536,
    outputCompareMode: 'EXACT',
    questions: [],
    testCases: [],
    ...overrides
  };
}
