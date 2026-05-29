<template>
  <main class="auth-admin-page">
    <header class="admin-header">
      <div>
        <p class="eyebrow">AUTH</p>
        <h1>用户权限管理</h1>
      </div>
      <button type="button" class="ghost-action" :disabled="loading" @click="loadAll">
        {{ loading ? '刷新中' : '刷新' }}
      </button>
    </header>

    <p v-if="feedback" class="status-line" :class="feedbackType">{{ feedback }}</p>

    <section class="admin-grid" aria-label="用户和角色权限管理">
      <article class="admin-panel users-panel">
        <div class="panel-heading">
          <div>
            <p class="eyebrow">UI-AUTH-05 / UI-AUTH-08</p>
            <h2>用户管理</h2>
          </div>
          <span>{{ users.length }} 人</span>
        </div>

        <div v-if="loading" class="state-block">正在加载用户和角色</div>
        <div v-else-if="error" class="state-block error">{{ error }}</div>

        <form class="user-form" data-create-user-form @submit.prevent="createNewUser">
          <label>
            用户名
            <input v-model.trim="newUserForm.username" name="username" required />
          </label>
          <label>
            初始密码
            <input v-model.trim="newUserForm.password" name="password" type="password" required />
          </label>
          <label>
            显示名称
            <input v-model.trim="newUserForm.displayName" name="displayName" required />
          </label>
          <label>
            用户类型
            <select v-model="newUserForm.userType" name="userType">
              <option value="STUDENT">学生</option>
              <option value="TEACHER">教师</option>
              <option value="ADMIN">管理员</option>
            </select>
          </label>
          <label>
            手机
            <input v-model.trim="newUserForm.phone" name="phone" />
          </label>
          <label>
            邮箱
            <input v-model.trim="newUserForm.email" name="email" />
          </label>
          <div class="assignment-block create-user-roles">
            <p>初始角色</p>
            <label v-for="role in roles" :key="role.roleId">
              <input
                type="checkbox"
                :data-create-user-role="role.roleId"
                :checked="createUserRoleSelected(role.roleId)"
                @change="toggleCreateUserRole(role.roleId)"
              />
              {{ role.roleName }}
            </label>
          </div>
          <button type="submit" class="primary-action" data-create-user>新增用户</button>
        </form>

        <div v-if="!loading && !error && users.length === 0" class="state-block">暂无用户</div>

        <div v-if="!loading && !error && users.length > 0" class="user-list">
          <section v-for="user in users" :key="user.id" class="user-row">
            <div class="user-main">
              <strong>{{ user.username }}</strong>
              <span>{{ user.displayName }}</span>
              <mark>{{ user.accountStatus ?? 'ACTIVE' }}</mark>
            </div>
            <div class="assignment-block">
              <p>用户角色分配</p>
              <label v-for="role in roles" :key="role.roleId">
                <input
                  type="checkbox"
                  :data-user-role="`${user.id}-${role.roleId}`"
                  :checked="userRoleSelected(user.id, role.roleId)"
                  @change="toggleUserRole(user.id, role.roleId)"
                />
                {{ role.roleName }}
              </label>
            </div>
            <button
              type="button"
              class="primary-action"
              :data-save-user-roles="user.id"
              @click="saveUserRoles(user.id)"
            >
              保存角色
            </button>
            <button
              type="button"
              class="ghost-action"
              :data-toggle-user-status="user.id"
              @click="toggleUserStatus(user)"
            >
              {{ nextAccountStatus(user) === 'DISABLED' ? '禁用账号' : '启用账号' }}
            </button>
          </section>
        </div>
      </article>

      <article class="admin-panel roles-panel">
        <div class="panel-heading">
          <div>
            <p class="eyebrow">UI-AUTH-06 / UI-AUTH-07</p>
            <h2>角色管理</h2>
          </div>
          <span>{{ roles.length }} 类</span>
        </div>

        <form class="role-form" data-create-role-form @submit.prevent="createNewRole">
          <label>
            角色编码
            <input v-model.trim="newRoleForm.roleCode" name="roleCode" required />
          </label>
          <label>
            角色名称
            <input v-model.trim="newRoleForm.roleName" name="roleName" required />
          </label>
          <label>
            描述
            <input v-model.trim="newRoleForm.description" name="description" />
          </label>
          <label class="toggle-label">
            <input v-model="newRoleForm.enabled" type="checkbox" />
            启用
          </label>
          <button type="submit" class="primary-action" data-create-role>新增角色</button>
        </form>

        <div v-if="!loading && roles.length === 0" class="state-block">暂无角色</div>

        <section v-for="role in roles" v-else :key="role.roleId" class="role-row">
          <form class="role-edit-form" :data-save-role-form="role.roleId" @submit.prevent="saveRole(role.roleId)">
            <label>
              角色编码
              <input v-model.trim="roleDrafts[role.roleId].roleCode" :data-role-code="role.roleId" required />
            </label>
            <label>
              角色名称
              <input v-model.trim="roleDrafts[role.roleId].roleName" :data-role-name="role.roleId" required />
            </label>
            <label>
              描述
              <input v-model.trim="roleDrafts[role.roleId].description" :data-role-description="role.roleId" />
            </label>
            <label class="toggle-label">
              <input v-model="roleDrafts[role.roleId].enabled" type="checkbox" :data-role-enabled="role.roleId" />
              启用
            </label>
            <button type="submit" class="ghost-action" :data-save-role="role.roleId">保存角色</button>
          </form>
          <p class="permission-caption">权限分配</p>
          <div class="permission-list">
            <label v-for="permission in permissions" :key="permission.permissionId">
              <input
                type="checkbox"
                :data-role-permission="`${role.roleId}-${permission.permissionId}`"
                :checked="rolePermissionSelected(role.roleId, permission.permissionId)"
                @change="toggleRolePermission(role.roleId, permission.permissionId)"
              />
              <span>{{ permission.permissionCode }}</span>
            </label>
          </div>
          <button
            type="button"
            class="primary-action"
            :data-save-role-permissions="role.roleId"
            @click="saveRolePermissions(role.roleId)"
          >
            保存权限
          </button>
        </section>
      </article>
    </section>
  </main>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import {
  createAdminUser,
  createRole,
  listPermissions,
  listRoles,
  listUsers,
  updateRole,
  updateRolePermissions,
  updateUserStatus,
  updateUserRoles,
  type AdminUserPayload,
  type AuthUser,
  type RolePermission,
  type RolePayload,
  type RoleView
} from '../../api/auth/auth';

