<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus, FirstAidKit } from "@element-plus/icons-vue";
import { getAllCattle, type CattleRecord } from "../api/cattle";
import {
  createFollowUp,
  createTreatment,
  getHealthCases,
  getHealthCaseDetail,
  voidHealthRecord,
  reportAbnormality,
  type HealthCase,
  type HealthCaseDetail,
} from "../api/health";
import { getStoredUser } from "../auth/session";
import { formatDateTime } from "../utils/format";
const roles = getStoredUser()?.roles || [];
const canReport = roles.some((role) =>
  ["ADMIN", "FARM_MANAGER", "VET", "WORKER"].includes(role),
);
const canTreat = roles.some((role) =>
  ["ADMIN", "FARM_MANAGER", "VET"].includes(role),
);
const cases = ref<HealthCase[]>([]),
  cattle = ref<CattleRecord[]>([]),
  loading = ref(false),
  saving = ref(false),
  reportOpen = ref(false),
  treatOpen = ref(false),
  followOpen = ref(false),
  detailOpen = ref(false),
  detail = ref<HealthCaseDetail | null>(null),
  selected = ref<HealthCase | null>(null),
  status = ref(String(useRoute().query.status || ""));
const now = () => new Date().toISOString().slice(0, 16);
const report = reactive({
    cattleId: "",
    discoverDate: now(),
    symptom: "",
    severity: "NORMAL",
  }),
  treatment = reactive({
    treatmentDate: now(),
    diagnosis: "",
    treatmentPlan: "",
    needFollowUp: false,
    followUpDate: "",
    medicineName: "",
    dosage: undefined as number | undefined,
    unit: "ml",
    usageMethod: "",
    withdrawalDays: undefined as number | undefined,
  }),
  follow = reactive({
    followUpDate: now(),
    result: "CONTINUE_TREATMENT",
    description: "",
  });
const severityLabels: any = { NORMAL: "一般", SERIOUS: "严重", URGENT: "紧急" },
  healthLabels: any = {
    NORMAL: "正常",
    OBSERVING: "观察中",
    TREATING: "治疗中",
  };
