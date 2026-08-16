<template>
  <form
    class="filter-bar"
    :aria-label="ariaLabel"
    data-testid="filter-bar"
    @submit.prevent="submitFilters"
    @reset.prevent="resetFilters"
  >
    <div class="filter-bar__fields">
      <label
        v-for="(field, index) in fields"
        :key="field.key"
        class="filter-bar__field"
        :for="fieldId(field.key)"
      >
        <span class="filter-bar__label">{{ field.label }}</span>
        <slot
          name="field"
          :field="field"
          :index="index"
          :value="modelValue[field.key] ?? ''"
          :update="(value: string) => updateField(field.key, value)"
        >
          <input
            v-if="field.kind === 'search'"
            :id="fieldId(field.key)"
            type="search"
            :name="field.key"
            :value="modelValue[field.key] ?? ''"
            :placeholder="field.placeholder"
            :autocomplete="field.autocomplete ?? 'off'"
            :disabled="disabled || field.disabled"
            @input="updateField(field.key, inputValue($event))"
          />
          <select
            v-else
            :id="fieldId(field.key)"
            :name="field.key"
            :value="modelValue[field.key] ?? ''"
            :disabled="disabled || field.disabled"
            @change="updateField(field.key, inputValue($event))"
          >
            <option
              v-for="option in field.options ?? []"
              :key="option.value"
              :value="option.value"
              :disabled="option.disabled"
            >
              {{ option.label }}
            </option>
          </select>
        </slot>
      </label>
    </div>

    <div class="filter-bar__actions">
      <slot name="actions" :submit="submitFilters" :reset="resetFilters">
        <button
          type="submit"
          class="filter-bar__submit"
          data-testid="filter-submit"
          :disabled="disabled"
        >
          {{ submitLabel }}
        </button>
        <button
          v-if="showReset"
          type="reset"
          class="filter-bar__reset"
          data-testid="filter-reset"
          :disabled="disabled"
        >
          {{ resetLabel }}
        </button>
      </slot>
    </div>
  </form>
</template>

<script setup lang="ts">
import { useId } from 'vue';

export interface FilterOptionModel {
  value: string;
  label: string;
  disabled?: boolean;
}

export interface FilterFieldModel {
  key: string;
  label: string;
  kind: 'search' | 'select';
  placeholder?: string;
  autocomplete?: string;
  disabled?: boolean;
  options?: readonly FilterOptionModel[];
}

const props = withDefaults(defineProps<{
  fields: readonly FilterFieldModel[];
  modelValue: Readonly<Record<string, string>>;
  ariaLabel?: string;
  submitLabel?: string;
  resetLabel?: string;
  showReset?: boolean;
  disabled?: boolean;
}>(), {
  ariaLabel: '筛选条件',
  submitLabel: '筛选',
  resetLabel: '重置',
  showReset: true,
  disabled: false
});

const emit = defineEmits<{
  'update:modelValue': [value: Record<string, string>];
  submit: [value: Record<string, string>];
  reset: [];
}>();

const componentId = useId();

function fieldId(key: string) {
  return `${componentId}-filter-${key.replace(/[^a-zA-Z0-9_-]/g, '-')}`;
}

function inputValue(event: Event) {
  return (event.target as HTMLInputElement | HTMLSelectElement).value;
}

function updateField(key: string, value: string) {
  emit('update:modelValue', {
    ...props.modelValue,
    [key]: value
  });
}

function submitFilters() {
  emit('submit', { ...props.modelValue });
}

function resetFilters() {
  emit('reset');
}
</script>

<style scoped>
.filter-bar {
  display: flex;
  align-items: end;
  gap: 14px;
  width: 100%;
  min-width: 0;
  padding: 14px;
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius);
  background: var(--oj-surface);
  box-shadow: var(--oj-shadow-soft);
  backdrop-filter: var(--oj-blur);
  -webkit-backdrop-filter: var(--oj-blur);
}

.filter-bar__fields {
  display: grid;
  flex: 1 1 auto;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  gap: 12px;
  min-width: 0;
}

.filter-bar__field {
  display: grid;
  gap: 6px;
  min-width: 0;
}

.filter-bar__label {
  color: var(--oj-ink-soft);
  font-size: 0.76rem;
  font-weight: 800;
  line-height: 1.35;
}

.filter-bar__field input,
.filter-bar__field select {
  width: 100%;
  min-width: 0;
  min-height: 42px;
  padding: 8px 11px;
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius);
  background: var(--oj-surface-solid);
  color: var(--oj-ink);
}

.filter-bar__field input:focus-visible,
.filter-bar__field select:focus-visible {
  border-color: var(--oj-brand);
  box-shadow: 0 0 0 3px var(--oj-brand-soft);
}

.filter-bar__actions {
  display: flex;
  align-items: center;
  flex: 0 0 auto;
  gap: 8px;
}

.filter-bar__submit,
.filter-bar__reset {
  min-height: 42px;
  padding: 8px 15px;
  border-radius: var(--oj-radius);
  cursor: pointer;
  font-weight: 800;
  white-space: nowrap;
}

.filter-bar__submit {
  border: 1px solid var(--oj-brand);
  background: var(--oj-brand);
  color: #fff;
}

.filter-bar__submit:hover,
.filter-bar__submit:focus-visible {
  background: var(--oj-brand-strong);
}

.filter-bar__reset {
  border: 1px solid var(--oj-line-strong);
  background: var(--oj-surface-strong);
  color: var(--oj-brand);
}

.filter-bar__reset:hover,
.filter-bar__reset:focus-visible {
  background: var(--oj-brand-soft);
}

.filter-bar__submit:focus-visible,
.filter-bar__reset:focus-visible,
.filter-bar__actions :deep(a:focus-visible),
.filter-bar__actions :deep(button:focus-visible) {
  box-shadow: 0 0 0 3px var(--oj-brand-soft);
}

@media (max-width: 640px) {
  .filter-bar {
    align-items: stretch;
    flex-direction: column;
    padding: 12px;
  }

  .filter-bar__fields {
    grid-template-columns: minmax(0, 1fr);
  }

  .filter-bar__actions,
  .filter-bar__actions :deep(button),
  .filter-bar__actions :deep(a) {
    width: 100%;
  }

  .filter-bar__actions :deep(button),
  .filter-bar__actions :deep(a) {
    flex: 1 1 0;
    min-width: 0;
  }
}
</style>
