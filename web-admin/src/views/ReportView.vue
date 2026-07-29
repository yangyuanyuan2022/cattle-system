<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Download, Refresh } from '@element-plus/icons-vue'
import { createReportExport, downloadReportExport, getBreedingReport, getHealthReport, getInventoryReport, getReportExports, getReportOverview, getTaskReport, type ExportItem, type Metric, type ReportOverview } from '../api/report'
import { getStoredUser } from '../auth/session'

const user = getStoredUser()
const canExport = computed(() => user?.roles.some((role) => ['ADMIN', 'FARM_MANAGER'].includes(role)) ?? false)
const today = new Date()
const monthStart = new Date(today.getFullYear(), today.getMonth(), 1)
const dates = ref<[string,string]>([monthStart.toISOString().slice(0,10), today.toISOString().slice(0,10)])
const data = ref<ReportOverview | null>(null)
const loading = ref(false)
const exporting = ref(false)
const exports = ref<ExportItem[]>([])
const sections = computed(() => data.value ? [
  { title: '牛只变动', items: data.value.movements },
  { title: '繁育成效', items: data.value.breeding },
  { title: '健康防疫', items: data.value.healthVaccination },
  ...(data.value.feeding.length ? [{ title: '配料执行', items: data.value.feeding }] : []),
  { title: '任务履约', items: data.value.tasks },
] : [])
const maxStage = computed(() => Math.max(1, ...(data.value?.lifecycleStages.map((item) => item.value) || [])))
const maxHerd = computed(() => Math.max(1, ...(data.value?.herds.map((item) => item.value) || [])))
const stageLabels: Record<string,string> = { CALF:'犊牛', GROWING:'育成牛', RESERVE:'后备牛', COW:'成母牛', BULL:'种公牛' }

async function load() {
  loading.value = true
  try {
    if (canExport.value) {
      [data.value, exports.value] = await Promise.all([getReportOverview(dates.value[0], dates.value[1]), getReportExports()])
    } else {
      const [inventory, breeding, health, tasks] = await Promise.all([
        getInventoryReport(dates.value[0], dates.value[1]),
        getBreedingReport(dates.value[0], dates.value[1]),
        getHealthReport(dates.value[0], dates.value[1]),
        getTaskReport(dates.value[0], dates.value[1]),
      ])
      data.value = { startDate: inventory.startDate, endDate: inventory.endDate, inventory: inventory.metrics, lifecycleStages: inventory.lifecycleStages, herds: inventory.herds, movements: inventory.movements, breeding: breeding.metrics, healthVaccination: health.metrics, feeding: [], tasks: tasks.metrics }
      exports.value = []
    }
  } catch (error: any) { ElMessage.error(error.response?.data?.message || '报表加载失败') }
  finally { loading.value = false }
}

async function exportExcel() {
  exporting.value = true
  try {
    const item = await createReportExport(dates.value[0], dates.value[1])
    exports.value = await getReportExports()
    item.status === 'SUCCESS' ? ElMessage.success('Excel 报表已生成') : ElMessage.error(item.failReason || '导出失败')
  } catch (error: any) { ElMessage.error(error.response?.data?.message || '导出失败') }
  finally { exporting.value = false }
}
const formatMetric = (item: Metric) => Number(item.value).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
onMounted(load)
</script>

