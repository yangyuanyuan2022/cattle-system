<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCurrentUser, login } from '../api/auth'
import { saveSession } from '../auth/session'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const form = reactive({ username: 'admin', password: '123456' })

async function submit() {
  if (!form.username.trim() || !form.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    const result = await login(form.username.trim(), form.password)
    localStorage.setItem('access_token', result.accessToken)
    const user = await getCurrentUser()
    saveSession(result.accessToken, user)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    await router.replace(redirect)
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || '登录失败，请检查账号和密码')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-panel">
      <div class="login-brand">
        <span class="brand-mark">牛</span>
        <div><h1>牧衡牛场管理</h1><p>肉牛全生命周期管理平台</p></div>
      </div>
      <el-form :model="form" label-position="top" @submit.prevent="submit">
        <el-form-item label="账号"><el-input v-model="form.username" size="large" autocomplete="username" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" size="large" type="password" show-password autocomplete="current-password" /></el-form-item>
        <el-button type="primary" size="large" :loading="loading" native-type="submit">登录</el-button>
      </el-form>
    </section>
  </main>
</template>
