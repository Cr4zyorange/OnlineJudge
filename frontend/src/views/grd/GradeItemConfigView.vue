<template>
  <main class="grade-items">
    <section class="grade-items__panel" aria-label="成绩项配置">
      <form class="grade-items__form" @submit.prevent="submit">
        <label>
          <span>成绩项名称</span>
          <input v-model="form.name" name="name" type="text" />
        </label>
        <label>
          <span>来源模块</span>
          <select v-model="form.sourceType" name="sourceType">
            <option value="LAB">LAB</option>
            <option value="HWK">HWK</option>
            <option value="OTHER_COURSE_ITEM">OTHER_COURSE_ITEM</option>
          </select>
        </label>
        <label>
          <span>来源任务</span>
          <input v-model="form.sourceId" name="sourceId" type="number" min="1" />
        </label>
        <label>
          <span>满分</span>
          <input v-model="form.fullScore" name="fullScore" type="number" min="0" step="0.01" />
        </label>
        <label>
          <span>权重</span>
          <input v-model="form.weight" name="weight" type="number" min="0" max="1" step="0.01" />
        </label>
        <label>
          <span>排序</span>
          <input v-model="form.sortOrder" name="sortOrder" type="number" min="0" step="1" />
        </label>
        <label class="grade-items__checkbox">
          <input v-model="form.includedInFinal" name="includedInFinal" type="checkbox" />
          <span>计入总评</span>
        </label>
        <div class="grade-items__form-actions">
          <button type="submit" :disabled="saving">{{ saving ? '保存中' : submitText }}</button>
          <button type="button" :disabled="saving" @click="resetForm">清空</button>
        </div>
      </form>

      <div class="grade-items__actions">
        <button type="button" :disabled="runningValidation" @click="validateRules">
          {{ runningValidation ? '校验中' : '校验规则' }}
        </button>
      </div>

      <p v-if="feedback" class="grade-items__feedback">{{ feedback }}</p>
      <p v-if="errorMessage" class="grade-items__error">{{ errorMessage }}</p>
    </section>

    <section class="grade-items__list" aria-label="成绩项列表">
      <p v-if="loading">加载中</p>
      <p v-else-if="items.length === 0">暂无成绩项</p>
      <table v-else>
        <thead>
          <tr>
            <th>名称</th>
            <th>来源</th>
            <th>满分</th>
            <th>权重</th>
            <th>总评</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in items" :key="item.id">
            <td>{{ item.name }}</td>
            <td>{{ item.sourceType }}</td>
            <td>{{ item.fullScore }}</td>
            <td>{{ item.weight }}</td>
            <td>{{ item.includedInFinal ? '是' : '否' }}</td>
            <td class="grade-items__row-actions">
              <button type="button" @click="editItem(item)">编辑</button>
              <button type="button" @click="removeItem(item)">停用</button>
            </td>
          </tr>
        </tbody>
      </table>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import {
  createGradeItem,
  deleteGradeItem,
  listGradeItems,
  updateGradeItem,
  validateGradeRules
} from '../../api/grd/gradeItems';
import type { CreateGradeItemPayload, GradeItem, GradeItemSourceType } from '../../types/grd';

const props = defineProps<{
  courseId: number;
}>();

const items = ref<GradeItem[]>([]);
const loading = ref(false);
const saving = ref(false);
const runningValidation = ref(false);
const feedback = ref('');
const errorMessage = ref('');
const editingId = ref<number | null>(null);

const form = reactive({
  name: '',
  sourceType: 'LAB' as GradeItemSourceType,
  sourceId: '',
  fullScore: '',
  weight: '',
  sortOrder: '1',
  includedInFinal: true
});

const submitText = computed(() => (editingId.value === null ? '保存' : '更新'));

onMounted(loadItems);

async function loadItems() {
  loading.value = true;
  errorMessage.value = '';
  try {
    items.value = await listGradeItems(props.courseId);
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '成绩项加载失败';
  } finally {
    loading.value = false;
  }
}

async function submit() {
  feedback.value = '';
  errorMessage.value = validateForm();
  if (errorMessage.value) {
    return;
  }

  saving.value = true;
  try {
    const payload = buildPayload();
    if (editingId.value === null) {
      await createGradeItem(props.courseId, payload);
      feedback.value = '保存成功';
    } else {
      await updateGradeItem(editingId.value, {
        ...payload,
        enabled: true
      });
      feedback.value = '更新成功';
    }
    resetForm();
    await loadItems();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '成绩项保存失败';
  } finally {
    saving.value = false;
  }
}