<template>
  <div>
    <div class="page-heading">
      <div><p class="eyebrow">生产经营分析</p><h1>报表中心</h1></div>
      <div class="actions"><el-date-picker v-model="dates" type="daterange" value-format="YYYY-MM-DD" range-separator="至" /><el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button><el-button v-if="canExport" type="primary" :icon="Download" :loading="exporting" @click="exportExcel">导出 Excel</el-button></div>
    </div>
    <section v-if="data" v-loading="loading">
      <div class="inventory"><article v-for="item in data.inventory" :key="item.code" :class="{ alert: item.code === 'HEALTH_ALERT' && item.value > 0 }"><span>{{ item.label }}</span><strong>{{ formatMetric(item) }}</strong><small>{{ item.unit }}</small></article></div>
      <div class="report-grid">
        <article v-for="section in sections" :key="section.title" class="report-panel"><header><h2>{{ section.title }}</h2><span>{{ data.startDate }} 至 {{ data.endDate }}</span></header><div class="metric-grid"><div v-for="item in section.items" :key="item.code"><span>{{ item.label }}</span><strong>{{ formatMetric(item) }} <small>{{ item.unit }}</small></strong></div></div></article>
        <article class="report-panel"><header><h2>生命周期结构</h2></header><div v-for="item in data.lifecycleStages" :key="item.code" class="bar-row"><span>{{ stageLabels[item.code] || item.label }}</span><i><b :style="{ width: `${item.value / maxStage * 100}%` }" /></i><strong>{{ item.value }} 头</strong></div></article>
        <article class="report-panel"><header><h2>牛群分布</h2></header><div v-for="item in data.herds" :key="item.code" class="bar-row"><span>{{ item.label }}</span><i><b :style="{ width: `${item.value / maxHerd * 100}%` }" /></i><strong>{{ item.value }} 头</strong></div></article>
      </div>
      <article v-if="canExport" class="report-panel exports"><header><div><h2>导出记录</h2><p>文件保留 7 天，下载行为写入审计日志</p></div></header><el-table :data="exports" empty-text="暂无导出记录"><el-table-column prop="fileName" label="文件" min-width="280" /><el-table-column prop="createdAt" label="创建时间" width="180" /><el-table-column prop="rowCount" label="数据行" width="85" /><el-table-column label="状态" width="100"><template #default="{ row }"><el-tag :type="row.status === 'SUCCESS' ? 'success' : row.status === 'FAILED' ? 'danger' : 'info'">{{ row.status }}</el-tag></template></el-table-column><el-table-column label="操作" width="100"><template #default="{ row }"><el-button v-if="row.status === 'SUCCESS'" link type="primary" @click="downloadReportExport(row)">下载</el-button><span v-else>{{ row.failReason || '-' }}</span></template></el-table-column></el-table></article>
    </section>
  </div>
</template>

<style scoped>
.actions{display:flex;gap:10px}.inventory{display:grid;grid-template-columns:repeat(4,1fr);background:#fff;border:1px solid #dce3df;margin-bottom:16px}.inventory article{padding:18px;border-right:1px solid #e5e9e7}.inventory span,.inventory small{color:#75817d}.inventory strong{font-size:28px;margin:0 7px 0 14px}.inventory .alert strong{color:#b54f49}.report-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:16px}.report-panel{background:#fff;border:1px solid #dce3df;padding:18px}.report-panel header{display:flex;justify-content:space-between;align-items:start;border-bottom:1px solid #e7ebe9;padding-bottom:12px;margin-bottom:14px}.report-panel h2{font-size:17px;margin:0}.report-panel p,.report-panel header>span{color:#75817d;font-size:12px;margin:4px 0 0}.metric-grid{display:grid;grid-template-columns:1fr 1fr}.metric-grid>div{padding:12px;border-right:1px solid #edf0ee;border-bottom:1px solid #edf0ee}.metric-grid span{display:block;color:#75817d}.metric-grid strong{display:block;font-size:22px;margin-top:7px}.metric-grid small{font-size:12px;color:#75817d}.bar-row{display:grid;grid-template-columns:110px 1fr 60px;gap:12px;align-items:center;margin:13px 0}.bar-row i{height:8px;background:#edf1ef;overflow:hidden}.bar-row b{display:block;height:100%;background:#34815e}.bar-row strong{text-align:right;font-size:13px}.exports{margin-top:16px}.exports header{margin-bottom:0}@media(max-width:800px){.actions{width:100%;flex-wrap:wrap}.inventory{grid-template-columns:1fr 1fr}.report-grid{grid-template-columns:1fr}}@media(max-width:480px){.inventory article{padding:12px}.inventory strong{font-size:22px;margin-left:5px}}
</style>
