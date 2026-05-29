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
            <button class="menu-button" :class="{ active: activeTab === 'all' && !chapterCourse && !resourceCourse }" type="button" @click="switchTab('all')">
              <i class="bi bi-grid"></i> 全部课程
            </button>
          </li>
          <li>
            <button class="menu-button" :class="{ active: activeTab === 'managed' && !chapterCourse && !resourceCourse }" type="button" @click="switchTab('managed')">
              <i class="bi bi-person-check"></i> 我管理的
            </button>
          </li>
          <li>
            <button class="menu-button" :class="{ active: activeTab === 'archived' && !chapterCourse && !resourceCourse }" type="button" @click="switchTab('archived')">
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
          <div v-if="!chapterCourse && !resourceCourse" class="header-actions">
            <label class="search-box">
              <i class="bi bi-search"></i>
              <input v-model="keyword" type="search" placeholder="搜索课程、学期或分类" @keyup.enter="loadCourses" />
            </label>
            <button class="btn btn-secondary icon-btn" type="button" title="刷新" @click="loadCourses">
              <i class="bi bi-arrow-clockwise"></i>
            </button>
          </div>
          <div v-else class="header-actions">
            <button class="btn btn-secondary" type="button" @click="closeManagementWorkspace">
              <i class="bi bi-arrow-left"></i> 返回课程
            </button>
            <button class="btn btn-secondary icon-btn" type="button" title="刷新" @click="refreshManagementWorkspace">
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
              <input data-testid="chapter-title" v-model.trim="chapterForm.chapterName" type="text" maxlength="255" placeholder="例如：课程导论" />
            </label>
            <label>
              <span>父章节</span>
              <select data-testid="chapter-parent" v-model="chapterParentValue">
                <option value="">作为一级章节</option>
                <option v-for="item in flatChapters" :key="item.chapter.id" :value="String(item.chapter.id)" :disabled="editingChapter?.id === item.chapter.id">
                  {{ item.prefix }}{{ item.chapter.chapterName }}
                </option>
              </select>
            </label>
            <div class="form-grid">
              <label>
                <span>排序号</span>
                <input v-model.number="chapterForm.sortOrder" type="number" min="1" step="1" placeholder="默认追加到末尾" />
              </label>
              <label>
                <span>可见状态</span>
                <select v-model.number="chapterForm.visibleStatus">
                  <option :value="1">显示</option>
                  <option :value="0">隐藏</option>
                </select>
              </label>
              <label>
                <span>章节类型</span>
                <select v-model.number="chapterForm.chapterType">
                  <option :value="1">普通章节</option>
                  <option :value="2">实验章节</option>
                  <option :value="3">作业章节</option>
                </select>
              </label>
            </div>
            <label>
              <span>教学目标</span>
              <textarea data-testid="chapter-content" v-model.trim="chapterForm.objective" rows="5" placeholder="填写教学目标、知识点或学习建议"></textarea>
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
                @drag-sort="dragSortChapter"
              />
            </div>
          </section>
        </section>

        <section v-else-if="resourceCourse" class="workspace">
          <form class="course-form resource-form" @submit.prevent="submitResource">
            <div class="form-title">
              <h3>{{ editingResourceId ? '更新资源' : '上传资源' }}</h3>
              <button v-if="editingResourceId" class="text-button" type="button" @click="resetResourceForm">取消更新</button>
            </div>
            <label>
              <span>资源名称</span>
              <input v-model.trim="resourceForm.name" type="text" maxlength="255" placeholder="例如：第1章课件" />
            </label>
            <label>
              <span>所属章节</span>
              <select v-model="resourceChapterValue">
                <option value="">不绑定章节</option>
                <option v-for="item in flatDetailChapters" :key="item.chapter.id" :value="String(item.chapter.id)">
                  {{ item.prefix }}{{ item.chapter.chapterName }}
                </option>
              </select>
            </label>
            <div class="form-grid">
              <label>
                <span>资源类型</span>
                <select v-model="resourceForm.resourceType">
                  <option value="DOCUMENT">文档</option>
                  <option value="COURSEWARE">课件</option>
                  <option value="VIDEO">视频</option>
                  <option value="IMAGE">图片</option>
                  <option value="ARCHIVE">压缩包</option>
                  <option value="OTHER">其他</option>
                </select>
              </label>
              <label>
                <span>可见范围</span>
                <select v-model="resourceForm.visibility">
                  <option value="STUDENT">学生可见</option>
                  <option value="TEACHER">仅教师</option>
                </select>
              </label>
            </div>
            <label>
              <span>发布时间</span>
              <input v-model="resourceForm.publishAt" type="datetime-local" />
            </label>
            <label v-if="!editingResourceId">
              <span>文件</span>
              <input type="file" @change="selectResourceFile" />
            </label>
            <p v-if="resourceError" class="message error">{{ resourceError }}</p>
            <p v-if="resourceSuccess" class="message success">{{ resourceSuccess }}</p>
            <button class="btn submit-btn" type="submit" :disabled="resourceSubmitting">
              <i class="bi bi-cloud-arrow-up"></i>
              {{ resourceSubmitting ? '提交中' : editingResourceId ? '保存资源' : '上传资源' }}
            </button>
          </form>

          <section class="course-panel">
            <div class="resource-toolbar">
              <label class="resource-filter">
                <span>按章节查看资源</span>
                <select v-model="selectedDetailChapterValue">
                  <option value="">全部章节</option>
                  <option v-for="item in flatDetailChapters" :key="item.chapter.id" :value="String(item.chapter.id)">
                    {{ item.prefix }}{{ item.chapter.chapterName }}
                  </option>
                </select>
              </label>
            </div>
            <div v-if="detailChapterLoading || detailResourceLoading" class="state-card">资源加载中...</div>
            <div v-else-if="detailChapterError || detailResourceError" class="state-card error">{{ detailChapterError || detailResourceError }}</div>
            <div v-else class="resource-management-grid">
              <div>
                <h4>章节树</h4>
                <div v-if="detailChapters.length === 0" class="state-card">暂无章节目录</div>
                <div v-else class="compact-tree">
                  <CompactChapterNode v-for="chapter in detailChapters" :key="chapter.id" :chapter="chapter" :depth="0" />
                </div>
              </div>
              <div>
                <h4>章节资源</h4>
                <div v-if="filteredDetailResources.length === 0" class="state-card">暂无资源</div>
                <div v-else class="resource-list">
                  <article v-for="resource in filteredDetailResources" :key="resource.id" class="resource-row">
                    <div>
                      <strong>{{ resource.name }}</strong>
                      <p>{{ resourceTypeText(resource.resourceType) }} · {{ formatFileSize(resource.fileSize) }} · {{ chapterName(resource.chapterId) }}</p>
                    </div>
                    <button class="card-btn" type="button" @click="downloadCourseResource(resource)">
                      <i class="bi bi-download"></i> 下载
                    </button>
                    <button class="card-btn" type="button" @click="editResource(resource)">编辑</button>
                    <button class="card-btn danger" type="button" @click="removeResource(resource)">删除</button>
                  </article>
                </div>
              </div>
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
                    <button class="card-btn" type="button" :disabled="!course.manageable" @click.stop="openResourceManagement(course)">
                      <i class="bi bi-folder2-open"></i> 资源
                    </button>
                    <button class="card-btn danger" type="button" :disabled="!course.manageable" @click.stop="archive(course)">
                      <i class="bi bi-archive"></i> 归档
                    </button>
                  </div>
                  <div v-else class="card-actions">
                    <button v-if="course.manageable" class="card-btn" type="button" @click.stop="openCourseDetail(course)">
                      <i class="bi bi-kanban"></i> 管理课程
                    </button>
                    <button v-else-if="course.member" class="card-btn" type="button" @click.stop="enterCourse(course)">
                      <i class="bi bi-box-arrow-in-right"></i> 进入学习
                    </button>
                    <button v-else class="card-btn" type="button" :disabled="joiningCourseId === course.id" @click.stop="joinVisibleCourse(course)">
                      <i class="bi bi-person-plus"></i> 加入课程
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

        <div class="detail-block">
          <span>教学资源</span>
          <label class="resource-filter">
            <span>章节</span>
            <select v-model="selectedDetailChapterValue">
              <option value="">全部章节</option>
              <option v-for="item in flatDetailChapters" :key="item.chapter.id" :value="String(item.chapter.id)">
                {{ item.prefix }}{{ item.chapter.chapterName }}
              </option>
            </select>
          </label>
          <p v-if="detailResourceLoading">资源加载中...</p>
          <p v-else-if="detailResourceError">{{ detailResourceError }}</p>
          <p v-else-if="filteredDetailResources.length === 0">暂无可下载资源</p>
          <div v-else class="resource-list">
            <article v-for="resource in filteredDetailResources" :key="resource.id" class="resource-row">
              <div>
                <strong>{{ resource.name }}</strong>
                <p>{{ resourceTypeText(resource.resourceType) }} · {{ formatFileSize(resource.fileSize) }} · {{ chapterName(resource.chapterId) }}</p>
              </div>
              <button class="card-btn" type="button" @click="downloadCourseResource(resource)">
                <i class="bi bi-download"></i> 下载
              </button>
            </article>
          </div>
        </div>

        <div class="modal-actions-placeholder">
          <span>预留操作区</span>
          <div class="placeholder-actions">
            <button v-if="!selectedCourse.manageable && selectedCourse.member" class="card-btn" type="button" @click="enterCourse(selectedCourse)">
              <i class="bi bi-box-arrow-in-right"></i> 进入学习
            </button>
            <button v-else-if="!selectedCourse.manageable" class="card-btn" type="button" :disabled="joiningCourseId === selectedCourse.id" @click="joinVisibleCourse(selectedCourse)">
              <i class="bi bi-person-plus"></i> 加入课程
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
  deleteResource,
  deleteChapter,
  downloadResource,
  getCourse,
  joinCourse,
  listChapters,
  listCourses,
  listResources,
  updateChapter,
  updateCourse,
  updateResource,
  uploadResource
} from '../../api/crs/courses';
import type { CourseScope } from '../../api/crs/courses';
import type { Chapter, ChapterPayload, Course, CoursePayload, CourseResource, ResourcePayload } from '../../types/crs';

