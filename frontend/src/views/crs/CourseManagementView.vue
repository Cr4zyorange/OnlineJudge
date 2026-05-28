<template>
  <div class="page-shell">
    <header class="navbar-container">
      <nav class="navbar">
        <div class="navbar-logo">
          <h2><i class="bi bi-book-half"></i> 学知实训平台</h2>
        </div>
        <div class="navbar-menu">
          <a class="active" href="/courses">课程中心</a>
          <a>实训模块</a>
          <a>作业评测</a>
          <a :class="{ disabled: !gradeAnalysisHref }" :href="gradeAnalysisHref || undefined" :aria-disabled="!gradeAnalysisHref">
            成绩分析
          </a>
        </div>
        <div class="navbar-user">
          <i class="bi bi-bell"></i>
          <span class="avatar">T</span>
        </div>
      </nav>
    </header>

    <div class="container">
      <aside class="sidebar">
        <div class="sidebar-title">
          <h3>课程管理</h3>
        </div>
        <ul class="sidebar-menu">
          <li>
            <button class="menu-button" :class="{ active: activeTab === 'all' && !chapterCourse }" type="button" @click="switchTab('all')">
              <i class="bi bi-grid"></i> 全部课程
            </button>
          </li>
          <li>
            <button class="menu-button" :class="{ active: activeTab === 'managed' && !chapterCourse }" type="button" @click="switchTab('managed')">
              <i class="bi bi-person-check"></i> 我管理的
            </button>
          </li>
          <li>
            <button class="menu-button" :class="{ active: activeTab === 'archived' && !chapterCourse }" type="button" @click="switchTab('archived')">
              <i class="bi bi-archive"></i> 归档记录
            </button>
          </li>
        </ul>

        <div class="sidebar-card">
          <h4><i class="bi bi-graph-up"></i> 本页统计</h4>
          <div class="stat-row">
            <span>课程总数</span>
            <strong>{{ stats.all }}</strong>
          </div>
          <div class="stat-row">
            <span>可管理</span>
            <strong>{{ stats.managed }}</strong>
          </div>
          <div class="stat-row">
            <span>已归档</span>
            <strong>{{ stats.archived }}</strong>
          </div>
        </div>
      </aside>

      <main class="main-content">
        <div class="page-header">
          <div>
            <h2>{{ pageTitle }}</h2>
            <p>{{ pageSubtitle }}</p>
          </div>
          <div v-if="!chapterCourse" class="header-actions">
            <label class="search-box">
              <i class="bi bi-search"></i>
              <input v-model="keyword" type="search" placeholder="搜索课程、学期或分类" @keyup.enter="loadCourses" />
            </label>
            <button class="btn btn-secondary icon-btn" type="button" title="刷新" @click="loadCourses">
              <i class="bi bi-arrow-clockwise"></i>
            </button>
          </div>
          <div v-else class="header-actions">
            <button class="btn btn-secondary" type="button" @click="closeChapterManagement">
              <i class="bi bi-arrow-left"></i> 返回课程
            </button>
            <button class="btn btn-secondary icon-btn" type="button" title="刷新章节" @click="loadChapters">
              <i class="bi bi-arrow-clockwise"></i>
            </button>
          </div>
        </div>

        <section v-if="chapterCourse" class="workspace">
          <form class="course-form" data-testid="chapter-form" @submit.prevent="submitChapter">
            <div class="form-title">
              <h3>{{ editingChapter ? '编辑章节' : '创建章节' }}</h3>
              <button v-if="editingChapter" class="text-button" type="button" @click="resetChapterForm">取消编辑</button>
            </div>
            <label>
              <span>章节标题</span>
              <input data-testid="chapter-title" v-model.trim="chapterForm.title" type="text" maxlength="200" placeholder="例如：课程导论" />
            </label>
            <label>
              <span>父章节</span>
              <select data-testid="chapter-parent" v-model="chapterParentValue">
                <option value="">作为一级章节</option>
                <option v-for="item in flatChapters" :key="item.chapter.id" :value="String(item.chapter.id)" :disabled="editingChapter?.id === item.chapter.id">
                  {{ item.prefix }}{{ item.chapter.title }}
                </option>
              </select>
            </label>
            <label>
              <span>排序号</span>
              <input v-model.number="chapterForm.orderNum" type="number" min="0" step="1" placeholder="默认追加到末尾" />
            </label>
            <label>
              <span>章节说明</span>
              <textarea data-testid="chapter-content" v-model.trim="chapterForm.content" rows="5" placeholder="填写教学目标、知识点或学习建议"></textarea>
            </label>
            <p v-if="chapterError" class="message error">{{ chapterError }}</p>
            <p v-if="chapterSuccess" class="message success">{{ chapterSuccess }}</p>
            <button class="btn submit-btn" type="submit" :disabled="chapterSubmitting">
              <i class="bi bi-check2-circle"></i>
              {{ chapterSubmitting ? '提交中' : editingChapter ? '保存章节' : '创建章节' }}
            </button>
          </form>

          <section class="course-panel">
            <div v-if="chapterLoading" class="state-card">章节加载中...</div>
            <div v-else-if="chapterLoadError" class="state-card error">{{ chapterLoadError }}</div>
            <div v-else-if="chapters.length === 0" class="state-card">暂无章节，创建第一个章节后会展示为目录树。</div>
            <div v-else class="chapter-tree">
              <ChapterNode
                v-for="chapter in chapters"
                :key="chapter.id"
                :chapter="chapter"
                :course-id="chapterCourse.id"
                :depth="0"
                @edit="editChapter"
                @delete="removeChapter"
                @move="moveChapter"
              />
            </div>
          </section>
        </section>

        <section v-else class="workspace" :class="{ single: activeTab !== 'managed' }">
          <form v-if="activeTab === 'managed'" class="course-form" @submit.prevent="submitCourse">
            <div class="form-title">
              <h3>{{ editingCourse ? '编辑课程' : '创建课程' }}</h3>
              <button v-if="editingCourse" class="text-button" type="button" @click="resetForm">取消编辑</button>
            </div>

            <label>
              <span>课程名称</span>
              <input v-model.trim="form.name" type="text" maxlength="100" placeholder="例如：软件工程基础" />
            </label>
            <label>
              <span>课程简介</span>
              <textarea v-model.trim="form.description" rows="4" placeholder="填写课程目标、适用对象和教学安排"></textarea>
            </label>

            <div class="form-grid">
              <label>
                <span>学期</span>
                <input v-model.trim="form.semester" type="text" placeholder="2026春" />
              </label>
              <label>
                <span>课程分类</span>
                <input v-model.trim="form.category" type="text" placeholder="软件工程" />
              </label>
              <label>
                <span>开课日期</span>
                <input v-model="form.startDate" type="date" />
              </label>
              <label>
                <span>结课日期</span>
                <input v-model="form.endDate" type="date" />
              </label>
            </div>

            <div class="form-grid">
              <label>
                <span>加入方式</span>
                <select v-model="form.enrollmentMode">
                  <option value="PUBLIC">公开加入</option>
                  <option value="INVITE">邀请码加入</option>
                  <option value="REVIEW">申请审核</option>
                </select>
              </label>
              <label>
                <span>课程状态</span>
                <select v-model="form.status">
                  <option value="DRAFT">草稿</option>
                  <option value="NOT_STARTED">未开课</option>
                  <option value="ACTIVE">已发布</option>
                  <option value="CLOSED">已结课</option>
                </select>
              </label>
            </div>

            <p v-if="formError" class="message error">{{ formError }}</p>
            <p v-if="successMessage" class="message success">{{ successMessage }}</p>

            <button class="btn submit-btn" type="submit" :disabled="submitting">
              <i class="bi bi-check2-circle"></i>
              {{ submitting ? '提交中' : editingCourse ? '保存课程' : '创建课程' }}
            </button>
          </form>

          <section class="course-panel">
            <div v-if="loading" class="state-card">课程加载中...</div>
            <div v-else-if="loadError" class="state-card error">{{ loadError }}</div>
            <div v-else-if="visibleCourses.length === 0" class="state-card">{{ emptyText }}</div>
            <div v-else class="card-grid">
              <article
                v-for="course in visibleCourses"
                :key="course.id"
                class="course-card"
                :class="{ interactive: canOpenCourseDetail }"
                @click="openCourseDetail(course)"
              >
                <div class="card-content">
                  <div class="card-topline">
                    <span class="card-tag">{{ course.category || '未分类' }}</span>
                    <span class="status-pill">{{ statusText(course.status) }}</span>
                  </div>
                  <h3>{{ course.name }}</h3>
                  <p class="card-desc" :title="course.description || '暂无课程简介'">{{ summarizeDescription(course.description) }}</p>
                  <div class="card-meta">
                    <span><i class="bi bi-calendar-event"></i> {{ course.semester || '未设置学期' }}</span>
                    <span><i class="bi bi-people"></i> {{ course.memberCount }} 人</span>
                    <span><i class="bi bi-person-badge"></i> {{ course.teacherName }}</span>
                  </div>
                  <div v-if="activeTab === 'managed'" class="card-actions">
                    <button class="card-btn" type="button" :disabled="!course.manageable" @click.stop="editCourse(course)">
                      <i class="bi bi-pencil-square"></i> 编辑
                    </button>
                    <button class="card-btn" type="button" :disabled="!course.manageable" @click.stop="openChapterManagement(course)">
                      <i class="bi bi-list-nested"></i> 章节
                    </button>
                    <button class="card-btn danger" type="button" :disabled="!course.manageable" @click.stop="archive(course)">
                      <i class="bi bi-archive"></i> 归档
                    </button>
                  </div>
                </div>
              </article>
            </div>
          </section>
        </section>
      </main>
    </div>

    <div v-if="selectedCourse" class="modal-backdrop" @click.self="closeCourseDetail">
      <section class="course-modal" role="dialog" aria-modal="true" aria-label="课程详情">
        <div class="modal-header">
          <div>
            <p class="modal-label">课程详情</p>
            <p class="modal-eyebrow">{{ selectedCourse.category || '未分类' }}</p>
            <h3>{{ selectedCourse.name }}</h3>
          </div>
          <button class="modal-close" type="button" title="关闭详情" @click="closeCourseDetail">
            <i class="bi bi-x-lg"></i>
          </button>
        </div>

        <div class="modal-status-row">
          <span class="card-tag">{{ enrollmentModeText(selectedCourse.enrollmentMode) }}</span>
          <span class="status-pill">{{ statusText(selectedCourse.status) }}</span>
        </div>

        <div class="modal-grid">
          <div class="detail-item">
            <span>教师</span>
            <strong>{{ selectedCourse.teacherName }}</strong>
          </div>
          <div class="detail-item">
            <span>学期</span>
            <strong>{{ selectedCourse.semester || '未设置' }}</strong>
          </div>
          <div class="detail-item">
            <span>开课日期</span>
            <strong>{{ selectedCourse.startDate || '未设置' }}</strong>
          </div>
          <div class="detail-item">
            <span>结课日期</span>
            <strong>{{ selectedCourse.endDate || '未设置' }}</strong>
          </div>
        </div>

        <div class="detail-block">
          <span>完整简介</span>
          <p>{{ selectedCourse.description || '暂无课程简介' }}</p>
        </div>

        <div class="detail-block">
          <span>章节目录</span>
          <p v-if="detailChapterLoading">章节加载中...</p>
          <p v-else-if="detailChapterError">{{ detailChapterError }}</p>
          <p v-else-if="detailChapters.length === 0">暂无章节目录</p>
          <div v-else class="compact-tree">
            <CompactChapterNode v-for="chapter in detailChapters" :key="chapter.id" :chapter="chapter" :depth="0" />
          </div>
        </div>

        <div class="modal-actions-placeholder">
          <span>预留操作区</span>
          <div class="placeholder-actions">
            <button class="card-btn" type="button" disabled>
              <i class="bi bi-box-arrow-in-right"></i> 加入课程
            </button>
            <button v-if="selectedCourse.manageable" class="card-btn" type="button" @click="manageSelectedCourseChapters">
              <i class="bi bi-list-nested"></i> 管理章节
            </button>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onMounted, reactive, ref } from 'vue';
