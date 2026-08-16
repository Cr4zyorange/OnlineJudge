<template>
  <header class="foundation-page-header" data-testid="page-header">
    <div class="foundation-page-header__copy">
      <slot name="eyebrow">
        <p
          v-if="eyebrow"
          class="foundation-page-header__eyebrow"
          data-testid="page-header-eyebrow"
        >
          {{ eyebrow }}
        </p>
      </slot>

      <component :is="headingTag" class="foundation-page-header__title">
        {{ title }}
      </component>

      <slot name="subtitle">
        <p
          v-if="subtitle"
          class="foundation-page-header__subtitle"
          data-testid="page-header-subtitle"
        >
          {{ subtitle }}
        </p>
      </slot>

      <div v-if="$slots.meta" class="foundation-page-header__meta">
        <slot name="meta" />
      </div>
    </div>

    <div v-if="$slots.actions" class="foundation-page-header__actions" aria-label="页面操作">
      <slot name="actions" />
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue';

const props = withDefaults(defineProps<{
  title: string;
  subtitle?: string;
  eyebrow?: string;
  headingLevel?: 1 | 2 | 3;
}>(), {
  subtitle: undefined,
  eyebrow: undefined,
  headingLevel: 1
});

const headingTag = computed(() => `h${props.headingLevel}`);
</script>

<style scoped>
.foundation-page-header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
  gap: 24px;
  width: 100%;
  min-width: 0;
  padding: 22px 24px;
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius);
  background: var(--oj-surface);
  box-shadow: var(--oj-shadow-soft);
  color: var(--oj-ink);
  backdrop-filter: var(--oj-blur);
  -webkit-backdrop-filter: var(--oj-blur);
}

.foundation-page-header__copy {
  min-width: 0;
}

.foundation-page-header__eyebrow {
  margin: 0 0 6px;
  color: var(--oj-brand);
  font-size: 0.76rem;
  font-weight: 800;
  letter-spacing: 0.08em;
  line-height: 1.4;
  text-transform: uppercase;
}

.foundation-page-header__title {
  margin: 0;
  color: var(--oj-ink);
  font-size: clamp(1.55rem, 2.5vw, 2.1rem);
  font-weight: 800;
  line-height: 1.2;
  overflow-wrap: anywhere;
}

.foundation-page-header__subtitle {
  max-width: 72ch;
  margin: 9px 0 0;
  color: var(--oj-ink-soft);
  font-size: 0.95rem;
  line-height: 1.65;
}

.foundation-page-header__meta,
.foundation-page-header__actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.foundation-page-header__meta {
  margin-top: 13px;
  color: var(--oj-muted);
  font-size: 0.84rem;
  font-weight: 600;
}

.foundation-page-header__actions {
  justify-content: flex-end;
}

.foundation-page-header__actions :deep(a:focus-visible),
.foundation-page-header__actions :deep(button:focus-visible) {
  box-shadow: 0 0 0 3px var(--oj-brand-soft);
}

@media (max-width: 640px) {
  .foundation-page-header {
    grid-template-columns: minmax(0, 1fr);
    gap: 18px;
    padding: 18px;
  }

  .foundation-page-header__title {
    font-size: 1.55rem;
  }

  .foundation-page-header__actions {
    justify-content: stretch;
    width: 100%;
  }

  .foundation-page-header__actions :deep(button),
  .foundation-page-header__actions :deep(a) {
    flex: 1 1 auto;
    min-width: 0;
  }
}
</style>
