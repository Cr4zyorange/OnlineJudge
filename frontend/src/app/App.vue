<template>
  <RouterView />
</template>

<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue';
import { RouterView, useRouter } from 'vue-router';

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
