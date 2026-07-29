<script setup lang="ts">
import { computed, ref } from 'vue'
import { onPullDownRefresh, onShow } from '@dcloudio/uni-app'
import { request } from '../../api/request'

interface TaskItem { taskId:string; taskType:string; title:string; earTagNo:string|null; dueDate:string; priority:string; status:string; assigneeName:string|null; version:number }
const active = ref('PENDING')
const loading = ref(false)
const tasks = ref<TaskItem[]>([])
const tabs = [{ label:'待处理', value:'PENDING' }, { label:'进行中', value:'IN_PROGRESS' }, { label:'已完成', value:'COMPLETED' }]
const dateText = computed(() => new Intl.DateTimeFormat('zh-CN', { month:'long', day:'numeric', weekday:'short' }).format(new Date()))
const typeLabels:Record<string,string> = { INSPECTION:'巡检', WEIGHT_RECORD:'称重', TRANSFER:'转群', OTHER:'其他' }

async function load() {
  loading.value = true
  try { tasks.value = await request<TaskItem[]>({ url:`/tasks?status=${active.value}`, method:'GET' }) }
  catch (error) { uni.showToast({ title:error instanceof Error ? error.message : '任务加载失败', icon:'none' }) }
  finally { loading.value = false; uni.stopPullDownRefresh() }
}
async function complete(task:TaskItem) {
  try {
    await request({ url:`/tasks/${task.taskId}/complete`, method:'POST', header:{ 'X-Idempotency-Key':`${Date.now()}-${task.taskId}` }, data:{ result:'小程序现场完成', version:task.version } })
    uni.showToast({ title:'任务已完成', icon:'success' }); await load()
  } catch (error) { uni.showToast({ title:error instanceof Error ? error.message : '操作失败', icon:'none' }) }
}
function change(value:string){ active.value=value; load() }
onShow(load); onPullDownRefresh(load)
</script>

<template><view class="safe-page">
  <view class="heading"><view><text class="date">{{ dateText }}</text><text class="title">我的任务</text></view><text class="count">{{ tasks.length }} 项</text></view>
  <view class="tabs"><button v-for="tab in tabs" :key="tab.value" :class="{active:active===tab.value}" @tap="change(tab.value)">{{ tab.label }}</button></view>
  <view v-if="loading" class="empty">正在加载...</view><view v-else-if="!tasks.length" class="empty">当前没有任务</view>
  <view v-else class="task-list"><view v-for="task in tasks" :key="task.taskId" class="panel task">
    <view class="task-top"><text class="type">{{ typeLabels[task.taskType] || task.taskType }}</text><text class="time" :class="{urgent:task.priority==='URGENT'}">截止 {{ task.dueDate }}</text></view>
    <text class="task-title">{{ task.title }}</text><text class="owner">{{ task.earTagNo ? `牛只 ${task.earTagNo} · ` : '' }}负责人 {{ task.assigneeName || '待分配' }}</text>
    <button v-if="!['COMPLETED','CANCELLED'].includes(task.status)" class="handle" @tap="complete(task)">完成任务</button>
  </view></view>
</view></template>

<style scoped>
.heading{display:flex;align-items:center;justify-content:space-between;margin-bottom:30rpx}.heading>view{display:flex;flex-direction:column}.date{color:#7b8783;font-size:23rpx}.title{font-size:42rpx;font-weight:700;margin-top:8rpx}.count{color:#176b52}.tabs{display:grid;grid-template-columns:repeat(3,1fr);border-bottom:1rpx solid #dce2df;margin-bottom:24rpx}.tabs button{background:transparent;height:76rpx;line-height:76rpx;color:#77827f;font-size:27rpx;border-radius:0}.tabs button.active{color:#176b52;border-bottom:4rpx solid #176b52;font-weight:600}.task-list{display:grid;gap:20rpx}.task{padding:26rpx}.task-top{display:flex;justify-content:space-between}.type{background:#e4f0eb;color:#176b52;padding:6rpx 14rpx;border-radius:6rpx;font-size:23rpx}.time{color:#65716d;font-size:24rpx}.time.urgent{color:#bb4c46}.task-title{display:block;margin-top:22rpx;font-size:32rpx;font-weight:600}.owner{display:block;margin-top:10rpx;color:#77837f;font-size:24rpx}.handle{margin:24rpx 0 0;height:72rpx;line-height:72rpx;background:#176b52;color:#fff;border-radius:8rpx;font-size:27rpx}.empty{text-align:center;color:#7b8783;padding:120rpx 0}
</style>
