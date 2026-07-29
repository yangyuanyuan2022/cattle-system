<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus, Select } from "@element-plus/icons-vue";
import { getBarns, getHerds, type Barn, type Herd } from "../api/location";
import { getAllCattle, type CattleRecord } from "../api/cattle";
import {
  createVaccinationPlan,
  executeVaccination,
  getVaccinationPlans,
  getVaccinationExecutions,
  cancelVaccinationPlan,
  voidVaccinationExecution,
  type VaccinationPlan,
  type VaccinationExecution,
} from "../api/vaccination";
import { getStoredUser } from "../auth/session";
import { formatDateTime } from "../utils/format";
const roles = getStoredUser()?.roles || [];
const canCreate = roles.some((role) =>
  ["ADMIN", "FARM_MANAGER", "VET"].includes(role),
);
const canExecute = roles.some((role) =>
  ["ADMIN", "FARM_MANAGER", "VET", "WORKER"].includes(role),
);
const plans = ref<VaccinationPlan[]>([]),
  executions = ref<VaccinationExecution[]>([]),
  barns = ref<Barn[]>([]),
  herds = ref<Herd[]>([]),
  cattle = ref<CattleRecord[]>([]),
  loading = ref(false),
  saving = ref(false),
  planOpen = ref(false),
  execOpen = ref(false),
  selected = ref<VaccinationPlan | null>(null),
  status = ref("");
const today = () => new Date().toISOString().slice(0, 10),
  now = () => new Date().toISOString().slice(0, 16);
