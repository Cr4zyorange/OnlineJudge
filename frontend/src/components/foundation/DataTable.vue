<template>
  <section
    class="data-table"
    :aria-label="ariaLabel ?? caption"
    :aria-busy="busy ? 'true' : undefined"
    data-testid="data-table"
  >
    <template v-if="rows.length > 0">
      <div class="data-table__desktop-wrap">
        <table class="data-table__desktop" data-testid="data-table-desktop">
          <caption>{{ caption }}</caption>
          <thead>
            <tr>
              <th
                v-for="column in columns"
                :key="column.key"
                scope="col"
                :data-align="column.align ?? 'start'"
                :style="column.width ? { width: column.width } : undefined"
              >
                <slot :name="`header-${column.key}`" :column="column">
                  {{ column.label }}
                </slot>
              </th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, rowIndex) in rows" :key="rowIdentity(row, rowIndex)">
              <td
                v-for="column in columns"
                :key="column.key"
                :data-label="column.label"
                :data-align="column.align ?? 'start'"
              >
                <slot
                  :name="`cell-${column.key}`"
                  :row="row"
                  :row-index="rowIndex"
                  :column="column"
                  :value="row[column.key]"
                >
                  {{ displayValue(row[column.key]) }}
                </slot>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <ul
        class="data-table__mobile"
        data-mobile-layout="cards"
        data-testid="data-table-mobile"
      >
        <li v-for="(row, rowIndex) in rows" :key="rowIdentity(row, rowIndex)">
          <slot name="mobile-card" :row="row" :row-index="rowIndex" :columns="mobileColumns">
            <article class="data-table__card" :aria-label="mobileRowLabel(row, rowIndex)">
              <dl>
                <div v-for="column in mobileColumns" :key="column.key" class="data-table__card-field">
                  <dt>{{ column.mobileLabel ?? column.label }}</dt>
                  <dd :data-align="column.align ?? 'start'">
                    <slot
                      :name="`cell-${column.key}`"
                      :row="row"
                      :row-index="rowIndex"
                      :column="column"
                      :value="row[column.key]"
                      mobile
                    >
                      {{ displayValue(row[column.key]) }}
                    </slot>
                  </dd>
                </div>
              </dl>
            </article>
          </slot>
        </li>
      </ul>
    </template>

    <div v-else class="data-table__empty" role="status" aria-live="polite" data-testid="data-table-empty">
      <slot name="empty">
        <strong>{{ emptyTitle }}</strong>
        <p>{{ emptyMessage }}</p>
      </slot>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue';

export type DataTableRow = Record<string, unknown>;

export interface DataTableColumn {
  key: string;
  label: string;
  mobileLabel?: string;
  align?: 'start' | 'center' | 'end';
  width?: string;
  hideOnMobile?: boolean;
}

const props = withDefaults(defineProps<{
  columns: readonly DataTableColumn[];
  rows: readonly DataTableRow[];
  caption: string;
  ariaLabel?: string;
  rowKey?: string | ((row: DataTableRow, index: number) => string | number);
  rowLabel?: (row: DataTableRow, index: number) => string;
  emptyTitle?: string;
  emptyMessage?: string;
  busy?: boolean;
}>(), {
  ariaLabel: undefined,
  rowKey: undefined,
  rowLabel: undefined,
  emptyTitle: '暂无数据',
  emptyMessage: '当前条件下还没有可展示的记录。',
  busy: false
});

const mobileColumns = computed(() => props.columns.filter((column) => !column.hideOnMobile));

function rowIdentity(row: DataTableRow, index: number) {
  if (typeof props.rowKey === 'function') {
    return props.rowKey(row, index);
  }
  if (props.rowKey) {
    const value = row[props.rowKey];
    if (typeof value === 'string' || typeof value === 'number') {
      return value;
    }
  }
  return index;
}

function mobileRowLabel(row: DataTableRow, index: number) {
  return props.rowLabel?.(row, index) ?? `第 ${index + 1} 项`;
}

function displayValue(value: unknown) {
  if (value === null || value === undefined || value === '') {
    return '—';
  }
  return String(value);
}
</script>

<style scoped>
.data-table {
  width: 100%;
  min-width: 0;
  overflow: hidden;
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius);
  background: var(--oj-surface);
  box-shadow: var(--oj-shadow-soft);
  backdrop-filter: var(--oj-blur);
  -webkit-backdrop-filter: var(--oj-blur);
}

.data-table__desktop-wrap {
  width: 100%;
  min-width: 0;
  overflow-x: auto;
}

.data-table__desktop {
  width: 100%;
  border-collapse: collapse;
  table-layout: auto;
}

.data-table__desktop caption {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.data-table__desktop th,
.data-table__desktop td {
  padding: 13px 15px;
  border-bottom: 1px solid var(--oj-line);
  color: var(--oj-ink);
  line-height: 1.5;
  overflow-wrap: anywhere;
  vertical-align: middle;
}

.data-table__desktop th {
  background: var(--oj-brand-soft);
  color: var(--oj-brand);
  font-size: 0.78rem;
  font-weight: 800;
  letter-spacing: 0.02em;
  text-align: left;
}

.data-table__desktop td {
  font-size: 0.88rem;
}

.data-table__desktop tbody tr:last-child td {
  border-bottom: 0;
}

.data-table__desktop tbody tr:hover {
  background: var(--oj-surface-strong);
}

.data-table :deep(a:focus-visible),
.data-table :deep(button:focus-visible) {
  box-shadow: 0 0 0 3px var(--oj-brand-soft);
}

[data-align='center'] {
  text-align: center !important;
}

[data-align='end'] {
  text-align: right !important;
}

.data-table__mobile {
  display: none;
  margin: 0;
  padding: 0;
  list-style: none;
}

.data-table__card {
  min-width: 0;
  padding: 14px;
  border: 1px solid var(--oj-line);
  border-radius: var(--oj-radius);
  background: var(--oj-surface-strong);
}

.data-table__card dl {
  display: grid;
  gap: 10px;
  margin: 0;
}

.data-table__card-field {
  display: grid;
  grid-template-columns: minmax(84px, 0.38fr) minmax(0, 1fr);
  align-items: start;
  gap: 12px;
}

.data-table__card-field dt,
.data-table__card-field dd {
  min-width: 0;
  margin: 0;
  overflow-wrap: anywhere;
}

.data-table__card-field dt {
  color: var(--oj-muted);
  font-size: 0.75rem;
  font-weight: 800;
  line-height: 1.5;
}

.data-table__card-field dd {
  color: var(--oj-ink);
  font-size: 0.86rem;
  line-height: 1.5;
}

.data-table__empty {
  display: grid;
  justify-items: center;
  gap: 5px;
  padding: 34px 20px;
  color: var(--oj-ink);
  text-align: center;
}

.data-table__empty strong {
  font-size: 1rem;
}

.data-table__empty p {
  max-width: 52ch;
  margin: 0;
  color: var(--oj-ink-soft);
  font-size: 0.86rem;
  line-height: 1.6;
}

@media (max-width: 640px) {
  .data-table {
    overflow: visible;
    border: 0;
    background: transparent;
    box-shadow: none;
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
  }

  .data-table__desktop-wrap,
  .data-table__desktop {
    display: none !important;
  }

  .data-table__mobile {
    display: grid;
    gap: 10px;
  }

  .data-table__empty {
    border: 1px solid var(--oj-line);
    border-radius: var(--oj-radius);
    background: var(--oj-surface);
    box-shadow: var(--oj-shadow-soft);
    backdrop-filter: var(--oj-blur);
    -webkit-backdrop-filter: var(--oj-blur);
  }
}
</style>
