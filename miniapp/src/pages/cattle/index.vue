<script setup lang="ts">
import { ref } from 'vue'
import { request } from '../../api/request'

interface CattleItem { cattleId:string; earTagNo:string; name:string|null; sex:string; lifecycleStage:string; presenceStatus:string; healthStatus:string }
interface CattlePage { total:number; items:CattleItem[] }
const keyword = ref('')
const loading = ref(false)
const searched = ref(false)
const cattle = ref<CattleItem[]>([])
const healthLabels:Record<string,string>={ NORMAL:'健康正常', OBSERVING:'观察中', TREATING:'治疗中' }
const stageLabels:Record<string,string>={ CALF:'犊牛', GROWING:'育成牛', RESERVE:'后备牛', COW:'母牛', BULL:'种公牛' }

async function search() {
  loading.value = true; searched.value = true
  try {
    const query = encodeURIComponent(keyword.value.trim())
    const result = await request<CattlePage>({ url:`/cattle?page=1&pageSize=20&keyword=${query}`, method:'GET' })
    cattle.value = result.items
  } catch (error) { uni.showToast({ title:error instanceof Error ? error.message : '牛只加载失败', icon:'none' }) }
  finally { loading.value = false }
}
function scan() {
  uni.scanCode({ scanType:['qrCode'], success:({result}) => {
    const match = result.match(/(?:cattleId=|cattle\/)(\d+)/)
    keyword.value = match?.[1] || result
    search()
  } })
}
</script>

<template><view class="safe-page">
  <text class="title">查找牛只</text>
  <view class="search panel"><input v-model="keyword" placeholder="输入耳号或牛只名称" confirm-type="search" @confirm="search" /><button @tap="search">查询</button><button class="scan" @tap="scan">扫码</button></view>
  <view v-if="loading" class="empty">正在查询...</view><view v-else-if="searched && !cattle.length" class="empty">未找到匹配牛只</view>
  <view v-else class="results"><view v-for="item in cattle" :key="item.cattleId" class="panel cattle">
    <view class="photo">牛</view><view class="detail"><text class="tag">{{ item.earTagNo }} {{ item.name || '' }}</text><text class="meta">{{ item.sex==='MALE'?'公牛':'母牛' }} · {{ stageLabels[item.lifecycleStage] || item.lifecycleStage }}</text><text class="status" :class="{warning:item.healthStatus!=='NORMAL'}">{{ item.presenceStatus==='IN_FIELD'?'在场':'已离场' }} · {{ healthLabels[item.healthStatus] || item.healthStatus }}</text></view>
  </view></view>
</view></template>

<style scoped>
.title{display:block;font-size:42rpx;font-weight:700;margin-bottom:28rpx}.search{display:grid;grid-template-columns:1fr 112rpx 112rpx;align-items:center;overflow:hidden}.search input{height:88rpx;padding-left:24rpx}.search button{height:88rpx;line-height:88rpx;background:#176b52;color:#fff;border-radius:0;font-size:25rpx}.search .scan{background:#365e52;border-left:1rpx solid #fff}.results{display:grid;gap:18rpx;margin-top:28rpx}.cattle{padding:22rpx;display:flex;gap:22rpx}.photo{width:104rpx;height:104rpx;background:#dfe7e3;display:flex;align-items:center;justify-content:center;color:#52625d;border-radius:8rpx}.detail{display:flex;flex-direction:column;gap:8rpx;min-width:0}.tag{font-size:32rpx;font-weight:700}.meta,.status{color:#74807c;font-size:23rpx}.status{color:#27745b}.status.warning{color:#b04e48}.empty{text-align:center;color:#7b8783;padding:120rpx 0}
</style>
