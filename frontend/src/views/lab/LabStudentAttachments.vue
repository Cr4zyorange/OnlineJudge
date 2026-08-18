<template>
  <section
    class="lab-attachments"
    data-testid="lab-student-attachments"
    aria-labelledby="lab-attachments-title"
    :aria-busy="loading ? 'true' : undefined"
  >
    <header class="lab-attachments__heading">
      <div>
        <p>任务资料</p>
        <h2 id="lab-attachments-title">实验附件</h2>
        <span>仅展示当前课程中对学生已发布的资料。</span>
      </div>
      <strong v-if="configuredIds.length">{{ configuredIds.length }} 项</strong>
    </header>

    <p
      v-if="configuredIds.length === 0"
      class="lab-attachments__empty"
      data-testid="lab-attachment-empty"
    >
      本实验无附件。
    </p>

    <p
      v-else-if="loading"
      class="lab-attachments__state"
      data-testid="lab-attachment-loading"
      role="status"
      aria-live="polite"
    >
      正在加载附件信息…
    </p>

    <div
      v-else-if="metadataError"
      class="lab-attachments__error"
      data-testid="lab-attachment-error"
      role="alert"
    >
      <p>{{ metadataError }}</p>
      <button
        type="button"
        data-testid="lab-attachment-retry"
        @click="loadAttachments"
      >
        重试附件加载
      </button>
    </div>

    <template v-else>
      <p
        v-if="unavailableCount > 0"
        class="lab-attachments__warning"
        data-testid="lab-attachment-partial"
        role="status"
      >
        部分附件已失效或暂不可访问（{{ unavailableCount }} 个），仅展示当前可下载项。
      </p>
      <p
        v-if="attachments.length === 0"
        class="lab-attachments__empty"
        data-testid="lab-attachment-empty"
      >
        当前暂无可下载附件。
      </p>
      <ul v-else class="lab-attachments__list" data-testid="lab-attachment-list">
        <li v-for="resource in attachments" :key="resource.id" class="lab-attachment">
          <div class="lab-attachment__copy">
            <strong data-testid="lab-attachment-name">{{ resource.name }}</strong>
            <span>原文件：{{ resource.originalFilename }}</span>
            <span>{{ resourceTypeLabel(resource.resourceType) }} · {{ formatFileSize(resource.fileSize) }}</span>
          </div>
          <button
            class="lab-attachment__download"
            type="button"
            data-testid="lab-attachment-download"
            :aria-label="`下载附件：${resource.name}`"
            :disabled="isDownloading(resource.id)"
            @click="downloadAttachment(resource)"
          >
            {{ isDownloading(resource.id) ? '下载中…' : '下载附件' }}
          </button>
          <p
            v-if="downloadErrors[resource.id]"
            class="lab-attachment__error"
            data-testid="lab-attachment-download-error"
            role="alert"
          >
            {{ downloadErrors[resource.id] }}
          </p>
        </li>
      </ul>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { downloadResource, listResources } from '../../api/crs/courses';
import type { CourseResource, ResourceType } from '../../types/crs';
import { localizedLabError } from './labDisplay';

const props = defineProps<{
  courseId: number;
  labId: number;
  attachmentIds: number[];
}>();

const attachments = ref<CourseResource[]>([]);
const unavailableCount = ref(0);
const loading = ref(false);
const metadataError = ref('');
const downloadingIds = ref<Set<number>>(new Set());
const downloadErrors = ref<Record<number, string>>({});
let loadGeneration = 0;
let downloadContextGeneration = 0;
let disposed = false;

const configuredIds = computed(() => {
  const seen = new Set<number>();
  return (Array.isArray(props.attachmentIds) ? props.attachmentIds : [])
    .filter((id) => Number.isSafeInteger(id) && id > 0)
    .filter((id) => {
      if (seen.has(id)) return false;
      seen.add(id);
      return true;
    });
});
const attachmentSignature = computed(() => configuredIds.value.join(','));

watch(
  [() => props.courseId, () => props.labId, attachmentSignature],
  () => void loadAttachments(),
  { immediate: true }
);

onBeforeUnmount(() => {
  disposed = true;
  loadGeneration += 1;
  invalidateDownloads();
});