function editItem(item: GradeItem) {
  feedback.value = '';
  errorMessage.value = '';
  editingId.value = item.id;
  form.name = item.name;
  form.sourceType = item.sourceType;
  form.sourceId = item.sourceId === null ? '' : String(item.sourceId);
  form.fullScore = String(item.fullScore);
  form.weight = String(item.weight);
  form.sortOrder = String(item.sortOrder);
  form.includedInFinal = item.includedInFinal;
}

async function removeItem(item: GradeItem) {
  feedback.value = '';
  errorMessage.value = '';
  try {
    await deleteGradeItem(item.id);
    feedback.value = '已停用成绩项';
    if (editingId.value === item.id) {
      resetForm();
    }
    await loadItems();
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '成绩项停用失败';
  }
}

async function validateRules() {
  runningValidation.value = true;
  feedback.value = '';
  errorMessage.value = '';
  try {
    const result = await validateGradeRules(props.courseId);
    if (result.valid) {
      feedback.value = `规则校验通过，当前总权重 ${result.totalIncludedWeight}`;
    } else {
      errorMessage.value = result.errors.join('；') || '成绩规则校验未通过';
    }
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '成绩规则校验失败';
  } finally {
    runningValidation.value = false;
  }
}

function buildPayload(): CreateGradeItemPayload {
  return {
    name: form.name.trim(),
    sourceType: form.sourceType,
    sourceId: form.sourceId ? Number(form.sourceId) : null,
    fullScore: Number(form.fullScore).toFixed(2),
    weight: Number(form.weight).toFixed(2),
    includedInFinal: form.includedInFinal,
    sortOrder: Number(form.sortOrder)
  };
}

function validateForm() {
  const errors: string[] = [];
  const fullScore = Number(form.fullScore);
  const weight = Number(form.weight);
  const sortOrder = Number(form.sortOrder);
  const sourceId = Number(form.sourceId);

  if (!form.name.trim()) {
    errors.push('成绩项名称不能为空');
  }
  if (!Number.isFinite(fullScore) || fullScore <= 0) {
    errors.push('满分值必须大于 0');
  }
  if (!Number.isFinite(weight) || weight < 0 || weight > 1) {
    errors.push('权重必须在 0 到 1 之间');
  }
  if (!Number.isInteger(sortOrder) || sortOrder < 0) {
    errors.push('排序必须为非负整数');
  }
  if (
    (form.sourceType === 'LAB' || form.sourceType === 'HWK') &&
    (form.sourceId === '' || !Number.isInteger(sourceId) || sourceId <= 0)
  ) {
    errors.push('来源任务编号必须大于 0');
  }

  return errors.join('；');
}

function resetForm() {
  editingId.value = null;
  form.name = '';
  form.sourceType = 'LAB';
  form.sourceId = '';
  form.fullScore = '';
  form.weight = '';
  form.sortOrder = String(items.value.length + 1);
  form.includedInFinal = true;
}
</script>

<style scoped>
.grade-items {
  background: #f6f8fb;
  color: #1f2937;
  display: grid;
  gap: 20px;
  min-height: 100vh;
  padding: 24px;
}

.grade-items__panel,
.grade-items__list {
  background: #ffffff;
  border: 1px solid #d7dde8;
  border-radius: 8px;
  padding: 18px;
}

.grade-items__form {
  display: grid;
  gap: 14px;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
}

label {
  display: grid;
  gap: 6px;
}

input,
select {
  background: #ffffff;
  border: 1px solid #b8c2d2;
  color: #111827;
  min-height: 36px;
  padding: 6px 8px;
}

button {
  background: #ffffff;
  border: 1px solid #aeb8c8;
  color: #111827;
  min-height: 36px;
  padding: 6px 12px;
}

button:disabled {
  color: #697386;
}

.grade-items__checkbox,
.grade-items__form-actions,
.grade-items__actions,
.grade-items__row-actions {
  align-items: center;
  display: flex;
  gap: 8px;
}

.grade-items__actions {
  margin-top: 14px;
}

.grade-items__feedback {
  color: #116329;
}

.grade-items__error {
  color: #b42318;
}

table {
  border-collapse: collapse;
  width: 100%;
}

th,
td {
  border-bottom: 1px solid #d7dde8;
  padding: 10px;
  text-align: left;
}
</style>
