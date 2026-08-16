<template>
  <section
    class="page-state"
    :class="`page-state--${state}`"
    :data-state="state"
    :role="announcementRole"
    :aria-live="announcementMode"
    :aria-busy="state === 'loading' ? 'true' : undefined"
    data-testid="page-state"
  >
    <div class="page-state__symbol" aria-hidden="true">
      <slot name="illustration" :state="state">
        <span v-if="state === 'loading'" class="page-state__spinner" />
        <span v-else>{{ stateSymbol }}</span>
      </slot>
    </div>

    <div class="page-state__copy">
      <h2>{{ resolvedTitle }}</h2>
      <p>{{ resolvedMessage }}</p>
    </div>

    <div v-if="retryLabel || $slots.actions" class="page-state__actions">
      <button
        v-if="retryLabel"
        type="button"
        class="page-state__retry"
        data-testid="page-state-retry"
        @click="$emit('retry')"
      >
        {{ retryLabel }}
      </button>
      <slot name="actions" />
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue';

export type PageStateKind = 'loading' | 'empty' | 'error' | 'forbidden';

const props = withDefaults(defineProps<{
  state: PageStateKind;
  title?: string;
  message?: string;
  retryLabel?: string;
}>(), {
  title: undefined,
  message: undefined,
  retryLabel: undefined
});

defineEmits<{
  retry: [];
}>();

const stateCopy: Record<PageStateKind, { title: string; message: string }> = {
  loading: {
    title: '正在加载',
    message: '正在准备页面内容，请稍候。'
  },
  empty: {
    title: '暂无内容',
    message: '当前条件下还没有可展示的数据。'
  },
  error: {
    title: '加载失败',
    message: '页面内容暂时无法加载，请稍后重试。'
  },
  forbidden: {
    title: '暂无访问权限',
    message: '当前账号不能访问此页面，请返回可用入口。'
  }
};

const stateSymbols: Record<Exclude<PageStateKind, 'loading'>, string> = {
  empty: '—',
  error: '!',
  forbidden: '×'
};

const resolvedTitle = computed(() => props.title ?? stateCopy[props.state].title);
const resolvedMessage = computed(() => props.message ?? stateCopy[props.state].message);
const announcementRole = computed(() => (
  props.state === 'error' || props.state === 'forbidden' ? 'alert' : 'status'
));
const announcementMode = computed(() => (
  props.state === 'error' || props.state === 'forbidden' ? 'assertive' : 'polite'
));
const stateSymbol = computed(() => (
  props.state === 'loading' ? '' : stateSymbols[props.state]
));
</script>

<style scoped>
.page-state {
  display: grid;
  justify-items: center;
  gap: 14px;
  width: 100%;
  min-width: 0;
  padding: 38px 24px;
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius);
  background: var(--oj-surface);
  box-shadow: var(--oj-shadow-soft);
  color: var(--oj-ink);
  text-align: center;
  backdrop-filter: var(--oj-blur);
  -webkit-backdrop-filter: var(--oj-blur);
}

.page-state--error,
.page-state--forbidden {
  border-color: rgba(157, 47, 34, 0.18);
  background: rgba(248, 239, 238, 0.68);
}

.page-state__symbol {
  display: grid;
  width: 46px;
  height: 46px;
  border-radius: 50%;
  place-items: center;
  background: var(--oj-brand-soft);
  color: var(--oj-brand);
  font-size: 1.35rem;
  font-weight: 900;
}

.page-state--error .page-state__symbol,
.page-state--forbidden .page-state__symbol {
  background: rgba(190, 49, 49, 0.12);
  color: #8f2d24;
}

.page-state__spinner {
  width: 20px;
  height: 20px;
  border: 3px solid rgba(22, 66, 60, 0.2);
  border-top-color: var(--oj-brand);
  border-radius: 50%;
  animation: page-state-spin 0.8s linear infinite;
}

.page-state__copy {
  display: grid;
  gap: 6px;
  max-width: 58ch;
}

.page-state__copy h2,
.page-state__copy p {
  margin: 0;
}

.page-state__copy h2 {
  color: var(--oj-ink);
  font-size: 1.1rem;
  font-weight: 800;
  line-height: 1.4;
}

.page-state__copy p {
  color: var(--oj-ink-soft);
  font-size: 0.9rem;
  line-height: 1.65;
}

.page-state__actions {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 10px;
}

.page-state__retry {
  min-height: 40px;
  padding: 8px 18px;
  border: 1px solid var(--oj-brand);
  border-radius: var(--oj-radius);
  background: var(--oj-brand);
  color: #fff;
  cursor: pointer;
  font-weight: 800;
}

.page-state__retry:hover,
.page-state__retry:focus-visible {
  background: var(--oj-brand-strong);
}

.page-state__retry:focus-visible,
.page-state__actions :deep(a:focus-visible),
.page-state__actions :deep(button:focus-visible) {
  box-shadow: 0 0 0 3px var(--oj-brand-soft);
}

@keyframes page-state-spin {
  to {
    transform: rotate(360deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .page-state__spinner {
    animation: none;
  }
}

@media (max-width: 640px) {
  .page-state {
    padding: 30px 18px;
  }

  .page-state__actions,
  .page-state__actions :deep(button),
  .page-state__actions :deep(a) {
    width: 100%;
  }
}
</style>
