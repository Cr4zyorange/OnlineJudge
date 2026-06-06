<template>
  <main class="reminder-settings">
    <nav class="reminder-settings__topbar" aria-label="页面导航">
      <a class="reminder-settings__home" data-testid="lrn-home-entry" href="/learning/tasks" aria-label="返回学习任务中心">
        &lt;-
      </a>
    </nav>
    <section class="reminder-settings__shell">
      <aside class="reminder-settings__summary" aria-label="提醒规则概览">
        <h1>提醒规则设置</h1>
        <p>管理任务截止提醒和通知偏好</p>
        <dl>
          <div>
            <dt>启用规则</dt>
            <dd>{{ enabledRuleCount }}</dd>
          </div>
          <div>
            <dt>提醒窗口</dt>
            <dd>24h / 1h</dd>
          </div>
        </dl>
        <a class="reminder-settings__back" href="/notifications">返回通知中心</a>
      </aside>

      <section class="reminder-settings__content" aria-label="提醒规则表单">
        <header class="reminder-settings__header">
          <div>
            <h2>截止提醒与通知偏好</h2>
          </div>
          <button type="button" :disabled="loading" data-testid="retry-reminder-rules" @click="loadSettings">
            刷新
          </button>
        </header>

        <p v-if="loading" class="reminder-settings__state">加载中...</p>
        <section v-else-if="errorMessage" class="reminder-settings__state reminder-settings__state--error">
          <p>{{ errorMessage }}</p>
          <button type="button" data-testid="retry-reminder-rules" @click="loadSettings">重试</button>
        </section>

        <form v-else-if="overview" class="reminder-settings__form" @submit.prevent="saveSettings">
          <section class="preference-panel" aria-label="通知偏好">
            <h3>通知偏好</h3>
            <div class="preference-grid">
              <label v-for="preference in preferences" :key="preference.key" class="toggle-row">
                <input
                  v-model="overview.settings[preference.key]"
                  type="checkbox"
                  :data-testid="`setting-${preference.key}`"
                />
                <span>
                  <strong>{{ preference.label }}</strong>
                  <small>{{ preference.detail }}</small>
                </span>
              </label>
            </div>
          </section>

          <section class="rule-panel" aria-label="提醒规则">
            <h3>提醒规则</h3>
            <article v-for="rule in overview.rules" :key="ruleKey(rule)" class="rule-card">
              <label class="toggle-row">
                <input
                  v-model="rule.enabled"
                  type="checkbox"
                  :disabled="rule.required"
                  :data-testid="`rule-${rule.sourceModule}-${rule.aheadMinutes}`"
                />
                <span>
                  <strong>{{ ruleTitle(rule) }}</strong>
                  <small>{{ aheadLabel(rule.aheadMinutes) }}</small>
                </span>
              </label>
            </article>
          </section>

          <footer class="reminder-settings__footer">
            <p v-if="feedbackMessage" class="reminder-settings__feedback">{{ feedbackMessage }}</p>
            <button type="button" :disabled="saving" data-testid="save-reminder-rules" @click="saveSettings">
              {{ saving ? '保存中...' : '保存设置' }}
            </button>
          </footer>
        </form>
      </section>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { getReminderRules, saveReminderRules } from '../../api/lrn/reminderRules';
import type { NotificationSettingItem, ReminderRuleItem, ReminderRuleOverview } from '../../types/lrn';

type PreferenceKey = keyof NotificationSettingItem;

const overview = ref<ReminderRuleOverview | null>(null);
const loading = ref(false);
const saving = ref(false);
const errorMessage = ref('');
const feedbackMessage = ref('');

const preferences: Array<{ key: PreferenceKey; label: string; detail: string }> = [
  { key: 'enableExperiment', label: '实验通知', detail: '实验发布、截止提醒' },
  { key: 'enableHomework', label: '作业通知', detail: '作业发布、截止提醒' },
  { key: 'enableGrade', label: '成绩通知', detail: '成绩发布保留站内触达' },
  { key: 'enableAnnouncement', label: '公告通知', detail: '课程公告与系统公告' },
  { key: 'enableNonCriticalReminder', label: '非必要提醒', detail: '关闭后不再接收截止前提醒' }
];

const enabledRuleCount = computed(() => overview.value?.rules.filter((rule) => rule.enabled).length ?? 0);

onMounted(loadSettings);

async function loadSettings() {
  loading.value = true;
  errorMessage.value = '';
  feedbackMessage.value = '';
  try {
    overview.value = await getReminderRules();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '提醒规则加载失败';
  } finally {
    loading.value = false;
  }
}

