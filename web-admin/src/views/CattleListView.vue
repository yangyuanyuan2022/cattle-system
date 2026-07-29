<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import {
  ElMessage,
  type FormInstance,
  type FormRules,
  type UploadFile,
} from "element-plus";
import { Plus, Refresh, Search } from "@element-plus/icons-vue";
import {
  archiveCattle,
  createCattle,
  getCattleDetail,
  getCattlePage,
  getCattleTimeline,
  getCattlePedigree,
  restoreCattle,
  updateCattle,
  type ArchiveCattlePayload,
  type CattleRecord,
  type CattleTimelineEvent,
  type CattlePedigree,
  type CreateCattlePayload,
  type UpdateCattlePayload,
} from "../api/cattle";
import {
  getBarns,
  getHerds,
  transferCattle,
  type Barn,
  type Herd,
} from "../api/location";
import { getStoredUser } from "../auth/session";
import { getCattleQr } from "../api/operations";
import { getTransfers, type TransferItem } from "../api/operations";
import { getBreedingEvents, type BreedingEvent } from "../api/breeding";
import { getHealthCases, type HealthCase } from "../api/health";
import { getGrowthTrend, type GrowthTrend } from "../api/growth";
import {
  downloadAttachment,
  getAttachments,
  uploadAttachment,
  type AttachmentItem,
} from "../api/attachment";
import { getDictionaryItems, type DictionaryItem } from "../api/platform";

const roles = getStoredUser()?.roles || [];
const route = useRoute();
const canManage = roles.some((role) =>
  ["ADMIN", "FARM_MANAGER"].includes(role),
);
const canTransfer = roles.some((role) =>
  ["ADMIN", "FARM_MANAGER", "WORKER"].includes(role),
);

const loading = ref(false);
const saving = ref(false);
const dialogVisible = ref(false);
const detailVisible = ref(false);
const editVisible = ref(false);
const editSaving = ref(false);
const archiveVisible = ref(false);
const archiveSaving = ref(false);
const restoreVisible = ref(false);
const restoreSaving = ref(false);
const transferVisible = ref(false);
const transferSaving = ref(false);
const qrVisible = ref(false);
const qrUrl = ref("");
const barns = ref<Barn[]>([]);
const herds = ref<Herd[]>([]);
const breeds = ref<DictionaryItem[]>([]);
const detailLoading = ref(false);
const detail = ref<CattleRecord | null>(null);
const timeline = ref<CattleTimelineEvent[]>([]);
const detailTab = ref("overview");
const detailBreeding = ref<BreedingEvent[]>([]);
const detailHealth = ref<HealthCase[]>([]);
const detailGrowth = ref<GrowthTrend | null>(null);
const detailTransfers = ref<TransferItem[]>([]);
const detailAttachments = ref<AttachmentItem[]>([]);
const detailPedigree = ref<CattlePedigree | null>(null);
const attachmentUploading = ref(false);
const records = ref<CattleRecord[]>([]);
const total = ref(0);
const formRef = ref<FormInstance>();
const editFormRef = ref<FormInstance>();
const archiveFormRef = ref<FormInstance>();
const restoreFormRef = ref<FormInstance>();
const transferFormRef = ref<FormInstance>();
const query = reactive({
  page: 1,
  pageSize: 20,
  keyword: "",
  presenceStatus: String(route.query.presenceStatus || ""),
  lifecycleStage: String(route.query.lifecycleStage || ""),
  sex: "",
  breedId: "",
  sourceType: "",
  healthStatus: "",
  barnId: "",
});
const form = reactive<CreateCattlePayload>({
  earTagNo: "",
  name: "",
  sex: "MALE",
  breedId: "",
  birthDate: "",
  sourceType: "PURCHASE",
  entryDate: new Date().toISOString().slice(0, 10),
  lifecycleStage: "GROWING",
  barnId: "",
  herdId: "",
  remark: "",
});
const editForm = reactive<UpdateCattlePayload>({
  earTagNo: "",
  name: "",
  birthDate: "",
  remark: "",
  changeReason: "",
  version: 0,
});
const archiveForm = reactive<ArchiveCattlePayload>({
  exitType: "SALE",
  exitDate: new Date().toISOString().slice(0, 10),
  reason: "",
  treatingRiskConfirmed: false,
  version: 0,
});
const restoreForm = reactive({ reason: "", version: 0 });
const transferForm = reactive({
  toBarnId: "",
  toHerdId: "",
  transferDate: new Date().toISOString().slice(0, 16),
  reason: "",
  version: 0,
});
const rules: FormRules = {
  earTagNo: [{ required: true, message: "请输入耳号", trigger: "blur" }],
  sex: [{ required: true, message: "请选择性别", trigger: "change" }],
  sourceType: [{ required: true, message: "请选择来源", trigger: "change" }],
  entryDate: [{ required: true, message: "请选择入场日期", trigger: "change" }],
  lifecycleStage: [
    { required: true, message: "请选择阶段", trigger: "change" },
  ],
};
const editRules: FormRules = {
  earTagNo: [{ required: true, message: "请输入耳号", trigger: "blur" }],
  changeReason: [
    { required: true, message: "请填写本次修改原因", trigger: "blur" },
  ],
};
const archiveRules: FormRules = {
  exitType: [{ required: true, message: "请选择离场类型", trigger: "change" }],
  exitDate: [{ required: true, message: "请选择离场日期", trigger: "change" }],
  reason: [{ required: true, message: "请填写离场原因", trigger: "blur" }],
};
const restoreRules: FormRules = {
  reason: [{ required: true, message: "请填写恢复原因", trigger: "blur" }],
};
const transferRules: FormRules = {
  toBarnId: [{ required: true, message: "请选择目标栏舍", trigger: "change" }],
  transferDate: [
    { required: true, message: "请选择转群时间", trigger: "change" },
  ],
  reason: [{ required: true, message: "请填写转群原因", trigger: "blur" }],
};
const stageLabels: Record<string, string> = {
  CALF: "犊牛",
  GROWING: "育成牛",
  RESERVE: "后备牛",
  COW: "母牛",
  BULL: "种公牛",
};
const presenceLabels: Record<string, string> = {
  IN_FIELD: "在场",
  EXITED: "已离场",
};
const eventLabels: Record<string, string> = {
  CATTLE_CREATED: "创建档案",
  CATTLE_EAR_TAG_CHANGED: "耳号纠错",
  CATTLE_ARCHIVED: "办理离场",
  CATTLE_RESTORED: "恢复在场",
  CATTLE_TRANSFERRED: "牛只转群",
};
const availableHerds = () =>
  herds.value.filter((item) => item.barnId === transferForm.toBarnId);