import type { Component, VNode } from 'vue';
import {
  archiveCourse,
  createChapter,
  createCourse,
  deleteChapter,
  listChapters,
  listCourses,
  updateChapter,
  updateCourse
} from '../../api/crs/courses';
import type { CourseScope } from '../../api/crs/courses';
import type { Chapter, ChapterPayload, Course, CoursePayload } from '../../types/crs';

const ChapterNode: Component = defineComponent({
  name: 'ChapterNode',
  props: {
    chapter: { type: Object as () => Chapter, required: true },
    courseId: { type: Number, required: true },
    depth: { type: Number, required: true }
  },
  emits: ['edit', 'delete', 'move'],
  setup(props, { emit }) {
    return (): VNode => h('div', { class: 'chapter-node', style: { marginLeft: `${props.depth * 22}px` } }, [
      h('div', { class: 'chapter-row' }, [
        h('div', { class: 'chapter-main' }, [
          h('span', { class: 'chapter-order' }, String(props.chapter.orderNum)),
          h('div', [
            h('strong', props.chapter.title),
            props.chapter.content ? h('p', props.chapter.content) : null
          ])
        ]),
        h('div', { class: 'chapter-actions' }, [
          h('button', { class: 'card-btn', type: 'button', title: '上移', onClick: () => emit('move', props.chapter, -1) }, '↑'),
          h('button', { class: 'card-btn', type: 'button', title: '下移', onClick: () => emit('move', props.chapter, 1) }, '↓'),
          h('button', { class: 'card-btn', type: 'button', onClick: () => emit('edit', props.chapter) }, '编辑'),
          h('button', { class: 'card-btn danger', type: 'button', onClick: () => emit('delete', props.chapter) }, '删除')
        ])
      ]),
      ...props.chapter.children.map((child) => h(ChapterNode, {
        chapter: child,
        courseId: props.courseId,
        depth: props.depth + 1,
        onEdit: (chapter: Chapter) => emit('edit', chapter),
        onDelete: (chapter: Chapter) => emit('delete', chapter),
        onMove: (chapter: Chapter, delta: number) => emit('move', chapter, delta)
      }))
    ]);
  }
});