const users = ref<AuthUser[]>([]);
const roles = ref<RoleView[]>([]);
const permissions = ref<RolePermission[]>([]);
const userRoleSelection = reactive<Record<number, number[]>>({});
const rolePermissionSelection = reactive<Record<number, number[]>>({});
const roleDrafts = reactive<Record<number, RolePayload>>({});
const newRoleForm = reactive<RolePayload>({
  roleCode: '',
  roleName: '',
  description: '',
  enabled: true
});
const newUserForm = reactive<AdminUserPayload>({
  username: '',
  password: '',
  userType: 'TEACHER',
  displayName: '',
  phone: '',
  email: '',
  roleIds: []
});
const newUserRoleIds = ref<number[]>([]);
const loading = ref(false);
const error = ref('');
const feedback = ref('');
const feedbackType = ref<'success' | 'error'>('success');

onMounted(loadAll);

async function loadAll() {
  loading.value = true;
  error.value = '';
  feedback.value = '';
  try {
    const [userPage, roleList, permissionList] = await Promise.all([
      listUsers({ page: 1, size: 50 }),
      listRoles(),
      listPermissions()
    ]);
    users.value = userPage.records;
    roles.value = roleList;
    permissions.value = permissionList;
    initializeSelections();
  } catch (loadError) {
    error.value = loadError instanceof Error ? loadError.message : '加载失败';
  } finally {
    loading.value = false;
  }
}

async function saveUserRoles(userId: number) {
  await runAction('用户角色已更新', async () => {
    const updatedUser = await updateUserRoles(userId, userRoleSelection[userId] ?? []);
    users.value = users.value.map((user) => user.id === userId ? updatedUser : user);
    userRoleSelection[userId] = roleIdsForUser(updatedUser);
  });
}

async function toggleUserStatus(user: AuthUser) {
  await runAction('账号状态已更新', async () => {
    const updatedUser = await updateUserStatus(user.id, nextAccountStatus(user));
    users.value = users.value.map((current) => current.id === user.id ? updatedUser : current);
    userRoleSelection[user.id] = roleIdsForUser(updatedUser);
  });
}