const createHerds = () =>
  herds.value.filter((item) => item.barnId === form.barnId);

async function load() {
  loading.value = true;
  try {
    const data = await getCattlePage({
      page: query.page,
      pageSize: query.pageSize,
      keyword: query.keyword || undefined,
      presenceStatus: query.presenceStatus || undefined,
      lifecycleStage: query.lifecycleStage || undefined,
      sex: query.sex || undefined,
      breedId: query.breedId || undefined,
      sourceType: query.sourceType || undefined,
      healthStatus: query.healthStatus || undefined,
      barnId: query.barnId || undefined,
    });
    records.value = data.items;
    total.value = data.total;
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || "牛只档案加载失败");
  } finally {
    loading.value = false;
  }
}

function search() {
  query.page = 1;
  load();
}
function reset() {
  Object.assign(query, {
    page: 1,
    keyword: "",
    presenceStatus: "",
    lifecycleStage: "",
    sex: "",
    breedId: "",
    sourceType: "",
    healthStatus: "",
    barnId: "",
  });
  load();
}
async function openCreate() {
  try {
    [barns.value, herds.value] = await Promise.all([
      getBarns("ENABLED"),
      getHerds("ENABLED"),
    ]);
  } catch {
    return ElMessage.error("栏舍与牛群加载失败");
  }
  Object.assign(form, {
    earTagNo: "",
    name: "",
    sex: "MALE",
    breedId: "",
    birthDate: "",
    sourceType: "PURCHASE",
    entryDate: new Date().toISOString().slice(0, 10),
    lifecycleStage: "GROWING",
    barnId: "",
    herdId: "",
    remark: "",
  });
  dialogVisible.value = true;
}
async function openDetail(row: CattleRecord) {
  detailVisible.value = true;
  detailLoading.value = true;
  detailTab.value = "overview";
  try {
    const [record, events, breeding, health, growth, transfers, attachments, pedigree] =
      await Promise.all([
        getCattleDetail(row.cattleId),
        getCattleTimeline(row.cattleId),
        getBreedingEvents(row.cattleId),
        getHealthCases(),
        getGrowthTrend(row.cattleId),
        getTransfers(),
        getAttachments("CATTLE", row.cattleId),
        getCattlePedigree(row.cattleId),
      ]);
    detail.value = record;
    timeline.value = events;
    detailBreeding.value = breeding;
    detailHealth.value = health.filter(
      (item) => item.cattleId === row.cattleId,
    );
    detailGrowth.value = growth;
    detailTransfers.value = transfers.filter(
      (item) => item.cattleId === row.cattleId,
    );
    detailAttachments.value = attachments;
    detailPedigree.value = pedigree;
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || "牛只详情加载失败");
    detailVisible.value = false;
  } finally {
    detailLoading.value = false;
  }
}
async function addAttachment(upload: UploadFile) {
  if (!detail.value || !upload.raw) return;
  attachmentUploading.value = true;
  try {
    await uploadAttachment("CATTLE", detail.value.cattleId, upload.raw);
    detailAttachments.value = await getAttachments(
      "CATTLE",
      detail.value.cattleId,
    );
    ElMessage.success("附件上传成功");
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || "附件上传失败");
  } finally {
    attachmentUploading.value = false;
  }
}
async function openQr() {
  if (!detail.value) return;
  try {
    if (qrUrl.value) URL.revokeObjectURL(qrUrl.value);
    qrUrl.value = URL.createObjectURL(await getCattleQr(detail.value.cattleId));
    qrVisible.value = true;
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || "二维码生成失败");
  }
}
function printQr() {
  const win = window.open("", "_blank", "width=480,height=620");
  if (!win || !detail.value) return;
  win.document.write(
    `<title>${detail.value.earTagNo} 牛牌</title><style>body{text-align:center;font-family:sans-serif;padding:32px}img{width:320px;height:320px}h1{font-size:28px}</style><h1>${detail.value.earTagNo}</h1><img src="${qrUrl.value}" onload="window.print()">`,
  );
  win.document.close();
}
function openEdit() {
  if (!detail.value) return;
  Object.assign(editForm, {
    earTagNo: detail.value.earTagNo,
    name: detail.value.name || "",
    birthDate: detail.value.birthDate || "",
    remark: detail.value.remark || "",
    changeReason: "",
    version: detail.value.version,
  });
  editVisible.value = true;
}
async function saveEdit() {
  if (!detail.value || !(await editFormRef.value?.validate())) return;
  editSaving.value = true;
  try {
    const payload = { ...editForm };
    if (!payload.name) delete payload.name;
    if (!payload.birthDate) delete payload.birthDate;
    if (!payload.remark) delete payload.remark;
    const updated = await updateCattle(
      detail.value.cattleId,
      payload,
      crypto.randomUUID(),
    );
    detail.value = updated;
    timeline.value = await getCattleTimeline(updated.cattleId);
    editVisible.value = false;
    ElMessage.success("牛只档案纠错成功，审计记录已保存");
    await load();
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || "档案纠错失败");
  } finally {
    editSaving.value = false;
  }
}
function openArchive() {
  if (!detail.value) return;
  Object.assign(archiveForm, {
    exitType: "SALE",
    exitDate: new Date().toISOString().slice(0, 10),
    reason: "",
    treatingRiskConfirmed: false,
    version: detail.value.version,
  });
  archiveVisible.value = true;
}
async function saveArchive() {
  if (!detail.value || !(await archiveFormRef.value?.validate())) return;
  archiveSaving.value = true;
  try {
    detail.value = await archiveCattle(
      detail.value.cattleId,
      { ...archiveForm },
      crypto.randomUUID(),
    );
    timeline.value = await getCattleTimeline(detail.value.cattleId);
    archiveVisible.value = false;
    ElMessage.success("离场办理成功");
    await load();
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || "离场办理失败");
  } finally {
    archiveSaving.value = false;
  }
}
function openRestore() {
  if (!detail.value) return;
  Object.assign(restoreForm, { reason: "", version: detail.value.version });
  restoreVisible.value = true;
}
async function saveRestore() {
  if (!detail.value || !(await restoreFormRef.value?.validate())) return;
  restoreSaving.value = true;
  try {
    detail.value = await restoreCattle(
      detail.value.cattleId,
      { ...restoreForm },
      crypto.randomUUID(),
    );
    timeline.value = await getCattleTimeline(detail.value.cattleId);
    restoreVisible.value = false;
    ElMessage.success("已恢复在场");
    await load();
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || "恢复在场失败");
  } finally {
    restoreSaving.value = false;
  }
}
async function openTransfer() {
  if (!detail.value) return;
  try {
    [barns.value, herds.value] = await Promise.all([
      getBarns("ENABLED"),
      getHerds("ENABLED"),
    ]);
    Object.assign(transferForm, {
      toBarnId: "",
      toHerdId: "",
      transferDate: new Date().toISOString().slice(0, 16),
      reason: "",
      version: detail.value.version,
    });
    transferVisible.value = true;
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || "位置数据加载失败");
  }
}
async function saveTransfer() {
  if (!detail.value || !(await transferFormRef.value?.validate())) return;
  transferSaving.value = true;
  try {
    const result = await transferCattle(
      {
        cattleId: detail.value.cattleId,
        toBarnId: transferForm.toBarnId,
        toHerdId: transferForm.toHerdId || undefined,
        transferDate: transferForm.transferDate + ":00",
        reason: transferForm.reason,
        version: transferForm.version,
      },
      crypto.randomUUID(),
    );
    detail.value = await getCattleDetail(detail.value.cattleId);
    timeline.value = await getCattleTimeline(detail.value.cattleId);
    transferVisible.value = false;
    result.capacityExceeded
      ? ElMessage.warning(result.warning || "目标栏舍容量已超限")
      : ElMessage.success("转群完成");
    await load();
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || "转群失败");
  } finally {
    transferSaving.value = false;
  }
}
async function save() {
  if (!(await formRef.value?.validate())) return;
  saving.value = true;
  try {
    const payload = { ...form };
    if (!payload.name) delete payload.name;
    if (!payload.birthDate) delete payload.birthDate;
    if (!payload.remark) delete payload.remark;
    if (!payload.barnId) delete payload.barnId;
    if (!payload.herdId) delete payload.herdId;
    await createCattle(payload, crypto.randomUUID());
    ElMessage.success("牛只建档成功");
    dialogVisible.value = false;
    query.page = 1;
    await load();
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || "建档失败");
  } finally {
    saving.value = false;
  }
}

