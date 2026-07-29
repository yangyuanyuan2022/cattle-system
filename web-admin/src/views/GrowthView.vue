<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus } from "@element-plus/icons-vue";
import { getAllCattle, type CattleRecord } from "../api/cattle";
import {
  getGrowthTrend,
  getHerdGrowthTrend,
  recordBodyCondition,
  recordWeight,
  voidGrowthRecord,
  type WeightItem,
  type BodyConditionItem,
  type GrowthTrend,
  type HerdGrowthTrend,
} from "../api/growth";
import { getHerds, type Herd } from "../api/location";
import { getStoredUser } from "../auth/session";
import { formatDate } from "../utils/format";
const canWrite =
  getStoredUser()?.roles.some((role) =>
    ["ADMIN", "FARM_MANAGER", "WORKER"].includes(role),
  ) ?? false;
const cattle = ref<CattleRecord[]>([]),
  herds = ref<Herd[]>([]),
  cattleId = ref(""),
  herdId = ref(""),
  viewMode = ref("cattle"),
  trend = ref<GrowthTrend | null>(null),
  herdTrend = ref<HerdGrowthTrend | null>(null),
  loading = ref(false),
  saving = ref(false),
  weightOpen = ref(false),
  conditionOpen = ref(false);
const now = () => new Date().toISOString().slice(0, 16);
const weight = reactive({
    measureDate: now(),
    weightKg: 500,
    measureMethod: "SCALE",
    remark: "",
  }),
  condition = reactive({ scoreDate: now(), score: 3, remark: "" });
const latestWeight = computed(() => {
    const rows = trend.value?.weights || [];
    return rows[rows.length - 1];
  }),
  latestCondition = computed(() => {
    const rows = trend.value?.bodyConditions || [];
    return rows[rows.length - 1];
  });
