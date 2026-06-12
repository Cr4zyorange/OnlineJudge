<template>
  <video
    v-if="selectedBackground.kind === 'video'"
    class="live-background-video"
    data-testid="live-background-video"
    :src="selectedBackground.src"
    autoplay
    muted
    loop
    playsinline
    aria-hidden="true"
  />
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
        <div v-if="activeSection === 'courses'" class="background-picker">
          <button
            class="background-picker__toggle"
            type="button"
            data-testid="background-picker-toggle"
            :aria-expanded="backgroundMenuOpen"
            aria-label="选择主题"
            ref="backgroundToggle"
            @click="toggleBackgroundMenu"
          >
            <i class="bi bi-image"></i>
            主题
          </button>
        </div>
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
        <button
          v-if="hasActiveSession"
          class="logout-button"
          type="button"
          data-testid="platform-nav-logout"
          :disabled="logoutPending"
          :aria-busy="logoutPending"
          @click="handleLogout"
        >
          {{ logoutPending ? '退出中' : '退出' }}
        </button>
      </div>
    </nav>
  </header>
  <Teleport to="body">
    <div
      v-if="backgroundMenuOpen"
      class="background-picker__menu"
      aria-label="主题选择"
      :style="backgroundMenuStyle"
    >
      <button
        v-for="option in backgroundOptions"
        :key="option.id"
        class="background-picker__option"
        type="button"
        :data-testid="`background-option-${option.id}`"
        :title="option.label"
        :aria-label="option.label"
        :class="{ active: selectedBackgroundId === option.id, 'is-video': option.kind === 'video' }"
        @click="selectBackground(option)"
      >
        <span v-if="option.kind !== 'video'" class="background-picker__preview" :style="previewStyle(option)" />
        <span v-else class="background-picker__video-preview">
          <video
            class="background-picker__preview-video"
            :src="option.src"
            muted
            playsinline
            preload="metadata"
            aria-hidden="true"
          />
          <span class="background-picker__video-label">{{ option.label }}</span>
        </span>
      </button>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue';
import { logout } from '../api/auth/auth';
import {
  BACKGROUND_STORAGE_KEY,
  applyBackgroundOption,
  backgroundOptions,
  findBackgroundOption,
  type BackgroundOption
} from '../backgroundOptions';
import { readLocalStorage, writeLocalStorage } from '../utils/browserStorage';

const props = defineProps<{
  currentPath: string;
}>();

const backgroundMenuOpen = ref(false);
const backgroundToggle = ref<HTMLElement | null>(null);
const backgroundMenuStyle = ref<Record<string, string>>({});
const selectedBackgroundId = ref(backgroundOptions[0].id);
const selectedBackground = computed(() => findBackgroundOption(selectedBackgroundId.value));
const logoutPending = ref(false);
const hasActiveSession = ref(Boolean(readLocalStorage('onlinejudge.authToken')));

