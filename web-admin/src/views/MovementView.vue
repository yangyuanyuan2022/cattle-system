<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus } from "@element-plus/icons-vue";
import { getAllCattle, getCattlePage, type CattleRecord } from "../api/cattle";
import { getBarns, getHerds, type Barn, type Herd } from "../api/location";
import {
  batchTransfer,
  getExits,
  getTransfers,
  voidExit,
  voidTransfer,
  type ExitItem,
  type TransferItem,
} from "../api/operations";
import { getStoredUser } from "../auth/session";
const roles = getStoredUser()?.roles || [];
const canManage = roles.some((r) => ["ADMIN", "FARM_MANAGER"].includes(r)),
  canViewExits = !roles.includes("WORKER"),
  tab = ref("transfers"),
  loading = ref(false),
  dialog = ref(false),
  saving = ref(false),
  transfers = ref<TransferItem[]>([]),
  exits = ref<ExitItem[]>([]),
  cattle = ref<CattleRecord[]>([]),
  barns = ref<Barn[]>([]),
  herds = ref<Herd[]>([]);
const form = reactive({
  cattleIds: [] as string[],
  toBarnId: "",
  toHerdId: "",
  transferDate: new Date().toISOString().slice(0, 16),
  reason: "",
});
const availableHerds = computed(() =>
  herds.value.filter((x) => x.barnId === form.toBarnId),
);
const exitLabels: Record<string, string> = {
  SALE: "出售",
  DEATH: "死亡",
  CULL: "淘汰",
  OTHER: "其他",
};
async function load() {
  loading.value = true;
  try {
    [transfers.value, exits.value] = await Promise.all([
      getTransfers(),
      canViewExits ? getExits() : Promise.resolve([]),
    ]);
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || "变动记录加载失败");
  } finally {
    loading.value = false;
  }
}
async function openBatch() {
  try {
    const [records, b, h] = await Promise.all([
      getAllCattle({ presenceStatus: "IN_FIELD" }),
      getBarns("ENABLED"),
      getHerds("ENABLED"),
    ]);
    cattle.value = records;
    barns.value = b;
    herds.value = h;
    Object.assign(form, {
      cattleIds: [],
      toBarnId: "",
      toHerdId: "",
      transferDate: new Date().toISOString().slice(0, 16),
      reason: "",
    });
    dialog.value = true;
  } catch {
    ElMessage.error("基础数据加载失败");
  }
}
async function submit() {
  if (!form.cattleIds.length || !form.toBarnId || !form.reason)
    return ElMessage.warning("请选择牛只、目标栏舍并填写原因");
  saving.value = true;
  try {
    await batchTransfer({
      items: form.cattleIds.map((id) => {
        const c = cattle.value.find((x) => x.cattleId === id)!;
        return { cattleId: id, version: c.version };
      }),
      toBarnId: form.toBarnId,
      toHerdId: form.toHerdId || undefined,
      transferDate: form.transferDate + ":00",
      reason: form.reason,
    });
    dialog.value = false;
    ElMessage.success(`已完成 ${form.cattleIds.length} 头牛批量转群`);
    await load();
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || "批量转群失败");
  } finally {
    saving.value = false;
  }
}
async function undoTransfer(row: TransferItem) {
  const c = await getCattlePage({
    page: 1,
    pageSize: 1,
    keyword: row.earTagNo,
  });
  const record = c.items.find((x) => x.cattleId === row.cattleId);
  if (!record) return ElMessage.error("未找到牛只当前版本");
  const { value } = await ElMessageBox.prompt("请填写作废原因", "作废转群", {
    inputPattern: /\S+/,
    inputErrorMessage: "原因不能为空",
  });
  await voidTransfer(row.transferId, value, record.version);
  ElMessage.success("转群记录已作废");
  await load();
}
async function undoExit(row: ExitItem) {
  const c = await getCattlePage({
    page: 1,
    pageSize: 1,
    keyword: row.earTagNo,
  });
  const record = c.items.find((x) => x.cattleId === row.cattleId);
  if (!record) return ElMessage.error("未找到牛只当前版本");
  const { value } = await ElMessageBox.prompt("请填写作废原因", "作废离场", {
    inputPattern: /\S+/,
    inputErrorMessage: "原因不能为空",
  });
  await voidExit(row.exitId, value, record.version);
  ElMessage.success("离场记录已作废并恢复牛只");
  await load();
}
onMounted(load);
</script>
<template>
  <div>
    <div class="page-heading">
      <div>
        <p class="eyebrow">牛只管理</p>
        <h1>转群与离场记录</h1>
      </div>
      <el-button v-if="canManage" type="primary" :icon="Plus" @click="openBatch"
        >批量转群</el-button
      >
    </div>
    <el-tabs v-model="tab"
      ><el-tab-pane label="转群记录" name="transfers" /><el-tab-pane
        v-if="canViewExits"
        label="离场档案"
        name="exits"
    /></el-tabs>
    <section class="table-panel" v-loading="loading">
      <el-table
        v-if="tab === 'transfers'"
        :data="transfers"
        empty-text="暂无转群记录"
        ><el-table-column
          prop="earTagNo"
          label="耳号"
          min-width="140"
        /><el-table-column label="原位置" min-width="160"
          ><template #default="s"
            >{{ s.row.fromBarnName || "-" }} /
            {{ s.row.fromHerdName || "-" }}</template
          ></el-table-column
        ><el-table-column label="目标位置" min-width="160"
          ><template #default="s"
            >{{ s.row.toBarnName }} / {{ s.row.toHerdName || "-" }}</template
          ></el-table-column
        ><el-table-column
          prop="transferDate"
          label="转群时间"
          min-width="170"
        /><el-table-column
          prop="reason"
          label="原因"
          min-width="180"
        /><el-table-column
          prop="operatorName"
          label="经办人"
          width="100"
        /><el-table-column label="状态" width="90"
          ><template #default="s"
            ><el-tag :type="s.row.voided ? 'info' : 'success'" effect="plain">{{
              s.row.voided ? "已作废" : "有效"
            }}</el-tag></template
          ></el-table-column
        ><el-table-column v-if="canManage" label="操作" width="80"
          ><template #default="s"
            ><el-button
              v-if="!s.row.voided"
              link
              type="danger"
              @click="undoTransfer(s.row)"
              >作废</el-button
            ></template
          ></el-table-column
        ></el-table
      >
      <el-table v-else-if="canViewExits" :data="exits" empty-text="暂无离场档案"
        ><el-table-column
          prop="earTagNo"
          label="耳号"
          min-width="140"
        /><el-table-column label="离场类型" width="100"
          ><template #default="s">{{
            exitLabels[s.row.exitType] || s.row.exitType
          }}</template></el-table-column
        ><el-table-column
          prop="exitDate"
          label="离场日期"
          width="120"
        /><el-table-column
          prop="reason"
          label="原因"
          min-width="200"
        /><el-table-column
          prop="operatorName"
          label="经办人"
          width="100"
        /><el-table-column label="状态" min-width="120"
          ><template #default="s"
            ><el-tag
              :type="
                s.row.voided ? 'info' : s.row.restoredAt ? 'warning' : 'success'
              "
              effect="plain"
              >{{
                s.row.voided ? "已作废" : s.row.restoredAt ? "已恢复" : "已离场"
              }}</el-tag
            ></template
          ></el-table-column
        ><el-table-column v-if="canManage" label="操作" width="80"
          ><template #default="s"
            ><el-button
              v-if="!s.row.voided && !s.row.restoredAt"
              link
              type="danger"
              @click="undoExit(s.row)"
              >作废</el-button
            ></template
          ></el-table-column
        ></el-table
      >
    </section>
    <el-dialog v-model="dialog" title="批量转群" width="min(620px,94vw)"
      ><el-form label-position="top"
        ><el-form-item label="选择牛只" required
          ><el-select v-model="form.cattleIds" multiple filterable collapse-tags
            ><el-option
              v-for="c in cattle"
              :key="c.cattleId"
              :label="`${c.earTagNo} ${c.name || ''}`"
              :value="c.cattleId" /></el-select></el-form-item
        ><el-form-item label="目标栏舍" required
          ><el-select v-model="form.toBarnId" @change="form.toHerdId = ''"
            ><el-option
              v-for="b in barns"
              :key="b.barnId"
              :label="`${b.barnName}（${b.cattleCount}/${b.capacity ?? '不限'}）`"
              :value="b.barnId" /></el-select></el-form-item
        ><el-form-item label="目标牛群"
          ><el-select v-model="form.toHerdId" clearable
            ><el-option
              v-for="h in availableHerds"
              :key="h.herdId"
              :label="h.herdName"
              :value="h.herdId" /></el-select></el-form-item
        ><el-form-item label="转群时间"
          ><el-date-picker
            v-model="form.transferDate"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm" /></el-form-item
        ><el-form-item label="转群原因" required
          ><el-input
            v-model="form.reason"
            type="textarea"
            maxlength="255"
            show-word-limit /></el-form-item></el-form
      ><template #footer
        ><el-button @click="dialog = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="submit"
          >确认转群</el-button
        ></template
      ></el-dialog
    >
  </div>
</template>
<style scoped>
.el-select,
.el-date-editor {
  width: 100%;
}
</style>