const chartPoints = computed(() => {
  const rows = trend.value?.weights || [];
  if (!rows.length) return "";
  const values = rows.map((x) => Number(x.weightKg)),
    min = Math.min(...values),
    max = Math.max(...values),
    range = Math.max(1, max - min);
  return rows
    .map(
      (x, i) =>
        `${30 + i * (640 / Math.max(1, rows.length - 1))},${190 - ((Number(x.weightKg) - min) / range) * 150}`,
    )
    .join(" ");
});
const herdChartPoints = computed(() => {
  const rows = herdTrend.value?.weights || [];
  if (!rows.length) return "";
  const values = rows.map((row) => Number(row.averageWeightKg));
  const min = Math.min(...values);
  const max = Math.max(...values);
  const range = Math.max(1, max - min);
  return rows
    .map(
      (row, index) =>
        `${30 + index * (640 / Math.max(1, rows.length - 1))},${190 - ((Number(row.averageWeightKg) - min) / range) * 150}`,
    )
    .join(" ");
});
async function init() {
  try {
    [cattle.value, herds.value] = await Promise.all([getAllCattle({ presenceStatus: "IN_FIELD" }), getHerds("ENABLED")]);
    if (herds.value.length) herdId.value = herds.value[0].herdId;
    if (cattle.value.length) {
      cattleId.value = cattle.value[0].cattleId;
      await load();
    }
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || "生长数据加载失败");
  }
}
async function loadHerd() {
  if (!herdId.value) return;
  loading.value = true;
  try { herdTrend.value = await getHerdGrowthTrend(herdId.value); }
  catch (e:any) { ElMessage.error(e.response?.data?.message || "牛群趋势加载失败"); }
  finally { loading.value = false; }
}
async function changeMode() { if (viewMode.value === "herd") await loadHerd(); }
async function voidRecord(kind: "weights" | "body-conditions", row: WeightItem | BodyConditionItem) {
  try {
    const title = kind === "weights" ? "作废称重记录" : "作废体况记录";
    const { value } = await ElMessageBox.prompt("请输入作废原因，操作将保留在审计日志中。", title, { inputValidator: (v) => !!v.trim() || "必须填写作废原因", confirmButtonText: "确认作废", cancelButtonText: "取消" });
    const id = kind === "weights" ? (row as WeightItem).weightId : (row as BodyConditionItem).bodyConditionId;
    await voidGrowthRecord(kind, id, row.version, value.trim());
    ElMessage.success("记录已作废");
    await load();
  } catch (e: any) {
    if (e !== "cancel" && e !== "close") ElMessage.error(e.response?.data?.message || "作废失败");
  }
}
async function load() {
  if (!cattleId.value) return;
  loading.value = true;
  try {
    trend.value = await getGrowthTrend(cattleId.value);
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || "趋势加载失败");
  } finally {
    loading.value = false;
  }
}
async function saveWeight() {
  if (weight.weightKg <= 0) return ElMessage.warning("体重必须大于0");
  saving.value = true;
  try {
    const result = await recordWeight(
      {
        cattleId: cattleId.value,
        measureDate: weight.measureDate + ":00",
        weightKg: weight.weightKg,
        measureMethod: weight.measureMethod,
        remark: weight.remark,
      },
      crypto.randomUUID(),
    );
    weightOpen.value = false;
    result.abnormal
      ? ElMessage.warning(result.warning || "体重数据异常，请复核")
      : ElMessage.success("称重记录已保存");
    await load();
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || "称重登记失败");
  } finally {
    saving.value = false;
  }
}
async function saveCondition() {
  saving.value = true;
  try {
    await recordBodyCondition(
      {
        cattleId: cattleId.value,
        scoreDate: condition.scoreDate + ":00",
        score: condition.score,
        remark: condition.remark,
      },
      crypto.randomUUID(),
    );
    conditionOpen.value = false;
    ElMessage.success("体况评分已保存");
    await load();
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || "体况登记失败");
  } finally {
    saving.value = false;
  }
}
function openWeight() {
  Object.assign(weight, {
    measureDate: now(),
    weightKg: latestWeight.value?.weightKg || 500,
    measureMethod: "SCALE",
    remark: "",
  });
  weightOpen.value = true;
}
function openCondition() {
  Object.assign(condition, {
    scoreDate: now(),
    score: latestCondition.value?.score || 3,
    remark: "",
  });
  conditionOpen.value = true;
}
onMounted(init);
</script>
<template>
  <div>
    <div class="page-heading">
      <div>
        <p class="eyebrow">生长管理</p>
        <h1>{{ viewMode === "cattle" ? "单牛生长趋势" : "牛群平均体重趋势" }}</h1>
      </div>
      <div v-if="canWrite && viewMode === 'cattle'" class="filter-actions">
        <el-button :icon="Plus" :disabled="!cattleId" @click="openCondition"
          >体况评分</el-button
        ><el-button
          type="primary"
          :icon="Plus"
          :disabled="!cattleId"
          @click="openWeight"
          >登记称重</el-button
        >
      </div>
    </div>
    <el-segmented v-model="viewMode" :options="[{label:'单牛趋势',value:'cattle'},{label:'牛群平均趋势',value:'herd'}]" class="growth-mode" @change="changeMode" />
    <section v-if="viewMode === 'cattle'" class="growth-selector">
      <label>查看牛只</label
      ><el-select v-model="cattleId" filterable @change="load"
        ><el-option
          v-for="c in cattle"
          :key="c.cattleId"
          :label="`${c.earTagNo} ${c.name || ''}`"
          :value="c.cattleId"
      /></el-select>
    </section>
    <section v-else class="growth-selector"><label>查看牛群</label><el-select v-model="herdId" filterable @change="loadHerd"><el-option v-for="h in herds" :key="h.herdId" :label="h.herdName" :value="h.herdId" /></el-select></section>
    <section v-if="viewMode === 'herd'" v-loading="loading" class="table-panel herd-trend-panel">
      <div class="panel-heading"><div><h2>{{ herdTrend?.herdName || '牛群' }}平均体重趋势</h2><p>按称重日期聚合可见牛只的平均体重</p></div></div>
      <div v-if="herdTrend?.weights.length" class="chart-wrap herd-chart">
        <svg viewBox="0 0 700 220" role="img" aria-label="牛群平均体重变化曲线">
          <line x1="30" y1="190" x2="670" y2="190" class="axis" />
          <polyline :points="herdChartPoints" class="weight-line" />
          <circle
            v-for="(row, index) in herdTrend.weights"
            :key="row.measureDate"
            :cx="30 + index * (640 / Math.max(1, herdTrend.weights.length - 1))"
            :cy="Number(herdChartPoints.split(' ')[index].split(',')[1])"
            r="5"
            class="point"
          >
            <title>{{ formatDate(row.measureDate) }}：{{ row.averageWeightKg }} kg，{{ row.cattleCount }} 头</title>
          </circle>
        </svg>
      </div>
      <el-table :data="herdTrend?.weights || []" empty-text="该牛群暂无称重记录"><el-table-column label="日期" min-width="180"><template #default="s">{{ formatDate(s.row.measureDate) }}</template></el-table-column><el-table-column prop="averageWeightKg" label="平均体重(kg)" min-width="140" /><el-table-column prop="cattleCount" label="参与牛只数" min-width="120" /></el-table>
    </section>
    <div v-if="viewMode === 'cattle'" v-loading="loading">
      <section class="growth-stats">
        <div>
          <span>最新体重</span
          ><strong>{{
            latestWeight ? latestWeight.weightKg + " kg" : "-"
          }}</strong
          ><small v-if="latestWeight?.changeKg != null"
            >较上次 {{ latestWeight.changeKg > 0 ? "+" : ""
            }}{{ latestWeight.changeKg }} kg</small
          >
        </div>
        <div>
          <span>平均日增重</span
          ><strong>{{
            latestWeight?.averageDailyGain != null
              ? latestWeight.averageDailyGain + " kg"
              : "-"
          }}</strong
          ><small>按相邻称重日期计算</small>
        </div>
        <div>
          <span>最新体况</span
          ><strong>{{ latestCondition?.score ?? "-" }}</strong
          ><small>评分范围 1 至 5</small>
        </div>
      </section>
      <section class="growth-chart">
        <div class="panel-heading">
          <div>
            <h2>体重曲线</h2>
            <p>按实际称重日期排序</p>
          </div>
        </div>
        <div v-if="trend?.weights.length" class="chart-wrap">
          <svg viewBox="0 0 700 220" role="img" aria-label="体重变化曲线">
            <line x1="30" y1="190" x2="670" y2="190" class="axis" />
            <polyline :points="chartPoints" class="weight-line" />
            <circle
              v-for="(row, i) in trend.weights"
              :key="row.weightId"
              :cx="30 + i * (640 / Math.max(1, trend.weights.length - 1))"
              :cy="Number(chartPoints.split(' ')[i].split(',')[1])"
              r="5"
              :class="row.abnormal ? 'point abnormal' : 'point'"
            >
              <title>{{ row.measureDate }}：{{ row.weightKg }} kg</title>
            </circle>
          </svg>
        </div>
        <el-empty v-else description="尚无称重记录" :image-size="80" />
      </section>
      <div class="growth-lists">
        <section class="table-panel">
          <div class="panel-heading"><h2>称重记录</h2></div>
          <el-table :data="[...(trend?.weights || [])].reverse()"
            ><el-table-column
              prop="measureDate"
              label="日期"
              min-width="165"
            /><el-table-column
              prop="weightKg"
              label="体重(kg)"
            /><el-table-column label="变化"
              ><template #default="s">{{
                s.row.changeKg == null ? "-" : s.row.changeKg
              }}</template></el-table-column
            ><el-table-column label="校验"
              ><template #default="s"
                ><el-tooltip v-if="s.row.abnormal" :content="s.row.warning"
                  ><el-tag type="warning">需复核</el-tag></el-tooltip
                ><span v-else>正常</span></template
              ></el-table-column><el-table-column v-if="canWrite" label="操作" width="80" fixed="right"><template #default="s"><el-button link type="danger" @click="voidRecord('weights', s.row)">作废</el-button></template></el-table-column
            ></el-table
          >
        </section>
        <section class="table-panel">
          <div class="panel-heading"><h2>体况历史</h2></div>
          <el-table :data="[...(trend?.bodyConditions || [])].reverse()"
            ><el-table-column
              prop="scoreDate"
              label="日期"
              min-width="165"
            /><el-table-column prop="score" label="评分" /><el-table-column
              prop="remark"
              label="备注"
              ><template #default="s">{{
                s.row.remark || "-"
              }}</template></el-table-column><el-table-column v-if="canWrite" label="操作" width="80" fixed="right"><template #default="s"><el-button link type="danger" @click="voidRecord('body-conditions', s.row)">作废</el-button></template></el-table-column
            ></el-table
          >
        </section>
      </div>
    </div>
    <el-dialog v-model="weightOpen" title="登记称重" width="min(500px,92vw)"
      ><el-form label-position="top"
        ><el-form-item label="称重时间"
          ><el-date-picker
            v-model="weight.measureDate"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm" /></el-form-item
        ><el-form-item label="体重（kg）" required
          ><el-input-number
            v-model="weight.weightKg"
            :min="0.01"
            :precision="2" /></el-form-item
        ><el-form-item label="测量方式"
          ><el-select v-model="weight.measureMethod"
            ><el-option label="电子秤" value="SCALE" /><el-option
              label="胸围估重"
              value="TAPE_ESTIMATE" /><el-option
              label="其他"
              value="OTHER" /></el-select></el-form-item
        ><el-form-item label="备注"
          ><el-input
            v-model="weight.remark"
            type="textarea" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="weightOpen = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="saveWeight"
          >保存</el-button
        ></template
      ></el-dialog
    >
    <el-dialog v-model="conditionOpen" title="体况评分" width="min(500px,92vw)"
      ><el-form label-position="top"
        ><el-form-item label="评分时间"
          ><el-date-picker
            v-model="condition.scoreDate"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm" /></el-form-item
        ><el-form-item label="体况评分"
          ><el-slider
            v-model="condition.score"
            :min="1"
            :max="5"
            :step="0.5"
            show-stops /></el-form-item
        ><el-form-item label="备注"
          ><el-input
            v-model="condition.remark"
            type="textarea" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="conditionOpen = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="saveCondition"
          >保存</el-button
        ></template
      ></el-dialog
    >
  </div>
