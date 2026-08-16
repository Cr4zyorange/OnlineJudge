<template>
  <div class="app-shell">
    <a class="app-shell__skip-link" data-testid="skip-to-content" href="#main-content">跳到主要内容</a>
    <PlatformNav />
    <div id="main-content" class="app-shell__content" tabindex="-1">
      <RouterView />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue';
import { RouterView, useRouter } from 'vue-router';
import PlatformNav from '../components/foundation/PlatformNav.vue';

const router = useRouter();

function syncExternalNavigation() {
  const destination = `${window.location.pathname}${window.location.search}${window.location.hash}`;
  if (router.currentRoute.value.fullPath !== destination) {
    void router.replace(destination);
  }
}

onMounted(() => window.addEventListener('onlinejudge:navigation', syncExternalNavigation));
onUnmounted(() => window.removeEventListener('onlinejudge:navigation', syncExternalNavigation));
</script>

<style scoped>
.app-shell__skip-link {
  position: fixed;
  z-index: 1000;
  top: 8px;
  left: 12px;
  border-radius: 8px;
  padding: 10px 14px;
  background: var(--oj-brand, #16423c);
  color: white;
  font-weight: 800;
  text-decoration: none;
  transform: translateY(-160%);
}

.app-shell__skip-link:focus {
  transform: translateY(0);
}

.app-shell__content:focus {
  outline: none;
}

@media (prefers-reduced-motion: reduce) {
  .app-shell__skip-link {
    transition: none;
  }
}
</style>