async function loadAttachments() {
  const generation = ++loadGeneration;
  const requestedCourseId = props.courseId;
  const requestedLabId = props.labId;
  const requestedIds = [...configuredIds.value];
  const requestedSignature = requestedIds.join(',');
  invalidateDownloads();
  attachments.value = [];
  unavailableCount.value = 0;
  metadataError.value = '';

  if (requestedIds.length === 0) {
    loading.value = false;
    return;
  }

  loading.value = true;
  try {
    const listed = await listResources(requestedCourseId);
    if (!isCurrentMetadataRequest(
      generation,
      requestedCourseId,
      requestedLabId,
      requestedSignature
    )) return;
    if (!Array.isArray(listed)) {
      throw new Error('附件元数据格式异常，请重试。');
    }

    const requestedIdSet = new Set(requestedIds);
    const availableById = new Map<number, CourseResource>();
    for (const resource of listed) {
      if (!requestedIdSet.has(resource.id)
        || resource.courseId !== requestedCourseId
        || resource.visibility !== 'STUDENT'
        || !isPublished(resource.publishAt)) continue;
      if (!availableById.has(resource.id)) availableById.set(resource.id, resource);
    }
    const available = requestedIds
      .map((id) => availableById.get(id))
      .filter((resource): resource is CourseResource => Boolean(resource));
    attachments.value = available;
    unavailableCount.value = requestedIds.length - available.length;
  } catch (error) {
    if (isCurrentMetadataRequest(
      generation,
      requestedCourseId,
      requestedLabId,
      requestedSignature
    )) {
      metadataError.value = localizedLabError(error, '附件信息加载失败，请稍后重试。');
    }
  } finally {
    if (isCurrentMetadataRequest(
      generation,
      requestedCourseId,
      requestedLabId,
      requestedSignature
    )) loading.value = false;
  }
}

async function downloadAttachment(resource: CourseResource) {
  if (isDownloading(resource.id)) return;
  const contextGeneration = downloadContextGeneration;
  const requestedCourseId = props.courseId;
  const requestedLabId = props.labId;
  const requestedSignature = attachmentSignature.value;
  if (!isCurrentDownloadContext(
    contextGeneration,
    requestedCourseId,
    requestedLabId,
    requestedSignature
  ) || !attachments.value.some((item) => item.id === resource.id && item.courseId === requestedCourseId)) return;

  setDownloading(resource.id, true);
  setDownloadError(resource.id, '');
  try {
    const { blob, filename } = await downloadResource(requestedCourseId, resource.id);
    if (!isCurrentDownloadContext(
      contextGeneration,
      requestedCourseId,
      requestedLabId,
      requestedSignature
    )) return;
    const objectUrl = window.URL.createObjectURL(blob);
    try {
      const link = document.createElement('a');
      link.href = objectUrl;
      link.download = filename || resource.originalFilename || resource.name;
      document.body.appendChild(link);
      link.click();
      link.remove();
    } finally {
      window.URL.revokeObjectURL(objectUrl);
    }
  } catch (error) {
    if (isCurrentDownloadContext(
      contextGeneration,
      requestedCourseId,
      requestedLabId,
      requestedSignature
    )) {
      setDownloadError(
        resource.id,
        localizedLabError(error, '附件下载失败，请稍后重试。')
      );
    }
  } finally {
    if (isCurrentDownloadContext(
      contextGeneration,
      requestedCourseId,
      requestedLabId,
      requestedSignature
    )) setDownloading(resource.id, false);
  }
}

function invalidateDownloads() {
  downloadContextGeneration += 1;
  downloadingIds.value = new Set();
  downloadErrors.value = {};
}

function setDownloading(resourceId: number, value: boolean) {
  const next = new Set(downloadingIds.value);
  if (value) next.add(resourceId);
  else next.delete(resourceId);
  downloadingIds.value = next;
}

function isDownloading(resourceId: number) {
  return downloadingIds.value.has(resourceId);
}

function setDownloadError(resourceId: number, message: string) {
  const next = { ...downloadErrors.value };
  if (message) next[resourceId] = message;
  else delete next[resourceId];
  downloadErrors.value = next;
}

function isCurrentMetadataRequest(
  generation: number,
  courseId: number,
  labId: number,
  signature: string
) {
  return !disposed
    && generation === loadGeneration
    && courseId === props.courseId
    && labId === props.labId
    && signature === attachmentSignature.value;
}

function isCurrentDownloadContext(
  generation: number,
  courseId: number,
  labId: number,
  signature: string
) {
  return !disposed
    && generation === downloadContextGeneration
    && courseId === props.courseId
    && labId === props.labId
    && signature === attachmentSignature.value;
}

