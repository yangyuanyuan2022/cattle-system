<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Edit, Plus } from '@element-plus/icons-vue'
import { createRole, createUser, getPermissions, getRoles, getUsers, updateRole, updateUser, type PermissionGroup, type RoleItem, type UserItem } from '../api/user'
import { getBarns, getHerds, type Barn, type Herd } from '../api/location'
import { getStoredUser } from '../auth/session'

const standardRoles = new Set(['ADMIN','FARM_MANAGER','VET','BREEDER','WORKER'])
const isAdmin = getStoredUser()?.roles.includes('ADMIN') ?? false
const tab = ref('users')
const loading = ref(false)
const saving = ref(false)
const userDialog = ref(false)
const roleDialog = ref(false)
const editingUser = ref<UserItem | null>(null)
const editingRole = ref<RoleItem | null>(null)
const users = ref<UserItem[]>([])
const roles = ref<RoleItem[]>([])
const permissions = ref<PermissionGroup[]>([])
const barns = ref<Barn[]>([])
const herds = ref<Herd[]>([])
const userForm = reactive({ username:'', realName:'', phone:'', password:'', status:'ENABLED', roleCodes:[] as string[], scopeType:'SELF_ASSIGNED', scopeObjectId:'' })
const roleForm = reactive({ roleCode:'', roleName:'', status:'ENABLED', permissionCodes:[] as string[], version:0 })
const roleLabels: Record<string,string> = { ADMIN:'管理员', FARM_MANAGER:'场长', VET:'兽医', BREEDER:'繁育员', WORKER:'饲养员' }
const scopeLabels: Record<string,string> = { ALL:'全部数据', HERD:'指定牛群', BARN:'指定栏舍', SELF_CREATED:'本人创建', SELF_ASSIGNED:'本人任务' }
const scopeObjects = computed(() => userForm.scopeType === 'HERD' ? herds.value.map((item) => ({ id:item.herdId, name:item.herdName })) : barns.value.map((item) => ({ id:item.barnId, name:item.barnName })))

async function load() {
  loading.value = true
  try { [users.value, roles.value, permissions.value, barns.value, herds.value] = await Promise.all([getUsers(), getRoles(), getPermissions(), getBarns(), getHerds()]) }
  catch (error:any) { ElMessage.error(error.response?.data?.message || '用户权限数据加载失败') }
  finally { loading.value = false }
}

function openUser(row?: UserItem) {
  editingUser.value = row || null
  const scope = row?.dataScopes?.[0]
  Object.assign(userForm, row ? { username:row.username, realName:row.realName, phone:row.phone || '', password:'', status:row.status, roleCodes:[...row.roles], scopeType:scope?.scopeType || 'SELF_ASSIGNED', scopeObjectId:scope?.scopeObjectId || '' } : { username:'', realName:'', phone:'', password:'', status:'ENABLED', roleCodes:['WORKER'], scopeType:'SELF_ASSIGNED', scopeObjectId:'' })
  userDialog.value = true
}

function userScopes() {
  if (['HERD','BARN'].includes(userForm.scopeType)) return [{ scopeType:userForm.scopeType, scopeObjectId:userForm.scopeObjectId }]
  return [{ scopeType:userForm.scopeType, scopeObjectId:null }]
}

async function saveUser() {
  if (!userForm.realName.trim() || !userForm.roleCodes.length || (!editingUser.value && (!userForm.username.trim() || userForm.password.length < 6))) return ElMessage.warning('请完整填写必填项')
  if (['HERD','BARN'].includes(userForm.scopeType) && !userForm.scopeObjectId) return ElMessage.warning('请选择数据范围对象')
  saving.value = true
  try {
    const common = { realName:userForm.realName, phone:userForm.phone || undefined, roleCodes:userForm.roleCodes, dataScopes:userScopes() }
    if (editingUser.value) await updateUser(editingUser.value.userId, { ...common, status:userForm.status, newPassword:userForm.password || undefined, version:editingUser.value.version })
    else await createUser({ ...common, username:userForm.username, password:userForm.password })
    userDialog.value = false
    ElMessage.success('用户信息已保存')
    await load()
  } catch (error:any) { ElMessage.error(error.response?.data?.message || '保存失败') }
  finally { saving.value = false }
}

function openRole(row?: RoleItem) {
  editingRole.value = row || null
  Object.assign(roleForm, row ? { roleCode:row.roleCode, roleName:row.roleName, status:row.status, permissionCodes:[...row.permissionCodes], version:row.version } : { roleCode:'', roleName:'', status:'ENABLED', permissionCodes:[], version:0 })
  roleDialog.value = true
}

async function saveRole() {
  if (!roleForm.roleName.trim() || !roleForm.permissionCodes.length || (!editingRole.value && !/^[A-Z][A-Z0-9_]{2,49}$/.test(roleForm.roleCode))) return ElMessage.warning('请填写有效角色编码、名称并选择权限组')
  saving.value = true
  try {
    if (editingRole.value) await updateRole(editingRole.value.roleId, { roleName:roleForm.roleName, status:roleForm.status, permissionCodes:roleForm.permissionCodes, version:roleForm.version })
    else await createRole({ roleCode:roleForm.roleCode, roleName:roleForm.roleName, permissionCodes:roleForm.permissionCodes })
    roleDialog.value = false
    ElMessage.success('角色权限已保存')
    await load()
  } catch (error:any) { ElMessage.error(error.response?.data?.message || '保存失败') }
  finally { saving.value = false }
}
onMounted(load)
</script>