async function saveSettings() {
  if (!overview.value) {
    return;
  }
  saving.value = true;
  errorMessage.value = '';
  feedbackMessage.value = '';
  try {
    overview.value = await saveReminderRules({
      rules: overview.value.rules.map((rule) => ({ ...rule })),
      settings: { ...overview.value.settings }
    });
    feedbackMessage.value = '提醒规则已保存';
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '提醒规则保存失败';
  } finally {
    saving.value = false;
  }
}

function ruleKey(rule: ReminderRuleItem) {
  return `${rule.reminderType}-${rule.sourceModule}-${rule.aheadMinutes}`;
}

function ruleTitle(rule: ReminderRuleItem) {
  return rule.sourceModule === 'HWK' ? '作业截止提醒' : '实验截止提醒';
}

function aheadLabel(minutes: number) {
  if (minutes === 1440) {
    return '提前 24 小时';
  }
  if (minutes === 60) {
    return '提前 1 小时';
  }
  return `提前 ${minutes} 分钟`;
}
</script>

<style scoped>
.reminder-settings {
  min-height: 100vh;
  padding: 24px;
  color: #102033;
  background-image: url("../../assets/back.jpg");
  background-size: cover;
  background-position: top center;
  background-repeat: no-repeat;
  background-attachment: fixed;
}

.reminder-settings__topbar {
  display: flex;
  margin: 0 auto 18px;
  width: min(1180px, 100%);
}

.reminder-settings__home {
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

.reminder-settings__shell {
  display: grid;
  grid-template-columns: minmax(240px, 300px) minmax(0, 1fr);
  gap: 24px;
  width: min(1180px, 100%);
  margin: 0 auto;
}

.reminder-settings__summary,
.reminder-settings__content,
.preference-panel,
.rule-panel,
.rule-card,
.reminder-settings__summary dl div {
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.15);
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.1);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
}

.reminder-settings__summary {
  align-self: start;
  padding: 24px;
}

.reminder-settings__summary h1,
.reminder-settings__header h2,
.preference-panel h3,
.rule-panel h3 {
  margin: 0;
  color: #14324a;
}

.reminder-settings__summary p,
.toggle-row small,
.reminder-settings__state {
  color: #000;
}

.reminder-settings__eyebrow {
  margin: 0 0 8px;
  color: #1f5345;
  font-size: 0.78rem;
  font-weight: 700;
}

.reminder-settings__summary dl {
  display: grid;
  gap: 12px;
  margin: 24px 0;
}

.reminder-settings__summary dl div {
  padding: 14px;
}

.reminder-settings__summary dt {
  color: #000;
  font-size: 0.8rem;
}

.reminder-settings__summary dd {
  margin: 6px 0 0;
  font-size: 1.5rem;
  font-weight: 800;
}

.reminder-settings__back,
.reminder-settings__header button,
.reminder-settings__footer button,
.reminder-settings__state button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 38px;
  padding: 0 16px;
  border: 0;
  border-radius: 8px;
  color: #fff;
  background: #2e7d68;
  font-weight: 700;
  text-decoration: none;
  cursor: pointer;
}

.reminder-settings__content {
  padding: 24px;
}

.reminder-settings__header,
.reminder-settings__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.reminder-settings__form {
  display: grid;
  gap: 18px;
  margin-top: 20px;
}

.preference-panel,
.rule-panel {
  padding: 18px;
}

.preference-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px;
  margin-top: 14px;
}

.toggle-row {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 58px;
  padding: 12px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.54);
}

.toggle-row input {
  width: 20px;
  height: 20px;
  accent-color: #2e7d68;
  flex: 0 0 auto;
}

.toggle-row span {
  display: grid;
  gap: 4px;
}

.rule-panel {
  display: grid;
  gap: 12px;
}

.rule-panel h3 {
  margin-bottom: 2px;
}

.rule-card {
  padding: 4px;
}

.reminder-settings__state {
  margin: 22px 0 0;
  padding: 24px;
  text-align: center;
}

.reminder-settings__state--error {
  color: #8c2f39;
}

.reminder-settings__feedback {
  margin: 0;
  color: #2e7d68;
  font-weight: 700;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

@media (max-width: 820px) {
  .reminder-settings {
    padding: 18px;
  }

  .reminder-settings__shell,
  .reminder-settings__header,
  .reminder-settings__footer {
    grid-template-columns: 1fr;
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