const ChapterNode: Component = defineComponent({
  name: 'ChapterNode',
  props: {
    chapter: { type: Object as () => Chapter, required: true },
    courseId: { type: Number, required: true },
    depth: { type: Number, required: true }
  },
  emits: ['edit', 'delete', 'move', 'drag-sort'],
  setup(props, { emit }) {
    return (): VNode => h('div', { class: 'chapter-node', style: { marginLeft: `${props.depth * 22}px` } }, [
      h('div', {
        class: ['chapter-row', { dragging: draggedChapterId.value === props.chapter.id }],
        draggable: true,
        onDragstart: (event: DragEvent) => {
          draggedChapterId.value = props.chapter.id;
          event.dataTransfer?.setData('text/plain', String(props.chapter.id));
          event.dataTransfer?.setData('application/x-parent-id', props.chapter.parentId == null ? '' : String(props.chapter.parentId));
          if (event.dataTransfer) {
            event.dataTransfer.effectAllowed = 'move';
          }
        },
        onDragend: () => {
          draggedChapterId.value = null;
        },
        onDragover: (event: DragEvent) => {
          if (canDropOnChapter(props.chapter)) {
            event.preventDefault();
            if (event.dataTransfer) {
              event.dataTransfer.dropEffect = 'move';
            }
          }
        },
        onDrop: (event: DragEvent) => {
          event.preventDefault();
          emit('drag-sort', props.chapter);
        }
      }, [
        h('div', { class: 'chapter-main' }, [
          h('span', { class: 'chapter-order' }, String(props.chapter.sortOrder)),
          h('div', [
            h('strong', props.chapter.chapterName),
            h('div', { class: 'chapter-badges' }, [
              h('span', props.chapter.visibleStatus === 1 ? '显示' : '隐藏'),
              h('span', chapterTypeText(props.chapter.chapterType))
            ]),
            props.chapter.objective ? h('p', props.chapter.objective) : null
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
        onMove: (chapter: Chapter, delta: number) => emit('move', chapter, delta),
        onDragSort: (chapter: Chapter) => emit('drag-sort', chapter)
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
      h('span', `${props.chapter.sortOrder}. ${props.chapter.chapterName}`),
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
  chapterName: '',
  objective: '',
  sortOrder: undefined,
  visibleStatus: 1,
  chapterType: 1
});

const blankResourceForm = (): ResourcePayload => ({
  chapterId: null,
  name: '',
  resourceType: 'DOCUMENT',
  visibility: 'STUDENT',
  publishAt: null
});

const form = reactive<CoursePayload>(blankForm());
const chapterForm = reactive<ChapterPayload>(blankChapterForm());
const resourceForm = reactive<ResourcePayload>(blankResourceForm());
const chapterParentValue = ref('');
const resourceChapterValue = ref('');
const courses = ref<Course[]>([]);
const chapters = ref<Chapter[]>([]);
const detailChapters = ref<Chapter[]>([]);
const detailResources = ref<CourseResource[]>([]);
const keyword = ref('');
const selectedDetailChapterValue = ref('');
const loading = ref(false);
const submitting = ref(false);
const chapterLoading = ref(false);
const chapterSubmitting = ref(false);
const detailChapterLoading = ref(false);
const detailResourceLoading = ref(false);
const resourceSubmitting = ref(false);
const loadError = ref('');
const formError = ref('');
const successMessage = ref('');
const chapterLoadError = ref('');
const chapterError = ref('');
const chapterSuccess = ref('');
const detailChapterError = ref('');
const detailResourceError = ref('');
const resourceError = ref('');
const resourceSuccess = ref('');
const editingCourse = ref<Course | null>(null);
const selectedCourse = ref<Course | null>(null);
const chapterCourse = ref<Course | null>(null);
const resourceCourse = ref<Course | null>(null);
const editingChapter = ref<Chapter | null>(null);
const editingResourceId = ref<number | null>(null);
const selectedResourceFile = ref<File | null>(null);
const joiningCourseId = ref<number | null>(null);
const draggedChapterId = ref<number | null>(null);
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
  if (resourceCourse.value) {
    return `资源管理：${resourceCourse.value.name}`;
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
  if (resourceCourse.value) {
    return '按章节组织教学资源，上传、更新、删除资源，并查看章节对应资源。';
  }
  if (activeTab.value === 'managed') {
    return '创建课程、维护基础信息，并从课程卡片进入章节目录或资源管理。';
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
const flatDetailChapters = computed(() => flattenChapters(detailChapters.value));
const filteredDetailResources = computed(() => {
  if (!selectedDetailChapterValue.value) {
    return detailResources.value;
  }
  const chapterId = Number(selectedDetailChapterValue.value);
  return detailResources.value.filter((resource) => resource.chapterId === chapterId);
});
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
  closeManagementWorkspace();
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
  selectedDetailChapterValue.value = '';
  detailChapters.value = [];
  detailResources.value = [];
  detailChapterError.value = '';
  detailResourceError.value = '';
  detailChapterLoading.value = true;
  detailResourceLoading.value = true;
  try {
    detailChapters.value = await listChapters(course.id);
  } catch (error) {
    detailChapterError.value = error instanceof Error ? error.message : '章节目录加载失败';
  } finally {
    detailChapterLoading.value = false;
  }
  try {
    detailResources.value = await listResources(course.id);
  } catch (error) {
    detailResourceError.value = error instanceof Error ? error.message : '资源列表加载失败';
  } finally {
    detailResourceLoading.value = false;
  }
}

function closeCourseDetail() {
  selectedCourse.value = null;
  resetResourceForm();
}

async function openChapterManagement(course: Course) {
  chapterCourse.value = course;
  resourceCourse.value = null;
  closeCourseDetail();
  resetChapterForm();
  await loadChapters();
}

async function openResourceManagement(course: Course) {
  resourceCourse.value = course;
  chapterCourse.value = null;
  closeCourseDetail();
  resetResourceForm();
  selectedDetailChapterValue.value = '';
  await loadResourceWorkspace();
}

async function enterCourse(course: Course) {
  window.history.pushState({}, '', `/courses/${course.id}`);
  await openCourseDetail(course);
}

async function joinVisibleCourse(course: Course) {
  joiningCourseId.value = course.id;
  try {
    await joinCourse(course.id);
    const joinedCourse = await getCourse(course.id);
    const index = courses.value.findIndex((item) => item.id === course.id);
    if (index >= 0) {
      courses.value[index] = joinedCourse;
    }
    await openCourseDetail(joinedCourse);
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '加入课程失败';
  } finally {
    joiningCourseId.value = null;
  }
}

function closeChapterManagement() {
  chapterCourse.value = null;
  chapters.value = [];
  resetChapterForm();
}

function closeResourceManagement() {
  resourceCourse.value = null;
  detailChapters.value = [];
  detailResources.value = [];
  selectedDetailChapterValue.value = '';
  resetResourceForm();
}

function closeManagementWorkspace() {
  closeChapterManagement();
  closeResourceManagement();
}

async function refreshManagementWorkspace() {
  if (chapterCourse.value) {
    await loadChapters();
  }
  if (resourceCourse.value) {
    await loadResourceWorkspace();
  }
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
  if (!chapterForm.chapterName?.trim()) {
    chapterError.value = '请填写章节标题';
    return;
  }
  chapterSubmitting.value = true;
  const payload = normalizeChapterPayload();
  try {
    if (editingChapter.value) {
      await updateChapter(editingChapter.value.id, payload);
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
    chapterName: chapter.chapterName,
    objective: chapter.objective ?? '',
    sortOrder: chapter.sortOrder,
    visibleStatus: chapter.visibleStatus,
    chapterType: chapter.chapterType,
    parentId: chapter.parentId ?? null
  });
  chapterParentValue.value = chapter.parentId == null ? '' : String(chapter.parentId);
}

async function removeChapter(chapter: Chapter) {
  if (!chapterCourse.value || !window.confirm(`确认删除章节《${chapter.chapterName}》？子章节也会一并删除。`)) {
    return;
  }
  await deleteChapter(chapter.id);
  await loadChapters();
}

async function moveChapter(chapter: Chapter, delta: number) {
  if (!chapterCourse.value) {
    return;
  }
  await updateChapter(chapter.id, {
    parentId: chapter.parentId ?? null,
    chapterName: chapter.chapterName,
    objective: chapter.objective ?? '',
    visibleStatus: chapter.visibleStatus,
    chapterType: chapter.chapterType,
    sortOrder: Math.max(1, chapter.sortOrder + delta)
  });
  await loadChapters();
}

async function dragSortChapter(target: Chapter) {
  if (!chapterCourse.value || draggedChapterId.value == null || draggedChapterId.value === target.id) {
    return;
  }
  const dragged = findChapterById(chapters.value, draggedChapterId.value);
  draggedChapterId.value = null;
  if (!dragged) {
    return;
  }
  if ((dragged.parentId ?? null) !== (target.parentId ?? null)) {
    chapterError.value = '只能在同一层级内拖拽排序';
    return;
  }
  chapterError.value = '';
  await updateChapter(dragged.id, {
    parentId: dragged.parentId ?? null,
    chapterName: dragged.chapterName,
    objective: dragged.objective ?? '',
    visibleStatus: dragged.visibleStatus,
    chapterType: dragged.chapterType,
    sortOrder: target.sortOrder
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
    chapterName: chapterForm.chapterName.trim(),
    objective: chapterForm.objective?.trim() || '',
    parentId: chapterParentValue.value ? Number(chapterParentValue.value) : null,
    sortOrder: Number.isFinite(chapterForm.sortOrder) ? Number(chapterForm.sortOrder) : undefined,
    visibleStatus: chapterForm.visibleStatus ?? 1,
    chapterType: chapterForm.chapterType ?? 1
  };
}

function selectResourceFile(event: Event) {
  const input = event.target as HTMLInputElement;
  selectedResourceFile.value = input.files?.[0] ?? null;
  if (selectedResourceFile.value && !resourceForm.name) {
    resourceForm.name = selectedResourceFile.value.name;
  }
}

async function loadResourceWorkspace() {
  if (!resourceCourse.value) {
    return;
  }
  detailChapterLoading.value = true;
  detailResourceLoading.value = true;
  detailChapterError.value = '';
  detailResourceError.value = '';
  try {
    detailChapters.value = await listChapters(resourceCourse.value.id);
  } catch (error) {
    detailChapterError.value = error instanceof Error ? error.message : '章节目录加载失败';
  } finally {
    detailChapterLoading.value = false;
  }
  try {
    detailResources.value = await listResources(resourceCourse.value.id);
  } catch (error) {
    detailResourceError.value = error instanceof Error ? error.message : '资源列表加载失败';
  } finally {
    detailResourceLoading.value = false;
  }
}

async function submitResource() {
  if (!resourceCourse.value) {
    return;
  }
  resourceError.value = '';
  resourceSuccess.value = '';
  if (!resourceForm.name.trim()) {
    resourceError.value = '请填写资源名称';
    return;
  }
  resourceSubmitting.value = true;
  const payload = normalizeResourcePayload();
  try {
    if (editingResourceId.value) {
      await updateResource(resourceCourse.value.id, editingResourceId.value, payload);
      resourceSuccess.value = '资源已更新';
    } else {
      if (!selectedResourceFile.value) {
        resourceError.value = '请选择要上传的文件';
        return;
      }
      await uploadResource(resourceCourse.value.id, payload, selectedResourceFile.value);
      resourceSuccess.value = '资源上传成功';
    }
    resetResourceForm();
    detailResources.value = await listResources(resourceCourse.value.id);
  } catch (error) {
    resourceError.value = error instanceof Error ? error.message : '资源提交失败';
  } finally {
    resourceSubmitting.value = false;
  }
}

function editResource(resource: CourseResource) {
  editingResourceId.value = resource.id;
  Object.assign(resourceForm, {
    chapterId: resource.chapterId ?? null,
    name: resource.name,
    resourceType: resource.resourceType,
    visibility: resource.visibility,
    publishAt: resource.publishAt ?? null
  });
  resourceChapterValue.value = resource.chapterId == null ? '' : String(resource.chapterId);
}

async function removeResource(resource: CourseResource) {
  if (!resourceCourse.value || !window.confirm(`确认删除资源《${resource.name}》？`)) {
    return;
  }
  await deleteResource(resourceCourse.value.id, resource.id);
  detailResources.value = await listResources(resourceCourse.value.id);
}

async function downloadCourseResource(resource: CourseResource) {
  resourceError.value = '';
  detailResourceError.value = '';
  try {
    const { blob, filename } = await downloadResource(resource.courseId, resource.id);
    const objectUrl = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = objectUrl;
    link.download = filename || resource.originalFilename || resource.name;
    document.body.appendChild(link);
    link.click();
    link.remove();
    window.URL.revokeObjectURL(objectUrl);
  } catch (error) {
    const message = error instanceof Error ? error.message : '资源下载失败';
    if (resourceCourse.value) {
      resourceError.value = message;
    } else {
      detailResourceError.value = message;
    }
  }
}

function resetResourceForm() {
  editingResourceId.value = null;
  selectedResourceFile.value = null;
  Object.assign(resourceForm, blankResourceForm());
  resourceChapterValue.value = '';
}

function normalizeResourcePayload(): ResourcePayload {
  return {
    ...resourceForm,
    chapterId: resourceChapterValue.value ? Number(resourceChapterValue.value) : null,
    name: resourceForm.name.trim()
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

function chapterTypeText(type: Chapter['chapterType']) {
  const map: Record<Chapter['chapterType'], string> = {
    1: '普通',
    2: '实验',
    3: '作业'
  };
  return map[type];
}

function resourceTypeText(type: CourseResource['resourceType']) {
  const map: Record<CourseResource['resourceType'], string> = {
    DOCUMENT: '文档',
    COURSEWARE: '课件',
    VIDEO: '视频',
    IMAGE: '图片',
    ARCHIVE: '压缩包',
    LINK: '链接',
    OTHER: '其他'
  };
  return map[type];
}

function formatFileSize(size: number) {
  if (size >= 1024 * 1024) {
    return `${(size / 1024 / 1024).toFixed(1)} MB`;
  }
  if (size >= 1024) {
    return `${(size / 1024).toFixed(1)} KB`;
  }
  return `${size} B`;
}

function chapterName(chapterId?: number | null) {
  if (chapterId == null) {
    return '未绑定章节';
  }
  return findChapterById(detailChapters.value, chapterId)?.chapterName ?? '未知章节';
}

function canDropOnChapter(target: Chapter) {
  if (draggedChapterId.value == null || draggedChapterId.value === target.id) {
    return false;
  }
  const dragged = findChapterById(chapters.value, draggedChapterId.value);
  return !!dragged && (dragged.parentId ?? null) === (target.parentId ?? null);
}

function findChapterById(items: Chapter[], id: number): Chapter | null {
  for (const chapter of items) {
    if (chapter.id === id) {
      return chapter;
    }
    const child = findChapterById(chapter.children, id);
    if (child) {
      return child;
    }
  }
  return null;
}

function flattenChapters(items: Chapter[], depth = 0): Array<{ chapter: Chapter; prefix: string }> {
  return items.flatMap((chapter) => [
    { chapter, prefix: `${'　'.repeat(depth)}${depth > 0 ? '└ ' : ''}` },
    ...flattenChapters(chapter.children, depth + 1)
  ]);
}

onMounted(async () => {
  await Promise.all([loadCourses(), loadStats()]);
  const courseId = Number(window.location.pathname.match(/\/courses\/(\d+)(?:\/|$)/)?.[1]);
  if (Number.isFinite(courseId) && courseId > 0) {
    try {
      await openCourseDetail(await getCourse(courseId));
    } catch (error) {
      loadError.value = error instanceof Error ? error.message : '课程详情加载失败';
    }
  }
});
</script>