onMounted(async () => {
  try {
    [barns.value, herds.value, breeds.value] = await Promise.all([getBarns("ENABLED"), getHerds("ENABLED"), getDictionaryItems("CATTLE_BREED")]);
  } catch (error: any) {
    ElMessage.error(error.response?.data?.message || "筛选选项加载失败");
  }
  await load();
});
</script>

<template>
  <div class="cattle-page">
    <div class="page-heading">
      <div>
        <p class="eyebrow">基础档案</p>
        <h1>牛只档案</h1>
      </div>
      <el-button
        v-if="canManage"
        type="primary"
        :icon="Plus"
        @click="openCreate"
        >牛只建档</el-button
      >
    </div>
    <section class="filter-bar">
      <el-input
        v-model="query.keyword"
        clearable
        placeholder="耳号或名称"
        :prefix-icon="Search"
        @keyup.enter="search"
      />
      <el-select v-model="query.presenceStatus" clearable placeholder="在场状态"
        ><el-option label="在场" value="IN_FIELD" /><el-option
          label="已离场"
          value="EXITED"
      /></el-select>
      <el-select v-model="query.lifecycleStage" clearable placeholder="生命周期"
        ><el-option
          v-for="(label, value) in stageLabels"
          :key="value"
          :label="label"
          :value="value"
      /></el-select>
      <el-select v-model="query.sex" clearable placeholder="性别"><el-option label="公牛" value="MALE" /><el-option label="母牛" value="FEMALE" /></el-select>
      <el-select v-model="query.breedId" clearable filterable placeholder="品种"><el-option v-for="item in breeds.filter((x) => x.status === 'ENABLED')" :key="item.itemId" :label="item.itemName" :value="item.itemId" /></el-select>
      <el-select v-model="query.sourceType" clearable placeholder="来源"><el-option label="场内出生" value="BIRTH" /><el-option label="外购" value="PURCHASE" /></el-select>
      <el-select v-model="query.healthStatus" clearable placeholder="健康状态"><el-option label="正常" value="NORMAL" /><el-option label="观察中" value="OBSERVING" /><el-option label="治疗中" value="TREATING" /></el-select>
      <el-select v-model="query.barnId" clearable filterable placeholder="栏舍"><el-option v-for="item in barns" :key="item.barnId" :label="item.barnName" :value="item.barnId" /></el-select>
      <div class="filter-actions">
        <el-button :icon="Refresh" @click="reset">重置</el-button
        ><el-button type="primary" :icon="Search" @click="search"
          >查询</el-button
        >
      </div>
    </section>
    <section class="table-panel">
      <el-table
        v-loading="loading"
        :data="records"
        class="desktop-cattle-table"
        empty-text="暂无牛只档案"
        row-class-name="clickable-row"
        @row-click="openDetail"
      >
        <el-table-column prop="earTagNo" label="耳号" min-width="150"
          ><template #default="scope"
            ><strong>{{ scope.row.earTagNo }}</strong></template
          ></el-table-column
        >
        <el-table-column prop="name" label="名称" min-width="110"
          ><template #default="scope">{{
            scope.row.name || "-"
          }}</template></el-table-column
        >
        <el-table-column label="性别" width="80"
          ><template #default="scope">{{
            scope.row.sex === "MALE" ? "公" : "母"
          }}</template></el-table-column
        >
        <el-table-column label="生命周期" width="110"
          ><template #default="scope">{{
            stageLabels[scope.row.lifecycleStage] || scope.row.lifecycleStage
          }}</template></el-table-column
        >
        <el-table-column label="在场状态" width="100"
          ><template #default="scope"
            ><el-tag effect="plain" type="success">{{
              presenceLabels[scope.row.presenceStatus] ||
              scope.row.presenceStatus
            }}</el-tag></template
          ></el-table-column
        >
        <el-table-column prop="entryDate" label="入场日期" width="120" />
        <el-table-column prop="createdAt" label="建档时间" min-width="170" />
      </el-table>
      <div v-loading="loading" class="mobile-cattle-list">
        <button
          v-for="row in records"
          :key="row.cattleId"
          class="cattle-card"
          type="button"
          @click="openDetail(row)"
        >
          <span class="card-main"
            ><strong>{{ row.earTagNo }}</strong
            ><small>{{ row.name || "未设置名称" }}</small></span
          >
          <span class="card-meta"
            ><span>{{ row.sex === "MALE" ? "公牛" : "母牛" }}</span
            ><span>{{
              stageLabels[row.lifecycleStage] || row.lifecycleStage
            }}</span
            ><el-tag
              effect="plain"
              :type="row.presenceStatus === 'IN_FIELD' ? 'success' : 'info'"
              >{{
                presenceLabels[row.presenceStatus] || row.presenceStatus
              }}</el-tag
            ></span
          >
        </button>
        <el-empty
          v-if="!loading && !records.length"
          description="暂无牛只档案"
          :image-size="72"
        />
      </div>
      <div class="pagination">
        <span>共 {{ total }} 头</span
        ><el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.pageSize"
          background
          layout="prev, pager, next"
          :total="total"
          @current-change="load"
        />
      </div>
    </section>
    <el-dialog
      v-model="dialogVisible"
      title="牛只建档"
      width="min(640px, 92vw)"
      destroy-on-close
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="cattle-form"
      >
        <el-form-item label="耳号" prop="earTagNo"
          ><el-input
            v-model="form.earTagNo"
            maxlength="50"
            placeholder="例如：CN-2026-001"
        /></el-form-item>
        <el-form-item label="名称"
          ><el-input v-model="form.name" maxlength="50" placeholder="选填"
        /></el-form-item>
        <el-form-item label="性别" prop="sex"
          ><el-segmented
            v-model="form.sex"
            :options="[
              { label: '公牛', value: 'MALE' },
              { label: '母牛', value: 'FEMALE' },
            ]"
        /></el-form-item>
        <el-form-item label="品种"><el-select v-model="form.breedId" clearable filterable placeholder="请先在系统配置维护品种"><el-option v-for="item in breeds.filter((x) => x.status === 'ENABLED')" :key="item.itemId" :label="item.itemName" :value="item.itemId" /></el-select></el-form-item>
        <el-form-item label="生命周期" prop="lifecycleStage"
          ><el-select v-model="form.lifecycleStage"
            ><el-option
              v-for="(label, value) in stageLabels"
              :key="value"
              :label="label"
              :value="value" /></el-select
        ></el-form-item>
        <el-form-item label="来源" prop="sourceType"
          ><el-select v-model="form.sourceType"
            ><el-option label="外购" value="PURCHASE" /><el-option
              label="场内出生"
              value="BIRTH" /></el-select
        ></el-form-item>
        <el-form-item label="入场日期" prop="entryDate"
          ><el-date-picker
            v-model="form.entryDate"
            type="date"
            value-format="YYYY-MM-DD"
        /></el-form-item>
        <el-form-item label="入场栏舍"
          ><el-select v-model="form.barnId" clearable @change="form.herdId = ''"
            ><el-option
              v-for="barn in barns"
              :key="barn.barnId"
              :label="barn.barnName"
              :value="barn.barnId" /></el-select
        ></el-form-item>
        <el-form-item label="入场牛群"
          ><el-select v-model="form.herdId" clearable :disabled="!form.barnId"
            ><el-option
              v-for="herd in createHerds()"
              :key="herd.herdId"
              :label="herd.herdName"
              :value="herd.herdId" /></el-select
        ></el-form-item>
        <el-form-item label="出生日期"
          ><el-date-picker
            v-model="form.birthDate"
            type="date"
            value-format="YYYY-MM-DD"
        /></el-form-item>
        <el-form-item label="备注" class="form-wide"
          ><el-input
            v-model="form.remark"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
        /></el-form-item>
      </el-form>
      <template #footer
        ><el-button @click="dialogVisible = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save"
          >确认建档</el-button
        ></template
      >
    </el-dialog>
    <el-drawer v-model="detailVisible" title="牛只详情" size="min(620px, 96vw)">
      <div v-loading="detailLoading" class="detail-drawer">
        <template v-if="detail">
          <div class="detail-title">
            <div>
              <span class="detail-ear">{{ detail.earTagNo }}</span
              ><small>{{ detail.name || "未设置名称" }}</small>
            </div>
            <div class="detail-actions">
              <el-tag
                effect="plain"
                :type="
                  detail.presenceStatus === 'IN_FIELD' ? 'success' : 'info'
                "
                >{{
                  presenceLabels[detail.presenceStatus] || detail.presenceStatus
                }}</el-tag
              ><el-button size="small" @click="openQr">二维码</el-button
              ><el-button v-if="canManage" size="small" @click="openEdit"
                >纠错档案</el-button
              ><el-button
                v-if="canTransfer && detail.presenceStatus === 'IN_FIELD'"
                size="small"
                type="primary"
                plain
                @click="openTransfer"
                >转群</el-button
              ><el-button
                v-if="canManage && detail.presenceStatus === 'IN_FIELD'"
                size="small"
                type="danger"
                plain
                @click="openArchive"
                >办理离场</el-button
              ><el-button
                v-else-if="canManage"
                size="small"
                type="primary"
                plain
                @click="openRestore"
                >恢复在场</el-button
              >
            </div>
          </div>
          <el-tabs v-model="detailTab" class="detail-tabs">
            <el-tab-pane label="概览" name="overview"
              ><el-descriptions :column="2" border class="detail-fields">
                <el-descriptions-item label="性别">{{
                  detail.sex === "MALE" ? "公牛" : "母牛"
                }}</el-descriptions-item>
                <el-descriptions-item label="生命周期">{{
                  stageLabels[detail.lifecycleStage] || detail.lifecycleStage
                }}</el-descriptions-item>
                <el-descriptions-item label="来源">{{
                  detail.sourceType === "PURCHASE" ? "外购" : "场内出生"
                }}</el-descriptions-item>
                <el-descriptions-item label="健康状态">{{
                  detail.healthStatus === "NORMAL"
                    ? "正常"
                    : detail.healthStatus
                }}</el-descriptions-item>
                <el-descriptions-item label="出生日期">{{
                  detail.birthDate || "-"
                }}</el-descriptions-item>
                <el-descriptions-item label="入场日期">{{
                  detail.entryDate
                }}</el-descriptions-item>
                <el-descriptions-item label="品种">{{ breeds.find((x) => x.itemId === detail?.breedId)?.itemName || '-' }}</el-descriptions-item>
                <el-descriptions-item label="父系">{{ detailPedigree?.sireEarTagNo || detailPedigree?.sireText || '未知' }}</el-descriptions-item>
                <el-descriptions-item label="母系">{{ detailPedigree?.damEarTagNo || '未知' }}</el-descriptions-item>
                <el-descriptions-item label="后代">{{ detailPedigree?.offspring.map((x) => x.earTagNo).join('、') || '无记录' }}</el-descriptions-item>
                <el-descriptions-item label="当前栏舍">{{
                  barns.find((b) => b.barnId === detail?.barnId)?.barnName ||
                  detail.barnId ||
                  "未分配"
                }}</el-descriptions-item>
                <el-descriptions-item label="当前牛群">{{
                  herds.find((h) => h.herdId === detail?.herdId)?.herdName ||
                  detail.herdId ||
                  "未分配"
                }}</el-descriptions-item>
                <el-descriptions-item label="备注" :span="2">{{
                  detail.remark || "-"
                }}</el-descriptions-item>
              </el-descriptions></el-tab-pane
            >
            <el-tab-pane label="时间轴" name="timeline"
              ><section class="timeline-section">
                <h3>生命周期时间轴</h3>
                <el-timeline v-if="timeline.length"
                  ><el-timeline-item
                    v-for="event in timeline"
                    :key="event.eventId"
                    :timestamp="event.eventDate"
                    placement="top"
                    ><strong>{{ event.summary }}</strong
                    ><small>{{
                      eventLabels[event.eventType] || event.eventType
                    }}</small></el-timeline-item
                  ></el-timeline
                ><el-empty
                  v-else
                  description="暂无时间轴事件"
                  :image-size="80"
                /></section
            ></el-tab-pane>
            <el-tab-pane label="繁育" name="breeding"
              ><el-table :data="detailBreeding" empty-text="暂无繁育记录"
                ><el-table-column
                  prop="eventDate"
                  label="时间"
                  min-width="170" /><el-table-column
                  prop="summary"
                  label="事件"
                  min-width="240" /></el-table
            ></el-tab-pane>
            <el-tab-pane label="健康" name="health"
              ><el-table :data="detailHealth" empty-text="暂无健康病例"
                ><el-table-column
                  prop="caseNo"
                  label="病例号"
                  min-width="160" /><el-table-column
                  prop="discoverDate"
                  label="发现时间"
                  min-width="170" /><el-table-column
                  prop="symptom"
                  label="症状"
                  min-width="220" /><el-table-column
                  prop="caseStatus"
                  label="状态"
                  width="100" /></el-table
            ></el-tab-pane>
            <el-tab-pane label="生长" name="growth"
              ><el-table
                :data="detailGrowth?.weights || []"
                empty-text="暂无称重记录"
                ><el-table-column
                  prop="measureDate"
                  label="称重时间"
                  min-width="170" /><el-table-column
                  prop="weightKg"
                  label="体重(kg)"
                  width="110" /><el-table-column
                  prop="changeKg"
                  label="变化(kg)"
                  width="110" /></el-table
            ></el-tab-pane>
            <el-tab-pane label="转群" name="transfers"
              ><el-table :data="detailTransfers" empty-text="暂无转群记录"
                ><el-table-column
                  prop="transferDate"
                  label="时间"
                  min-width="170" /><el-table-column
                  label="位置"
                  min-width="220"
                  ><template #default="s"
                    >{{ s.row.fromBarnName || "-" }} →
                    {{ s.row.toBarnName }}</template
                  ></el-table-column
                ><el-table-column
                  prop="reason"
                  label="原因"
                  min-width="180" /></el-table
            ></el-tab-pane>
            <el-tab-pane label="附件" name="attachments"
              ><div class="attachment-head">
                <span>支持图片、PDF、表格和 Word，单个不超过 10 MB</span
                ><el-upload
                  :auto-upload="false"
                  :show-file-list="false"
                  :on-change="addAttachment"
                  accept=".jpg,.jpeg,.png,.webp,.pdf,.xlsx,.xls,.csv,.docx"
                  ><el-button
                    type="primary"
                    plain
                    :loading="attachmentUploading"
                    >上传附件</el-button
                  ></el-upload
                >
              </div>
              <el-table :data="detailAttachments" empty-text="暂无附件"
                ><el-table-column
                  prop="fileName"
                  label="文件"
                  min-width="220"
                /><el-table-column label="大小" width="100"
                  ><template #default="s"
                    >{{ (s.row.fileSize / 1024).toFixed(1) }} KB</template
                  ></el-table-column
                ><el-table-column
                  prop="uploadedAt"
                  label="上传时间"
                  min-width="170"
                /><el-table-column label="操作" width="80"
                  ><template #default="s"
                    ><el-button
                      link
                      type="primary"
                      @click="downloadAttachment(s.row)"
                      >下载</el-button
                    ></template
                  ></el-table-column
                ></el-table
              ></el-tab-pane
            >
          </el-tabs>
        </template>
      </div>
    </el-drawer>
    <el-dialog
      v-model="qrVisible"
      title="牛牌二维码"
      width="min(420px, 92vw)"
      append-to-body
    >
      <div class="qr-preview">
        <strong>{{ detail?.earTagNo }}</strong>
        <img v-if="qrUrl" :src="qrUrl" alt="牛牌二维码" />
        <span>扫码进入该牛只档案</span>
      </div>
      <template #footer
        ><el-button type="primary" @click="printQr"
          >打印牛牌</el-button
        ></template
      >
    </el-dialog>
    <el-dialog
      v-model="editVisible"
      title="牛只档案纠错"
      width="min(560px, 92vw)"
      append-to-body
    >
      <el-alert
        title="耳号等关键档案修改会写入操作审计；耳号变化同时进入牛只时间轴。"
        type="warning"
        :closable="false"
        show-icon
      />
      <el-form
        ref="editFormRef"
        :model="editForm"
        :rules="editRules"
        label-position="top"
        class="edit-form"
      >
        <el-form-item label="耳号" prop="earTagNo"
          ><el-input v-model="editForm.earTagNo" maxlength="50"
        /></el-form-item>
        <el-form-item label="名称"
          ><el-input v-model="editForm.name" maxlength="50"
        /></el-form-item>
        <el-form-item label="出生日期"
          ><el-date-picker
            v-model="editForm.birthDate"
            type="date"
            value-format="YYYY-MM-DD"
        /></el-form-item>
        <el-form-item label="备注"
          ><el-input
            v-model="editForm.remark"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
        /></el-form-item>
        <el-form-item label="修改原因" prop="changeReason"
          ><el-input
            v-model="editForm.changeReason"
            type="textarea"
            :rows="3"
            maxlength="500"
            placeholder="请说明纠错依据和原因"
            show-word-limit
        /></el-form-item>
      </el-form>
      <template #footer
        ><el-button @click="editVisible = false">取消</el-button
        ><el-button type="primary" :loading="editSaving" @click="saveEdit"
          >确认并保存审计</el-button
        ></template
      >
    </el-dialog>
    <el-dialog
      v-model="archiveVisible"
      title="办理牛只离场"
      width="min(520px, 92vw)"
      append-to-body
    >
      <el-form
        ref="archiveFormRef"
        :model="archiveForm"
        :rules="archiveRules"
        label-position="top"
      >
        <el-form-item label="离场类型" prop="exitType"
          ><el-select v-model="archiveForm.exitType"
            ><el-option label="出售" value="SALE" /><el-option
              label="死亡"
              value="DEATH" /><el-option label="淘汰" value="CULL" /><el-option
              label="其他"
              value="OTHER" /></el-select
        ></el-form-item>
        <el-form-item label="离场日期" prop="exitDate"
          ><el-date-picker
            v-model="archiveForm.exitDate"
            type="date"
            value-format="YYYY-MM-DD"
        /></el-form-item>
        <el-form-item label="离场原因" prop="reason"
          ><el-input
            v-model="archiveForm.reason"
            type="textarea"
            :rows="3"
            maxlength="500"
        /></el-form-item>
        <el-checkbox
          v-if="detail?.healthStatus === 'TREATING'"
          v-model="archiveForm.treatingRiskConfirmed"
          >已确认该牛正在治疗中，并知悉离场风险</el-checkbox
        >
      </el-form>
      <template #footer
        ><el-button @click="archiveVisible = false">取消</el-button
        ><el-button type="danger" :loading="archiveSaving" @click="saveArchive"
          >确认离场</el-button
        ></template
      >
    </el-dialog>
    <el-dialog
      v-model="restoreVisible"
      title="恢复牛只在场"
      width="min(500px, 92vw)"
      append-to-body
    >
      <el-form
        ref="restoreFormRef"
        :model="restoreForm"
        :rules="restoreRules"
        label-position="top"
        ><el-form-item label="恢复原因" prop="reason"
          ><el-input
            v-model="restoreForm.reason"
            type="textarea"
            :rows="3"
            maxlength="500"
            placeholder="请说明恢复在场的依据" /></el-form-item
      ></el-form>
      <template #footer
        ><el-button @click="restoreVisible = false">取消</el-button
        ><el-button type="primary" :loading="restoreSaving" @click="saveRestore"
          >确认恢复</el-button
        ></template
      >
    </el-dialog>
    <el-dialog
      v-model="transferVisible"
      title="单头转群"
      width="min(520px, 92vw)"
      append-to-body
    >
      <el-form
        ref="transferFormRef"
        :model="transferForm"
        :rules="transferRules"
        label-position="top"
      >
        <el-form-item label="目标栏舍" prop="toBarnId"
          ><el-select
            v-model="transferForm.toBarnId"
            @change="transferForm.toHerdId = ''"
            ><el-option
              v-for="barn in barns"
              :key="barn.barnId"
              :label="`${barn.barnName}（${barn.cattleCount}/${barn.capacity ?? '不限'}）`"
              :value="barn.barnId" /></el-select
        ></el-form-item>
        <el-form-item label="目标牛群"
          ><el-select
            v-model="transferForm.toHerdId"
            clearable
            placeholder="可不选择"
            ><el-option
              v-for="herd in availableHerds()"
              :key="herd.herdId"
              :label="herd.herdName"
              :value="herd.herdId" /></el-select
        ></el-form-item>
        <el-form-item label="转群时间" prop="transferDate"
          ><el-date-picker
            v-model="transferForm.transferDate"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm"
        /></el-form-item>
        <el-form-item label="转群原因" prop="reason"
          ><el-input
            v-model="transferForm.reason"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
        /></el-form-item>
      </el-form>
      <template #footer
        ><el-button @click="transferVisible = false">取消</el-button
        ><el-button
          type="primary"
          :loading="transferSaving"
          @click="saveTransfer"
          >确认转群</el-button
        ></template
      >
    </el-dialog>
  </div>