const avatarText = computed(() => {
  const username = readLocalStorage('onlinejudge.username') ?? '';
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

onMounted(() => {
  const option = findBackgroundOption(readStoredBackgroundId());
  selectedBackgroundId.value = option.id;
  applyBackgroundOption(option);
});

function selectBackground(option: BackgroundOption) {
  selectedBackgroundId.value = option.id;
  applyBackgroundOption(option);
  saveStoredBackgroundId(option.id);
  backgroundMenuOpen.value = false;
}

function readStoredBackgroundId() {
  return readLocalStorage(BACKGROUND_STORAGE_KEY);
}

function saveStoredBackgroundId(id: string) {
  writeLocalStorage(BACKGROUND_STORAGE_KEY, id);
}

async function toggleBackgroundMenu() {
  if (backgroundMenuOpen.value) {
    backgroundMenuOpen.value = false;
    return;
  }
  updateBackgroundMenuPosition();
  backgroundMenuOpen.value = true;
  await nextTick();
  updateBackgroundMenuPosition();
}

async function handleLogout() {
  if (logoutPending.value) {
    return;
  }
  logoutPending.value = true;
  try {
    await logout();
  } catch {
    // logout() clears local auth state even when the server already expired the session.
  } finally {
    hasActiveSession.value = false;
    logoutPending.value = false;
    redirectToLogin();
  }
}

function redirectToLogin() {
  if (window.location.pathname !== '/login') {
    window.history.pushState({}, '', '/login');
  }
  window.dispatchEvent(new Event('onlinejudge:navigation'));
}

function updateBackgroundMenuPosition() {
  const button = backgroundToggle.value;
  if (!button) {
    return;
  }
  const rect = button.getBoundingClientRect();
  const nav = button.closest('.navbar-container') as HTMLElement | null;
  const navRect = nav?.getBoundingClientRect();
  const stableNavBottom = nav ? nav.offsetTop + nav.offsetHeight : rect.bottom;
  const menuWidth = 270;
  const left = Math.max(12, Math.min(window.innerWidth - menuWidth - 12, rect.right - menuWidth));
  const top = Math.max(rect.bottom, navRect?.bottom ?? rect.bottom, stableNavBottom) + 10;
  backgroundMenuStyle.value = {
    left: `${left}px`,
    top: `${top}px`
  };
}

function previewStyle(option: BackgroundOption) {
  return { backgroundImage: `url(${option.src})` };
}
</script>

<style scoped>
.live-background-video {
  position: fixed;
  inset: 0;
  z-index: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  pointer-events: none;
}

.navbar-container {
  left: 0;
  position: fixed;
  right: 0;
  top: 0;
  z-index: 300;
  padding: 0 16px;
}

.navbar {
  overflow: visible;
}

.navbar-logo {
  color: inherit;
  text-decoration: none;
}

.navbar-user a.active {
  color: var(--oj-brand);
}

.logout-button {
  min-height: 34px;
  padding: 0 12px;
  border: 1px solid rgba(220, 38, 38, 0.22);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.2);
  color: #9f1239;
  cursor: pointer;
  font-weight: 700;
  white-space: nowrap;
}

.logout-button:hover:not(:disabled),
.logout-button:focus-visible {
  border-color: rgba(220, 38, 38, 0.38);
  background: rgba(255, 255, 255, 0.34);
}

.logout-button:disabled {
  cursor: wait;
  opacity: 0.68;
}

.background-picker {
  position: relative;
}

.background-picker__toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 34px;
  padding: 0 10px;
  border: 1px solid rgba(255, 255, 255, 0.28);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.18);
  color: var(--oj-brand);
  cursor: pointer;
  font-weight: 700;
  white-space: nowrap;
}

.background-picker__menu {
  position: fixed;
  z-index: 1000;
  display: grid;
  grid-template-columns: repeat(3, 76px);
  gap: 10px;
  width: max-content;
  padding: 10px;
  border: 1px solid rgba(255, 255, 255, 0.34);
  border-radius: 8px;
  background: rgba(246, 251, 252, 0.78);
  box-shadow: var(--oj-shadow-soft);
  backdrop-filter: var(--oj-blur);
  -webkit-backdrop-filter: var(--oj-blur);
}

.background-picker__option {
  display: block;
  width: 76px;
  height: 58px;
  padding: 4px;
  border-radius: 8px;
  background: transparent;
  cursor: pointer;
}

.background-picker__option.active,
.background-picker__option:hover {
  background: rgba(22, 66, 60, 0.12);
  color: var(--oj-brand);
}

.background-picker__preview {
  display: block;
  width: 100%;
  height: 100%;
  border: 1px solid rgba(22, 66, 60, 0.14);
  border-radius: 6px;
  background-position: center;
  background-size: cover;
}

.background-picker__video-preview {
  position: relative;
  display: block;
  width: 100%;
  height: 100%;
  overflow: hidden;
  border: 1px solid rgba(22, 66, 60, 0.14);
  border-radius: 6px;
  background: rgba(22, 66, 60, 0.08);
}

.background-picker__preview-video {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.background-picker__video-label {
  position: absolute;
  left: 4px;
  right: 4px;
  bottom: 3px;
  overflow: hidden;
  padding: 1px 4px;
  border-radius: 4px;
  background: rgba(22, 66, 60, 0.72);
  color: #fff;
  font-size: 0.62rem;
  font-weight: 700;
  line-height: 1.35;
  text-align: center;
  text-overflow: ellipsis;
  white-space: nowrap;
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
    gap: 0.5rem;
  }

  .logout-button {
    width: 34px;
    padding: 0;
    overflow: hidden;
    font-size: 0;
  }

  .logout-button::before {
    content: "退";
    font-size: 0.82rem;
  }

  .background-picker__toggle {
    width: 34px;
    padding: 0;
    justify-content: center;
  }

  .background-picker__toggle i {
    margin: 0;
  }

  .background-picker__toggle {
    font-size: 0;
  }

  .background-picker__toggle i {
    font-size: 1rem;
  }
}

</style>
