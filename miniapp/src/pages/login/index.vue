<script setup lang="ts">
import { ref } from 'vue'
import { request } from '../../api/request'

interface LoginResult { accessToken: string; realName: string; roles: string[] }
const username = ref('')
const password = ref('')
const loading = ref(false)

async function login() {
  if (!username.value.trim() || !password.value) {
    uni.showToast({ title: '请输入账号和密码', icon: 'none' })
    return
  }
  loading.value = true
  try {
    const result = await request<LoginResult>({
      url: '/auth/login', method: 'POST',
      data: { username: username.value.trim(), password: password.value },
    })
    uni.setStorageSync('access_token', result.accessToken)
    uni.setStorageSync('current_user', { realName: result.realName, roles: result.roles })
    uni.switchTab({ url: '/pages/tasks/index' })
  } catch (error) {
    uni.showToast({ title: error instanceof Error ? error.message : '登录失败', icon: 'none' })
  } finally { loading.value = false }
}
</script>

<template>
  <view class="login-page">
    <view class="identity"><view class="mark">牛</view><text class="name">牧衡牛场</text><text class="subtitle">肉牛现场管理</text></view>
    <view class="login-actions panel">
      <input v-model="username" placeholder="员工账号" confirm-type="next" />
      <input v-model="password" password placeholder="登录密码" confirm-type="done" @confirm="login" />
      <button class="primary" :loading="loading" @tap="login">登录</button>
      <text class="tip">使用管理端分配的员工账号登录</text>
    </view>
  </view>
</template>

<style scoped>
.login-page { min-height: 100vh; box-sizing: border-box; padding: 130rpx 48rpx calc(60rpx + env(safe-area-inset-bottom)); background: #eef2f0; display: flex; flex-direction: column; justify-content: space-between; }
.identity { display: flex; flex-direction: column; align-items: center; }.mark { width: 112rpx; height: 112rpx; border-radius: 16rpx; background: #e6b650; color: #18332c; display: flex; align-items: center; justify-content: center; font-size: 54rpx; font-weight: 800; }.name { margin-top: 34rpx; font-size: 44rpx; font-weight: 700; }.subtitle { margin-top: 14rpx; color: #74807c; }
.login-actions { padding: 30rpx; }.login-actions input { height: 88rpx; padding: 0 22rpx; border-bottom: 1rpx solid #dce3df; }.primary { margin-top: 28rpx; height: 94rpx; line-height: 94rpx; background: #176b52; color: #fff; border-radius: 8rpx; font-size: 32rpx; }.tip { display: block; margin-top: 22rpx; text-align: center; color: #88938f; font-size: 22rpx; }
</style>
