<template>
  <header class="navbar-container" data-testid="platform-navigation">
    <nav class="navbar" aria-label="平台主导航">
      <a class="navbar-logo" href="/courses" aria-label="进入课程中心">
        <h2><i class="bi bi-book-half"></i> 学知实训平台</h2>
      </a>
      <div class="navbar-menu">
        <a
          data-testid="platform-nav-courses"
          href="/courses"
          :class="{ active: activeSection === 'courses' }"
        >
          课程中心
        </a>
        <a
          data-testid="platform-nav-learning"
          href="/learning/tasks"
          :class="{ active: activeSection === 'learning' }"
        >
          学习任务
        </a>
      </div>
      <div class="navbar-user">
        <a
          href="/notifications"
          data-testid="platform-nav-notifications"
          title="消息通知中心"
          aria-label="消息通知中心"
          :class="{ active: activeSection === 'notifications' }"
        >
          <i class="bi bi-bell"></i>
        </a>
        <a class="avatar" href="/profile" data-testid="platform-nav-profile" aria-label="个人中心">
          {{ avatarText }}
        </a>
      </div>
    </nav>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue';

const props = defineProps<{
  currentPath: string;
}>();

const avatarText = computed(() => {
  const username = window.localStorage.getItem('onlinejudge.username') ?? '';
  return username.trim().charAt(0).toUpperCase() || 'T';
});

const activeSection = computed(() => {
  if (props.currentPath === '/notifications') {
    return 'notifications';
  }
  if (props.currentPath.startsWith('/learning')) {
    return 'learning';
  }
  return 'courses';
});
</script>

<style scoped>
.navbar-container {
  left: 0;
  position: fixed;
  right: 0;
  top: 0;
  padding: 0 16px;
}

.navbar-logo {
  color: inherit;
  text-decoration: none;
}

.navbar-user a.active {
  color: var(--oj-brand);
}

@media (max-width: 820px) {
  .navbar-container {
    padding: 0 12px;
  }

  .navbar {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    gap: 0.75rem;
    width: 100%;
    padding: 10px 12px 12px;
  }

  .navbar-logo {
    min-width: 0;
  }

  .navbar-logo h2 {
    font-size: 1rem;
    overflow: hidden;
    text-overflow: ellipsis;
  }

  .navbar-menu {
    display: grid;
    grid-column: 1 / -1;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 6px;
  }

  .navbar-menu a {
    width: auto;
    min-width: 0;
    padding: 7px 4px;
    border-radius: 8px;
    font-size: 0.82rem;
    line-height: 1.2;
  }

  .navbar-user {
    justify-self: end;
    gap: 0.75rem;
  }
}

</style>
