<template>
  <header class="platform-nav" data-testid="platform-navigation">
    <nav class="platform-nav__inner" aria-label="平台主导航">
      <RouterLink class="platform-nav__brand" to="/courses" aria-label="进入课程中心">
        <span aria-hidden="true">学</span>
        <strong>学知实训平台</strong>
      </RouterLink>
      <div class="platform-nav__links">
        <RouterLink to="/courses" data-testid="platform-nav-courses">课程</RouterLink>
        <RouterLink to="/learning/tasks" data-testid="platform-nav-learning">学习任务</RouterLink>
        <RouterLink v-if="isAdmin" to="/admin/auth" data-testid="platform-nav-admin">权限管理</RouterLink>
      </div>
      <div class="platform-nav__account">
        <RouterLink to="/notifications" data-testid="platform-nav-notifications" aria-label="消息通知">
          <span class="platform-nav__desktop-label">通知</span>
          <span class="platform-nav__mobile-label" aria-hidden="true">通知</span>
        </RouterLink>
        <RouterLink class="platform-nav__avatar" to="/profile" data-testid="platform-nav-profile" aria-label="个人中心">
          {{ avatarText }}
        </RouterLink>
        <button
          type="button"
          :aria-label="logoutPending ? '正在退出' : '退出登录'"
          :disabled="logoutPending"
          @click="handleLogout"
        >
          <span class="platform-nav__desktop-label">{{ logoutPending ? '退出中' : '退出' }}</span>
          <span class="platform-nav__mobile-label" aria-hidden="true">{{ logoutPending ? '…' : '退出' }}</span>
        </button>
      </div>
    </nav>
  </header>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { RouterLink, useRouter } from 'vue-router';
import { logout } from '../../api/auth/auth';
import { currentCourse, currentUser } from '../../app/runtimeContext';

const router = useRouter();
const logoutPending = ref(false);
const isAdmin = computed(() => (
  currentUser.value?.userType === 'ADMIN'
  || currentUser.value?.roles.includes('ADMIN') === true
));
const avatarText = computed(() => (
  currentUser.value?.displayName?.trim().charAt(0)
  || currentUser.value?.username?.trim().charAt(0)
  || '用'
));

async function handleLogout() {
  logoutPending.value = true;
  try {
    await logout();
  } finally {
    currentUser.value = null;
    currentCourse.value = null;
    logoutPending.value = false;
    await router.replace('/login');
  }
}
</script>

<style scoped>
.platform-nav {
  position: sticky;
  z-index: 30;
  top: 12px;
  width: min(1180px, calc(100% - 32px));
  margin: 12px auto 16px;
  border: 1px solid rgba(255, 255, 255, 0.54);
  border-radius: 12px;
  background: rgba(248, 251, 252, 0.88);
  box-shadow: 0 12px 34px rgba(15, 45, 41, 0.12);
  backdrop-filter: blur(12px);
}

.platform-nav__inner,
.platform-nav__links,
.platform-nav__account,
.platform-nav__brand {
  display: flex;
  align-items: center;
}

.platform-nav__inner {
  min-height: 60px;
  padding: 8px 12px;
  gap: 24px;
}

.platform-nav__brand {
  gap: 9px;
  color: var(--oj-ink, #172b35);
  text-decoration: none;
  white-space: nowrap;
}

.platform-nav__brand span {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border-radius: 10px;
  background: var(--oj-brand, #16423c);
  color: white;
}

.platform-nav__links {
  flex: 1;
  gap: 4px;
}

.platform-nav__account {
  gap: 8px;
}

.platform-nav__mobile-label {
  display: none;
}

.platform-nav a,
.platform-nav button {
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--oj-ink-soft, #5d7177);
  font: inherit;
  font-weight: 700;
  padding: 9px 11px;
  text-decoration: none;
}

.platform-nav__links a.router-link-active,
.platform-nav a:hover,
.platform-nav button:hover {
  background: rgba(22, 66, 60, 0.1);
  color: var(--oj-brand, #16423c);
}

.platform-nav__avatar {
  display: grid;
  width: 36px;
  height: 36px;
  box-sizing: border-box;
  place-items: center;
  border-radius: 50% !important;
  background: var(--oj-brand, #16423c) !important;
  color: white !important;
}

@media (max-width: 700px) {
  .platform-nav {
    top: 8px;
    width: calc(100% - 20px);
  }

  .platform-nav__inner {
    min-height: 52px;
    gap: 8px;
    padding: 6px 8px;
  }

  .platform-nav__brand strong,
  .platform-nav__desktop-label {
    display: none;
  }

  .platform-nav__mobile-label {
    display: inline;
  }

  .platform-nav__account {
    gap: 3px;
  }

  .platform-nav__account > a:first-child,
  .platform-nav__account button {
    display: grid;
    width: 34px;
    height: 34px;
    place-items: center;
    padding: 0;
  }

  .platform-nav__links {
    justify-content: center;
  }

  .platform-nav a {
    padding: 8px;
    font-size: 0.88rem;
  }
}
</style>
