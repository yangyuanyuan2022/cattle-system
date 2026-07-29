<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import { ElMessage, ElMessageBox, type UploadFile } from "element-plus";
import { Plus, Upload } from "@element-plus/icons-vue";
import { getHerds, type Herd } from "../api/location";
import { getUsers, type UserItem } from "../api/user";
import {
  activateFormula,
  calculateOrder,
  cancelOrder,
  confirmOrder,
  createFormula,
  createIngredient,
  executeOrder,
  getExecutions,
  getFormulas,
  getIngredients,
  getOrders,
  importFormula,
  updateFormula,
  updateIngredient,
  type Formula,
  type Ingredient,
  type MixingExecution,
  type MixingOrder,
} from "../api/feeding";
import { getStoredUser } from "../auth/session";
const roles = getStoredUser()?.roles || [],
  canManage = roles.some((r) => ["ADMIN", "FARM_MANAGER"].includes(r)),
  canExecute = roles.some((r) =>
    ["ADMIN", "FARM_MANAGER", "WORKER"].includes(r),
  ),
  isWorker = roles.includes("WORKER");
const tab = ref(
    String(useRoute().query.tab || (isWorker ? "orders" : "ingredients")),
  ),
  loading = ref(false),
  saving = ref(false),
  dialog = ref(""),
  ingredients = ref<Ingredient[]>([]),
  formulas = ref<Formula[]>([]),
  orders = ref<MixingOrder[]>([]),
  executions = ref<MixingExecution[]>([]),
  herds = ref<Herd[]>([]),
  users = ref<UserItem[]>([]),
  editingIngredient = ref<Ingredient | null>(null),
  editingFormula = ref<Formula | null>(null),
  selectedOrder = ref<MixingOrder | null>(null);
const ingredient = reactive({
    ingredientName: "",
    ingredientType: "ENERGY",
    dryMatterPct: 0,
    crudeProteinPct: 0,
    energyValue: 0 as number | null,
    ndfPct: 0,
    unitPrice: 0,
    status: "ENABLED",
    remark: "",
  }),
  formula = reactive({
    formulaName: "",
    targetType: "HERD",
    targetObjectId: "",
    dailyIntakeKg: 0,
    remark: "",
    items: [] as {
      ingredientId: string;
      ratioPct: number;
      dailyAmountKg: number;
    }[],
  }),
  order = reactive({
    formulaId: "",
    targetHerdId: "",
    assigneeId: "",
    cattleCount: 1,
    feedDate: new Date().toISOString().slice(0, 10),
  }),
  execution = reactive({
    deviationNote: "",
    items: [] as {
      ingredientId: string;
      ingredientName: string;
      actualAmountKg: number;
    }[],
  });
const activeFormulas = computed(() =>
    formulas.value.filter((x) => x.status === "ACTIVE"),
  ),
  amountSum = computed(() =>
    formula.items.reduce((n, x) => n + Number(x.dailyAmountKg || 0), 0),
  );
