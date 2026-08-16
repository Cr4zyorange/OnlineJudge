<template>
  <span
    class="status-badge"
    :class="`status-badge--${tone}`"
    :data-tone="tone"
    :aria-label="ariaLabel"
    :title="title"
    data-testid="status-badge"
  >
    <span v-if="dot" class="status-badge__dot" aria-hidden="true" />
    <span class="status-badge__label">{{ label }}</span>
  </span>
</template>

<script setup lang="ts">
export type StatusBadgeTone = 'neutral' | 'brand' | 'success' | 'warning' | 'danger' | 'info';

withDefaults(defineProps<{
  label: string;
  tone?: StatusBadgeTone;
  dot?: boolean;
  ariaLabel?: string;
  title?: string;
}>(), {
  tone: 'neutral',
  dot: true,
  ariaLabel: undefined,
  title: undefined
});
</script>

<style scoped>
.status-badge {
  --status-badge-color: var(--oj-ink-soft);
  --status-badge-background: rgba(93, 113, 119, 0.12);

  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 100%;
  min-height: 26px;
  padding: 4px 9px;
  border: 1px solid var(--oj-line-strong);
  border-radius: 999px;
  background: var(--status-badge-background);
  color: var(--status-badge-color);
  font-size: 0.76rem;
  font-weight: 800;
  line-height: 1.25;
  vertical-align: middle;
}

.status-badge--brand,
.status-badge--info {
  --status-badge-color: var(--oj-brand);
  --status-badge-background: var(--oj-brand-soft);
}

.status-badge--success {
  --status-badge-color: var(--oj-brand-strong);
  --status-badge-background: rgba(22, 66, 60, 0.14);
}

.status-badge--warning {
  --status-badge-color: #7a4700;
  --status-badge-background: rgba(194, 123, 0, 0.14);
}

.status-badge--danger {
  --status-badge-color: #8f2d24;
  --status-badge-background: rgba(190, 49, 49, 0.12);
}

.status-badge__dot {
  flex: 0 0 auto;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
}

.status-badge__label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