</template>
<style scoped>
.growth-selector {
  display: flex;
  align-items: center;
  gap: 14px;
  background: #fff;
  border: 1px solid #dde3df;
  padding: 15px 18px;
  margin-bottom: 16px;
}
.growth-mode { margin-bottom: 14px; }
.growth-selector label {
  font-size: 13px;
  color: #697570;
}
.growth-selector .el-select {
  width: min(360px, 70vw);
}
.growth-stats {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  background: #fff;
  border: 1px solid #dde3df;
  margin-bottom: 16px;
}
.growth-stats > div {
  padding: 18px;
  display: grid;
  gap: 7px;
  border-right: 1px solid #e4e8e6;
}
.growth-stats > div:last-child {
  border: 0;
}
.growth-stats span,
.growth-stats small {
  color: #75817d;
}
.growth-stats strong {
  font-size: 24px;
}
.growth-chart {
  background: #fff;
  border: 1px solid #dde3df;
  margin-bottom: 16px;
}
.chart-wrap {
  height: 260px;
  padding: 12px;
}
.chart-wrap svg {
  width: 100%;
  height: 100%;
}
.herd-chart {
  border-top: 1px solid #eef1ef;
  border-bottom: 1px solid #eef1ef;
}
.axis {
  stroke: #d9e0dc;
  stroke-width: 2;
}
.weight-line {
  fill: none;
  stroke: #238060;
  stroke-width: 4;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.point {
  fill: #fff;
  stroke: #238060;
  stroke-width: 3;
}
.point.abnormal {
  stroke: #c86842;
  fill: #fff2ed;
}
.growth-lists {
  display: grid;
  grid-template-columns: 1.2fr 0.8fr;
  gap: 16px;
}
.el-date-editor,
.el-select {
  width: 100%;
}
@media (max-width: 760px) {
  .growth-stats,
  .growth-lists {
    grid-template-columns: 1fr;
  }
  .growth-stats > div {
    border-right: 0;
    border-bottom: 1px solid #e4e8e6;
  }
}
</style>