async function saveRolePermissions(roleId: number) {
  await runAction('角色权限已更新', async () => {
    const updatedRole = await updateRolePermissions(roleId, rolePermissionSelection[roleId] ?? []);
    roles.value = roles.value.map((role) => role.roleId === roleId ? updatedRole : role);
    rolePermissionSelection[roleId] = updatedRole.permissions.map((permission) => permission.permissionId);
    roleDrafts[roleId] = toRolePayload(updatedRole);
  });
}

async function createNewRole() {
  await runAction('角色已创建', async () => {
    const role = await createRole({ ...newRoleForm });
    roles.value = [...roles.value, role];
    rolePermissionSelection[role.roleId] = role.permissions.map((permission) => permission.permissionId);
    roleDrafts[role.roleId] = toRolePayload(role);
    Object.assign(newRoleForm, {
      roleCode: '',
      roleName: '',
      description: '',
      enabled: true
    });
  });
}

async function createNewUser() {
  await runAction('用户已创建', async () => {
    const createdUser = await createAdminUser({
      ...newUserForm,
      roleIds: initialRoleIds()
    });
    users.value = [...users.value, createdUser];
    userRoleSelection[createdUser.id] = roleIdsForUser(createdUser);
    Object.assign(newUserForm, {
      username: '',
      password: '',
      userType: 'TEACHER',
      displayName: '',
      phone: '',
      email: '',
      roleIds: []
    });
    newUserRoleIds.value = [];
  });
}

async function saveRole(roleId: number) {
  await runAction('角色已更新', async () => {
    const updatedRole = await updateRole(roleId, roleDrafts[roleId]);
    roles.value = roles.value.map((role) => role.roleId === roleId ? updatedRole : role);
    roleDrafts[roleId] = toRolePayload(updatedRole);
  });
}

function initializeSelections() {
  for (const user of users.value) {
    userRoleSelection[user.id] = roleIdsForUser(user);
  }
  for (const role of roles.value) {
    rolePermissionSelection[role.roleId] = role.permissions.map((permission) => permission.permissionId);
    roleDrafts[role.roleId] = toRolePayload(role);
  }
}

function toRolePayload(role: RoleView): RolePayload {
  return {
    roleCode: role.roleCode,
    roleName: role.roleName,
    description: role.description ?? '',
    enabled: role.enabled
  };
}

function userRoleSelected(userId: number, roleId: number) {
  return (userRoleSelection[userId] ?? []).includes(roleId);
}

function rolePermissionSelected(roleId: number, permissionId: number) {
  return (rolePermissionSelection[roleId] ?? []).includes(permissionId);
}

function createUserRoleSelected(roleId: number) {
  return newUserRoleIds.value.includes(roleId);
}

function toggleUserRole(userId: number, roleId: number) {
  userRoleSelection[userId] = toggleId(userRoleSelection[userId] ?? [], roleId);
}

function toggleRolePermission(roleId: number, permissionId: number) {
  rolePermissionSelection[roleId] = toggleId(rolePermissionSelection[roleId] ?? [], permissionId);
}

function toggleCreateUserRole(roleId: number) {
  newUserRoleIds.value = toggleId(newUserRoleIds.value, roleId);
}

function roleIdsForUser(user: AuthUser) {
  return roles.value
    .filter((role) => user.roles.includes(role.roleCode))
    .map((role) => role.roleId);
}

function toggleId(ids: number[], id: number) {
  return ids.includes(id)
    ? ids.filter((current) => current !== id)
    : [...ids, id].sort((left, right) => left - right);
}

function initialRoleIds() {
  if (newUserRoleIds.value.length > 0) {
    return newUserRoleIds.value;
  }
  return roles.value
    .filter((role) => role.roleCode === newUserForm.userType)
    .map((role) => role.roleId);
}

function nextAccountStatus(user: AuthUser) {
  return (user.accountStatus ?? 'ACTIVE') === 'DISABLED' ? 'ACTIVE' : 'DISABLED';
}

async function runAction(successMessage: string, action: () => Promise<void>) {
  feedback.value = '';
  try {
    await action();
    feedbackType.value = 'success';
    feedback.value = successMessage;
  } catch (actionError) {
    feedbackType.value = 'error';
    feedback.value = actionError instanceof Error ? actionError.message : '操作失败';
  }
}
</script>

<style scoped>
.auth-admin-page {
  min-height: 100vh;
  padding: 28px;
  background: #eef4f1;
  color: #172b27;
}

