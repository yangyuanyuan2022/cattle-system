<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import { ElMessage, ElMessageBox, type UploadFile } from "element-plus";
import { MagicStick, Plus, Upload } from "@element-plus/icons-vue";
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
  recommendBreedingNutrition,
  recommendMicronutrients,
  recommendFormula,
  updateFormula,
  updateIngredient,
  type Formula,
  type FormulaRecommendation,
  type Ingredient,
  type MixingExecution,
  type MixingOrder,
  type MicronutrientRecommendation,
  type BreedingNutritionRecommendation,
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
const recommendation = ref<FormulaRecommendation | null>(null);
const micronutrientRecommendation = ref<MicronutrientRecommendation | null>(null);
const breedingRecommendation = ref<BreedingNutritionRecommendation | null>(null);
const ingredientSearch = ref("");
const ingredientTypeFilter = ref("");
const recommendationIngredientSearch = ref("");
const ingredient = reactive({
    ingredientName: "",
    ingredientType: "ENERGY",
    dryMatterPct: 0,
    tdnPct: 0 as number | null,
    crudeProteinPct: 0,
    starchPct: 0 as number | null,
    energyValue: 0 as number | null,
    gainEnergyValue: 0 as number | null,
    ndfPct: 0,
    rdpPct: 0 as number | null,
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
const recommendationInput = reactive({
  bodySize: "LARGE",
  currentWeightKg: 400,
  targetWeightKg: 600,
  feedingDays: 180,
  roughageDryMatterPct: 55,
  proteinFeedDryMatterPct: 18,
  autoBalanceProtein: true,
  ingredientIds: [] as string[],
});
const micronutrientInput = reactive({
  productionStage: "GROWING",
  dryMatterIntakeKg: 8.6,
  cattleCount: 100,
});
const breedingInput = reactive({
  productionStage: "LATE_PREGNANCY",
  weightKg: 500,
  milkKgPerDay: 8,
  cattleCount: 100,
});
const ingredientTypeLabels: Record<string, string> = {
  ROUGHAGE: "粗饲料",
  ENERGY: "能量饲料",
  PROTEIN: "蛋白饲料",
  MINERAL: "矿物质（手动添加）",
  ADDITIVE: "添加剂（手动添加）",
  WATER: "水（手动添加）",
  OTHER: "待分类（手动添加）",
};
const automaticIngredientGroups = computed(() =>
  ["ROUGHAGE", "ENERGY", "PROTEIN"].map((type) => ({
    type,
    label: ingredientTypeLabels[type],
    items: ingredients.value.filter((item) => item.status === "ENABLED" && item.ingredientType === type),
  })).filter((group) => group.items.length),
);
const selectableIngredientGroups = computed(() => {
  const keyword = recommendationIngredientSearch.value.trim().toLowerCase();
  return automaticIngredientGroups.value
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => !keyword || item.ingredientName.toLowerCase().includes(keyword)),
    }))
    .filter((group) => group.items.length);
});
const selectedRecommendationIngredients = computed(() =>
  recommendationInput.ingredientIds
    .map((id) => ingredients.value.find((item) => item.ingredientId === id))
    .filter((item): item is Ingredient => Boolean(item)),
);
const visibleRecommendationIngredientIds = computed(() =>
  selectableIngredientGroups.value.flatMap((group) => group.items.map((item) => item.ingredientId)),
);
function selectVisibleRecommendationIngredients() {
  recommendationInput.ingredientIds = Array.from(new Set([
    ...recommendationInput.ingredientIds,
    ...visibleRecommendationIngredientIds.value,
  ]));
}
function removeRecommendationIngredient(id: string) {
  recommendationInput.ingredientIds = recommendationInput.ingredientIds.filter((item) => item !== id);
}
const filteredIngredients = computed(() => {
  const keyword = ingredientSearch.value.trim().toLowerCase();
  return ingredients.value.filter((item) =>
    (!ingredientTypeFilter.value || item.ingredientType === ingredientTypeFilter.value) &&
    (!keyword || item.ingredientName.toLowerCase().includes(keyword)),
  );
});
const ingredientStats = computed(() => ["ROUGHAGE", "ENERGY", "PROTEIN", "MINERAL", "ADDITIVE", "WATER"].map((type) => ({
  type,
  label: ingredientTypeLabels[type],
  count: ingredients.value.filter((item) => item.ingredientType === type).length,
})).filter((item) => item.count));
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
          tdnPct: 0,
          crudeProteinPct: 0,
          starchPct: 0,
          energyValue: 0,
          gainEnergyValue: 0,
          ndfPct: 0,
          rdpPct: 0,
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
function openRecommendation() {
  Object.assign(recommendationInput, {
    bodySize: "LARGE",
    currentWeightKg: 400,
    targetWeightKg: 600,
    feedingDays: 180,
    roughageDryMatterPct: 55,
    proteinFeedDryMatterPct: 18,
    autoBalanceProtein: true,
    ingredientIds: [],
  });
  recommendation.value = null;
  recommendationIngredientSearch.value = "";
  dialog.value = "recommend";
}
async function generateRecommendation() {
  if (recommendationInput.targetWeightKg <= recommendationInput.currentWeightKg)
    return ElMessage.warning("目标体重必须大于当前体重");
  if (recommendationInput.ingredientIds.length < 2)
    return ElMessage.warning("至少选择两种可用原料");
  saving.value = true;
  try {
    recommendation.value = await recommendFormula({
      bodySize: recommendationInput.bodySize,
      currentWeightKg: recommendationInput.currentWeightKg,
      targetWeightKg: recommendationInput.targetWeightKg,
      feedingDays: recommendationInput.feedingDays,
      roughageDryMatterPct: recommendationInput.roughageDryMatterPct,
      proteinFeedDryMatterPct: recommendationInput.autoBalanceProtein ? undefined : recommendationInput.proteinFeedDryMatterPct,
      ingredientIds: recommendationInput.ingredientIds,
    });
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || "配方推荐失败");
  } finally {
    saving.value = false;
  }
}
async function generateMicronutrients() {
  saving.value = true;
  try {
    micronutrientRecommendation.value = await recommendMicronutrients(micronutrientInput);
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || "微量营养需要量计算失败");
  } finally {
    saving.value = false;
  }
}
async function generateBreedingNutrition() {
  saving.value = true;
  try {
    breedingRecommendation.value = await recommendBreedingNutrition({
      productionStage: breedingInput.productionStage,
      weightKg: breedingInput.weightKg,
      milkKgPerDay: breedingInput.productionStage === "LACTATION" ? breedingInput.milkKgPerDay : undefined,
      cattleCount: breedingInput.cattleCount,
    });
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || "母牛营养需要量计算失败");
  } finally {
    saving.value = false;
  }
}
const breedingStageLabel = computed(() => ({
  REPLACEMENT_GROWTH: "后备母牛生长",
  MAINTENANCE: "成年母牛维持",
  LATE_PREGNANCY: "妊娠后期",
  LACTATION: "泌乳期",
}[breedingInput.productionStage] || breedingInput.productionStage));
function amountRange(min: number, max: number) {
  return Number(min) === Number(max) ? String(min) : `${min}-${max}`;
}
function useRecommendation() {
  const result = recommendation.value;
  if (!result) return;
  editingFormula.value = null;
  Object.assign(formula, {
    formulaName: `${recommendationInput.currentWeightKg}-${recommendationInput.targetWeightKg}kg育肥配方`,
    targetType: "CUSTOM",
    targetObjectId: "",
    dailyIntakeKg: result.dailyIntakeKg,
    remark: `系统建议：目标日增重 ${result.averageDailyGainKg} kg，育肥 ${recommendationInput.feedingDays} 天；保存前已由牛场主复核`,
    items: result.items.map((item) => ({
      ingredientId: item.ingredientId,
      ratioPct: item.ratioPct,
      dailyAmountKg: item.dailyAmountKg,
    })),
  });
  dialog.value = "formula";
}
async function openMicronutrientsFromRecommendation() {
  if (!recommendation.value) return;
  micronutrientInput.dryMatterIntakeKg = Number(recommendation.value.dryMatterTargetKg);
  dialog.value = "";
  tab.value = "micronutrients";
  await generateMicronutrients();
}
function nutrientStatus(value: number, min: number, max: number) {
  if (value < min) return { label: "偏低", type: "warning" as const };
  if (value > max) return { label: "偏高", type: "danger" as const };
  return { label: "达标", type: "success" as const };
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
        <el-button
          v-if="tab === 'formulas'"
          :icon="MagicStick"
          @click="openRecommendation"
          >智能生成</el-button
        >
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
          v-if="!isWorker"
          label="矿物质维生素"
          name="micronutrients" /><el-tab-pane
          v-if="!isWorker"
          label="母牛营养"
          name="breeding" /><el-tab-pane
          label="配料单"
        name="orders" /><el-tab-pane label="执行记录" name="executions"
    /></el-tabs>
    <section v-if="tab === 'ingredients'" class="table-panel">
      <div class="ingredient-summary">
        <button v-for="item in ingredientStats" :key="item.type" type="button" :class="{ active: ingredientTypeFilter === item.type }" @click="ingredientTypeFilter = ingredientTypeFilter === item.type ? '' : item.type">
          <span>{{ item.label.replace('（手动添加）', '') }}</span><b>{{ item.count }}</b>
        </button>
      </div>
      <div class="ingredient-tools">
        <el-input v-model="ingredientSearch" clearable placeholder="搜索原料名称" />
        <el-select v-model="ingredientTypeFilter" clearable placeholder="全部类型">
          <el-option v-for="item in ingredientStats" :key="item.type" :label="item.label" :value="item.type" />
        </el-select>
        <span>共 {{ filteredIngredients.length }} 种</span>
      </div>
      <el-table v-loading="loading" :data="filteredIngredients"
        ><el-table-column
          prop="ingredientName"
          label="原料"
          min-width="140"
          /><el-table-column label="类型" min-width="130"><template #default="s">{{ ingredientTypeLabels[s.row.ingredientType] || s.row.ingredientType }}</template></el-table-column><el-table-column
            prop="dryMatterPct"
            label="干物质 %"
          /><el-table-column
            prop="tdnPct"
            label="TDN %"
          /><el-table-column
            prop="crudeProteinPct"
            label="粗蛋白 %"
          /><el-table-column prop="starchPct" label="淀粉 %" /><el-table-column prop="ndfPct" label="NDF %" /><el-table-column
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
      <section v-if="tab === 'micronutrients'" class="micro-panel">
        <div class="micro-inputs">
          <div>
            <strong>微量营养需要量</strong>
            <span>按干物质采食量计算每头及整群目标摄入量</span>
          </div>
          <el-form label-position="top" class="micro-form">
            <el-form-item label="生产阶段">
              <el-segmented v-model="micronutrientInput.productionStage" :options="[{ label: '生长育肥', value: 'GROWING' }, { label: '母牛妊娠', value: 'PREGNANT' }, { label: '母牛哺乳前期', value: 'LACTATING' }]" />
            </el-form-item>
            <el-form-item label="干物质采食量（kg/头/天）"><el-input-number v-model="micronutrientInput.dryMatterIntakeKg" :min="0.1" :max="40" :precision="2" /></el-form-item>
            <el-form-item label="牛只数量"><el-input-number v-model="micronutrientInput.cattleCount" :min="1" :max="100000" /></el-form-item>
            <el-form-item label=" "><el-button type="primary" :icon="MagicStick" :loading="saving" @click="generateMicronutrients">计算需要量</el-button></el-form-item>
          </el-form>
        </div>
        <template v-if="micronutrientRecommendation">
          <div class="micro-summary">
            <span>干物质采食量<b>{{ micronutrientRecommendation.dryMatterIntakeKg }} kg/头/天</b></span>
            <span>计算牛数<b>{{ micronutrientRecommendation.cattleCount }} 头</b></span>
            <span>营养指标<b>{{ micronutrientRecommendation.items.length }} 项</b></span>
          </div>
          <el-table :data="micronutrientRecommendation.items" stripe>
            <el-table-column prop="category" label="类别" width="100" />
            <el-table-column prop="nutrientName" label="营养素" width="110" />
            <el-table-column label="目标浓度" width="150"><template #default="s">{{ amountRange(s.row.targetMin, s.row.targetMax) }} {{ s.row.concentrationUnit }}</template></el-table-column>
            <el-table-column label="每头每天" width="170"><template #default="s"><b>{{ amountRange(s.row.dailyMinPerHead, s.row.dailyMaxPerHead) }}</b> {{ s.row.intakeUnit }}</template></el-table-column>
            <el-table-column label="整群每天" width="180"><template #default="s"><b>{{ amountRange(s.row.herdDailyMin, s.row.herdDailyMax) }}</b> {{ s.row.intakeUnit.replace('/头', '') }}</template></el-table-column>
            <el-table-column prop="maximumTolerableConcentration" label="最大耐受浓度" width="170"><template #default="s">{{ s.row.maximumTolerableConcentration || '-' }}</template></el-table-column>
            <el-table-column prop="deficiencySymptoms" label="主要缺乏风险" min-width="260" show-overflow-tooltip />
          </el-table>
          <el-alert v-for="warning in micronutrientRecommendation.warnings" :key="warning" :title="warning" type="warning" :closable="false" class="recommend-warning" />
        </template>
        <el-empty v-else description="设置生产阶段、干物质采食量和牛数后计算" />
      </section>
      <section v-if="tab === 'breeding'" class="micro-panel">
        <div class="micro-inputs breeding-inputs">
          <div>
            <strong>繁殖母牛营养需要量</strong>
            <span>按工作簿中后备母牛、成年母牛维持、妊娠后期及泌乳增量标准计算</span>
          </div>
          <el-form label-position="top" class="breeding-form">
            <el-form-item label="生产阶段">
              <el-select v-model="breedingInput.productionStage">
                <el-option label="后备母牛生长" value="REPLACEMENT_GROWTH" />
                <el-option label="成年母牛维持" value="MAINTENANCE" />
                <el-option label="妊娠后期" value="LATE_PREGNANCY" />
                <el-option label="泌乳期" value="LACTATION" />
              </el-select>
            </el-form-item>
            <el-form-item label="平均体重（kg）"><el-input-number v-model="breedingInput.weightKg" :min="50" :max="1000" /></el-form-item>
            <el-form-item v-if="breedingInput.productionStage === 'LACTATION'" label="日产奶量（kg/头）"><el-input-number v-model="breedingInput.milkKgPerDay" :min="0.1" :max="30" :precision="1" /></el-form-item>
            <el-form-item label="牛只数量"><el-input-number v-model="breedingInput.cattleCount" :min="1" :max="100000" /></el-form-item>
            <el-form-item label=" "><el-button type="primary" :icon="MagicStick" :loading="saving" @click="generateBreedingNutrition">计算需要量</el-button></el-form-item>
          </el-form>
        </div>
        <template v-if="breedingRecommendation">
          <div class="standard-reference">
            <strong>{{ breedingStageLabel }}</strong>
            <span>输入体重 {{ breedingInput.weightKg }} kg，采用工作簿最接近的 {{ breedingRecommendation.referenceWeightKg }} kg 档位</span>
            <span>共 {{ breedingRecommendation.cattleCount }} 头；下列数值均为每日需要量</span>
          </div>
          <div class="breeding-concentrations">
            <span>粗蛋白浓度<b>{{ breedingRecommendation.crudeProteinPct }}%</b></span>
            <span>TDN 浓度<b>{{ breedingRecommendation.tdnPct }}%</b></span>
            <span>钙浓度<b>{{ breedingRecommendation.calciumPct }}%</b></span>
            <span>磷浓度<b>{{ breedingRecommendation.phosphorusPct }}%</b></span>
          </div>
          <el-table :data="[
            { name: '干物质采食量', head: breedingRecommendation.perHeadDaily.dryMatterIntakeKg, herd: breedingRecommendation.herdDaily.dryMatterIntakeKg, unit: 'kg' },
            { name: '粗蛋白', head: breedingRecommendation.perHeadDaily.crudeProteinG, herd: breedingRecommendation.herdDaily.crudeProteinG, unit: 'g' },
            { name: 'TDN', head: breedingRecommendation.perHeadDaily.tdnKg, herd: breedingRecommendation.herdDaily.tdnKg, unit: 'kg' },
            { name: '消化能', head: breedingRecommendation.perHeadDaily.digestibleEnergyMcal, herd: breedingRecommendation.herdDaily.digestibleEnergyMcal, unit: 'Mcal' },
            { name: '代谢能', head: breedingRecommendation.perHeadDaily.metabolizableEnergyMcal, herd: breedingRecommendation.herdDaily.metabolizableEnergyMcal, unit: 'Mcal' },
            { name: '钙', head: breedingRecommendation.perHeadDaily.calciumG, herd: breedingRecommendation.herdDaily.calciumG, unit: 'g' },
            { name: '磷', head: breedingRecommendation.perHeadDaily.phosphorusG, herd: breedingRecommendation.herdDaily.phosphorusG, unit: 'g' },
            { name: '维生素 A', head: breedingRecommendation.perHeadDaily.vitaminAThousandIu, herd: breedingRecommendation.herdDaily.vitaminAThousandIu, unit: '千 IU' },
          ]" stripe>
            <el-table-column prop="name" label="营养指标" min-width="140" />
            <el-table-column label="每头每天" min-width="180"><template #default="s"><b>{{ s.row.head }}</b> {{ s.row.unit }}</template></el-table-column>
            <el-table-column label="整群每天" min-width="180"><template #default="s"><b>{{ s.row.herd }}</b> {{ s.row.unit }}</template></el-table-column>
          </el-table>
          <el-alert v-for="warning in breedingRecommendation.warnings" :key="warning" :title="warning" type="warning" :closable="false" class="recommend-warning" />
        </template>
        <el-empty v-else description="选择生产阶段并填写体重、牛数后计算" />
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
          : dialog === 'recommend'
            ? '智能生成育肥配方'
          : dialog === 'formula'
            ? editingFormula
              ? '编辑配方'
              : '新建配方'
            : dialog === 'execution'
              ? '登记实际配料'
              : '配料计算'
      "
      width="min(920px,94vw)"
      ><el-form v-if="dialog === 'ingredient'" label-position="top" class="grid"
        ><el-form-item label="原料名称" required
          ><el-input v-model="ingredient.ingredientName" /></el-form-item
        ><el-form-item label="类型"
          ><el-select v-model="ingredient.ingredientType"
            ><el-option label="能量饲料" value="ENERGY" /><el-option
              label="蛋白饲料"
              value="PROTEIN" /><el-option
              label="粗饲料"
              value="ROUGHAGE" /><el-option label="矿物质（手动添加）" value="MINERAL" /><el-option label="添加剂（手动添加）" value="ADDITIVE" /><el-option label="水（手动添加）" value="WATER" /><el-option label="待分类（手动添加）" value="OTHER" /></el-select></el-form-item
        ><el-form-item label="干物质 %"
          ><el-input-number
            v-model="ingredient.dryMatterPct"
            :max="100" /></el-form-item
        ><el-form-item label="粗蛋白 %"
          ><el-input-number
            v-model="ingredient.crudeProteinPct"
            :max="100" /></el-form-item
        ><el-form-item label="TDN %（干物质基础）"
          ><el-input-number v-model="ingredient.tdnPct" :min="0" :max="200" :precision="2" /></el-form-item
        ><el-form-item label="淀粉 %（干物质基础）"
          ><el-input-number v-model="ingredient.starchPct" :min="0" :max="100" :precision="2" /></el-form-item
        ><el-form-item label="NDF %"
          ><el-input-number
            v-model="ingredient.ndfPct"
            :max="100" /></el-form-item
        ><el-form-item label="维持净能（Mcal/kgDM）"
          ><el-input-number v-model="ingredient.energyValue" :min="0" :precision="3" /></el-form-item
        ><el-form-item label="增重净能（Mcal/kgDM）"
          ><el-input-number v-model="ingredient.gainEnergyValue" :min="0" :precision="3" /></el-form-item
        ><el-form-item label="RDP（占粗蛋白 %）"
          ><el-input-number v-model="ingredient.rdpPct" :min="0" :max="100" :precision="2" /></el-form-item
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
      <div v-else-if="dialog === 'recommend'" class="recommendation-form">
        <el-alert title="建议配方用于生产决策辅助，启用前必须结合原料化验值和牛群实际情况复核。" type="warning" :closable="false" show-icon />
        <el-form label-position="top" class="grid recommend-inputs">
          <el-form-item label="体型标准" required><el-segmented v-model="recommendationInput.bodySize" :options="[{ label: '大体型肉牛', value: 'LARGE' }, { label: '中体型肉牛', value: 'MEDIUM' }]" /></el-form-item>
          <el-form-item label="当前平均体重（kg）" required><el-input-number v-model="recommendationInput.currentWeightKg" :min="80" :max="1200" /></el-form-item>
          <el-form-item label="目标体重（kg）" required><el-input-number v-model="recommendationInput.targetWeightKg" :min="100" :max="1500" /></el-form-item>
          <el-form-item label="计划育肥天数" required><el-input-number v-model="recommendationInput.feedingDays" :min="30" :max="730" /></el-form-item>
          <el-form-item label="粗饲料干物质占比（%）" required><el-input-number v-model="recommendationInput.roughageDryMatterPct" :min="35" :max="80" /></el-form-item>
          <el-form-item label="蛋白料比例"><el-switch v-model="recommendationInput.autoBalanceProtein" inline-prompt active-text="自动" inactive-text="手动" /></el-form-item>
          <el-form-item v-if="!recommendationInput.autoBalanceProtein" label="蛋白料干物质占比（%）" required><el-input-number v-model="recommendationInput.proteinFeedDryMatterPct" :min="0" :max="35" /></el-form-item>
          <el-form-item label="可使用原料" required>
            <el-popover placement="bottom-start" :width="520" trigger="click" popper-class="ingredient-picker-popover">
              <template #reference>
                <el-button class="ingredient-picker-trigger">
                  <span>{{ recommendationInput.ingredientIds.length ? `已选择 ${recommendationInput.ingredientIds.length} 种原料` : '点击选择原料' }}</span>
                  <span>展开</span>
                </el-button>
              </template>
              <div class="ingredient-picker">
                <el-input v-model="recommendationIngredientSearch" clearable placeholder="输入原料名称搜索" />
                <div class="ingredient-picker-actions">
                  <span>找到 {{ visibleRecommendationIngredientIds.length }} 种</span>
                  <el-button link type="primary" @click="selectVisibleRecommendationIngredients">全选当前结果</el-button>
                  <el-button link @click="recommendationInput.ingredientIds = []">清空已选</el-button>
                </div>
                <div class="ingredient-picker-list">
                  <div v-for="group in selectableIngredientGroups" :key="group.type" class="ingredient-picker-group">
                    <strong>{{ group.label }} <small>{{ group.items.length }}</small></strong>
                    <el-checkbox-group v-model="recommendationInput.ingredientIds">
                      <el-checkbox v-for="item in group.items" :key="item.ingredientId" :value="item.ingredientId">{{ item.ingredientName }}</el-checkbox>
                    </el-checkbox-group>
                  </div>
                  <el-empty v-if="!selectableIngredientGroups.length" :image-size="50" description="没有匹配的原料" />
                </div>
              </div>
            </el-popover>
            <div v-if="selectedRecommendationIngredients.length" class="selected-ingredients">
              <el-tag v-for="item in selectedRecommendationIngredients" :key="item.ingredientId" closable @close="removeRecommendationIngredient(item.ingredientId)">{{ item.ingredientName }}</el-tag>
            </div>
          </el-form-item>
        </el-form>
        <div class="recommend-action"><el-button type="primary" :icon="MagicStick" :loading="saving" @click="generateRecommendation">生成建议</el-button></div>
        <template v-if="recommendation">
          <div class="recommend-metrics">
            <span>目标日增重<b>{{ recommendation.averageDailyGainKg }} kg</b></span>
            <span>日采食量<b>{{ recommendation.dailyIntakeKg }} kg</b></span>
            <span>粗蛋白<b>{{ recommendation.estimatedCrudeProteinPct }}%</b></span>
            <span>NDF<b>{{ recommendation.estimatedNdfPct }}%</b></span>
            <span>TDN<b>{{ recommendation.estimatedTdnPct }}%</b></span>
            <span>淀粉<b>{{ recommendation.estimatedStarchPct }}%</b></span>
            <span>RDP<b>{{ recommendation.estimatedRdpPct }}% CP</b></span>
            <span>维持净能<b>{{ recommendation.estimatedMaintenanceNetEnergy }} Mcal/kgDM</b></span>
            <span>增重净能<b>{{ recommendation.estimatedGainNetEnergy }} Mcal/kgDM</b></span>
            <span>预计日成本<b>¥{{ recommendation.estimatedDailyCost }}</b></span>
          </div>
          <div class="standard-reference">
            <strong>采用工作簿营养标准</strong>
            <span>{{ recommendation.standardTarget.bodySize === 'LARGE' ? '大体型' : '中体型' }} · 参考体重 {{ recommendation.standardTarget.referenceWeightKg }} kg · 参考日增重 {{ recommendation.standardTarget.referenceDailyGainKg }} kg</span>
            <span>目标：DMI {{ recommendation.standardTarget.dryMatterIntakeKg }} kg，TDN {{ recommendation.standardTarget.tdnPct }}%，粗蛋白 {{ recommendation.standardTarget.crudeProteinPct }}%，淀粉 {{ recommendation.standardTarget.starchPct }}%，NDF {{ recommendation.standardTarget.ndfPct }}%</span>
          </div>
          <div class="nutrient-checks">
            <div>
              <span>粗蛋白</span><b>{{ recommendation.estimatedCrudeProteinPct }}%</b><small>目标 {{ recommendation.crudeProteinTargetMinPct }}-{{ recommendation.crudeProteinTargetMaxPct }}% · {{ nutrientStatus(recommendation.estimatedCrudeProteinPct, recommendation.crudeProteinTargetMinPct, recommendation.crudeProteinTargetMaxPct).label }}</small>
            </div>
            <div>
              <span>NDF</span><b>{{ recommendation.estimatedNdfPct }}%</b><small>目标 {{ recommendation.ndfTargetMinPct }}-{{ recommendation.ndfTargetMaxPct }}% · {{ nutrientStatus(recommendation.estimatedNdfPct, recommendation.ndfTargetMinPct, recommendation.ndfTargetMaxPct).label }}</small>
            </div>
            <div>
              <span>TDN</span><b>{{ recommendation.estimatedTdnPct }}%</b><small>工作簿目标 {{ recommendation.standardTarget.tdnPct }}%</small>
            </div>
            <div>
              <span>淀粉</span><b>{{ recommendation.estimatedStarchPct }}%</b><small>工作簿目标 {{ recommendation.standardTarget.starchPct }}%</small>
            </div>
            <div>
              <span>RDP</span><b>{{ recommendation.estimatedRdpPct }}% CP</b><small>工作簿目标 {{ recommendation.standardTarget.rdpPct }}% CP</small>
            </div>
            <div>
              <span>增重净能</span><b>{{ recommendation.estimatedGainNetEnergy }}</b><small>目标 {{ recommendation.standardTarget.gainNetEnergy }} Mcal/kgDM</small>
            </div>
            <div>
              <span>粗饲料干物质</span><b>{{ recommendation.roughageDryMatterPct }}%</b><small>按干物质口径 · 已采用</small>
            </div>
            <div>
              <span>蛋白料干物质</span><b>{{ recommendation.proteinFeedDryMatterPct }}%</b><small>{{ recommendationInput.autoBalanceProtein ? '根据粗蛋白目标自动反推' : '采用手动设定' }}</small>
            </div>
            <div>
              <span>成本完整度</span><b>{{ recommendation.pricedCostCoveragePct }}%</b><small>{{ recommendation.missingPriceIngredients.length ? `待补价格：${recommendation.missingPriceIngredients.join('、')}` : '全部原料已定价 · 完整' }}</small>
            </div>
          </div>
          <el-table :data="recommendation.items" size="small">
            <el-table-column prop="ingredientName" label="原料" min-width="140" />
            <el-table-column prop="ratioPct" label="占比 %" />
            <el-table-column prop="dailyAmountKg" label="kg/头/天" />
          </el-table>
          <el-alert v-for="warning in recommendation.warnings" :key="warning" :title="warning" type="warning" :closable="false" class="recommend-warning" />
        </template>
      </div>
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
        ><el-button v-if="dialog === 'recommend' && recommendation" @click="openMicronutrientsFromRecommendation">计算矿物质维生素</el-button
        ><el-button v-if="dialog === 'recommend'" type="primary" :disabled="!recommendation" @click="useRecommendation">采用并继续编辑</el-button
        ><el-button v-else type="primary" :loading="saving" @click="save"
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
.recommend-inputs { margin-top: 18px; }
.ingredient-picker-trigger { width: 100%; display: flex; justify-content: space-between; font-weight: 400; }
.ingredient-picker-trigger span:last-child { color: #6b7772; }
.ingredient-picker-actions { display: flex; align-items: center; gap: 8px; min-height: 34px; }
.ingredient-picker-actions > span { margin-right: auto; color: #75817d; font-size: 13px; }
.ingredient-picker-list { max-height: 340px; overflow: auto; border-top: 1px solid #e7ebe9; }
.ingredient-picker-group { padding: 12px 0 6px; }
.ingredient-picker-group > strong { display: block; margin-bottom: 8px; color: #26332e; }
.ingredient-picker-group small { color: #89938f; font-weight: 400; }
.ingredient-picker-group .el-checkbox-group { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 4px 10px; }
.ingredient-picker-group .el-checkbox { margin-right: 0; min-width: 0; }
.selected-ingredients { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 9px; }
.recommend-action { display: flex; justify-content: flex-end; margin-bottom: 16px; }
.recommend-metrics { display: grid; grid-template-columns: repeat(3, 1fr); border: 1px solid #e4e8e6; margin-bottom: 14px; }
.recommend-metrics span { padding: 10px; color: #75817d; }
.recommend-metrics b { display: block; color: #26332e; margin-top: 5px; }
.recommend-warning { margin-top: 10px; }
.ingredient-summary { display: grid; grid-template-columns: repeat(6, minmax(105px, 1fr)); gap: 8px; margin-bottom: 14px; }
.ingredient-summary button { border: 1px solid #dfe5e2; background: #fff; padding: 10px 12px; text-align: left; cursor: pointer; }
.ingredient-summary button:hover, .ingredient-summary button.active { border-color: #3f7a63; background: #f2f7f4; }
.ingredient-summary span { display: block; color: #75817d; font-size: 12px; }
.ingredient-summary b { display: block; margin-top: 4px; color: #26332e; font-size: 18px; }
.ingredient-tools { display: grid; grid-template-columns: minmax(220px, 1fr) 220px auto; align-items: center; gap: 10px; margin-bottom: 12px; }
.ingredient-tools > span { color: #75817d; white-space: nowrap; }
.nutrient-checks { display: grid; grid-template-columns: repeat(2, 1fr); border: 1px solid #e4e8e6; margin-bottom: 14px; }
.standard-reference { display: grid; grid-template-columns: auto 1fr; gap: 4px 14px; align-items: baseline; padding: 10px 12px; margin-bottom: 12px; border-left: 3px solid #3f7a63; background: #f4f7f5; }
.standard-reference strong { grid-row: 1 / span 2; color: #26332e; }
.standard-reference span { color: #68746f; }
.nutrient-checks > div { display: grid; grid-template-columns: 1fr auto; gap: 4px 10px; align-items: center; padding: 10px; border-bottom: 1px solid #eef1ef; }
.nutrient-checks > div:nth-child(odd) { border-right: 1px solid #eef1ef; }
.nutrient-checks span, .nutrient-checks small { color: #75817d; }
.nutrient-checks small { min-width: 0; overflow-wrap: anywhere; }
.nutrient-checks b { color: #26332e; }
.micro-panel { background: #fff; border: 1px solid #dde3df; padding: 18px; }
.micro-inputs { display: grid; grid-template-columns: 220px 1fr; gap: 24px; align-items: start; border-bottom: 1px solid #e5e9e7; margin-bottom: 16px; }
.micro-inputs > div span { display: block; margin-top: 6px; color: #75817d; line-height: 1.5; }
.micro-form { display: grid; grid-template-columns: 1.5fr 1fr 1fr auto; gap: 12px; align-items: end; }
.micro-summary { display: grid; grid-template-columns: repeat(3, 1fr); border: 1px solid #e4e8e6; margin-bottom: 14px; }
.micro-summary span { padding: 11px; color: #75817d; }
.micro-summary b { display: block; color: #26332e; margin-top: 4px; }
.breeding-form { display: grid; grid-template-columns: repeat(5, minmax(130px, 1fr)); gap: 12px; align-items: end; }
.breeding-concentrations { display: grid; grid-template-columns: repeat(4, 1fr); border: 1px solid #e4e8e6; margin-bottom: 14px; }
.breeding-concentrations span { padding: 11px; color: #75817d; }
.breeding-concentrations b { display: block; margin-top: 4px; color: #26332e; }
@media (max-width: 700px) {
  .cards,
  .grid,
  .line {
    grid-template-columns: 1fr;
  }
  .metrics {
    grid-template-columns: 1fr 1fr;
  }
  .recommend-metrics, .nutrient-checks { grid-template-columns: 1fr 1fr; }
  .ingredient-summary { grid-template-columns: repeat(2, 1fr); }
  .ingredient-tools { grid-template-columns: 1fr; }
  .nutrient-checks > div:nth-child(odd) { border-right: 0; }
  .micro-inputs, .micro-form, .breeding-form { grid-template-columns: 1fr; }
  .micro-summary { grid-template-columns: 1fr; }
  .breeding-concentrations { grid-template-columns: 1fr 1fr; }
}
</style>
