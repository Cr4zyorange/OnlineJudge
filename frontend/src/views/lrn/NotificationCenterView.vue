<template>
  <main class="notification-center">
    <nav class="notification-center__topbar" aria-label="页面导航">
      <a class="notification-center__home" data-testid="lrn-home-entry" href="/" aria-label="返回主页面">
        &lt;-
      </a>
    </nav>
    <section class="notification-center__shell">
      <aside class="notification-center__sidebar" aria-label="通知概览">
        <h1>消息通知中心</h1>
        <p>按任务、成绩、公告和系统消息分类查看站内通知。</p>
        <dl class="notification-center__stats">
          <div>
            <dt>全部通知</dt>
            <dd>{{ notificationPage?.total ?? 0 }}</dd>
          </div>
          <div>
            <dt>未读通知</dt>
            <dd>未读 {{ notificationPage?.unreadCount ?? 0 }}</dd>
          </div>
        </dl>
        <a class="notification-center__settings" data-testid="reminder-settings-entry" href="/learning/reminders">
          提醒规则设置
        </a>
      </aside>

      <section class="notification-center__content" aria-label="通知列表">
        <header class="notification-center__header">
          <div>
            <h2>我的通知</h2>
          </div>
          <button type="button" class="notification-center__refresh" :disabled="loading" @click="loadNotifications">
            刷新
          </button>
        </header>

        <form class="notification-center__filters" aria-label="通知筛选">
          <label>
            <span>通知类型</span>
            <select v-model="selectedType" name="type" @change="reloadFromFirstPage">
              <option value="">全部类型</option>
              <option value="LEARNING_REMINDER">学习提醒</option>
              <option value="TASK">任务通知</option>
              <option value="GRADE">成绩通知</option>
              <option value="SYSTEM_ANNOUNCEMENT">系统公告</option>
              <option value="TEACHER_ANNOUNCEMENT">教师公告</option>
            </select>
          </label>
          <label>
            <span>阅读状态</span>
            <select v-model="selectedReadState" name="isRead" @change="reloadFromFirstPage">
              <option value="">全部状态</option>
              <option value="false">未读</option>
              <option value="true">已读</option>
            </select>
          </label>
          <label>
            <span>开始时间</span>
            <input v-model="startTime" name="startTime" type="datetime-local" @change="reloadFromFirstPage" />
          </label>
          <label>
            <span>结束时间</span>
            <input v-model="endTime" name="endTime" type="datetime-local" @change="reloadFromFirstPage" />
          </label>
        </form>

        <section class="notification-center__actions" aria-label="通知操作">
          <button
            type="button"
            data-testid="mark-selected-read"
            :disabled="loading || selectedIds.length === 0"
            @click="markSelectedRead"
          >
            标记选中已读
          </button>
          <button
            type="button"
            data-testid="mark-all-read"
            :disabled="loading || (notificationPage?.unreadCount ?? 0) === 0"
            @click="markAllRead"
          >
            全部标为已读
          </button>
          <span v-if="feedbackMessage">{{ feedbackMessage }}</span>
        </section>

        <p v-if="loading" class="notification-center__state">加载中...</p>
        <section v-else-if="errorMessage" class="notification-center__state notification-center__state--error">
          <p>{{ errorMessage }}</p>
          <button type="button" data-testid="retry-notifications" @click="loadNotifications">重试</button>
        </section>
        <p v-else-if="notifications.length === 0" class="notification-center__state">暂无符合条件的通知</p>

        <div v-else class="notification-center__list">
          <article
            v-for="notification in notifications"
            :key="notification.notificationId"
            :data-testid="`notification-card-${notification.notificationId}`"
            :class="['notification-card', { 'notification-card--unread': !notification.isRead }]"
          >
            <label class="notification-card__select" :aria-label="`选择通知 ${notification.title}`">
              <input
                v-model="selectedIds"
                type="checkbox"
                :value="notification.notificationId"
                :data-testid="`notification-select-${notification.notificationId}`"
              />
            </label>
            <div class="notification-card__main">
              <div class="notification-card__badges">
                <span>{{ typeLabel(notification.type) }}</span>
                <span v-if="!notification.isRead" class="notification-card__unread">未读</span>
                <span v-if="notification.priority >= 3" class="notification-card__priority">高优先级</span>
              </div>
              <h3>{{ notification.title }}</h3>
              <p>{{ notification.content }}</p>
            </div>
            <dl class="notification-card__meta">
              <div>
                <dt>来源</dt>
                <dd>{{ notification.sourceModule }}{{ notification.sourceId ? ` #${notification.sourceId}` : '' }}</dd>
              </div>
              <div>
                <dt>创建时间</dt>
                <dd>{{ notification.createdAt }}</dd>
              </div>
            </dl>
            <div class="notification-card__commands">
              <a class="notification-card__link" :href="notification.actionUrl ?? '#'">查看详情</a>
              <button
                type="button"
                class="notification-card__delete"
                :data-testid="`delete-notification-${notification.notificationId}`"
                :disabled="loading"
                @click="deleteNotificationById(notification.notificationId)"
              >
                删除
              </button>
            </div>
          </article>
        </div>

        <nav
          v-if="notificationPage && notificationPage.total > 0"
          class="notification-center__pagination"
          aria-label="通知列表分页"
        >
          <button type="button" data-testid="prev-notification-page" :disabled="loading || !canGoPrevious" @click="goPreviousPage">
            上一页
          </button>
          <span>第 {{ notificationPage.page }} / {{ totalPages }} 页</span>
          <button type="button" data-testid="next-notification-page" :disabled="loading || !canGoNext" @click="goNextPage">
            下一页
          </button>
        </nav>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { deleteNotification, listNotifications, markNotificationsRead } from '../../api/lrn/notifications';