const plan = reactive({
  planName: "",
  vaccineItem: "",
  planDate: today(),
  dueDate: today(),
  targetType: "BARN",
  targetObjectId: "",
  remark: "",
});
const execution = reactive({
  executionDate: now(),
  batchNo: "",
  remark: "",
  cattleIds: [] as string[],
});
const statusLabels: any = {
  NOT_STARTED: "未开始",
  IN_PROGRESS: "进行中",
  DONE: "已完成",
  OVERDUE: "已逾期",
  CANCELLED: "已取消",
};
const targetOptions = computed(() =>
  plan.targetType === "BARN"
    ? barns.value.map((x) => ({ id: x.barnId, name: x.barnName }))
    : plan.targetType === "HERD"
      ? herds.value.map((x) => ({ id: x.herdId, name: x.herdName }))
      : cattle.value.map((x) => ({
          id: x.cattleId,
          name: x.earTagNo + " " + (x.name || ""),
        })),
);
async function preload() {
  [barns.value, herds.value] = await Promise.all([
    getBarns("ENABLED"),
    getHerds("ENABLED"),
  ]);
  cattle.value = await getAllCattle({ presenceStatus: "IN_FIELD" });
}
async function load() {
  loading.value = true;
  try {
    [plans.value, executions.value] = await Promise.all([
      getVaccinationPlans(status.value || undefined),
      getVaccinationExecutions(),
    ]);
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || "防疫计划加载失败");
  } finally {
    loading.value = false;
  }
}
async function cancelPlan(row: VaccinationPlan) {
  try {
    const { value } = await ElMessageBox.prompt("请输入取消原因，取消后计划不可继续执行。", "取消防疫计划", { inputValidator: (v) => !!v.trim() || "必须填写取消原因", confirmButtonText: "确认取消", cancelButtonText: "返回" });
    await cancelVaccinationPlan(row.planId, row.version, value.trim());
    ElMessage.success("计划已取消");
    await load();
  } catch (e: any) {
    if (e !== "cancel" && e !== "close") ElMessage.error(e.response?.data?.message || "取消失败");
  }
}
async function voidExecution(row: VaccinationExecution) {
  try {
    const { value } = await ElMessageBox.prompt("请输入作废原因，相关计划进度将自动重算。", "作废执行记录", { inputValidator: (v) => !!v.trim() || "必须填写作废原因", confirmButtonText: "确认作废", cancelButtonText: "返回" });
    await voidVaccinationExecution(row.executionId, row.version, value.trim());
    ElMessage.success("执行记录已作废");
    await load();
  } catch (e: any) {
    if (e !== "cancel" && e !== "close") ElMessage.error(e.response?.data?.message || "作废失败");
  }
}
async function openPlan() {
  try {
    await preload();
    Object.assign(plan, {
      planName: "",
      vaccineItem: "",
      planDate: today(),
      dueDate: today(),
      targetType: "BARN",
      targetObjectId: "",
      remark: "",
    });
    planOpen.value = true;
  } catch {
    ElMessage.error("基础数据加载失败");
  }
}
async function savePlan() {
  if (!plan.planName || !plan.vaccineItem || !plan.targetObjectId)
    return ElMessage.warning("请完整填写计划和适用范围");
  saving.value = true;
  try {
    await createVaccinationPlan(
      {
        planName: plan.planName,
        vaccineItem: plan.vaccineItem,
        planDate: plan.planDate,
        dueDate: plan.dueDate,
        remark: plan.remark,
        targets: [
          { targetType: plan.targetType, targetObjectId: plan.targetObjectId },
        ],
      },
      crypto.randomUUID(),
    );
    planOpen.value = false;
    ElMessage.success("防疫计划创建成功");
    await load();
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || "计划创建失败");
  } finally {
    saving.value = false;
  }
}
async function openExecution(row: VaccinationPlan) {
  try {
    selected.value = row;
    await preload();
    Object.assign(execution, {
      executionDate: now(),
      batchNo: "",
      remark: "",
      cattleIds: [],
    });
    execOpen.value = true;
  } catch {
    ElMessage.error("牛只数据加载失败");
  }
}
async function saveExecution() {
  if (!selected.value || !execution.cattleIds.length)
    return ElMessage.warning("请选择实际执行牛只");
  saving.value = true;
  try {
    const result = await executeVaccination(
      {
        planId: selected.value.planId,
        executionDate: execution.executionDate + ":00",
        vaccineItem: selected.value.vaccineItem,
        batchNo: execution.batchNo,
        remark: execution.remark,
        cattle: execution.cattleIds.map((cattleId) => ({ cattleId })),
      },
      crypto.randomUUID(),
    );
    execOpen.value = false;
    ElMessage.success(`已完成 ${result.cattleCount} 头牛防疫登记`);
    await load();
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || "执行登记失败");
  } finally {
    saving.value = false;
  }
}
onMounted(load);
</script>
<template>
  <div>
    <div class="page-heading">
      <div>
        <p class="eyebrow">防疫管理</p>
        <h1>防疫计划</h1>
      </div>
      <el-button v-if="canCreate" type="primary" :icon="Plus" @click="openPlan"
        >创建计划</el-button
      >
    </div>
    <section class="filter-bar vaccine-filter">
      <el-select
        v-model="status"
        clearable
        placeholder="计划状态"
        @change="load"
        ><el-option
          v-for="(label, value) in statusLabels"
          :key="value"
          :label="label"
          :value="value"
      /></el-select>
    </section>
    <section class="table-panel">
      <el-table v-loading="loading" :data="plans" empty-text="暂无防疫计划"
        ><el-table-column prop="planName" label="计划名称" min-width="160"
          ><template #default="s"
            ><strong>{{ s.row.planName }}</strong
            ><small class="cell-sub">{{ s.row.vaccineItem }}</small></template
          ></el-table-column
        ><el-table-column label="计划周期" min-width="190"
          ><template #default="s"
            >{{ s.row.planDate }} 至 {{ s.row.dueDate }}</template
          ></el-table-column
        ><el-table-column label="进度" width="160"
          ><template #default="s"
            ><el-progress
              :percentage="
                s.row.targetCount
                  ? Math.min(
                      100,
                      Math.round(
                        (s.row.executedCount / s.row.targetCount) * 100,
                      ),
                    )
                  : 0
              "
              :stroke-width="8"
            /><small
              >{{ s.row.executedCount }} / {{ s.row.targetCount }} 头</small
            ></template
          ></el-table-column
        ><el-table-column label="状态" width="100"
          ><template #default="s"
            ><el-tag
              :type="
                s.row.status === 'DONE'
                  ? 'success'
                  : s.row.status === 'OVERDUE'
                    ? 'danger'
                    : 'warning'
              "
              effect="plain"
              >{{ statusLabels[s.row.status] }}</el-tag
            ></template
          ></el-table-column
        ><el-table-column label="操作" width="180"
          ><template #default="s"
            ><el-button
              v-if="canExecute && !['DONE', 'CANCELLED'].includes(s.row.status)"
              link
              type="primary"
              :icon="Select"
              @click="openExecution(s.row)"
              >登记执行</el-button
            ><el-button v-if="canCreate && !['DONE', 'CANCELLED'].includes(s.row.status)" link type="danger" @click="cancelPlan(s.row)">取消计划</el-button></template
          ></el-table-column
        ></el-table
      >
    </section>
    <section class="table-panel">
      <div class="panel-heading"><div><h2>执行记录</h2><p>实际防疫执行结果和涉及牛只，可作废并自动回算计划进度</p></div></div>
      <el-table :data="executions" empty-text="暂无执行记录">
        <el-table-column label="执行时间" min-width="180"><template #default="s">{{ formatDateTime(s.row.executionDate) }}</template></el-table-column>
        <el-table-column label="计划 / 项目" min-width="190"><template #default="s"><strong>{{ s.row.planName || '独立执行' }}</strong><small class="cell-sub">{{ s.row.vaccineItem }}</small></template></el-table-column>
        <el-table-column prop="batchNo" label="批号" min-width="110"><template #default="s">{{ s.row.batchNo || '-' }}</template></el-table-column>
        <el-table-column label="执行牛只" min-width="180"><template #default="s">{{ s.row.cattleCount }} 头<small class="cell-sub">{{ s.row.cattleSummary }}</small></template></el-table-column>
        <el-table-column prop="executorName" label="执行人" min-width="100" />
        <el-table-column v-if="canCreate" label="操作" width="90" fixed="right"><template #default="s"><el-button link type="danger" @click="voidExecution(s.row)">作废</el-button></template></el-table-column>
      </el-table>
    </section>
    <el-dialog v-model="planOpen" title="创建防疫计划" width="min(580px,92vw)"
      ><el-form label-position="top" class="vaccine-form"
        ><el-form-item label="计划名称" required
          ><el-input v-model="plan.planName" /></el-form-item
        ><el-form-item label="疫苗 / 项目" required
          ><el-input v-model="plan.vaccineItem" /></el-form-item
        ><el-form-item label="计划日期"
          ><el-date-picker
            v-model="plan.planDate"
            type="date"
            value-format="YYYY-MM-DD" /></el-form-item
        ><el-form-item label="截止日期"
          ><el-date-picker
            v-model="plan.dueDate"
            type="date"
            value-format="YYYY-MM-DD" /></el-form-item
        ><el-form-item label="范围类型"
          ><el-segmented
            v-model="plan.targetType"
            :options="[
              { label: '栏舍', value: 'BARN' },
              { label: '牛群', value: 'HERD' },
              { label: '指定牛', value: 'CATTLE' },
            ]"
            @change="plan.targetObjectId = ''" /></el-form-item
        ><el-form-item label="适用范围" required
          ><el-select v-model="plan.targetObjectId" filterable
            ><el-option
              v-for="o in targetOptions"
              :key="o.id"
              :label="o.name"
              :value="o.id" /></el-select></el-form-item
        ><el-form-item label="备注" class="wide"
          ><el-input
            v-model="plan.remark"
            type="textarea" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="planOpen = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="savePlan"
          >创建计划</el-button
        ></template
      ></el-dialog
    >
    <el-dialog v-model="execOpen" title="登记防疫执行" width="min(600px,94vw)"
      ><el-alert
        v-if="selected"
        :title="`${selected.planName} · ${selected.vaccineItem}`"
        type="info"
        :closable="false"
      /><el-form label-position="top" class="execution-form"
        ><el-form-item label="执行时间"
          ><el-date-picker
            v-model="execution.executionDate"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm" /></el-form-item
        ><el-form-item label="疫苗批号"
          ><el-input v-model="execution.batchNo" /></el-form-item
        ><el-form-item label="实际执行牛只" class="wide" required
          ><el-select
            v-model="execution.cattleIds"
            multiple
            filterable
            collapse-tags
            :max-collapse-tags="3"
            ><el-option
              v-for="c in cattle"
              :key="c.cattleId"
              :label="`${c.earTagNo} ${c.name || ''}`"
              :value="c.cattleId" /></el-select></el-form-item
        ><el-form-item label="备注" class="wide"
          ><el-input
            v-model="execution.remark"
            type="textarea" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="execOpen = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="saveExecution"
          >确认执行</el-button
        ></template
      ></el-dialog
    >
  </div>
</template>
<style scoped>
.vaccine-filter {
  grid-template-columns: minmax(180px, 260px);
}
.cell-sub {
  display: block;
  color: #7d8885;
  margin-top: 4px;
}
.vaccine-form,
.execution-form {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
  margin-top: 16px;
}
.wide {
  grid-column: 1/-1;
}
.el-select,
.el-date-editor {
  width: 100%;
}
@media (max-width: 640px) {
  .vaccine-form,
  .execution-form {
    grid-template-columns: 1fr;
  }
  .wide {
    grid-column: auto;
  }
}
</style>
