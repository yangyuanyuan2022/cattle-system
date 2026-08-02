<script setup lang="ts">
import { computed, onMounted, reactive, ref } from "vue";
import { useRoute } from "vue-router";
import { ElMessage, ElMessageBox, type UploadFile } from "element-plus";
import { InfoFilled, MagicStick, Plus, Upload } from "@element-plus/icons-vue";
import { getHerds, type Herd } from "../api/location";
import { getUsers, type UserItem } from "../api/user";
import {
  activateFormula,
  deactivateFormula,
  calculateOrder,
  cancelOrder,
  confirmOrder,
  createFormula,
  createIngredient,
  deleteExecution,
  deleteIngredient,
  deleteFormula,
  deleteOrder,
  executeOrder,
  getExecutions,
  getFormulas,
  getIngredients,
  getOrders,
  importFormula,
  optimizeConcentrate,
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
  dialog = ref<string | boolean>(false),
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
const concentrateIngredientSearch = ref("");
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
    metabolizableEnergyValue: 0 as number | null,
    crudeProteinPct: 0,
    starchPct: 0 as number | null,
    energyValue: 0 as number | null,
    gainEnergyValue: 0 as number | null,
    ndfPct: 0,
    peNdfPct: 0 as number | null,
    adfPct: 0 as number | null,
    ashPct: 0 as number | null,
    crudeFatPct: 0 as number | null,
    calciumPct: 0 as number | null,
    phosphorusPct: 0 as number | null,
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
  productionStage: "FINISHING",
  bodySize: "LARGE",
  currentWeightKg: 400,
  targetWeightKg: 600,
  targetDailyGainKg: 1.1,
  concentrateFormulaId: "",
  roughageDryMatterPct: 55,
  ingredientIds: [] as string[],
  ingredientRatios: {} as Record<string, number>,
});
const concentrateInput = reactive({
  formulaName: "",
  ingredientIds: [] as string[],
  ratios: {} as Record<string, number>,
  targetCrudeProteinPct: 18,
  minimumMetabolizableEnergy: 2.8,
  maximumNdfPct: 25,
  maximumCrudeFatPct: 7,
  minimumStarchPct: 25,
  maximumStarchPct: 65,
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
  return automaticIngredientGroups.value.filter((group) => group.type === "ROUGHAGE")
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
const isConcentrateFormula = (value: Formula) => value.items.length > 0 && value.items.every((line) =>
  ingredients.value.find((item) => item.ingredientId === line.ingredientId)?.ingredientType !== "ROUGHAGE",
);
const concentrateFormulas = computed(() => formulas.value.filter(isConcentrateFormula));
const dailyRationFormulas = computed(() => formulas.value.filter((value) => !isConcentrateFormula(value)));
const concentrateIngredients = computed(() => {
  const keyword = concentrateIngredientSearch.value.trim().toLowerCase();
  return ingredients.value.filter((item) => item.status === "ENABLED" && item.ingredientType !== "ROUGHAGE" && item.ingredientType !== "WATER" && (!keyword || item.ingredientName.toLowerCase().includes(keyword)));
});
const selectedConcentrateIngredients = computed(() => concentrateInput.ingredientIds
  .map((id) => ingredients.value.find((item) => item.ingredientId === id))
  .filter((item): item is Ingredient => Boolean(item)));
const concentrateRatioTotal = computed(() => selectedConcentrateIngredients.value.reduce((sum, item) => sum + Number(concentrateInput.ratios[item.ingredientId] || 0), 0));
const nutrientValue = (item: Ingredient, key: keyof Ingredient) => {
  const value = item[key];
  return typeof value === "number" ? value : null;
};
const blendMetric = (items: Ingredient[], ratios: Record<string, number>, key: keyof Ingredient, dmBasis = true) => {
  if (!items.length || items.some((item) => nutrientValue(item, key) == null || item.dryMatterPct == null)) return null;
  const denominator = dmBasis ? items.reduce((sum, item) => sum + Number(ratios[item.ingredientId] || 0) * Number(item.dryMatterPct || 0) / 100, 0) : 100;
  if (!denominator) return null;
  const numerator = items.reduce((sum, item) => {
    const weight = Number(ratios[item.ingredientId] || 0);
    return sum + weight * (dmBasis ? Number(item.dryMatterPct || 0) / 100 : 1) * Number(nutrientValue(item, key) || 0);
  }, 0);
  return Number((numerator / denominator).toFixed(2));
};
const concentrateSummary = computed(() => {
  const items = selectedConcentrateIngredients.value;
  const ratios = concentrateInput.ratios;
  const price = items.reduce((sum, item) => sum + Number(ratios[item.ingredientId] || 0) / 100 * Number(item.unitPrice || 0), 0);
  return {
    price: Number(price.toFixed(2)), dryMatterPct: blendMetric(items, ratios, "dryMatterPct", false),
    crudeProteinPct: blendMetric(items, ratios, "crudeProteinPct"), tdnPct: blendMetric(items, ratios, "tdnPct"),
    metabolizableEnergyValue: blendMetric(items, ratios, "metabolizableEnergyValue"), energyValue: blendMetric(items, ratios, "energyValue"),
    gainEnergyValue: blendMetric(items, ratios, "gainEnergyValue"), ndfPct: blendMetric(items, ratios, "ndfPct"), peNdfPct: blendMetric(items, ratios, "peNdfPct"), adfPct: blendMetric(items, ratios, "adfPct"),
    ashPct: blendMetric(items, ratios, "ashPct"), crudeFatPct: blendMetric(items, ratios, "crudeFatPct"), calciumPct: blendMetric(items, ratios, "calciumPct"), phosphorusPct: blendMetric(items, ratios, "phosphorusPct"), rdpPct: blendMetric(items, ratios, "rdpPct"),
  };
});
const formulaMetric = (value: Formula, key: keyof Ingredient, dmBasis = true) => {
  const selected = value.items.map((line) => ingredients.value.find((item) => item.ingredientId === line.ingredientId)).filter((item): item is Ingredient => Boolean(item));
  const ratios = Object.fromEntries(value.items.map((line) => [line.ingredientId, Number(line.dailyAmountKg) / Number(value.dailyIntakeKg || 1) * 100]));
  return blendMetric(selected, ratios, key, dmBasis);
};
const recommendationRatioTotal = computed(() =>
  selectedRecommendationIngredients.value.reduce((total, item) => total + Number(recommendationInput.ingredientRatios[item.ingredientId] || 0), 0),
);
function distributeRecommendationRatios() {
  const selected = selectedRecommendationIngredients.value;
  if (!selected.length) return;
  const base = Math.floor((100 / selected.length) * 100) / 100;
  selected.forEach((item, index) => {
    recommendationInput.ingredientRatios[item.ingredientId] = index === selected.length - 1
      ? Number((100 - base * (selected.length - 1)).toFixed(2))
      : base;
  });
}
async function optimizeSelectedConcentrate() {
  if (selectedConcentrateIngredients.value.length < 2) return ElMessage.warning("自动配比至少选择两种原料");
  if (concentrateInput.minimumStarchPct > concentrateInput.maximumStarchPct) return ElMessage.warning("淀粉下限不能高于上限");
  saving.value = true;
  try {
    const result = await optimizeConcentrate({
      ingredientIds: concentrateInput.ingredientIds,
      targetCrudeProteinPct: concentrateInput.targetCrudeProteinPct,
      minimumMetabolizableEnergy: concentrateInput.minimumMetabolizableEnergy,
      maximumNdfPct: concentrateInput.maximumNdfPct,
      maximumCrudeFatPct: concentrateInput.maximumCrudeFatPct,
      minimumStarchPct: concentrateInput.minimumStarchPct,
      maximumStarchPct: concentrateInput.maximumStarchPct,
    });
    result.ratios.forEach((item) => concentrateInput.ratios[item.ingredientId] = item.dryMatterRatioPct);
    ElMessage.success(`已按营养目标自动配比，预计 ¥${result.estimatedUnitPrice}/kg`);
  } catch (e: any) { ElMessage.error(e.response?.data?.message || "自动配比失败"); }
  finally { saving.value = false; }
}
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
  delete recommendationInput.ingredientRatios[id];
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
    dailyRationFormulas.value.filter((x) => x.status === "ACTIVE"),
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
          metabolizableEnergyValue: 0,
          crudeProteinPct: 0,
          starchPct: 0,
          energyValue: 0,
          gainEnergyValue: 0,
          ndfPct: 0,
          peNdfPct: 0,
          adfPct: 0,
          ashPct: 0,
          crudeFatPct: 0,
          calciumPct: 0,
          phosphorusPct: 0,
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
    productionStage: "FINISHING",
    bodySize: "LARGE",
    currentWeightKg: 400,
    targetWeightKg: 600,
    targetDailyGainKg: 1.1,
    concentrateFormulaId: concentrateFormulas.value.find((item) => item.status === "ACTIVE")?.formulaId || concentrateFormulas.value[0]?.formulaId || "",
    roughageDryMatterPct: 55,
    ingredientIds: [],
    ingredientRatios: {},
  });
  recommendation.value = null;
  recommendationIngredientSearch.value = "";
  dialog.value = "recommend";
}
function openConcentrateBuilder() {
  Object.assign(concentrateInput, { formulaName: "", ingredientIds: [], ratios: {}, targetCrudeProteinPct: 18, minimumMetabolizableEnergy: 2.8, maximumNdfPct: 25, maximumCrudeFatPct: 7, minimumStarchPct: 25, maximumStarchPct: 65 });
  concentrateIngredientSearch.value = "";
  dialog.value = "concentrate";
}
async function saveConcentrate() {
  if (!concentrateInput.formulaName.trim()) return ElMessage.warning("请填写精料名称");
  if (selectedConcentrateIngredients.value.length < 2) return ElMessage.warning("精料至少选择两种原料");
  if (Math.abs(concentrateRatioTotal.value - 100) > 0.01) return ElMessage.warning(`精料配比合计必须为 100%，当前为 ${concentrateRatioTotal.value.toFixed(2)}%`);
  saving.value = true;
  try {
    await createFormula({
      formulaName: concentrateInput.formulaName.trim(), targetType: "CUSTOM", dailyIntakeKg: 1,
      remark: "精料配方；各原料按成品重量百分比配制，每公斤成本及营养成分由系统核算",
      items: selectedConcentrateIngredients.value.map((item) => ({
        ingredientId: item.ingredientId,
        ratioPct: Number(concentrateInput.ratios[item.ingredientId] || 0),
        dailyAmountKg: Number((Number(concentrateInput.ratios[item.ingredientId] || 0) / 100).toFixed(4)),
      })),
    });
    ElMessage.success("精料配方已保存"); dialog.value = false; await load();
  } catch (e: any) { ElMessage.error(e.response?.data?.message || e.message || "精料保存失败"); }
  finally { saving.value = false; }
}
async function generateRecommendation() {
  if (recommendationInput.targetWeightKg <= recommendationInput.currentWeightKg)
    return ElMessage.warning("目标体重必须大于当前体重");
  const concentrate = concentrateFormulas.value.find((item) => item.formulaId === recommendationInput.concentrateFormulaId);
  if (!concentrate) return ElMessage.warning("请先选择一款精料");
  if (!recommendationInput.ingredientIds.length) return ElMessage.warning("至少选择一种粗料");
  if (Math.abs(recommendationRatioTotal.value - 100) > 0.01) return ElMessage.warning(`粗料内部配比合计必须为 100%，当前为 ${recommendationRatioTotal.value.toFixed(2)}%`);
  const concentrateDmParts = concentrate.items.map((line) => {
    const item = ingredients.value.find((value) => value.ingredientId === line.ingredientId)!;
    return { item, dm: Number(line.dailyAmountKg) * Number(item?.dryMatterPct || 0) / 100 };
  });
  const concentrateDmTotal = concentrateDmParts.reduce((sum, line) => sum + line.dm, 0);
  if (!concentrateDmTotal) return ElMessage.warning("所选精料缺少有效干物质数据");
  const concentrateShare = 100 - recommendationInput.roughageDryMatterPct;
  const combinedRatios = [
    ...concentrateDmParts.map((line) => ({ ingredientId: line.item.ingredientId, dryMatterRatioPct: concentrateShare * line.dm / concentrateDmTotal })),
    ...selectedRecommendationIngredients.value.map((item) => ({ ingredientId: item.ingredientId, dryMatterRatioPct: recommendationInput.roughageDryMatterPct * Number(recommendationInput.ingredientRatios[item.ingredientId] || 0) / 100 })),
  ];
  saving.value = true;
  try {
    recommendation.value = await recommendFormula({
      bodySize: recommendationInput.bodySize,
      productionStage: recommendationInput.productionStage,
      currentWeightKg: recommendationInput.currentWeightKg,
      targetWeightKg: recommendationInput.targetWeightKg,
      feedingDays: Math.max(30, Math.min(730, Math.round((recommendationInput.targetWeightKg - recommendationInput.currentWeightKg) / recommendationInput.targetDailyGainKg))),
      roughageDryMatterPct: recommendationInput.roughageDryMatterPct,
      ingredientIds: combinedRatios.map((item) => item.ingredientId),
      ingredientRatios: combinedRatios,
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
    formulaName: `${recommendationInput.currentWeightKg}-${recommendationInput.targetWeightKg}kg日粮配方`,
    targetType: "CUSTOM",
    targetObjectId: "",
    dailyIntakeKg: result.dailyIntakeKg,
    remark: `精料与粗料二次配比：目标日增重 ${recommendationInput.targetDailyGainKg} kg，精料 ${100 - recommendationInput.roughageDryMatterPct}% DM，粗料 ${recommendationInput.roughageDryMatterPct}% DM；保存前已由牛场主复核`,
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
  dialog.value = false;
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
    dialog.value = false;
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
async function removeFormula(row: Formula) {
  try {
    await ElMessageBox.confirm(`确定删除配方“${row.formulaName} V${row.versionNo}”吗？删除后无法恢复。`, "删除配方", { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消", confirmButtonClass: "el-button--danger" });
  } catch { return; }
  await act(() => deleteFormula(row.formulaId), "配方已删除");
}
async function stopFormula(row: Formula) {
  try {
    await ElMessageBox.confirm(`停用配方“${row.formulaName} V${row.versionNo}”后，将不能再用它创建新的配料单。`, "停用配方", { type: "warning", confirmButtonText: "停用", cancelButtonText: "取消" });
  } catch { return; }
  await act(() => deactivateFormula(row.formulaId, row.rowVersion), "配方已停用");
}
async function removeIngredient(row: Ingredient) {
  try {
    await ElMessageBox.confirm(`确定删除原料“${row.ingredientName}”吗？删除后无法恢复。`, "删除原料", { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消", confirmButtonClass: "el-button--danger" });
  } catch { return; }
  await act(() => deleteIngredient(row.ingredientId), "原料已删除");
}
async function cancel(row: MixingOrder) {
  const { value } = await ElMessageBox.prompt("请填写取消原因", "取消配料单", {
    inputPattern: /\S+/,
    inputErrorMessage: "原因不能为空",
  });
  await act(() => cancelOrder(row, value), "配料单已取消");
}
async function removeOrder(row: MixingOrder) {
  try {
    await ElMessageBox.confirm(`确定删除配料单“${row.formulaName}（${row.feedDate}）”吗？删除后无法恢复。`, "删除配料单", { type: "warning", confirmButtonText: "删除", cancelButtonText: "取消", confirmButtonClass: "el-button--danger" });
  } catch { return; }
  await act(() => deleteOrder(row.orderId), "配料单已删除");
}
async function removeExecution(row: MixingExecution) {
  try {
    await ElMessageBox.confirm("删除执行记录后，对应配料单将回退到已确认状态，并重新开放登记执行。确定继续吗？", "删除执行记录", { type: "warning", confirmButtonText: "删除并回退", cancelButtonText: "取消", confirmButtonClass: "el-button--danger" });
  } catch { return; }
  await act(() => deleteExecution(row.executionId), "执行记录已删除，配料单已回退为已确认");
}
function orderStatusLabel(status: string) {
  return { PENDING_CONFIRM: "待确认", CONFIRMED: "已确认", EXECUTED: "已执行", CANCELLED: "已取消" }[status] || status;
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
          >日粮配比</el-button
        >
        <el-button v-if="tab === 'concentrates'" :icon="MagicStick" @click="openConcentrateBuilder">生成精料</el-button>
        <el-upload
          v-if="['formulas', 'concentrates'].includes(tab)"
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
          v-if="!isWorker"
          label="精料配方"
          name="concentrates" /><el-tab-pane
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
          /><el-table-column prop="starchPct" label="淀粉 %" /><el-table-column prop="ndfPct" label="NDF %" /><el-table-column prop="peNdfPct" label="PeNDF %" /><el-table-column prop="adfPct" label="ADF %" /><el-table-column prop="crudeFatPct" label="粗脂肪 %" /><el-table-column
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
        ><el-table-column v-if="canManage" label="操作" width="120"
          ><template #default="s"
            ><el-button link type="primary" @click="openIngredient(s.row)"
              >编辑</el-button><el-button link type="danger" @click="removeIngredient(s.row)">删除</el-button
            ></template
          ></el-table-column
        ></el-table
      >
    </section>
      <section v-if="['formulas', 'concentrates'].includes(tab)" class="cards">
      <article v-for="f in (tab === 'concentrates' ? concentrateFormulas : dailyRationFormulas)" :key="f.formulaId">
        <header>
          <strong>{{ f.formulaName }} V{{ f.versionNo }}</strong
          ><el-tag :type="f.status === 'ACTIVE' ? 'success' : f.status === 'ARCHIVED' ? 'warning' : 'info'">{{
            f.status === "ACTIVE" ? "已启用" : f.status === "ARCHIVED" ? "已停用" : "草稿"
          }}</el-tag>
        </header>
        <div class="metrics">
          <span
            >{{ tab === 'concentrates' ? '每公斤干物质' : '干物质' }}<b>{{ f.dryMatterKg }} kg</b></span
          ><span
            >粗蛋白<b>{{ f.crudeProteinPct }}%</b></span
          ><span
            >NDF<b>{{ f.ndfPct }}%</b></span
          ><span
            >{{ tab === 'concentrates' ? '精料单价' : '日成本' }}<b>¥{{ f.dailyCost }}{{ tab === 'concentrates' ? '/kg' : '' }}</b></span
          ><template v-if="tab === 'concentrates'">
            <span>TDN<b>{{ formulaMetric(f, 'tdnPct') ?? '-' }}%</b></span><span>代谢能<b>{{ formulaMetric(f, 'metabolizableEnergyValue') ?? '-' }} Mcal/kgDM</b></span>
            <span>维持净能<b>{{ formulaMetric(f, 'energyValue') ?? '-' }} Mcal/kgDM</b></span><span>增重净能<b>{{ formulaMetric(f, 'gainEnergyValue') ?? '-' }} Mcal/kgDM</b></span><span>PeNDF<b>{{ formulaMetric(f, 'peNdfPct') ?? '-' }}%</b></span>
            <span>ADF<b>{{ formulaMetric(f, 'adfPct') ?? '-' }}%</b></span><span>粗灰分<b>{{ formulaMetric(f, 'ashPct') ?? '-' }}%</b></span>
            <span>粗脂肪<b>{{ formulaMetric(f, 'crudeFatPct') ?? '-' }}%</b></span><span>RDP<b>{{ formulaMetric(f, 'rdpPct') ?? '-' }}% DM</b></span><span>钙 / 磷<b>{{ formulaMetric(f, 'calciumPct') ?? '-' }}% / {{ formulaMetric(f, 'phosphorusPct') ?? '-' }}%</b></span>
          </template
          >
        </div>
        <el-table :data="f.items" size="small"
          ><el-table-column
            prop="ingredientName"
            label="原料" /><el-table-column
            prop="dailyAmountKg"
            :label="tab === 'concentrates' ? 'kg/kg精料' : 'kg/头/日'" /><el-table-column prop="ratioPct" label="占比 %"
        /></el-table>
        <div v-if="canManage" class="card-actions">
          <el-button v-if="f.status === 'DRAFT'" link @click="openFormula(f)">编辑</el-button
          ><el-button v-if="['DRAFT', 'ARCHIVED'].includes(f.status)"
            link
            type="primary"
            @click="
              act(
                () => activateFormula(f.formulaId, f.rowVersion),
                '配方已启用',
              )
            "
            >{{ f.status === 'DRAFT' ? '审核并启用' : '重新启用' }}</el-button
          ><el-button v-if="f.status === 'ACTIVE'" link type="warning" @click="stopFormula(f)">停用</el-button
          ><el-tooltip :disabled="f.status !== 'ACTIVE'" content="请先停用配方再删除"><span><el-button link type="danger" :disabled="f.status === 'ACTIVE'" @click="removeFormula(f)">删除</el-button></span></el-tooltip>
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
          label="状态"
          ><template #default="s">{{ orderStatusLabel(s.row.status) }}</template
          ></el-table-column
        ><el-table-column label="操作" width="230"
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
            ><el-tooltip :disabled="['PENDING_CONFIRM', 'CANCELLED'].includes(s.row.status)" :content="s.row.status === 'EXECUTED' ? '请先删除对应执行记录' : '请先取消已确认配料单'"><span><el-button v-if="canManage" link type="danger" :disabled="!['PENDING_CONFIRM', 'CANCELLED'].includes(s.row.status)" @click="removeOrder(s.row)">删除</el-button></span></el-tooltip
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
          }}</template></el-table-column><el-table-column v-if="canManage" label="操作" width="90"><template #default="s"><el-button link type="danger" @click="removeExecution(s.row)">删除</el-button></template></el-table-column
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
            ? '精料与粗料日粮配比'
          : dialog === 'concentrate'
            ? '生成精料配方'
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
        ><el-form-item label="PeNDF %（干物质基础）"
          ><el-input-number v-model="ingredient.peNdfPct" :min="0" :max="100" :precision="2" /></el-form-item
        ><el-form-item label="ADF %（干物质基础）"
          ><el-input-number v-model="ingredient.adfPct" :min="0" :max="100" :precision="2" /></el-form-item
        ><el-form-item label="粗灰分 %（干物质基础）"
          ><el-input-number v-model="ingredient.ashPct" :min="0" :max="100" :precision="2" /></el-form-item
        ><el-form-item label="粗脂肪 %（干物质基础）"
          ><el-input-number v-model="ingredient.crudeFatPct" :min="0" :max="100" :precision="2" /></el-form-item
        ><el-form-item label="钙 %（干物质基础）"
          ><el-input-number v-model="ingredient.calciumPct" :min="0" :max="100" :precision="3" /></el-form-item
        ><el-form-item label="磷 %（干物质基础）"
          ><el-input-number v-model="ingredient.phosphorusPct" :min="0" :max="100" :precision="3" /></el-form-item
        ><el-form-item label="代谢能（Mcal/kgDM）"
          ><el-input-number v-model="ingredient.metabolizableEnergyValue" :min="0" :precision="3" /></el-form-item
        ><el-form-item label="维持净能（Mcal/kgDM）"
          ><el-input-number v-model="ingredient.energyValue" :min="0" :precision="3" /></el-form-item
        ><el-form-item label="增重净能（Mcal/kgDM）"
          ><el-input-number v-model="ingredient.gainEnergyValue" :min="0" :precision="3" /></el-form-item
        ><el-form-item label="RDP %（干物质基础）"
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
      <div v-else-if="dialog === 'concentrate'" class="recommendation-form">
        <el-alert title="先生成并保存精料，再用于日粮二次配比。精料比例按成品重量计算，合计必须为 100%。" type="info" :closable="false" show-icon />
        <el-form label-position="top" class="recommend-inputs">
          <el-form-item label="精料名称" required><el-input v-model="concentrateInput.formulaName" placeholder="例如：400-500kg育肥精料" /></el-form-item>
          <el-form-item label="选择精料原料" required>
            <el-input v-model="concentrateIngredientSearch" clearable placeholder="搜索能量料、蛋白料、矿物质或添加剂" />
            <el-checkbox-group v-model="concentrateInput.ingredientIds" class="concentrate-picker">
              <el-checkbox v-for="item in concentrateIngredients" :key="item.ingredientId" :value="item.ingredientId">{{ item.ingredientName }}<small>{{ ingredientTypeLabels[item.ingredientType] }}</small></el-checkbox>
            </el-checkbox-group>
          </el-form-item>
          <div v-if="selectedConcentrateIngredients.length" class="concentrate-targets">
            <el-form-item label="目标粗蛋白 ≥ %DM"><el-input-number v-model="concentrateInput.targetCrudeProteinPct" :min="8" :max="40" :precision="1" /></el-form-item>
            <el-form-item label="代谢能 ≥ Mcal/kgDM"><el-input-number v-model="concentrateInput.minimumMetabolizableEnergy" :min="1" :max="5" :precision="2" /></el-form-item>
            <el-form-item label="NDF ≤ %DM"><el-input-number v-model="concentrateInput.maximumNdfPct" :min="0" :max="80" :precision="1" /></el-form-item>
            <el-form-item label="粗脂肪 ≤ %DM"><el-input-number v-model="concentrateInput.maximumCrudeFatPct" :min="0" :max="20" :precision="1" /></el-form-item>
            <el-form-item label="淀粉下限 %DM"><el-input-number v-model="concentrateInput.minimumStarchPct" :min="0" :max="80" :precision="1" /></el-form-item>
            <el-form-item label="淀粉上限 %DM"><el-input-number v-model="concentrateInput.maximumStarchPct" :min="0" :max="90" :precision="1" /></el-form-item>
          </div>
        </el-form>
        <div v-if="selectedConcentrateIngredients.length" class="ratio-editor">
          <div class="ratio-editor-head"><div><strong>精料原料配比</strong><span>系统按营养约束和价格优化，生成后可人工微调</span></div><div :class="{ invalid: Math.abs(concentrateRatioTotal - 100) > 0.01 }"><span>当前合计</span><b>{{ concentrateRatioTotal.toFixed(2) }}%</b></div><el-button type="primary" :loading="saving" :icon="MagicStick" @click="optimizeSelectedConcentrate">按营养目标自动配比</el-button></div>
          <div class="ratio-editor-grid"><label v-for="item in selectedConcentrateIngredients" :key="item.ingredientId"><span>{{ item.ingredientName }}<small>{{ ingredientTypeLabels[item.ingredientType] }}</small></span><el-input-number v-model="concentrateInput.ratios[item.ingredientId]" :min="0" :max="100" :precision="2" /><em>%</em></label></div>
        </div>
        <div v-if="selectedConcentrateIngredients.length" class="recommend-metrics concentrate-summary">
          <span>每公斤精料价格<b>¥{{ concentrateSummary.price }}/kg</b></span><span>干物质<b>{{ concentrateSummary.dryMatterPct ?? '-' }}%</b></span><span>粗蛋白<b>{{ concentrateSummary.crudeProteinPct ?? '-' }}%</b></span>
          <span>TDN<b>{{ concentrateSummary.tdnPct ?? '-' }}%</b></span><span>代谢能<b>{{ concentrateSummary.metabolizableEnergyValue ?? '-' }} Mcal/kgDM</b></span><span>维持净能<b>{{ concentrateSummary.energyValue ?? '-' }} Mcal/kgDM</b></span>
          <span>增重净能<b>{{ concentrateSummary.gainEnergyValue ?? '-' }} Mcal/kgDM</b></span><span>NDF<b>{{ concentrateSummary.ndfPct ?? '-' }}%</b></span><span>PeNDF<b>{{ concentrateSummary.peNdfPct ?? '-' }}%</b></span><span>ADF<b>{{ concentrateSummary.adfPct ?? '-' }}%</b></span>
          <span>粗灰分<b>{{ concentrateSummary.ashPct ?? '-' }}%</b></span><span>粗脂肪<b>{{ concentrateSummary.crudeFatPct ?? '-' }}%</b></span><span>RDP<b>{{ concentrateSummary.rdpPct ?? '-' }}% DM</b></span><span>钙 / 磷<b>{{ concentrateSummary.calciumPct ?? '-' }}% / {{ concentrateSummary.phosphorusPct ?? '-' }}%</b></span>
        </div>
      </div>
      <div v-else-if="dialog === 'recommend'" class="recommendation-form">
        <el-alert title="建议配方用于生产决策辅助，启用前必须结合原料化验值和牛群实际情况复核。" type="warning" :closable="false" show-icon />
        <el-form label-position="top" class="grid recommend-inputs">
          <el-form-item label="生产阶段" required><el-segmented v-model="recommendationInput.productionStage" :options="[{ label: '生长牛', value: 'GROWING' }, { label: '肥育牛', value: 'FINISHING' }]" /></el-form-item>
          <el-form-item required>
            <template #label>
              <span class="form-label-with-help">体型标准<el-tooltip content="参考成年体重：大体型约 600 kg 以上；中体型约 400-600 kg。请结合品种成熟体重和生长潜力选择，不能仅按当前体重判断。" placement="top"><el-icon class="help-icon" tabindex="0" aria-label="查看体型标准参考体重"><InfoFilled /></el-icon></el-tooltip></span>
            </template>
            <el-segmented v-model="recommendationInput.bodySize" :options="[{ label: '大体型肉牛', value: 'LARGE' }, { label: '中体型肉牛', value: 'MEDIUM' }]" />
          </el-form-item>
          <el-form-item label="当前平均体重（kg）" required><el-input-number v-model="recommendationInput.currentWeightKg" :min="80" :max="1200" /></el-form-item>
          <el-form-item label="目标体重（kg）" required><el-input-number v-model="recommendationInput.targetWeightKg" :min="100" :max="1500" /></el-form-item>
          <el-form-item label="目标日增重（kg/天）" required><el-input-number v-model="recommendationInput.targetDailyGainKg" :min="0.2" :max="2" :step="0.1" :precision="2" /></el-form-item>
          <el-form-item label="选择精料" required><el-select v-model="recommendationInput.concentrateFormulaId" filterable placeholder="请先在精料配方中生成并保存"><el-option v-for="item in concentrateFormulas" :key="item.formulaId" :label="`${item.formulaName}（¥${item.dailyCost}/kg）`" :value="item.formulaId" /></el-select></el-form-item>
          <el-form-item label="粗料干物质占比（%）" required><el-input-number v-model="recommendationInput.roughageDryMatterPct" :min="recommendationInput.productionStage === 'GROWING' ? 45 : 10" :max="95" /><small class="field-hint">精料占 {{ 100 - recommendationInput.roughageDryMatterPct }}% DM · GB/T {{ recommendationInput.productionStage === 'GROWING' ? '生长牛粗料 45%-95%' : '肥育牛粗料不低于 10%' }}</small></el-form-item>
          <el-form-item label="选择粗料" required>
            <el-popover placement="bottom-start" :width="520" trigger="click" popper-class="ingredient-picker-popover">
              <template #reference>
                <el-button class="ingredient-picker-trigger">
                  <span>{{ recommendationInput.ingredientIds.length ? `已选择 ${recommendationInput.ingredientIds.length} 种粗料` : '点击选择粗料' }}</span>
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
        <div v-if="selectedRecommendationIngredients.length" class="ratio-editor">
          <div class="ratio-editor-head">
            <div><strong>粗料内部配比</strong><span>多种粗料之间按干物质分配，合计必须为 100%</span></div>
            <div :class="{ invalid: Math.abs(recommendationRatioTotal - 100) > 0.01 }"><span>当前合计</span><b>{{ recommendationRatioTotal.toFixed(2) }}%</b></div>
            <el-button @click="distributeRecommendationRatios">平均分配</el-button>
          </div>
          <div class="ratio-editor-grid">
            <label v-for="item in selectedRecommendationIngredients" :key="item.ingredientId">
              <span>{{ item.ingredientName }}<small>{{ ingredientTypeLabels[item.ingredientType] }}</small></span>
              <el-input-number v-model="recommendationInput.ingredientRatios[item.ingredientId]" :min="0" :max="100" :precision="2" :step="1" />
              <em>% DM</em>
            </label>
          </div>
          <el-progress :percentage="Math.min(100, recommendationRatioTotal)" :status="Math.abs(recommendationRatioTotal - 100) <= 0.01 ? 'success' : undefined" :show-text="false" />
        </div>
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
            <span>代谢能<b>{{ recommendation.estimatedMetabolizableEnergy }} Mcal/kgDM</b></span>
            <span>维持净能<b>{{ recommendation.estimatedMaintenanceNetEnergy }} Mcal/kgDM</b></span>
            <span>增重净能<b>{{ recommendation.estimatedGainNetEnergy }} Mcal/kgDM</b></span>
            <span>ADF<b>{{ recommendation.estimatedAdfPct == null ? '-' : `${recommendation.estimatedAdfPct}%` }}</b></span>
            <span>PeNDF<b>{{ recommendation.estimatedPeNdfPct == null ? '-' : `${recommendation.estimatedPeNdfPct}%` }}</b></span>
            <span>粗灰分<b>{{ recommendation.estimatedAshPct == null ? '-' : `${recommendation.estimatedAshPct}%` }}</b></span>
            <span>粗脂肪<b>{{ recommendation.estimatedCrudeFatPct == null ? '-' : `${recommendation.estimatedCrudeFatPct}%` }}</b></span>
            <span>钙<b>{{ recommendation.estimatedCalciumPct == null ? '-' : `${recommendation.estimatedCalciumPct}%` }}</b></span>
            <span>磷<b>{{ recommendation.estimatedPhosphorusPct == null ? '-' : `${recommendation.estimatedPhosphorusPct}%` }}</b></span>
            <span>预计日成本<b>¥{{ recommendation.estimatedDailyCost }}</b></span>
            <span>干物质单价<b>¥{{ recommendation.dryMatterUnitPrice }}/kgDM</b></span>
          </div>
          <div class="daily-supply">
            <div><span>粗蛋白供给</span><b>{{ recommendation.crudeProteinDailyKg }} kg/d</b><small>基础目标 {{ recommendation.standardTarget.crudeProteinRequiredKg }} kg/d</small></div>
            <div><span>代谢能供给</span><b>{{ recommendation.metabolizableEnergyDailyMcal }} Mcal/d</b><small>按配方浓度 × DMI 核算</small></div>
            <div><span>维持净能供给</span><b>{{ recommendation.maintenanceEnergyDailyMcal }} Mcal/d</b><small>基础目标 {{ recommendation.standardTarget.maintenanceEnergyRequiredMcal }} Mcal/d</small></div>
            <div><span>增重净能供给</span><b>{{ recommendation.gainEnergyDailyMcal }} Mcal/d</b><small>基础目标 {{ recommendation.standardTarget.gainEnergyRequiredMcal }} Mcal/d</small></div>
          </div>
          <div class="standard-reference">
            <strong>{{ recommendation.standardTarget.sourceStandard }}</strong>
            <span>{{ recommendation.standardTarget.bodySize === 'LARGE' ? '大体型' : '中体型' }} · 参考体重 {{ recommendation.standardTarget.referenceWeightKg }} kg · 参考日增重 {{ recommendation.standardTarget.referenceDailyGainKg }} kg</span>
            <span>基础值：DMI {{ recommendation.standardTarget.dryMatterIntakeKg }} kg，TDN {{ recommendation.standardTarget.tdnPct }}%，粗蛋白 {{ recommendation.standardTarget.crudeProteinPct }}%</span>
          </div>
          <div class="nutrient-checks">
            <div>
              <span>粗蛋白</span><b>{{ recommendation.estimatedCrudeProteinPct }}%</b><small>目标 {{ recommendation.crudeProteinTargetMinPct }}-{{ recommendation.crudeProteinTargetMaxPct }}% · {{ nutrientStatus(recommendation.estimatedCrudeProteinPct, recommendation.crudeProteinTargetMinPct, recommendation.crudeProteinTargetMaxPct).label }}</small>
            </div>
            <div>
              <span>NDF</span><b>{{ recommendation.estimatedNdfPct }}%</b><small>GB/T 最低值 {{ recommendation.ndfTargetMinPct }}% · {{ recommendation.estimatedNdfPct >= recommendation.ndfTargetMinPct ? '达标' : '偏低' }}</small>
            </div>
            <div>
              <span>TDN</span><b>{{ recommendation.estimatedTdnPct }}%</b><small>工作簿目标 {{ recommendation.standardTarget.tdnPct }}%</small>
            </div>
            <div>
              <span>淀粉</span><b>{{ recommendation.estimatedStarchPct }}%</b><small>GB/T {{ recommendation.standardTarget.productionStage === 'GROWING' ? '生长牛 15%-35%' : '肥育牛 35%-50%' }}</small>
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
              <span>精料干物质</span><b>{{ 100 - recommendation.roughageDryMatterPct }}%</b><small>来自所选精料配方，按内部组成展开核算</small>
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
        ><el-button @click="dialog = false">取消</el-button
        ><el-button v-if="dialog === 'concentrate'" type="primary" :loading="saving" @click="saveConcentrate">保存精料</el-button
        ><el-button v-if="dialog === 'recommend' && recommendation" @click="openMicronutrientsFromRecommendation">计算矿物质维生素</el-button
        ><el-button v-if="dialog === 'recommend'" type="primary" :disabled="!recommendation" @click="useRecommendation">采用并继续编辑</el-button
        ><el-button v-else-if="dialog !== 'concentrate'" type="primary" :loading="saving" @click="save"
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
.form-label-with-help { display: inline-flex; align-items: center; gap: 5px; }
.help-icon { color: #8a9590; cursor: help; font-size: 15px; }
.help-icon:hover, .help-icon:focus { color: #3f7a63; outline: none; }
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
.concentrate-picker { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); max-height: 240px; overflow: auto; gap: 5px 10px; margin-top: 10px; padding: 10px; border: 1px solid #e2e7e4; }
.concentrate-picker .el-checkbox { margin-right: 0; min-width: 0; }
.concentrate-picker small { display: block; color: #84908b; font-size: 11px; }
.concentrate-targets { display: grid; grid-template-columns: repeat(3, 1fr); gap: 0 12px; padding: 12px; border: 1px solid #e2e7e4; background: #f7faf8; }
.ratio-editor { margin: 0 0 16px; border: 1px solid #dfe5e2; padding: 14px; }
.ratio-editor-head { display: grid; grid-template-columns: 1fr auto auto; gap: 14px; align-items: center; margin-bottom: 12px; }
.ratio-editor-head > div:first-child span { display: block; margin-top: 3px; color: #75817d; font-size: 12px; }
.ratio-editor-head > div:nth-child(2) { display: flex; align-items: baseline; gap: 6px; color: #66736d; }
.ratio-editor-head > div:nth-child(2) b { color: #2f765a; font-size: 18px; }
.ratio-editor-head > div.invalid b { color: #c45656; }
.ratio-editor-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px 16px; margin-bottom: 12px; }
.ratio-editor-grid label { display: grid; grid-template-columns: minmax(120px, 1fr) 130px 42px; gap: 8px; align-items: center; }
.ratio-editor-grid label > span { min-width: 0; color: #26332e; }
.ratio-editor-grid small { display: block; color: #84908b; font-size: 11px; }
.ratio-editor-grid em { color: #75817d; font-style: normal; font-size: 12px; }
.recommend-action { display: flex; justify-content: flex-end; margin-bottom: 16px; }
.recommend-metrics { display: grid; grid-template-columns: repeat(3, 1fr); border: 1px solid #e4e8e6; margin-bottom: 14px; }
.recommend-metrics span { padding: 10px; color: #75817d; }
.recommend-metrics b { display: block; color: #26332e; margin-top: 5px; }
.field-hint { display: block; margin-top: 5px; color: #75817d; }
.daily-supply { display: grid; grid-template-columns: repeat(4, 1fr); margin-bottom: 14px; border: 1px solid #dce4e0; background: #f7faf8; }
.daily-supply > div { padding: 11px; border-right: 1px solid #e2e8e5; }
.daily-supply > div:last-child { border-right: 0; }
.daily-supply span, .daily-supply small { display: block; color: #75817d; }
.daily-supply b { display: block; margin: 5px 0 3px; color: #26332e; }
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
  .recommend-metrics, .nutrient-checks, .daily-supply { grid-template-columns: 1fr 1fr; }
  .daily-supply > div:nth-child(2) { border-right: 0; }
  .ingredient-summary { grid-template-columns: repeat(2, 1fr); }
  .ingredient-tools { grid-template-columns: 1fr; }
  .nutrient-checks > div:nth-child(odd) { border-right: 0; }
  .micro-inputs, .micro-form, .breeding-form { grid-template-columns: 1fr; }
  .micro-summary { grid-template-columns: 1fr; }
  .breeding-concentrations { grid-template-columns: 1fr 1fr; }
  .ratio-editor-head, .ratio-editor-grid { grid-template-columns: 1fr; }
  .concentrate-targets { grid-template-columns: 1fr 1fr; }
}
</style>