</template>

<style scoped>
.mobile-cattle-list {
  display: none;
}
.cattle-card {
  width: 100%;
  border: 0;
  border-bottom: 1px solid #e4e8e6;
  background: #fff;
  padding: 14px 4px;
  text-align: left;
  display: grid;
  gap: 10px;
  color: #26332e;
}
.card-main,
.card-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.card-main small {
  color: #75817d;
}
.card-meta {
  justify-content: flex-start;
  font-size: 13px;
  color: #64706c;
}
.card-meta .el-tag {
  margin-left: auto;
}
.qr-preview {
  display: grid;
  justify-items: center;
  gap: 12px;
}
.qr-preview strong {
  font-size: 22px;
}
.qr-preview img {
  width: min(280px, 70vw);
  aspect-ratio: 1;
}
.qr-preview span {
  color: #75817d;
}
.detail-tabs {
  margin-top: 18px;
}
.attachment-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  color: #75817d;
  font-size: 13px;
}
@media (max-width: 767px) {
  .desktop-cattle-table {
    display: none;
  }
  .mobile-cattle-list {
    display: block;
  }
  .table-panel {
    padding: 0 14px;
  }
  .pagination {
    padding: 12px 0;
  }
  .pagination > span {
    display: none;
  }
  .detail-title {
    align-items: flex-start;
  }
  .detail-actions {
    justify-content: flex-start;
  }
}
</style>
