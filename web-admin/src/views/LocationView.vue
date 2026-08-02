<script setup lang="ts">
import { onMounted, reactive, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Plus } from "@element-plus/icons-vue";
import {
  createBarn,
  createHerd,
  deleteBarn,
  deleteHerd,
  getBarns,
  getHerds,
  updateBarn,
  updateHerd,
  type Barn,
  type Herd,
} from "../api/location";
import { getStoredUser } from "../auth/session";
const canManage =
    getStoredUser()?.roles.some((role) =>
      ["ADMIN", "FARM_MANAGER"].includes(role),
    ) ?? false,
  barns = ref<Barn[]>([]),
  herds = ref<Herd[]>([]),
  loading = ref(false),
  barnOpen = ref(false),
  herdOpen = ref(false),
  saving = ref(false),
  editingBarn = ref<Barn | null>(null),
  editingHerd = ref<Herd | null>(null);
const barnForm = reactive({
    barnCode: "",
    barnName: "",
    barnType: "FATTENING",
    capacity: 80 as number | null,
    status: "ENABLED",
    remark: "",
  }),
  herdForm = reactive({
    herdCode: "",
    herdName: "",
    herdType: "FATTENING",
    barnId: "",
    status: "ENABLED",
    remark: "",
  });
async function load() {
  loading.value = true;
  try {
    [barns.value, herds.value] = await Promise.all([getBarns(), getHerds()]);
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || "栏舍牛群加载失败");
  } finally {
    loading.value = false;
  }
}
function openBarn(row?: Barn) {
  editingBarn.value = row || null;
  Object.assign(
    barnForm,
    row
      ? {
          barnCode: row.barnCode,
          barnName: row.barnName,
          barnType: row.barnType || "FATTENING",
          capacity: row.capacity,
          status: row.status,
          remark: row.remark || "",
        }
      : {
          barnCode: "",
          barnName: "",
          barnType: "FATTENING",
          capacity: 80,
          status: "ENABLED",
          remark: "",
        },
  );
  barnOpen.value = true;
}
function openHerd(row?: Herd) {
  editingHerd.value = row || null;
  Object.assign(
    herdForm,
    row
      ? {
          herdCode: row.herdCode,
          herdName: row.herdName,
          herdType: row.herdType || "FATTENING",
          barnId: row.barnId || "",
          status: row.status,
          remark: row.remark || "",
        }
      : {
          herdCode: "",
          herdName: "",
          herdType: "FATTENING",
          barnId: "",
          status: "ENABLED",
          remark: "",
        },
  );
  herdOpen.value = true;
}
async function saveBarn() {
  if (!barnForm.barnCode || !barnForm.barnName)
    return ElMessage.warning("请填写栏舍编号和名称");
  saving.value = true;
  try {
    editingBarn.value
      ? await updateBarn(editingBarn.value.barnId, {
          barnName: barnForm.barnName,
          barnType: barnForm.barnType,
          capacity: barnForm.capacity,
          status: barnForm.status,
          remark: barnForm.remark,
          version: editingBarn.value.version,
        })
      : await createBarn({ ...barnForm });
    barnOpen.value = false;
    ElMessage.success("栏舍已保存");
    await load();
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || "保存失败");
  } finally {
    saving.value = false;
  }
}
async function saveHerd() {
  if (!herdForm.herdCode || !herdForm.herdName)
    return ElMessage.warning("请填写牛群编号和名称");
  saving.value = true;
  try {
    editingHerd.value
      ? await updateHerd(editingHerd.value.herdId, {
          herdName: herdForm.herdName,
          herdType: herdForm.herdType,
          barnId: herdForm.barnId || undefined,
          status: herdForm.status,
          remark: herdForm.remark,
          version: editingHerd.value.version,
        })
      : await createHerd({ ...herdForm });
    herdOpen.value = false;
    ElMessage.success("牛群已保存");
    await load();
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || "保存失败");
  } finally {
    saving.value = false;
  }
}
async function removeBarn(row: Barn) {
  try {
    await ElMessageBox.confirm(`确定删除栏舍“${row.barnName}”吗？只有从未被使用的栏舍才能删除。`, "删除栏舍", { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消", confirmButtonClass: "el-button--danger" });
  } catch { return; }
  try {
    await deleteBarn(row.barnId);
    ElMessage.success("栏舍已删除");
    await load();
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || "删除失败");
  }
}
async function removeHerd(row: Herd) {
  try {
    await ElMessageBox.confirm(`确定删除牛群“${row.herdName}”吗？只有从未被使用的牛群才能删除。`, "删除牛群", { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消", confirmButtonClass: "el-button--danger" });
  } catch { return; }
  try {
    await deleteHerd(row.herdId);
    ElMessage.success("牛群已删除");
    await load();
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || "删除失败");
  }
}
onMounted(load);
</script>
<template>
  <div>
    <div class="page-heading">
      <div>
        <p class="eyebrow">位置管理</p>
        <h1>栏舍与牛群</h1>
      </div>
      <div v-if="canManage" class="filter-actions">
        <el-button :icon="Plus" @click="openBarn()">新建栏舍</el-button
        ><el-button type="primary" :icon="Plus" @click="openHerd()"
          >新建牛群</el-button
        >
      </div>
    </div>
    <div v-loading="loading" class="location-grid">
      <section class="table-panel">
        <div class="panel-heading">
          <div>
            <h2>栏舍</h2>
            <p>容量用于运营预警，超限仅提示</p>
          </div>
        </div>
        <el-table :data="barns" empty-text="暂无栏舍"
          ><el-table-column prop="barnCode" label="编号" /><el-table-column
            prop="barnName"
            label="名称"
          /><el-table-column label="在场 / 容量"
            ><template #default="s"
              ><strong>{{ s.row.cattleCount }}</strong> /
              {{ s.row.capacity ?? "不限" }}</template
            ></el-table-column
          ><el-table-column label="状态" width="80"
            ><template #default="s"
              ><el-tag
                :type="s.row.status === 'ENABLED' ? 'success' : 'info'"
                effect="plain"
                >{{ s.row.status === "ENABLED" ? "启用" : "停用" }}</el-tag
              ></template
            ></el-table-column
          ><el-table-column v-if="canManage" label="操作" width="120"
            ><template #default="s"
              ><el-button link type="primary" @click="openBarn(s.row)"
                >编辑</el-button><el-button link type="danger" @click="removeBarn(s.row)">删除</el-button
              ></template
            ></el-table-column
          ></el-table
        >
      </section>
      <section class="table-panel">
        <div class="panel-heading">
          <div>
            <h2>牛群</h2>
            <p>每个牛群对应一个主要栏舍</p>
          </div>
        </div>
        <el-table :data="herds" empty-text="暂无牛群"
          ><el-table-column prop="herdCode" label="编号" /><el-table-column
            prop="herdName"
            label="名称"
          /><el-table-column prop="barnName" label="主要栏舍"
            ><template #default="s">{{
              s.row.barnName || "-"
            }}</template></el-table-column
          ><el-table-column
            prop="cattleCount"
            label="牛只"
            width="70"
          /><el-table-column label="状态" width="80"
            ><template #default="s"
              ><el-tag
                :type="s.row.status === 'ENABLED' ? 'success' : 'info'"
                effect="plain"
                >{{ s.row.status === "ENABLED" ? "启用" : "停用" }}</el-tag
              ></template
            ></el-table-column
          ><el-table-column v-if="canManage" label="操作" width="120"
            ><template #default="s"
              ><el-button link type="primary" @click="openHerd(s.row)"
                >编辑</el-button><el-button link type="danger" @click="removeHerd(s.row)">删除</el-button
              ></template
            ></el-table-column
          ></el-table
        >
      </section>
    </div>
    <el-dialog
      v-model="barnOpen"
      :title="editingBarn ? '编辑栏舍' : '新建栏舍'"
      width="min(520px,92vw)"
      ><el-form label-position="top"
        ><el-form-item label="栏舍编号" required
          ><el-input
            v-model="barnForm.barnCode"
            :disabled="!!editingBarn" /></el-form-item
        ><el-form-item label="栏舍名称" required
          ><el-input v-model="barnForm.barnName" /></el-form-item
        ><el-form-item label="用途"
          ><el-select v-model="barnForm.barnType"
            ><el-option label="育肥" value="FATTENING" /><el-option
              label="繁育"
              value="BREEDING" /><el-option
              label="隔离"
              value="ISOLATION" /></el-select></el-form-item
        ><el-form-item label="容量"
          ><el-input-number
            v-model="barnForm.capacity"
            :min="0" /></el-form-item
        ><el-form-item v-if="editingBarn" label="状态"
          ><el-segmented
            v-model="barnForm.status"
            :options="[
              { label: '启用', value: 'ENABLED' },
              { label: '停用', value: 'DISABLED' },
            ]" /></el-form-item
        ><el-form-item label="备注"
          ><el-input
            v-model="barnForm.remark"
            type="textarea" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="barnOpen = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="saveBarn"
          >保存</el-button
        ></template
      ></el-dialog
    >
    <el-dialog
      v-model="herdOpen"
      :title="editingHerd ? '编辑牛群' : '新建牛群'"
      width="min(520px,92vw)"
      ><el-form label-position="top"
        ><el-form-item label="牛群编号" required
          ><el-input
            v-model="herdForm.herdCode"
            :disabled="!!editingHerd" /></el-form-item
        ><el-form-item label="牛群名称" required
          ><el-input v-model="herdForm.herdName" /></el-form-item
        ><el-form-item label="主要栏舍"
          ><el-select v-model="herdForm.barnId" clearable
            ><el-option
              v-for="b in barns.filter((x) => x.status === 'ENABLED')"
              :key="b.barnId"
              :label="b.barnName"
              :value="b.barnId" /></el-select></el-form-item
        ><el-form-item v-if="editingHerd" label="状态"
          ><el-segmented
            v-model="herdForm.status"
            :options="[
              { label: '启用', value: 'ENABLED' },
              { label: '停用', value: 'DISABLED' },
            ]" /></el-form-item
        ><el-form-item label="备注"
          ><el-input
            v-model="herdForm.remark"
            type="textarea" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="herdOpen = false">取消</el-button
        ><el-button type="primary" :loading="saving" @click="saveHerd"
          >保存</el-button
        ></template
      ></el-dialog
    >
  </div>
</template>
<style scoped>
.location-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
}
.el-select {
  width: 100%;
}
@media (max-width: 980px) {
  .location-grid {
    grid-template-columns: 1fr;
  }
}
</style>