const CompactChapterNode: Component = defineComponent({
  name: 'CompactChapterNode',
  props: {
    chapter: { type: Object as () => Chapter, required: true },
    depth: { type: Number, required: true }
  },
  setup(props) {
    return (): VNode => h('div', { class: 'compact-node', style: { marginLeft: `${props.depth * 18}px` } }, [
      h('span', `${props.chapter.orderNum}. ${props.chapter.title}`),
      ...props.chapter.children.map((child) => h(CompactChapterNode, { chapter: child, depth: props.depth + 1 }))
    ]);
  }
});

const blankForm = (): CoursePayload => ({
  name: '',
  description: '',
  semester: '',
  category: '',
  coverUrl: '',
  enrollmentMode: 'PUBLIC',
  inviteCode: '',
  maxStudents: undefined,
  startDate: '',
  endDate: '',
  status: 'DRAFT'
});

const blankChapterForm = (): ChapterPayload => ({
  parentId: null,
  title: '',
  content: '',
  orderNum: undefined
});

const form = reactive<CoursePayload>(blankForm());
const chapterForm = reactive<ChapterPayload>(blankChapterForm());
const chapterParentValue = ref('');
const courses = ref<Course[]>([]);
const chapters = ref<Chapter[]>([]);
const detailChapters = ref<Chapter[]>([]);
const keyword = ref('');
const loading = ref(false);
const submitting = ref(false);
const chapterLoading = ref(false);
const chapterSubmitting = ref(false);
const detailChapterLoading = ref(false);
const loadError = ref('');
const formError = ref('');
const successMessage = ref('');
const chapterLoadError = ref('');
const chapterError = ref('');
const chapterSuccess = ref('');
const detailChapterError = ref('');
const editingCourse = ref<Course | null>(null);
const selectedCourse = ref<Course | null>(null);
const chapterCourse = ref<Course | null>(null);
const editingChapter = ref<Chapter | null>(null);
const activeTab = ref<CourseScope>('all');
const stats = reactive<Record<CourseScope, number>>({
  all: 0,
  managed: 0,
  archived: 0
});