async function load() {
  loading.value = true;
  try {
    const values = await Promise.all([
      isWorker ? Promise.resolve([]) : getIngredients(),
      getFormulas(),
      getOrders(),
      getExecutions(),
      getHerds("ENABLED"),
      canManage ? getUsers() : Promise.resolve([]),
    ]);
    [
      ingredients.value,
      formulas.value,
      orders.value,
      executions.value,
      herds.value,
      users.value,
    ] = values as [
      Ingredient[],
      Formula[],
      MixingOrder[],
      MixingExecution[],
      Herd[],
      UserItem[],
    ];
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || "配料数据加载失败");
  } finally {
    loading.value = false;
  }
}
function addLine() {
  formula.items.push({ ingredientId: "", ratioPct: 0, dailyAmountKg: 0 });
}
function openIngredient(row?: Ingredient) {
  editingIngredient.value = row || null;
  Object.assign(
    ingredient,
    row
      ? { ...row, energyValue: row.energyValue ?? 0 }
      : {
          ingredientName: "",
          ingredientType: "ENERGY",
          dryMatterPct: 0,
          crudeProteinPct: 0,
          energyValue: 0,
          ndfPct: 0,
          unitPrice: 0,
          status: "ENABLED",
          remark: "",
        },
  );
  dialog.value = "ingredient";
}
function openFormula(row?: Formula) {
  editingFormula.value = row || null;
  Object.assign(
    formula,
    row
      ? {
          formulaName: row.formulaName,
          targetType: row.targetType,
          targetObjectId: row.targetObjectId || "",
          dailyIntakeKg: row.dailyIntakeKg,
          remark: "",
          items: row.items.map((x) => ({
            ingredientId: x.ingredientId,
            ratioPct: x.ratioPct,
            dailyAmountKg: x.dailyAmountKg,
          })),
        }
      : {
          formulaName: "",
          targetType: "HERD",
          targetObjectId: "",
          dailyIntakeKg: 0,
          remark: "",
          items: [],
        },
  );
  if (!formula.items.length) addLine();
  dialog.value = "formula";
}
function openOrder() {
  Object.assign(order, {
    formulaId: "",
    targetHerdId: "",
    assigneeId: "",
    cattleCount: 1,
    feedDate: new Date().toISOString().slice(0, 10),
  });
  dialog.value = "order";
}
function openExecution(row: MixingOrder) {
  selectedOrder.value = row;
  execution.deviationNote = "";
  execution.items = row.items.map((x) => ({
    ingredientId: x.ingredientId,
    ingredientName: x.ingredientName,
    actualAmountKg: Number(x.adjustedAmountKg ?? x.plannedAmountKg),
  }));
  dialog.value = "execution";
}
async function save() {
  saving.value = true;
  try {
    if (dialog.value === "ingredient") {
      if (!ingredient.ingredientName) throw new Error("请填写原料名称");
      editingIngredient.value
        ? await updateIngredient(editingIngredient.value.ingredientId, {
            ...ingredient,
            version: editingIngredient.value.version,
          })
        : await createIngredient(ingredient);
    } else if (dialog.value === "formula") {
      if (!formula.formulaName || !formula.items.length)
        throw new Error("请填写配方名称和明细");
      const payload = {
        ...formula,
        targetObjectId: formula.targetObjectId || undefined,
      };
      editingFormula.value
        ? await updateFormula(editingFormula.value.formulaId, {
            ...payload,
            version: editingFormula.value.rowVersion,
          })
        : await createFormula(payload);
    } else if (dialog.value === "order") {
      if (!order.formulaId || !order.assigneeId)
        throw new Error("请选择配方和负责人");
      await calculateOrder({
        ...order,
        targetHerdId: order.targetHerdId || undefined,
      });
    } else if (dialog.value === "execution" && selectedOrder.value) {
      await executeOrder(
        selectedOrder.value,
        execution.items.map((x) => ({
          ingredientId: x.ingredientId,
          actualAmountKg: x.actualAmountKg,
        })),
        execution.deviationNote,
      );
    }
    dialog.value = "";
    ElMessage.success("保存成功");
    await load();
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || e.message || "保存失败");
  } finally {
    saving.value = false;
  }
}
async function act(fn: () => Promise<unknown>, msg: string) {
  try {
    await fn();
    ElMessage.success(msg);
    await load();
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || "操作失败");
  }
}
async function cancel(row: MixingOrder) {
  const { value } = await ElMessageBox.prompt("请填写取消原因", "取消配料单", {
    inputPattern: /\S+/,
    inputErrorMessage: "原因不能为空",
  });
  await act(() => cancelOrder(row, value), "配料单已取消");
}
async function uploadFormula(file: UploadFile) {
  if (!file.raw) return;
  const { value } = await ElMessageBox.prompt("请输入配方名称", "导入配方", {
    inputPattern: /\S+/,
    inputErrorMessage: "名称不能为空",
  });
  saving.value = true;
  try {
    await importFormula(file.raw, value);
    ElMessage.success("配方导入成功");
    await load();
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || "配方导入失败");
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
        <p class="eyebrow">饲喂执行闭环</p>
        <h1>配料管理</h1>
      </div>
      <div v-if="canManage" class="filter-actions">
        <el-upload
          v-if="tab === 'formulas'"
          :auto-upload="false"
          :show-file-list="false"
          accept=".xlsx,.xls"
          :on-change="uploadFormula"
          ><el-button :icon="Upload">导入配方</el-button></el-upload
        ><el-button
          v-if="['ingredients', 'formulas', 'orders'].includes(tab)"
          type="primary"
          :icon="Plus"
          @click="
            tab === 'ingredients'
              ? openIngredient()
              : tab === 'formulas'
                ? openFormula()
                : openOrder()
          "
          >{{
            tab === "ingredients"
              ? "新增原料"
              : tab === "formulas"
                ? "新建配方"
                : "配料计算"
          }}</el-button
        >
      </div>
    </div>
    <el-tabs v-model="tab"
      ><el-tab-pane
        v-if="!isWorker"
        label="原料档案"
        name="ingredients" /><el-tab-pane
        label="日粮配方"
        name="formulas" /><el-tab-pane
        label="配料单"
        name="orders" /><el-tab-pane label="执行记录" name="executions"
    /></el-tabs>
    <section v-if="tab === 'ingredients'" class="table-panel">
      <el-table v-loading="loading" :data="ingredients"
        ><el-table-column
          prop="ingredientName"
          label="原料"
          min-width="140"
        /><el-table-column prop="ingredientType" label="类型" /><el-table-column
          prop="dryMatterPct"
          label="干物质 %"
        /><el-table-column
          prop="crudeProteinPct"
          label="粗蛋白 %"
        /><el-table-column prop="ndfPct" label="NDF %" /><el-table-column
          prop="unitPrice"
          label="单价 元/kg"
        /><el-table-column label="状态"
          ><template #default="s"
            ><el-tag
              :type="s.row.status === 'ENABLED' ? 'success' : 'info'"
              effect="plain"
              >{{ s.row.status === "ENABLED" ? "启用" : "停用" }}</el-tag
            ></template
          ></el-table-column
        ><el-table-column v-if="canManage" label="操作" width="80"
          ><template #default="s"
            ><el-button link type="primary" @click="openIngredient(s.row)"
              >编辑</el-button
            ></template
          ></el-table-column
        ></el-table
      >
    </section>
    <section v-if="tab === 'formulas'" class="cards">
      <article v-for="f in formulas" :key="f.formulaId">
        <header>
          <strong>{{ f.formulaName }} V{{ f.versionNo }}</strong
          ><el-tag :type="f.status === 'ACTIVE' ? 'success' : 'info'">{{
            f.status === "ACTIVE" ? "已启用" : "草稿"
          }}</el-tag>
        </header>
        <div class="metrics">
          <span
            >干物质<b>{{ f.dryMatterKg }} kg</b></span
          ><span
            >粗蛋白<b>{{ f.crudeProteinPct }}%</b></span
          ><span
            >NDF<b>{{ f.ndfPct }}%</b></span
          ><span
            >日成本<b>¥{{ f.dailyCost }}</b></span
          >
        </div>
        <el-table :data="f.items" size="small"
          ><el-table-column
            prop="ingredientName"
            label="原料" /><el-table-column
            prop="dailyAmountKg"
            label="kg/头" /><el-table-column prop="ratioPct" label="占比 %"
        /></el-table>
        <div v-if="canManage && f.status === 'DRAFT'" class="card-actions">
          <el-button link @click="openFormula(f)">编辑</el-button
          ><el-button
            link
            type="primary"
            @click="
              act(
                () => activateFormula(f.formulaId, f.rowVersion),
                '配方已启用',
              )
            "
            >审核并启用</el-button
          >
        </div>
      </article>
    </section>
    <section v-if="tab === 'orders'" class="table-panel">
      <el-table v-loading="loading" :data="orders"
        ><el-table-column type="expand"
          ><template #default="s"
            ><el-table :data="s.row.items" size="small"
              ><el-table-column
                prop="ingredientName"
                label="原料" /><el-table-column
                prop="plannedAmountKg"
                label="计划 kg" /><el-table-column
                prop="adjustedAmountKg"
                label="调整后 kg" /></el-table></template></el-table-column
        ><el-table-column prop="feedDate" label="日期" /><el-table-column
          prop="formulaName"
          label="配方"
        /><el-table-column prop="herdName" label="牛群" /><el-table-column
          prop="cattleCount"
          label="头数"
        /><el-table-column prop="totalKg" label="总量 kg" /><el-table-column
          prop="status"
          label="状态"
        /><el-table-column label="操作" width="180"
          ><template #default="s"
            ><el-button
              v-if="canManage && s.row.status === 'PENDING_CONFIRM'"
              link
              type="primary"
              @click="act(() => confirmOrder(s.row), '已确认并生成任务')"
              >确认</el-button
            ><el-button
              v-if="canExecute && s.row.status === 'CONFIRMED'"
              link
              type="success"
              @click="openExecution(s.row)"
              >登记执行</el-button
            ><el-button
              v-if="
                canManage &&
                ['PENDING_CONFIRM', 'CONFIRMED'].includes(s.row.status)
              "
              link
              type="danger"
              @click="cancel(s.row)"
              >取消</el-button
            ></template
          ></el-table-column
        ></el-table
      >
    </section>
    <section v-if="tab === 'executions'" class="table-panel">
      <el-table v-loading="loading" :data="executions" empty-text="暂无执行记录"
        ><el-table-column
          prop="executionTime"
          label="执行时间"
          min-width="180"
        /><el-table-column
          prop="executorName"
          label="执行人"
          width="110"
        /><el-table-column
          prop="actualSummary"
          label="实际投料"
          min-width="240"
        /><el-table-column prop="deviationNote" label="偏差说明" min-width="180"
          ><template #default="s">{{
            s.row.deviationNote || "-"
          }}</template></el-table-column
        ></el-table
      >
    </section>
    <el-dialog
      v-model="dialog"
      :title="
        dialog === 'ingredient'
          ? editingIngredient
            ? '编辑原料'
            : '新增原料'
          : dialog === 'formula'
            ? editingFormula
              ? '编辑配方'
              : '新建配方'
            : dialog === 'execution'
              ? '登记实际配料'
              : '配料计算'
      "
      width="min(760px,94vw)"
      ><el-form v-if="dialog === 'ingredient'" label-position="top" class="grid"
        ><el-form-item label="原料名称" required
          ><el-input v-model="ingredient.ingredientName" /></el-form-item
        ><el-form-item label="类型"
          ><el-select v-model="ingredient.ingredientType"
            ><el-option label="能量饲料" value="ENERGY" /><el-option
              label="蛋白饲料"
              value="PROTEIN" /><el-option
              label="粗饲料"
              value="ROUGHAGE" /></el-select></el-form-item
        ><el-form-item label="干物质 %"
          ><el-input-number
            v-model="ingredient.dryMatterPct"
            :max="100" /></el-form-item
        ><el-form-item label="粗蛋白 %"
          ><el-input-number
            v-model="ingredient.crudeProteinPct"
            :max="100" /></el-form-item
        ><el-form-item label="NDF %"
          ><el-input-number
            v-model="ingredient.ndfPct"
            :max="100" /></el-form-item
        ><el-form-item label="单价 元/kg"
          ><el-input-number
            v-model="ingredient.unitPrice"
            :min="0"
            :precision="2" /></el-form-item
        ><el-form-item v-if="editingIngredient" label="状态"
          ><el-segmented
            v-model="ingredient.status"
            :options="[
              { label: '启用', value: 'ENABLED' },
              { label: '停用', value: 'DISABLED' },
            ]" /></el-form-item
      ></el-form>
      <el-form v-else-if="dialog === 'formula'" label-position="top"
        ><div class="grid">
          <el-form-item label="配方名称" required
            ><el-input v-model="formula.formulaName" /></el-form-item
          ><el-form-item label="适用牛群"
            ><el-select v-model="formula.targetObjectId" clearable
              ><el-option
                v-for="h in herds"
                :key="h.herdId"
                :label="h.herdName"
                :value="h.herdId" /></el-select></el-form-item
          ><el-form-item label="日采食量 kg/头"
            ><el-input-number
              v-model="formula.dailyIntakeKg"
              :min="0.01"
              :precision="3"
          /></el-form-item>
        </div>
        <div class="line-head">
          <strong>配方明细</strong
          ><span>合计 {{ amountSum.toFixed(3) }} kg</span
          ><el-button link @click="addLine">添加</el-button>
        </div>
        <div v-for="(line, i) in formula.items" :key="i" class="line">
          <el-select v-model="line.ingredientId" placeholder="原料"
            ><el-option
              v-for="x in ingredients.filter((v) => v.status === 'ENABLED')"
              :key="x.ingredientId"
              :label="x.ingredientName"
              :value="x.ingredientId" /></el-select
          ><el-input-number
            v-model="line.dailyAmountKg"
            :min="0.001"
            :precision="3"
          /><el-input-number
            v-model="line.ratioPct"
            :min="0.0001"
            :precision="2"
          /><el-button text type="danger" @click="formula.items.splice(i, 1)"
            >删除</el-button
          >
        </div></el-form
      >
      <el-form v-else-if="dialog === 'order'" label-position="top" class="grid"
        ><el-form-item label="启用配方" required
          ><el-select v-model="order.formulaId"
            ><el-option
              v-for="f in activeFormulas"
              :key="f.formulaId"
              :label="f.formulaName"
              :value="f.formulaId" /></el-select></el-form-item
        ><el-form-item label="目标牛群"
          ><el-select v-model="order.targetHerdId" clearable
            ><el-option
              v-for="h in herds"
              :key="h.herdId"
              :label="h.herdName"
              :value="h.herdId" /></el-select></el-form-item
        ><el-form-item label="负责人" required
          ><el-select v-model="order.assigneeId" filterable
            ><el-option
              v-for="u in users.filter((x) => x.status === 'ENABLED')"
              :key="u.userId"
              :label="u.realName"
              :value="u.userId" /></el-select></el-form-item
        ><el-form-item label="饲喂头数"
          ><el-input-number
            v-model="order.cattleCount"
            :min="1" /></el-form-item
        ><el-form-item label="饲喂日期"
          ><el-date-picker
            v-model="order.feedDate"
            value-format="YYYY-MM-DD" /></el-form-item
      ></el-form>
      <el-form v-else label-position="top"
        ><el-form-item
          v-for="line in execution.items"
          :key="line.ingredientId"
          :label="line.ingredientName"
          ><el-input-number
            v-model="line.actualAmountKg"
            :min="0"
            :precision="3"
          /><span class="unit">kg</span></el-form-item
        ><el-form-item label="偏差说明"
          ><el-input
            v-model="execution.deviationNote"
            type="textarea"
            :rows="3" /></el-form-item></el-form
      ><template #footer
        ><el-button @click="dialog = ''">取消</el-button
        ><el-button type="primary" :loading="saving" @click="save"
          >保存</el-button
        ></template
      ></el-dialog
    >
  </div>
</template>
<style scoped>
.cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(420px, 1fr));
  gap: 14px;
}
.cards article {
  background: #fff;
  border: 1px solid #dde3df;
  padding: 16px;
}
.cards header,
.line-head,
.card-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-actions {
  justify-content: flex-end;
  margin-top: 10px;
}
.metrics {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  margin: 14px 0;
  border: 1px solid #e4e8e6;
}
.metrics span {
  padding: 9px;
  color: #75817d;
}
.metrics b {
  display: block;
  color: #26332e;
}
.grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}
.line {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr auto;
  gap: 8px;
  margin: 10px 0;
}
.el-select,
.el-date-editor {
  width: 100%;
}
.unit {
  margin-left: 8px;
  color: #75817d;
}
@media (max-width: 700px) {
  .cards,
  .grid,
  .line {
    grid-template-columns: 1fr;
  }
  .metrics {
    grid-template-columns: 1fr 1fr;
  }
}
</style>