<template>
  <div>
    <div class="page-heading"><div><p class="eyebrow">账号、角色与数据范围</p><h1>用户权限</h1></div><el-button v-if="isAdmin" type="primary" :icon="Plus" @click="tab === 'users' ? openUser() : openRole()">{{ tab === 'users' ? '创建用户' : '创建角色' }}</el-button></div>
    <el-tabs v-model="tab"><el-tab-pane label="用户管理" name="users" /><el-tab-pane label="角色权限" name="roles" /></el-tabs>
    <section v-if="tab === 'users'" class="table-panel">
      <el-table v-loading="loading" :data="users">
        <el-table-column label="用户" min-width="190"><template #default="{ row }"><strong>{{ row.realName }}</strong><small class="sub">{{ row.username }}</small></template></el-table-column>
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column label="角色" min-width="220"><template #default="{ row }"><el-tag v-for="role in row.roles" :key="role" effect="plain" class="role-tag">{{ roleLabels[role] || role }}</el-tag></template></el-table-column>
        <el-table-column label="数据范围" min-width="180"><template #default="{ row }"><span v-for="scope in row.dataScopes" :key="scope.scopeType + scope.scopeObjectId">{{ scopeLabels[scope.scopeType] || scope.scopeType }}</span></template></el-table-column>
        <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">{{ row.status === 'ENABLED' ? '启用' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column prop="lastLoginAt" label="最近登录" width="180" />
        <el-table-column v-if="isAdmin" label="操作" width="90"><template #default="{ row }"><el-button link type="primary" :icon="Edit" @click="openUser(row)">编辑</el-button></template></el-table-column>
      </el-table>
    </section>
    <section v-else class="permission-grid">
      <article v-for="role in roles" :key="role.roleCode"><header><div><strong>{{ role.roleName }}</strong><code>{{ role.roleCode }}</code></div><el-button v-if="isAdmin && !standardRoles.has(role.roleCode)" link type="primary" :icon="Edit" @click="openRole(role)">编辑</el-button></header><ul><li v-for="code in role.permissionCodes" :key="code">{{ permissions.find((item) => item.code === code)?.name || code }}</li></ul><el-empty v-if="!role.permissionCodes.length" description="暂无权限组" :image-size="45" /></article>
    </section>

    <el-dialog v-model="userDialog" :title="editingUser ? '编辑用户' : '创建用户'" width="min(640px,94vw)">
      <el-form label-position="top" class="form-grid">
        <el-form-item label="登录账号" required><el-input v-model="userForm.username" :disabled="!!editingUser" /></el-form-item>
        <el-form-item label="姓名" required><el-input v-model="userForm.realName" /></el-form-item>
        <el-form-item label="手机号"><el-input v-model="userForm.phone" /></el-form-item>
        <el-form-item :label="editingUser ? '新密码（不修改请留空）' : '初始密码'" :required="!editingUser"><el-input v-model="userForm.password" type="password" show-password /></el-form-item>
        <el-form-item label="角色" required class="wide"><el-checkbox-group v-model="userForm.roleCodes"><el-checkbox v-for="role in roles" :key="role.roleCode" :value="role.roleCode">{{ role.roleName }}</el-checkbox></el-checkbox-group></el-form-item>
        <el-form-item label="数据范围" required><el-select v-model="userForm.scopeType" @change="userForm.scopeObjectId=''"><el-option v-for="(label, value) in scopeLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item>
        <el-form-item v-if="['HERD','BARN'].includes(userForm.scopeType)" label="范围对象" required><el-select v-model="userForm.scopeObjectId" filterable><el-option v-for="item in scopeObjects" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
        <el-form-item v-if="editingUser" label="账号状态"><el-segmented v-model="userForm.status" :options="[{label:'启用',value:'ENABLED'},{label:'停用',value:'DISABLED'}]" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="userDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveUser">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="roleDialog" :title="editingRole ? '编辑自定义角色' : '创建自定义角色'" width="min(580px,94vw)">
      <el-form label-position="top"><el-form-item label="角色编码" required><el-input v-model="roleForm.roleCode" :disabled="!!editingRole" placeholder="例如 FIELD_SUPERVISOR" /></el-form-item><el-form-item label="角色名称" required><el-input v-model="roleForm.roleName" /></el-form-item><el-form-item label="权限组" required><el-checkbox-group v-model="roleForm.permissionCodes" class="permission-options"><el-checkbox v-for="item in permissions" :key="item.code" :value="item.code">{{ item.name }}</el-checkbox></el-checkbox-group></el-form-item><el-form-item v-if="editingRole" label="状态"><el-segmented v-model="roleForm.status" :options="[{label:'启用',value:'ENABLED'},{label:'停用',value:'DISABLED'}]" /></el-form-item></el-form>
      <template #footer><el-button @click="roleDialog=false">取消</el-button><el-button type="primary" :loading="saving" @click="saveRole">保存</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.sub{display:block;color:#77827e;margin-top:4px}.role-tag{margin:2px 5px 2px 0}.permission-grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(280px,1fr));gap:14px}.permission-grid article{background:#fff;border:1px solid #dce3df;padding:16px}.permission-grid header{display:flex;justify-content:space-between;border-bottom:1px solid #e7ebe9;padding-bottom:12px}.permission-grid header div{display:grid;gap:4px}.permission-grid code{color:#75817d}.permission-grid ul{margin:14px 0 0;padding-left:20px;color:#53615b;line-height:2}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:0 16px}.wide{grid-column:1/-1}.permission-options{display:grid;grid-template-columns:1fr 1fr}.el-select{width:100%}@media(max-width:620px){.form-grid,.permission-options{grid-template-columns:1fr}.wide{grid-column:auto}}
</style>
