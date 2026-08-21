<template>
  <main class="profile-page">
    <section class="profile-shell">
      <header class="profile-header">
        <h1>账号信息与密码安全</h1>
        <p v-if="profile">{{ profile.username }} · {{ roleLabel }}</p>
      </header>

      <PageState v-if="loading" state="loading" title="正在加载个人资料" />
      <PageState
        v-else-if="loadError"
        state="error"
        title="个人资料加载失败"
        :message="loadError"
        retry-label="重试"
        @retry="loadProfile"
      />

      <div v-else class="profile-grid">
        <form class="profile-form" data-profile-form="profile" @submit.prevent="submitProfile">
          <div class="form-heading">
            <h2>个人资料</h2>
            <p>维护昵称、联系方式与头像地址。</p>
          </div>

          <label>
            显示名称
            <input v-model.trim="profileForm.displayName" name="displayName" autocomplete="name" />
          </label>
          <label>
            手机号
            <input v-model.trim="profileForm.phone" name="phone" autocomplete="tel" />
          </label>
          <label>
            邮箱
            <input v-model.trim="profileForm.email" name="email" autocomplete="email" />
          </label>
          <label>
            头像 URL
            <input v-model.trim="profileForm.avatarUrl" name="avatarUrl" autocomplete="url" />
          </label>

          <button type="submit" :disabled="savingProfile">
            {{ savingProfile ? '保存中' : '保存资料' }}
          </button>
          <p v-if="profileFeedback" class="feedback" :class="profileFeedbackType">{{ profileFeedback }}</p>
        </form>

        <form class="profile-form" data-profile-form="password" @submit.prevent="submitPassword">
          <div class="form-heading">
            <h2>修改密码</h2>
            <p>修改成功后当前登录会话会失效，需要重新登录。</p>
          </div>

          <label>
            原密码
            <input v-model="passwordForm.oldPassword" name="oldPassword" type="password" autocomplete="current-password" />
          </label>
          <label>
            新密码
            <input v-model="passwordForm.newPassword" name="newPassword" type="password" autocomplete="new-password" />
          </label>
          <label>
            确认新密码
            <input v-model="passwordForm.confirmPassword" name="confirmPassword" type="password" autocomplete="new-password" />
          </label>

          <button type="submit" :disabled="savingPassword">
            {{ savingPassword ? '提交中' : '修改密码' }}
          </button>
          <p v-if="passwordFeedback" class="feedback" :class="passwordFeedbackType">{{ passwordFeedback }}</p>
          <a v-if="passwordChanged" class="login-link" href="/login">重新登录</a>
        </form>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import PageState from '../../components/foundation/PageState.vue';
import {
  changePassword,
  clearAuthSession,
  getProfile,
  updateProfile,
  type AuthUser
} from '../../api/auth/auth';

const loading = ref(true);
const loadError = ref('');
const savingProfile = ref(false);
const savingPassword = ref(false);
const profile = ref<AuthUser | null>(null);
const profileFeedback = ref('');
const passwordFeedback = ref('');
const profileFeedbackType = ref<'success' | 'error'>('success');
const passwordFeedbackType = ref<'success' | 'error'>('success');
const passwordChanged = ref(false);

const profileForm = reactive({
  displayName: '',
  phone: '',
  email: '',
  avatarUrl: ''
});

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
});

const roleLabel = computed(() => {
  const role = profile.value?.roles[0] ?? profile.value?.userType;
  if (role === 'ADMIN') {
    return '管理员';
  }
  if (role === 'TEACHER') {
    return '教师';
  }
  return '学生';
});

onMounted(loadProfile);

async function loadProfile() {
  loading.value = true;
  loadError.value = '';
  try {
    applyProfile(await getProfile());
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '个人资料加载失败';
  } finally {
    loading.value = false;
  }
}

async function submitProfile() {
  savingProfile.value = true;
  profileFeedback.value = '';
  try {
    applyProfile(await updateProfile({ ...profileForm }));
    profileFeedbackType.value = 'success';
    profileFeedback.value = '个人资料已更新';
  } catch (error) {
    profileFeedbackType.value = 'error';
    profileFeedback.value = error instanceof Error ? error.message : '个人资料保存失败';
  } finally {
    savingProfile.value = false;
  }
}

async function submitPassword() {
  passwordFeedback.value = '';
  passwordChanged.value = false;
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    passwordFeedbackType.value = 'error';
    passwordFeedback.value = '两次输入的新密码不一致';
    return;
  }
  savingPassword.value = true;
  try {
    await changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword
    });
    passwordForm.oldPassword = '';
    passwordForm.newPassword = '';
    passwordForm.confirmPassword = '';
    clearAuthSession();
    passwordChanged.value = true;
    passwordFeedbackType.value = 'success';
    passwordFeedback.value = '密码已修改';
  } catch (error) {
    passwordFeedbackType.value = 'error';
    passwordFeedback.value = error instanceof Error ? error.message : '密码修改失败';
  } finally {
    savingPassword.value = false;
  }
}

function applyProfile(nextProfile: AuthUser) {
  profile.value = nextProfile;
  profileForm.displayName = nextProfile.displayName ?? '';
  profileForm.phone = nextProfile.phone ?? '';
  profileForm.email = nextProfile.email ?? '';
  profileForm.avatarUrl = nextProfile.avatarUrl ?? '';
}
</script>

<style scoped>
.profile-page {
  min-height: 100vh;
  padding: 32px 18px;
}

.profile-shell {
  width: min(1080px, 100%);
  margin: 0 auto;
}

.profile-header {
  margin-bottom: 22px;
}

.profile-header h1 {
  margin: 0;
  color: #1f2f2c;
  font-size: 1.9rem;
}

.profile-header p:last-child {
  margin: 8px 0 0;
  color: #4e635e;
}

.profile-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.profile-form,
.state-block {
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.2);
  background: rgba(255, 255, 255, 0.15);
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.1);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
}

.profile-form {
  display: grid;
  gap: 14px;
  padding: 22px;
}

.form-heading h2 {
  margin: 0 0 6px;
  color: #1f2f2c;
  font-size: 1.18rem;
}

.form-heading p {
  margin: 0;
  color: #64756f;
  line-height: 1.5;
}

.profile-form label {
  display: grid;
  gap: 6px;
  color: #314541;
  font-weight: 700;
}

.profile-form input {
  min-height: 42px;
  width: 100%;
  border: 1px solid rgba(22, 66, 60, 0.18);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.78);
  color: #1f2f2c;
  padding: 0 12px;
}

.profile-form button,
.login-link {
  min-height: 42px;
  border-radius: 8px;
  background: #16423c;
  color: #fff;
  cursor: pointer;
  font-weight: 800;
}

.login-link {
  display: grid;
  place-items: center;
  text-decoration: none;
}

.feedback,
.state-block {
  margin: 0;
  padding: 10px 12px;
  border-radius: 8px;
}

.feedback.success {
  background: rgba(22, 66, 60, 0.12);
  color: #16423c;
}

.feedback.error,
.state-block.error {
  background: rgba(174, 48, 48, 0.12);
  color: #8a1f1f;
}

@media (max-width: 760px) {
  .profile-grid {
    grid-template-columns: 1fr;
  }
}
</style>