import type { NotificationItem, NotificationPage, NotificationType } from '../../types/lrn';

const loading = ref(false);
const errorMessage = ref('');
const notificationPage = ref<NotificationPage | null>(null);
const selectedType = ref<'' | NotificationType>('');
const selectedReadState = ref('');
const startTime = ref('');
const endTime = ref('');
const page = ref(1);
const size = ref(20);
const selectedIds = ref<number[]>([]);
const feedbackMessage = ref('');

const notifications = computed(() => notificationPage.value?.records ?? []);
const totalPages = computed(() => {
  if (!notificationPage.value || notificationPage.value.total <= 0) {
    return 1;
  }
  return Math.max(1, Math.ceil(notificationPage.value.total / notificationPage.value.size));
});
const canGoPrevious = computed(() => page.value > 1);
const canGoNext = computed(() => page.value < totalPages.value);

onMounted(loadNotifications);

async function reloadFromFirstPage() {
  page.value = 1;
  await loadNotifications();
}

async function loadNotifications() {
  loading.value = true;
  errorMessage.value = '';
  try {
    notificationPage.value = await listNotifications({
      type: selectedType.value || undefined,
      isRead: selectedReadState.value === '' ? undefined : selectedReadState.value === 'true',
      startTime: startTime.value || undefined,
      endTime: endTime.value || undefined,
      page: page.value,
      size: size.value
    });
    const visibleIds = new Set(notifications.value.map((notification) => notification.notificationId));
    selectedIds.value = selectedIds.value.filter((notificationId) => visibleIds.has(notificationId));
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '通知加载失败';
  } finally {
    loading.value = false;
  }
}

async function goPreviousPage() {
  if (!canGoPrevious.value) {
    return;
  }
  page.value -= 1;
  await loadNotifications();
}

async function goNextPage() {
  if (!canGoNext.value) {
    return;
  }
  page.value += 1;
  await loadNotifications();
}

async function markSelectedRead() {
  if (selectedIds.value.length === 0) {
    return;
  }
  await mutateNotifications(async () => {
    const result = await markNotificationsRead({
      notificationIds: selectedIds.value,
      readAll: false
    });
    feedbackMessage.value = `已标记 ${result.updatedCount} 条通知`;
    selectedIds.value = [];
  });
}

async function markAllRead() {
  await mutateNotifications(async () => {
    const result = await markNotificationsRead({
      notificationIds: [],
      readAll: true
    });
    feedbackMessage.value = `已标记 ${result.updatedCount} 条通知`;
    selectedIds.value = [];
  });
}

async function deleteNotificationById(notificationId: number) {
  await mutateNotifications(async () => {
    const result = await deleteNotification(notificationId);
    feedbackMessage.value = result.updatedCount > 0 ? '通知已删除' : '通知未发生变化';
    selectedIds.value = selectedIds.value.filter((selectedId) => selectedId !== notificationId);
  });
}

async function mutateNotifications(action: () => Promise<void>) {
  loading.value = true;
  errorMessage.value = '';
  feedbackMessage.value = '';
  try {
    await action();
    await loadNotifications();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '通知操作失败';
  } finally {
    loading.value = false;
  }
}

function typeLabel(type: NotificationItem['type']) {
  const labels: Record<NotificationItem['type'], string> = {
    LEARNING_REMINDER: '学习提醒',
    TASK: '任务通知',
    GRADE: '成绩通知',
    SYSTEM_ANNOUNCEMENT: '系统公告',
    TEACHER_ANNOUNCEMENT: '教师公告'
  };
  return labels[type];
}
</script>

<style scoped>
.notification-center {
  background-attachment: fixed;
  background-image: url("../../assets/back1.jpg");
  background-position: top center;
  background-repeat: no-repeat;
  background-size: cover;
  min-height: 100vh;
  padding: 24px;
}

.notification-center__topbar {
  display: flex;
  margin: 0 auto 18px;
  max-width: 1280px;
}

.notification-center__home {
  align-items: center;
  background: #16423c;
  border: 1px solid #16423c;
  border-radius: 8px;
  color: #ffffff;
  display: inline-flex;
  font-weight: 800;
  min-height: 40px;
  padding: 0 14px;
  text-decoration: none;
}

.notification-center__shell {
  display: grid;
  gap: 24px;
  grid-template-columns: minmax(220px, 280px) minmax(0, 1fr);
  margin: 0 auto;
  max-width: 1280px;
}