const pageTitle = computed(() => {
  if (chapterCourse.value) {
    return `章节目录：${chapterCourse.value.name}`;
  }
  if (activeTab.value === 'managed') {
    return '课程创建与管理';
  }
  if (activeTab.value === 'archived') {
    return '归档记录';
  }
  return '全部课程';
});

const pageSubtitle = computed(() => {
  if (chapterCourse.value) {
    return '维护课程章节树，支持一级章节、子章节、编辑、排序和删除。';
  }
  if (activeTab.value === 'managed') {
    return '创建课程、维护基础信息，并从课程卡片进入章节目录管理。';
  }
  if (activeTab.value === 'archived') {
    return '查看已经归档的课程，保留历史课程信息。';
  }
  return '师生共用课程列表，可按课程名称、学期或分类搜索。';
});

const emptyText = computed(() => {
  if (activeTab.value === 'managed') {
    return '暂无可管理课程，创建第一门课程后会显示在这里。';
  }
  if (activeTab.value === 'archived') {
    return '暂无归档课程。';
  }
  return '暂无课程。';
});

const visibleCourses = computed(() => {
  if (activeTab.value === 'archived') {
    return courses.value.filter((course) => course.status === 'ARCHIVED');
  }
  return courses.value.filter((course) => course.status !== 'ARCHIVED');
});

