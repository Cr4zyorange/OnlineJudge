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
          <a
            :class="{ disabled: !gradeAnalysisHref }"
            :href="gradeAnalysisHref || undefined"
            :aria-disabled="!gradeAnalysisHref"
          >
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
            <button class="menu-button" :class="{ active: activeTab === 'all' }" type="button" @click="switchTab('all')">
              <i class="bi bi-grid"></i> 全部课程
            </button>
          </li>
          <li>
            <button class="menu-button" :class="{ active: activeTab === 'managed' }" type="button" @click="switchTab('managed')">
              <i class="bi bi-person-check"></i> 我管理的
            </button>
          </li>
          <li>
            <button class="menu-button" :class="{ active: activeTab === 'archived' }" type="button" @click="switchTab('archived')">
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
          <div class="header-actions">
            <label class="search-box">
              <i class="bi bi-search"></i>
              <input v-model="keyword" type="search" placeholder="搜索课程、学期或分类" @keyup.enter="loadCourses" />
            </label>
            <button class="btn btn-secondary icon-btn" type="button" title="刷新" @click="loadCourses">
              <i class="bi bi-arrow-clockwise"></i>
            </button>
          </div>
        </div>

        <section class="workspace" :class="{ single: activeTab !== 'managed' }">
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
          <div class="detail-item">
            <span>加入方式</span>
            <strong>{{ enrollmentModeText(selectedCourse.enrollmentMode) }}</strong>
          </div>
          <div class="detail-item">
            <span>课程状态</span>
            <strong>{{ statusText(selectedCourse.status) }}</strong>
          </div>
        </div>

        <div class="detail-block">
          <span>完整简介</span>
          <p>{{ selectedCourse.description || '暂无课程简介' }}</p>
        </div>

        <div class="modal-actions-placeholder">
          <span>预留操作区</span>
          <div class="placeholder-actions">
            <button class="card-btn" type="button" disabled>
              <i class="bi bi-box-arrow-in-right"></i> 加入课程
            </button>
            <button class="card-btn" type="button" disabled>
              <i class="bi bi-people"></i> 管理人员
            </button>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { archiveCourse, createCourse, listCourses, updateCourse } from '../../api/crs/courses';
import type { CourseScope } from '../../api/crs/courses';
import type { Course, CoursePayload } from '../../types/crs';

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

const form = reactive<CoursePayload>(blankForm());
const courses = ref<Course[]>([]);
const keyword = ref('');
const loading = ref(false);
const submitting = ref(false);
const loadError = ref('');
const formError = ref('');
const successMessage = ref('');
const editingCourse = ref<Course | null>(null);
const selectedCourse = ref<Course | null>(null);
const activeTab = ref<CourseScope>('all');
const stats = reactive<Record<CourseScope, number>>({
  all: 0,
  managed: 0,
  archived: 0
});

const pageTitle = computed(() => {
  if (activeTab.value === 'managed') {
    return '课程创建与管理';
  }
  if (activeTab.value === 'archived') {
    return '归档记录';
  }
  return '全部课程';
});

const pageSubtitle = computed(() => {
  if (activeTab.value === 'managed') {
    return '创建课程、维护基础信息，并自动绑定创建教师。';
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

const canOpenCourseDetail = computed(() => activeTab.value === 'all' || activeTab.value === 'archived');
const gradeAnalysisCourse = computed(() => {
  if (editingCourse.value?.id) {
    return editingCourse.value;
  }
  if (selectedCourse.value?.id) {
    return selectedCourse.value;
  }
  return visibleCourses.value.find((course) => course.manageable) ?? visibleCourses.value[0] ?? null;
});
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

function openCourseDetail(course: Course) {
  if (!canOpenCourseDetail.value) {
    return;
  }
  selectedCourse.value = course;
}

function closeCourseDetail() {
  selectedCourse.value = null;
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

onMounted(async () => {
  await Promise.all([loadCourses(), loadStats()]);
});
</script>
