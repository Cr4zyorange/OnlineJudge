<template>
  <main class="auth-page">
    <section class="auth-panel">
      <div class="auth-copy">
        <p class="auth-eyebrow">OnlineJudgeForSE</p>
        <h1>{{ mode === 'login' ? '用户登录' : '创建平台账号' }}</h1>
        <p>学生、教师和管理员使用统一入口进入课程、作业、实验、成绩和平台管理流程。</p>
      </div>

      <div class="auth-tabs" aria-label="认证模式">
        <button type="button" :class="{ active: mode === 'login' }" @click="mode = 'login'">登录</button>
        <button type="button" data-auth-mode="register" :class="{ active: mode === 'register' }" @click="mode = 'register'">注册</button>
      </div>

      <form v-if="mode === 'login'" class="auth-form" data-auth-form="login" @submit.prevent="submitLogin">
        <label>
          账号
          <input v-model.trim="loginForm.account" name="account" autocomplete="username" placeholder="学号、工号、邮箱或手机号" />
        </label>
        <label>
          密码
          <input v-model="loginForm.password" name="password" type="password" autocomplete="current-password" placeholder="请输入密码" />
        </label>
        <button class="primary-action" type="submit" :disabled="submitting">
          {{ submitting ? '登录中' : '登录' }}
        </button>
      </form>

      <form v-else class="auth-form" data-auth-form="register" @submit.prevent="submitRegister">
        <label>
          账号
          <input v-model.trim="registerForm.username" name="username" autocomplete="username" placeholder="学号、工号或系统账号" />
        </label>
        <label>
          显示名称
          <input v-model.trim="registerForm.displayName" name="displayName" placeholder="姓名或昵称" />
        </label>
        <input type="hidden" name="userType" value="STUDENT" />
        <label>
          密码
          <input v-model="registerForm.password" name="registerPassword" type="password" autocomplete="new-password" placeholder="至少 8 位，包含字母和数字" />
        </label>
        <button class="primary-action" type="submit" :disabled="submitting">
          {{ submitting ? '创建中' : '创建账号' }}
        </button>
      </form>

      <p v-if="feedback" class="auth-feedback" :class="feedbackType">{{ feedback }}</p>
      <a v-if="landingHref" class="landing-link" :href="landingHref">{{ landingText }}</a>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue';
import { login, register, type AuthUser } from '../../api/auth/auth';

const props = withDefaults(defineProps<{
  initialMode?: 'login' | 'register';
}>(), {
  initialMode: 'login'
});

const mode = ref<'login' | 'register'>(props.initialMode);
const submitting = ref(false);
const feedback = ref('');
const feedbackType = ref<'success' | 'error'>('success');
const currentUser = ref<AuthUser | null>(null);

const loginForm = reactive({
  account: '',
  password: ''
});

const registerForm = reactive({
  username: '',
  displayName: '',
  userType: 'STUDENT',
  password: ''
});

const landingText = computed(() => {
  const role = currentUser.value?.roles[0] ?? currentUser.value?.userType;
  if (role === 'ADMIN') {
    return '管理员工作台';
  }
  if (role === 'TEACHER') {
    return '教师工作台';
  }
  if (role === 'STUDENT') {
    return '学生工作台';
  }
  return '';
});

const landingHref = computed(() => currentUser.value ? '/courses' : '');

async function submitLogin() {
  await run(async () => {
    const result = await login({ ...loginForm });
    currentUser.value = result.user;
    feedbackType.value = 'success';
    feedback.value = '登录成功';
  });
}

async function submitRegister() {
  await run(async () => {
    currentUser.value = await register({ ...registerForm });
    feedbackType.value = 'success';
    feedback.value = '账号创建成功，请登录';
    mode.value = 'login';
    loginForm.account = registerForm.username;
  });
}

async function run(action: () => Promise<void>) {
  submitting.value = true;
  feedback.value = '';
  try {
    await action();
  } catch (error) {
    feedbackType.value = 'error';
    feedback.value = error instanceof Error ? error.message : '操作失败';
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped>
.auth-page {
  display: grid;
  min-height: 100vh;
  place-items: center;
  padding: 32px 16px;
}

.auth-panel {
  width: min(440px, 100%);
  padding: 28px;
  border: 1px solid rgba(255, 255, 255, 0.32);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.26);
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.14);
  backdrop-filter: blur(14px);
}

.auth-copy {
  margin-bottom: 20px;
}

.auth-eyebrow {
  margin: 0 0 8px;
  color: #16423c;
  font-size: 0.85rem;
  font-weight: 700;
}

.auth-copy h1 {
  margin: 0 0 10px;
  font-size: 1.75rem;
}

.auth-copy p:last-child {
  margin: 0;
  color: #4e635e;
  line-height: 1.6;
}

.auth-tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-bottom: 18px;
}

.auth-tabs button,
.primary-action,
.landing-link {
  min-height: 42px;
  border-radius: 8px;
  cursor: pointer;
}

.auth-tabs button {
  background: rgba(255, 255, 255, 0.48);
  color: #2c3e50;
}

.auth-tabs button.active {
  background: #16423c;
  color: #fff;
}

.auth-form {
  display: grid;
  gap: 14px;
}

.auth-form label {
  display: grid;
  gap: 6px;
  color: #314541;
  font-weight: 600;
}

.auth-form input,
.auth-form select {
  width: 100%;
  min-height: 42px;
  padding: 0 12px;
  border: 1px solid rgba(22, 66, 60, 0.18);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.7);
  color: #1f2f2c;
}

.primary-action,
.landing-link {
  display: grid;
  place-items: center;
  background: #16423c;
  color: #fff;
  font-weight: 700;
  text-decoration: none;
}

.auth-feedback {
  margin: 16px 0 0;
  padding: 10px 12px;
  border-radius: 8px;
}

.auth-feedback.success {
  background: rgba(22, 66, 60, 0.12);
  color: #16423c;
}

.auth-feedback.error {
  background: rgba(174, 48, 48, 0.12);
  color: #8a1f1f;
}

.landing-link {
  margin-top: 14px;
}
</style>