const flatChapters = computed(() => flattenChapters(chapters.value));
const canOpenCourseDetail = computed(() => activeTab.value === 'all' || activeTab.value === 'archived');
const gradeAnalysisCourse = computed(() => editingCourse.value ?? selectedCourse.value ?? chapterCourse.value ?? visibleCourses.value.find((course) => course.manageable) ?? visibleCourses.value[0] ?? null);
const gradeAnalysisHref = computed(() => {
  const course = gradeAnalysisCourse.value;
  return course ? `/courses/${course.id}/grd/grade-items` : '';
});

async function loadCourses() {
  loading.value = true;
  loadError.value = '';
  try {
    const page = await listCourses(keyword.value, activeTab.value);
    courses.value = page.list;
    syncActiveTabStat(page.list, page.total);
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '课程列表加载失败';
  } finally {
    loading.value = false;
  }
}

async function loadStats() {
  const [all, managed, archived] = await Promise.all([
    listCourses('', 'all'),
    listCourses('', 'managed'),
    listCourses('', 'archived')
  ]);
  stats.all = all.list.filter((course) => course.status !== 'ARCHIVED').length || all.total;
  stats.managed = managed.list.filter((course) => course.status !== 'ARCHIVED').length || managed.total;
  stats.archived = archived.list.filter((course) => course.status === 'ARCHIVED').length;
}

async function switchTab(tab: CourseScope) {
  activeTab.value = tab;
  keyword.value = '';
  closeCourseDetail();
  closeChapterManagement();
  resetForm();
  await loadCourses();
}

async function submitCourse() {
  formError.value = '';
  successMessage.value = '';
  const missingFields = requiredMissingFields();
  if (missingFields.length > 0) {
    const message = `请先填写：${missingFields.join('、')}`;
    formError.value = message;
    window.alert(message);
    return;
  }

  submitting.value = true;
  try {
    if (editingCourse.value) {
      await updateCourse(editingCourse.value.id, form);
      successMessage.value = '课程信息已保存';
    } else {
      await createCourse(form);
      successMessage.value = '课程创建成功';
    }
    resetForm();
    await Promise.all([loadCourses(), loadStats()]);
  } catch (error) {
    formError.value = error instanceof Error ? error.message : '课程提交失败';
  } finally {
    submitting.value = false;
  }
}

function editCourse(course: Course) {
  editingCourse.value = course;
  Object.assign(form, {
    name: course.name,
    description: course.description ?? '',
    semester: course.semester ?? '',
    category: course.category ?? '',
    coverUrl: course.coverUrl ?? '',
    enrollmentMode: course.enrollmentMode,
    inviteCode: course.inviteCode ?? '',
    maxStudents: course.maxStudents,
    startDate: course.startDate ?? '',
    endDate: course.endDate ?? '',
    status: course.status === 'ARCHIVED' ? 'CLOSED' : course.status
  });
}

async function archive(course: Course) {
  if (!window.confirm(`确认归档课程《${course.name}》？`)) {
    return;
  }
  await archiveCourse(course.id);
  activeTab.value = 'archived';
  keyword.value = '';
  await Promise.all([loadCourses(), loadStats()]);
}

function resetForm() {
  editingCourse.value = null;
  Object.assign(form, blankForm());
}

async function openCourseDetail(course: Course) {
  if (!canOpenCourseDetail.value) {
    return;
  }
  selectedCourse.value = course;
  detailChapters.value = [];
  detailChapterError.value = '';
  detailChapterLoading.value = true;
  try {
    detailChapters.value = await listChapters(course.id);
  } catch (error) {
    detailChapterError.value = error instanceof Error ? error.message : '章节目录加载失败';
  } finally {
    detailChapterLoading.value = false;
  }
}

function closeCourseDetail() {
  selectedCourse.value = null;
}

async function openChapterManagement(course: Course) {
  chapterCourse.value = course;
  closeCourseDetail();
  resetChapterForm();
  await loadChapters();
}

function closeChapterManagement() {
  chapterCourse.value = null;
  chapters.value = [];
  resetChapterForm();
}

async function loadChapters() {
  if (!chapterCourse.value) {
    return;
  }
  chapterLoading.value = true;
  chapterLoadError.value = '';
  try {
    chapters.value = await listChapters(chapterCourse.value.id);
  } catch (error) {
    chapterLoadError.value = error instanceof Error ? error.message : '章节目录加载失败';
  } finally {
    chapterLoading.value = false;
  }
}