function isPublished(publishAt: string | null | undefined) {
  if (!publishAt) return true;
  const timestamp = new Date(publishAt).getTime();
  return Number.isFinite(timestamp) && timestamp <= Date.now();
}

function resourceTypeLabel(type: ResourceType) {
  const labels: Record<ResourceType, string> = {
    DOCUMENT: '文档',
    COURSEWARE: '课件',
    VIDEO: '视频',
    IMAGE: '图片',
    ARCHIVE: '压缩包',
    LINK: '链接',
    OTHER: '其他'
  };
  return labels[type] ?? '其他';
}

function formatFileSize(size: number) {
  if (!Number.isFinite(size) || size < 0) return '大小未知';
  if (size >= 1024 * 1024) return `${(size / 1024 / 1024).toFixed(1)} MB`;
  if (size >= 1024) return `${(size / 1024).toFixed(1)} KB`;
  return `${size} B`;
}
</script>

<style scoped>
.lab-attachments {
  display: grid;
  gap: 14px;
  min-width: 0;
}

.lab-attachments__heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.lab-attachments__heading div {
  min-width: 0;
}

.lab-attachments__heading p,
.lab-attachments__heading h2,
.lab-attachments__heading span,
.lab-attachments__heading strong {
  margin: 0;
}

.lab-attachments__heading p {
  color: var(--oj-brand);
  font-size: .72rem;
  font-weight: 900;
  letter-spacing: .08em;
  text-transform: uppercase;
}

.lab-attachments__heading h2 {
  margin-top: 3px;
  font-size: 1.12rem;
}

.lab-attachments__heading span {
  display: block;
  margin-top: 5px;
  color: var(--oj-muted);
  font-size: .82rem;
  line-height: 1.5;
}

.lab-attachments__heading strong {
  flex: 0 0 auto;
  padding: 5px 9px;
  border-radius: 999px;
  background: var(--oj-brand-soft);
  color: var(--oj-brand);
  font-size: .76rem;
}

.lab-attachments__state,
.lab-attachments__empty,
.lab-attachments__warning,
.lab-attachments__error,
.lab-attachment__error {
  margin: 0;
  padding: 10px 12px;
  border-radius: var(--oj-radius-control);
  font-size: .84rem;
  line-height: 1.55;
}

.lab-attachments__state,
.lab-attachments__empty {
  background: rgba(93,113,119,.08);
  color: var(--oj-ink-soft);
}

.lab-attachments__warning {
  background: rgba(194,123,0,.12);
  color: #704400;
}

.lab-attachments__error,
.lab-attachment__error {
  background: rgba(190,49,49,.11);
  color: #8f2d24;
}

.lab-attachments__error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.lab-attachments__error p {
  margin: 0;
}

.lab-attachments__error button,
.lab-attachment__download {
  box-sizing: border-box;
  min-height: 42px;
  padding: 9px 14px;
  border: 1px solid var(--oj-line-strong);
  border-radius: var(--oj-radius-control);
  background: rgba(255,255,255,.86);
  color: var(--oj-brand);
  cursor: pointer;
  font: inherit;
  font-weight: 800;
}

.lab-attachments__error button:hover,
.lab-attachments__error button:focus-visible,
.lab-attachment__download:hover,
.lab-attachment__download:focus-visible {
  border-color: var(--oj-brand);
  box-shadow: 0 0 0 3px var(--oj-brand-soft);
}

.lab-attachment__download:disabled {
  cursor: not-allowed;
  opacity: .55;
}

.lab-attachments__list {
  display: grid;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.lab-attachment {
  display: grid;
  grid-template-columns: minmax(0,1fr) auto;
  align-items: center;
  gap: 10px 14px;
  min-width: 0;
  padding: 13px;
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius-control);
  background: rgba(255,255,255,.55);
}

.lab-attachment__copy {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.lab-attachment__copy strong,
.lab-attachment__copy span {
  min-width: 0;
  overflow-wrap: anywhere;
}

.lab-attachment__copy span {
  color: var(--oj-muted);
  font-size: .78rem;
  line-height: 1.45;
}

.lab-attachment__error {
  grid-column: 1 / -1;
}

@media (max-width: 640px) {
  .lab-attachments__heading,
  .lab-attachments__error {
    align-items: stretch;
    flex-direction: column;
  }

  .lab-attachments__heading strong {
    align-self: flex-start;
  }

  .lab-attachment {
    grid-template-columns: minmax(0,1fr);
  }

  .lab-attachments__error button,
  .lab-attachment__download {
    width: 100%;
  }
}
</style>