const followUpLabels: Record<string, string> = {
  CONTINUE_TREATMENT: "继续治疗",
  OBSERVE: "转为观察",
  RECOVERED: "康复结案",
};
async function load() {
  loading.value = true;
  try {
    cases.value = await getHealthCases(status.value || undefined);
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || "健康病例加载失败");
  } finally {
    loading.value = false;
  }
}
async function openDetail(row: HealthCase) {
  selected.value = row;
  try {
    detail.value = await getHealthCaseDetail(row.caseId);
    detailOpen.value = true;
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || "病例详情加载失败");
  }
}
async function voidRecord(kind: "cases" | "treatments" | "follow-ups", id: string, version: number) {
  try {
    const { value } = await ElMessageBox.prompt("请输入作废原因。有关联记录时请按时间倒序作废。", "作废健康记录", { inputValidator: (v) => !!v.trim() || "必须填写作废原因", confirmButtonText: "确认作废", cancelButtonText: "取消" });
    await voidHealthRecord(kind, id, version, value.trim());
    ElMessage.success("记录已作废");
    await load();
    if (kind !== "cases" && selected.value) detail.value = await getHealthCaseDetail(selected.value.caseId);
    else detailOpen.value = false;
  } catch (e: any) {
    if (e !== "cancel" && e !== "close") ElMessage.error(e.response?.data?.message || "作废失败");
  }
}
async function openReport() {
  try {
    cattle.value = await getAllCattle({ presenceStatus: "IN_FIELD" });
    Object.assign(report, {
      cattleId: "",
      discoverDate: now(),
      symptom: "",
      severity: "NORMAL",
    });
    reportOpen.value = true;
  } catch {
    ElMessage.error("牛只档案加载失败");
  }
}
function openTreat(row: HealthCase) {
  selected.value = row;
  Object.assign(treatment, {
    treatmentDate: now(),
    diagnosis: "",
    treatmentPlan: "",
    needFollowUp: false,
    followUpDate: "",
    medicineName: "",
    dosage: undefined,
    unit: "ml",
    usageMethod: "",
    withdrawalDays: undefined,
  });
  treatOpen.value = true;
}
function openFollow(row: HealthCase) {
  selected.value = row;
  Object.assign(follow, {
    followUpDate: now(),
    result: "CONTINUE_TREATMENT",
    description: "",
  });
  followOpen.value = true;
}
async function saveReport() {
  if (!report.cattleId || !report.symptom)
    return ElMessage.warning("请选择牛只并填写症状");
  saving.value = true;
  try {
    await reportAbnormality(
      { ...report, discoverDate: report.discoverDate + ":00" },
      crypto.randomUUID(),
    );
    reportOpen.value = false;
    ElMessage.success("健康异常已上报，牛只转为观察中");
    await load();
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || "上报失败");
  } finally {
    saving.value = false;
  }
}
async function saveTreatment() {
  if (!selected.value || !treatment.diagnosis)
    return ElMessage.warning("请填写诊断");
  saving.value = true;
  try {
    const meds = treatment.medicineName
      ? [
          {
            medicineName: treatment.medicineName,
            dosage: treatment.dosage,
            unit: treatment.unit,
            usageMethod: treatment.usageMethod,
            withdrawalDays: treatment.withdrawalDays,
          },
        ]
      : [];
    await createTreatment(
      {
        caseId: selected.value.caseId,
        treatmentDate: treatment.treatmentDate + ":00",
        diagnosis: treatment.diagnosis,
        treatmentPlan: treatment.treatmentPlan,
        needFollowUp: treatment.needFollowUp,
        followUpDate: treatment.followUpDate || null,
        medications: meds,
      },
      crypto.randomUUID(),
    );
    treatOpen.value = false;
    ElMessage.success("诊疗已登记，牛只转为治疗中");
    await load();
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || "诊疗登记失败");
  } finally {
    saving.value = false;
  }
}
async function saveFollow() {
  if (!selected.value) return;
  saving.value = true;
  try {
    await createFollowUp(
      {
        caseId: selected.value.caseId,
        followUpDate: follow.followUpDate + ":00",
        result: follow.result,
        description: follow.description,
      },
      crypto.randomUUID(),
    );
    followOpen.value = false;
    ElMessage.success(
      follow.result === "RECOVERED" ? "病例已康复结案" : "复查已登记",
    );
    await load();
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || "复查登记失败");
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
        <p class="eyebrow">健康诊疗</p>
        <h1>健康病例</h1>
      </div>
      <el-button
        v-if="canReport"
        type="primary"
        :icon="Plus"
        @click="openReport"
        >上报健康异常</el-button
      >
    </div>
    <section class="filter-bar health-filter">
      <el-select
        v-model="status"
        clearable
        placeholder="病例状态"
        @change="load"
        ><el-option label="处理中" value="PROCESSING" /><el-option
          label="已结案"
          value="CLOSED"
      /></el-select>
    </section>
    <section class="table-panel">
      <el-table v-loading="loading" :data="cases" empty-text="暂无健康病例"
        ><el-table-column
          prop="caseNo"
          label="病例号"
          min-width="180"
        /><el-table-column label="牛只" min-width="140"
          ><template #default="s"
            ><strong>{{ s.row.earTagNo }}</strong
            ><small class="cell-sub">{{
              s.row.cattleName || "-"
            }}</small></template
          ></el-table-column
        ><el-table-column
          prop="symptom"
          label="症状"
          min-width="220"
          show-overflow-tooltip
        /><el-table-column label="严重程度" width="95"
          ><template #default="s">{{
            severityLabels[s.row.severity]
          }}</template></el-table-column
        ><el-table-column label="健康状态" width="100"
          ><template #default="s"
            ><el-tag
              :type="s.row.healthStatus === 'TREATING' ? 'danger' : 'warning'"
              effect="plain"
              >{{ healthLabels[s.row.healthStatus] }}</el-tag
            ></template
          ></el-table-column
        ><el-table-column label="诊疗 / 休药" min-width="140"
          ><template #default="s"
            >{{ s.row.treatmentCount }} 次<small class="cell-sub">{{
              s.row.withdrawalUntil
                ? "休药至 " + s.row.withdrawalUntil
                : "无休药限制"
            }}</small></template
          ></el-table-column
        ><el-table-column label="操作" width="250" fixed="right"
          ><template #default="s"
            ><el-button link @click="openDetail(s.row)">详情</el-button><el-button
              v-if="canTreat && s.row.caseStatus === 'PROCESSING'"
              link
              type="primary"
              @click="openTreat(s.row)"
              >诊疗</el-button
            ><el-button
              v-if="canTreat && s.row.caseStatus === 'PROCESSING'"
              link
              @click="openFollow(s.row)"
              >复查</el-button
            ><el-button v-if="canTreat" link type="danger" @click="voidRecord('cases', s.row.caseId, s.row.version)">作废</el-button></template
          ></el-table-column
        ></el-table
      >
    </section>
    <el-drawer v-model="detailOpen" title="病例诊疗记录" size="min(720px,94vw)">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="病例号">{{ detail.caseInfo.caseNo }}</el-descriptions-item>
          <el-descriptions-item label="牛只">{{ detail.caseInfo.earTagNo }}</el-descriptions-item>
          <el-descriptions-item label="症状" :span="2">{{ detail.caseInfo.symptom }}</el-descriptions-item>
        </el-descriptions>
        <h3>诊疗记录</h3>
        <el-table :data="detail.treatments" empty-text="暂无诊疗记录">
          <el-table-column label="时间" min-width="170"><template #default="s">{{ formatDateTime(s.row.treatmentDate) }}</template></el-table-column>
          <el-table-column prop="diagnosis" label="诊断" min-width="180" />
          <el-table-column prop="vetName" label="兽医" min-width="90" />
          <el-table-column v-if="canTreat" label="操作" width="80"><template #default="s"><el-button link type="danger" @click="voidRecord('treatments',s.row.treatmentId,s.row.version)">作废</el-button></template></el-table-column>
        </el-table>
        <h3>复查记录</h3>
        <el-table :data="detail.followUps" empty-text="暂无复查记录">
          <el-table-column label="时间" min-width="170"><template #default="s">{{ formatDateTime(s.row.followUpDate) }}</template></el-table-column>
          <el-table-column label="结果" min-width="130"><template #default="s">{{ followUpLabels[s.row.result] || s.row.result || "-" }}</template></el-table-column>
          <el-table-column prop="description" label="说明" min-width="180" />
          <el-table-column v-if="canTreat" label="操作" width="80"><template #default="s"><el-button link type="danger" @click="voidRecord('follow-ups',s.row.followUpId,s.row.version)">作废</el-button></template></el-table-column>
        </el-table>
      </template>
    </el-drawer>
    <el-dialog v-model="reportOpen" title="上报健康异常" width="min(540px,92vw)"
      ><el-form label-position="top"
        ><el-form-item label="牛只" required
          ><el-select v-model="report.cattleId" filterable
            ><el-option
              v-for="c in cattle"
              :key="c.cattleId"
              :label="`${c.earTagNo} ${c.name || ''}`"
              :value="c.cattleId" /></el-select></el-form-item
        ><el-form-item label="发现时间"
          ><el-date-picker
            v-model="report.discoverDate"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm" /></el-form-item
        ><el-form-item label="严重程度"
          ><el-segmented
            v-model="report.severity"
            :options="[
              { label: '一般', value: 'NORMAL' },
              { label: '严重', value: 'SERIOUS' },
              { label: '紧急', value: 'URGENT' },
            ]" /></el-form-item
        ><el-form-item label="症状" required
          ><el-input
            v-model="report.symptom"
            type="textarea"
            :rows="3"
            maxlength="500" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="reportOpen = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="saveReport"
          >确认上报</el-button
        ></template
      ></el-dialog
    >
    <el-dialog
      v-model="treatOpen"
      title="登记诊疗与用药"
      width="min(620px,94vw)"
      ><el-form label-position="top" class="health-form"
        ><el-form-item label="诊疗时间"
          ><el-date-picker
            v-model="treatment.treatmentDate"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm" /></el-form-item
        ><el-form-item label="诊断" required
          ><el-input
            v-model="treatment.diagnosis"
            maxlength="500" /></el-form-item
        ><el-form-item label="治疗方案" class="wide"
          ><el-input
            v-model="treatment.treatmentPlan"
            type="textarea" /></el-form-item
        ><el-divider content-position="left">用药明细（可选）</el-divider
        ><el-form-item label="药品"
          ><el-input v-model="treatment.medicineName" /></el-form-item
        ><el-form-item label="剂量"
          ><el-input-number v-model="treatment.dosage" :min="0.01" /><el-input
            v-model="treatment.unit"
            class="unit-input" /></el-form-item
        ><el-form-item label="用法"
          ><el-input v-model="treatment.usageMethod" /></el-form-item
        ><el-form-item label="休药期（天）"
          ><el-input-number
            v-model="treatment.withdrawalDays"
            :min="0" /></el-form-item
        ><el-checkbox v-model="treatment.needFollowUp">需要复查</el-checkbox
        ><el-date-picker
          v-if="treatment.needFollowUp"
          v-model="treatment.followUpDate"
          type="date"
          value-format="YYYY-MM-DD" /></el-form
      ><template #footer
        ><el-button @click="treatOpen = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="saveTreatment"
          >保存诊疗</el-button
        ></template
      ></el-dialog
    >
    <el-dialog v-model="followOpen" title="登记健康复查" width="min(520px,92vw)"
      ><el-form label-position="top"
        ><el-form-item label="复查时间"
          ><el-date-picker
            v-model="follow.followUpDate"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm" /></el-form-item
        ><el-form-item label="复查结果"
          ><el-select v-model="follow.result"
            ><el-option label="继续治疗" value="CONTINUE_TREATMENT" /><el-option
              label="转观察"
              value="OBSERVE" /><el-option
              label="康复结案"
              value="RECOVERED" /></el-select></el-form-item
        ><el-form-item label="复查说明"
          ><el-input
            v-model="follow.description"
            type="textarea"
            :rows="3" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="followOpen = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="saveFollow"
          >确认复查</el-button
        ></template
      ></el-dialog
    >
  </div>
</template>
<style scoped>
.health-filter {
  grid-template-columns: minmax(180px, 260px);
}
.cell-sub {
  display: block;
  color: #7d8885;
  margin-top: 4px;
}
.el-select,
.el-date-editor {
  width: 100%;
}
.health-form {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}
.health-form .wide,
.health-form .el-divider {
  grid-column: 1/-1;
}
.unit-input {
  width: 80px;
  margin-left: 8px;
}
@media (max-width: 640px) {
  .health-form {
    grid-template-columns: 1fr;
  }
  .health-form .wide,
  .health-form .el-divider {
    grid-column: auto;
  }
}
</style>
