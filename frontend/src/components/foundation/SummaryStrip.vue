<template>
  <dl class="summary-strip" :aria-label="ariaLabel" data-testid="summary-strip">
    <div
      v-for="(item, index) in items"
      :key="item.key"
      class="summary-strip__item"
      :data-summary-tone="item.tone ?? 'neutral'"
    >
      <dt class="summary-strip__label">
        <slot name="label" :item="item" :index="index">
          {{ item.label }}
        </slot>
      </dt>
      <dd class="summary-strip__content">
        <slot name="item" :item="item" :index="index">
          <strong class="summary-strip__value">{{ item.value }}</strong>
          <span v-if="item.hint" class="summary-strip__hint">{{ item.hint }}</span>
        </slot>
      </dd>
    </div>
  </dl>
</template>

<script setup lang="ts">
export interface SummaryStripItem {
  key: string | number;
  label: string;
  value: string | number;
  hint?: string;
  tone?: 'neutral' | 'brand' | 'success' | 'warning' | 'danger';
}

withDefaults(defineProps<{
  items: readonly SummaryStripItem[];
  ariaLabel?: string;
}>(), {
  ariaLabel: '页面摘要'
});
</script>

<style scoped>
.summary-strip {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 12px;
  width: 100%;
  min-width: 0;
  margin: 0;
}

.summary-strip__item {
  position: relative;
  min-width: 0;
  padding: 15px 16px 14px;
  overflow: hidden;
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius);
  background: var(--oj-surface);
  box-shadow: var(--oj-shadow-soft);
  backdrop-filter: var(--oj-blur);
  -webkit-backdrop-filter: var(--oj-blur);
}

.summary-strip__item::before {
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  background: var(--summary-accent, var(--oj-line-strong));
  content: '';
}

.summary-strip__item[data-summary-tone='brand'],
.summary-strip__item[data-summary-tone='success'] {
  --summary-accent: var(--oj-brand);
}

.summary-strip__item[data-summary-tone='warning'] {
  --summary-accent: #9a6100;
}

.summary-strip__item[data-summary-tone='danger'] {
  --summary-accent: #8f2d24;
}

.summary-strip__label {
  margin: 0;
  color: var(--oj-muted);
  font-size: 0.75rem;
  font-weight: 800;
  line-height: 1.4;
}

.summary-strip__content {
  display: grid;
  gap: 3px;
  min-width: 0;
  margin: 6px 0 0;
}

.summary-strip__value {
  color: var(--oj-ink);
  font-size: 1.12rem;
  font-weight: 800;
  line-height: 1.35;
  overflow-wrap: anywhere;
}

.summary-strip__hint {
  color: var(--oj-ink-soft);
  font-size: 0.76rem;
  line-height: 1.45;
}

@media (max-width: 640px) {
  .summary-strip {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 10px;
  }

  .summary-strip__item {
    padding: 13px 12px;
  }

  .summary-strip__value {
    font-size: 1rem;
  }
}

@media (max-width: 350px) {
  .summary-strip {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