.notification-center__sidebar,
.notification-center__content,
.notification-card,
.notification-center__stats div {
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.1);
}

.notification-center__sidebar {
  align-self: start;
  display: grid;
  gap: 18px;
  padding: 24px;
  position: sticky;
  top: 24px;
}

.notification-center__sidebar h1,
.notification-center__header h2,
.notification-card h3 {
  margin: 0;
}

.notification-center__sidebar p,
.notification-card p {
  color: #000;
  margin: 0;
}

.notification-center__eyebrow {
  color: #55746d;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0;
  margin: 0 0 4px;
}

.notification-center__stats {
  display: grid;
  gap: 12px;
  margin: 0;
}

.notification-center__stats div {
  padding: 14px;
}

.notification-center__stats dt,
.notification-card__meta dt {
  color: #000;
  font-size: 13px;
}

.notification-center__stats dd {
  font-size: 24px;
  font-weight: 700;
  margin: 4px 0 0;
}

.notification-center__content {
  display: grid;
  gap: 18px;
  padding: 24px;
}

.notification-center__header,
.notification-center__filters,
.notification-center__actions,
.notification-card,
.notification-card__badges,
.notification-card__meta {
  align-items: center;
  display: grid;
  gap: 12px;
}

.notification-center__header {
  grid-template-columns: 1fr auto;
}

.notification-center__filters {
  grid-template-columns: repeat(4, minmax(140px, 1fr));
}

.notification-center__actions {
  grid-template-columns: repeat(2, max-content) 1fr;
}

.notification-center__actions span {
  color: #16423c;
  font-weight: 700;
}

.notification-center__filters label {
  display: grid;
  gap: 6px;
}

.notification-center__filters span {
  color: #41504c;
  font-size: 13px;
  font-weight: 600;
}

select,
input,
button,
.notification-card__link,
.notification-center__settings {
  border-radius: 8px;
  min-height: 40px;
}

select,
input {
  background: #ffffff;
  border: 1px solid #becdc7;
  color: #20302c;
  padding: 0 10px;
}

button,
.notification-card__link,
.notification-center__settings {
  background: #16423c;
  border: 1px solid #16423c;
  color: #ffffff;
  cursor: pointer;
  font-weight: 700;
  padding: 0 14px;
  text-decoration: none;
}

.notification-center__settings {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  margin-top: 18px;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.notification-center__state {
  background: rgba(255, 255, 255, 0.64);
  border: 1px dashed #b8c8c2;
  border-radius: 8px;
  margin: 0;
  padding: 24px;
}

.notification-center__state--error {
  align-items: center;
  color: #9d2f22;
  display: flex;
  justify-content: space-between;
}

.notification-center__list {
  display: grid;
  gap: 14px;
}

.notification-card {
  grid-template-columns: 28px minmax(0, 1.2fr) minmax(220px, 0.8fr) 120px;
  padding: 18px;
}

.notification-card__select {
  align-self: start;
  display: flex;
  justify-content: center;
  padding-top: 4px;
}

.notification-card__select input {
  min-height: auto;
}

.notification-card--unread {
  border-color: rgba(22, 66, 60, 0.42);
  box-shadow: 0 10px 34px rgba(22, 66, 60, 0.18);
}

.notification-card__main {
  display: grid;
  gap: 8px;
  min-width: 0;
}

.notification-card__badges {
  display: flex;
  flex-wrap: wrap;
}

.notification-card__badges span {
  background: rgba(22, 66, 60, 0.12);
  border-radius: 6px;
  color: #16423c;
  font-size: 12px;
  font-weight: 700;
  padding: 4px 8px;
}

.notification-card__unread {
  background: rgba(190, 49, 49, 0.14) !important;
  color: #9d2f22 !important;
}

.notification-card__priority {
  background: rgba(170, 112, 26, 0.16) !important;
  color: #875c12 !important;
}

.notification-card__meta {
  grid-template-columns: 1fr;
  margin: 0;
}

.notification-card__meta dd {
  font-weight: 700;
  margin: 4px 0 0;
}

.notification-card__link {
  align-items: center;
  display: inline-flex;
  justify-content: center;
}

.notification-card__commands {
  display: grid;
  gap: 8px;
}

.notification-card__delete {
  background: #9d2f22;
  border-color: #9d2f22;
}

.notification-center__pagination {
  align-items: center;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  justify-content: flex-end;
}

.notification-center__pagination span {
  color: #20302c;
  font-weight: 700;
}

@media (max-width: 980px) {
  .notification-center__shell,
  .notification-card {
    grid-template-columns: 1fr;
  }

  .notification-center__sidebar {
    position: static;
  }

  .notification-center__filters {
    grid-template-columns: repeat(2, minmax(140px, 1fr));
  }
}

@media (max-width: 620px) {
  .notification-center {
    padding: 18px;
  }

  .notification-center__filters,
  .notification-center__actions,
  .notification-center__header {
    grid-template-columns: 1fr;
  }

  .notification-center__refresh {
    width: 100%;
  }
}
</style>