.admin-header,
.panel-heading,
.role-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.admin-header {
  margin-bottom: 18px;
}

.eyebrow {
  margin: 0 0 4px;
  color: #2f6b5f;
  font-size: 0.78rem;
  font-weight: 800;
  letter-spacing: 0;
}

h1,
h2,
p {
  margin: 0;
}

h1 {
  font-size: 1.8rem;
}

h2 {
  font-size: 1.05rem;
}

.admin-grid {
  display: grid;
  grid-template-columns: minmax(360px, 1.1fr) minmax(360px, 0.9fr);
  gap: 18px;
}

.admin-panel {
  border: 1px solid rgba(23, 43, 39, 0.12);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.76);
  box-shadow: 0 12px 30px rgba(31, 54, 49, 0.08);
}

.panel-heading {
  padding: 18px 18px 12px;
  border-bottom: 1px solid rgba(23, 43, 39, 0.1);
}

.panel-heading span,
.user-main mark {
  border-radius: 999px;
  padding: 4px 9px;
  background: #dcebe6;
  color: #214c44;
  font-size: 0.78rem;
  font-weight: 800;
}

.user-list,
.roles-panel {
  display: grid;
  gap: 12px;
}

.user-list {
  padding: 14px;
}

.role-row {
  display: grid;
  gap: 12px;
  padding: 14px 18px 18px;
  border-bottom: 1px solid rgba(23, 43, 39, 0.08);
}

.user-form,
.role-form,
.role-edit-form {
  display: grid;
  grid-template-columns: minmax(110px, 0.8fr) minmax(120px, 1fr) minmax(140px, 1.2fr) auto auto;
  align-items: end;
  gap: 10px;
  padding: 14px 18px;
}

.user-form {
  grid-template-columns: repeat(3, minmax(130px, 1fr));
  border-bottom: 1px solid rgba(23, 43, 39, 0.08);
}

.role-edit-form {
  padding: 0;
}

.user-row {
  display: grid;
  grid-template-columns: minmax(150px, 0.8fr) minmax(240px, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border: 1px solid rgba(23, 43, 39, 0.1);
  border-radius: 8px;
  background: #fbfdfc;
}

.user-main {
  display: grid;
  gap: 4px;
}

.user-main span,
.permission-caption {
  color: #5a6f69;
  font-size: 0.88rem;
}

.assignment-block,
.permission-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.assignment-block p,
.permission-caption {
  width: 100%;
  font-weight: 700;
}

label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 34px;
  padding: 0 10px;
  border: 1px solid rgba(23, 43, 39, 0.12);
  border-radius: 8px;
  background: #ffffff;
  color: #243b36;
  font-size: 0.88rem;
}

label input,
label select {
  width: 100%;
  min-width: 0;
}

.user-form label,
.role-form label,
.role-edit-form label {
  display: grid;
  gap: 5px;
  padding: 8px 10px;
}

.create-user-roles {
  align-self: stretch;
  padding: 8px 10px;
  border: 1px solid rgba(23, 43, 39, 0.12);
  border-radius: 8px;
  background: #ffffff;
}

select {
  border: 1px solid rgba(23, 43, 39, 0.18);
  border-radius: 6px;
  background: #fff;
}

.toggle-label {
  align-self: stretch;
  display: inline-flex;
  justify-content: center;
}

input {
  accent-color: #174c43;
}

code {
  color: #28584f;
  font-weight: 800;
}

.primary-action,
.ghost-action {
  min-height: 36px;
  padding: 0 14px;
  border: 1px solid #174c43;
  border-radius: 8px;
  cursor: pointer;
  font-weight: 800;
}

.primary-action {
  background: #174c43;
  color: #fff;
}

.ghost-action {
  background: transparent;
  color: #174c43;
}

.status-line,
.state-block {
  margin-bottom: 14px;
  padding: 12px 14px;
  border-radius: 8px;
  font-weight: 700;
}

.status-line.success {
  background: #dcebe6;
  color: #174c43;
}

.status-line.error,
.state-block.error {
  background: #f7dddd;
  color: #922b2b;
}

.state-block {
  margin: 14px;
  background: #eef4f1;
  color: #4d635e;
}

@media (max-width: 900px) {
  .auth-admin-page {
    padding: 18px;
  }

  .admin-grid,
  .user-row,
  .user-form,
  .role-form,
  .role-edit-form {
    grid-template-columns: 1fr;
  }
}
</style>
