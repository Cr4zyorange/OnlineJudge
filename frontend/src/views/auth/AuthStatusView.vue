<template>
  <main class="auth-status-shell">
    <section class="status-panel" :data-status-kind="kind">
      <p class="status-kicker">{{ content.kicker }}</p>
      <h1>{{ content.title }}</h1>
      <p class="status-message">{{ content.message }}</p>
      <div class="status-actions">
        <a :href="content.primaryHref" class="primary-action">{{ content.primaryAction }}</a>
        <a v-if="content.secondaryHref" :href="content.secondaryHref" class="secondary-action">
          {{ content.secondaryAction }}
        </a>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { clearAuthSession } from '../../api/auth/auth';

const props = defineProps<{
  kind: 'forbidden' | 'expired' | 'account-disabled';
}>();

const content = computed(() => {
  if (props.kind === 'account-disabled') {
    return {
      kicker: 'ACCOUNT LOCKED',
      title: '账号状态异常',
      message: '当前账号已被禁用、冻结或锁定，请联系管理员处理后重新登录。',
      primaryAction: '重新登录',
      primaryHref: '/login',
      secondaryAction: '',
      secondaryHref: ''
    };
  }
  if (props.kind === 'expired') {
    return {
      kicker: 'SESSION EXPIRED',
      title: '登录状态已失效',
      message: '当前会话已过期或认证信息无效，请重新登录后继续访问平台功能。',
      primaryAction: '重新登录',
      primaryHref: '/login',
      secondaryAction: '',
      secondaryHref: ''
    };
  }
  return {
    kicker: 'ACCESS DENIED',
    title: '无权限访问',
    message: '当前账号没有访问该页面或资源的权限。请返回课程首页，或联系管理员调整角色权限。',
    primaryAction: '返回课程首页',
    primaryHref: '/courses',
    secondaryAction: '重新登录',
    secondaryHref: '/login'
  };
});

onMounted(() => {
  if (props.kind === 'expired' || props.kind === 'account-disabled') {
    clearAuthSession();
  }
});
</script>

<style scoped>
.auth-status-shell {
  align-items: center;
  background:
    repeating-linear-gradient(90deg, rgba(255, 255, 255, 0.06) 0 1px, transparent 1px 28px),
    linear-gradient(135deg, #172033, #334155 52%, #f0f4f8);
  display: flex;
  min-height: 100vh;
  padding: 40px 24px;
}

.status-panel {
  backdrop-filter: blur(18px);
  background: rgba(248, 250, 252, 0.9);
  border: 1px solid rgba(255, 255, 255, 0.62);
  border-radius: 8px;
  box-shadow: 0 24px 60px rgba(15, 23, 42, 0.24);
  color: #1f2937;
  max-width: 560px;
  padding: 34px;
  width: 100%;
}

.status-kicker {
  color: #7c2d12;
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0;
  margin: 0 0 12px;
}

.status-panel[data-status-kind='expired'] .status-kicker {
  color: #0f766e;
}

h1 {
  font-size: 2rem;
  line-height: 1.2;
  margin: 0;
}

.status-message {
  color: #4b5563;
  line-height: 1.75;
  margin: 16px 0 0;
}

.status-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 28px;
}

.primary-action,
.secondary-action {
  border-radius: 6px;
  font-weight: 700;
  padding: 10px 16px;
  text-decoration: none;
}

.primary-action {
  background: #1f2937;
  color: #ffffff;
}

.secondary-action {
  border: 1px solid rgba(31, 41, 55, 0.22);
  color: #1f2937;
}

@media (max-width: 640px) {
  .auth-status-shell {
    padding: 24px 16px;
  }

  .status-panel {
    padding: 26px;
  }

  h1 {
    font-size: 1.6rem;
  }
}
</style>