async function submitChapter() {
  if (!chapterCourse.value) {
    return;
  }
  chapterError.value = '';
  chapterSuccess.value = '';
  if (!chapterForm.title?.trim()) {
    chapterError.value = '请填写章节标题';
    return;
  }
  chapterSubmitting.value = true;
  const payload = normalizeChapterPayload();
  try {
    if (editingChapter.value) {
      await updateChapter(chapterCourse.value.id, editingChapter.value.id, payload);
      chapterSuccess.value = '章节已保存';
    } else {
      await createChapter(chapterCourse.value.id, payload);
      chapterSuccess.value = '章节创建成功';
    }
    resetChapterForm();
    await loadChapters();
  } catch (error) {
    chapterError.value = error instanceof Error ? error.message : '章节提交失败';
  } finally {
    chapterSubmitting.value = false;
  }
}

function editChapter(chapter: Chapter) {
  editingChapter.value = chapter;
  Object.assign(chapterForm, {
    title: chapter.title,
    content: chapter.content ?? '',
    orderNum: chapter.orderNum,
    parentId: chapter.parentId ?? null
  });
  chapterParentValue.value = chapter.parentId == null ? '' : String(chapter.parentId);
}

async function removeChapter(chapter: Chapter) {
  if (!chapterCourse.value || !window.confirm(`确认删除章节《${chapter.title}》？子章节也会一并删除。`)) {
    return;
  }
  await deleteChapter(chapterCourse.value.id, chapter.id);
  await loadChapters();
}

async function moveChapter(chapter: Chapter, delta: number) {
  if (!chapterCourse.value) {
    return;
  }
  await updateChapter(chapterCourse.value.id, chapter.id, {
    parentId: chapter.parentId ?? null,
    title: chapter.title,
    content: chapter.content ?? '',
    orderNum: Math.max(0, chapter.orderNum + delta)
  });
  await loadChapters();
}

function resetChapterForm() {
  editingChapter.value = null;
  Object.assign(chapterForm, blankChapterForm());
  chapterParentValue.value = '';
}

function normalizeChapterPayload(): ChapterPayload {
  return {
    title: chapterForm.title.trim(),
    content: chapterForm.content?.trim() || '',
    parentId: chapterParentValue.value ? Number(chapterParentValue.value) : null,
    orderNum: Number.isFinite(chapterForm.orderNum) ? Number(chapterForm.orderNum) : undefined
  };
}

async function manageSelectedCourseChapters() {
  if (selectedCourse.value) {
    await openChapterManagement(selectedCourse.value);
  }
}

function requiredMissingFields() {
  const fields = [
    ['课程名称', form.name],
    ['学期', form.semester],
    ['课程分类', form.category],
    ['开课日期', form.startDate],
    ['结课日期', form.endDate]
  ];
  return fields.filter(([, value]) => !String(value ?? '').trim()).map(([label]) => label);
}

function syncActiveTabStat(list: Course[], total: number) {
  if (activeTab.value === 'archived') {
    stats.archived = list.filter((course) => course.status === 'ARCHIVED').length;
    return;
  }
  if (activeTab.value === 'managed') {
    stats.managed = list.filter((course) => course.status !== 'ARCHIVED').length || total;
    return;
  }
  stats.all = list.filter((course) => course.status !== 'ARCHIVED').length || total;
}

function summarizeDescription(description?: string) {
  const fallback = '暂无课程简介';
  if (!description || !description.trim()) {
    return fallback;
  }
  const trimmed = description.trim();
  return trimmed.length > 70 ? `${trimmed.slice(0, 70)}...` : trimmed;
}

function enrollmentModeText(mode: Course['enrollmentMode']) {
  const map: Record<Course['enrollmentMode'], string> = {
    PUBLIC: '公开加入',
    INVITE: '邀请码加入',
    REVIEW: '申请审核'
  };
  return map[mode];
}

function statusText(status: Course['status']) {
  const map: Record<Course['status'], string> = {
    DRAFT: '草稿',
    NOT_STARTED: '未开课',
    ACTIVE: '已发布',
    CLOSED: '已结课',
    ARCHIVED: '已归档'
  };
  return map[status];
}

function flattenChapters(items: Chapter[], depth = 0): Array<{ chapter: Chapter; prefix: string }> {
  return items.flatMap((chapter) => [
    { chapter, prefix: `${'　'.repeat(depth)}${depth > 0 ? '└ ' : ''}` },
    ...flattenChapters(chapter.children, depth + 1)
  ]);
}

onMounted(async () => {
  await Promise.all([loadCourses(), loadStats()]);
});
</script>
