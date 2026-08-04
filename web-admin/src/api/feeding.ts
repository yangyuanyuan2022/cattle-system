import { http, type ApiResponse } from "./http";
export interface Ingredient {
  ingredientId: string;
  ingredientName: string;
  ingredientType: string;
  dryMatterPct: number;
  tdnPct: number | null;
  metabolizableEnergyValue: number | null;
  crudeProteinPct: number;
  starchPct: number | null;
  energyValue: number | null;
  gainEnergyValue: number | null;
  ndfPct: number;
  peNdfPct: number | null;
  adfPct: number | null;
  ashPct: number | null;
  crudeFatPct: number | null;
  calciumPct: number | null;
  phosphorusPct: number | null;
  rdpPct: number | null;
  unitPrice: number;
  status: string;
  remark: string | null;
  version: number;
}
export interface FeedingPage<T> { page:number; pageSize:number; total:number; items:T[]; }
export interface IngredientPage extends FeedingPage<Ingredient> { typeCounts:Record<string,number>; }
export interface FormulaLine {
  ingredientId: string;
  ingredientName: string;
  ratioPct: number;
  dailyAmountKg: number;
  unitPrice: number;
}
export interface Formula {
  formulaId: string;
  formulaName: string;
  versionNo: number;
  targetType: string;
  targetObjectId: string | null;
  dailyIntakeKg: number;
  sourceFile: string | null;
  status: string;
  dryMatterKg: number | null;
  crudeProteinPct: number | null;
  ndfPct: number | null;
  dailyCost: number | null;
  nutrition: {
    tdnPct: number | null;
    metabolizableEnergyValue: number | null;
    energyValue: number | null;
    gainEnergyValue: number | null;
    peNdfPct: number | null;
    adfPct: number | null;
    ashPct: number | null;
    crudeFatPct: number | null;
    calciumPct: number | null;
    phosphorusPct: number | null;
    rdpPct: number | null;
    dryMatterUnitPrice: number | null;
  } | null;
  items: FormulaLine[];
  rowVersion: number;
}
export interface FormulaRecommendation {
  averageDailyGainKg: number;
  dryMatterTargetKg: number;
  dailyIntakeKg: number;
  estimatedCrudeProteinPct: number;
  crudeProteinTargetMinPct: number;
  crudeProteinTargetMaxPct: number;
  estimatedNdfPct: number;
  ndfTargetMinPct: number;
  ndfTargetMaxPct: number;
  estimatedTdnPct: number;
  estimatedStarchPct: number;
  estimatedRdpPct: number;
  estimatedMetabolizableEnergy: number;
  estimatedMaintenanceNetEnergy: number;
  estimatedGainNetEnergy: number;
  estimatedPeNdfPct: number | null;
  estimatedAdfPct: number | null;
  estimatedAshPct: number | null;
  estimatedCrudeFatPct: number | null;
  estimatedCalciumPct: number | null;
  estimatedPhosphorusPct: number | null;
  dryMatterUnitPrice: number;
  metabolizableEnergyDailyMcal: number;
  maintenanceEnergyDailyMcal: number;
  gainEnergyDailyMcal: number;
  crudeProteinDailyKg: number;
  roughageDryMatterPct: number;
  proteinFeedDryMatterPct: number;
  estimatedDailyCost: number;
  pricedCostCoveragePct: number;
  missingPriceIngredients: string[];
  standardTarget: {
    bodySize:string;
    referenceWeightKg:number;
    referenceDailyGainKg:number;
    dryMatterIntakeKg:number;
    tdnPct:number;
    crudeProteinPct:number;
    rdpPct:number;
    starchPct:number;
    ndfPct:number;
    maintenanceNetEnergy:number;
    gainNetEnergy:number;
    crudeProteinRequiredKg:number;
    maintenanceEnergyRequiredMcal:number;
    gainEnergyRequiredMcal:number;
    productionStage:string;
    sourceStandard:string;
  };
  items: { ingredientId:string; ingredientName:string; ingredientType:string; ratioPct:number; dailyAmountKg:number }[];
  warnings: string[];
}
export interface OrderLine {
  ingredientId: string;
  ingredientName: string;
  plannedAmountKg: number;
  adjustedAmountKg: number | null;
  unitPrice: number;
}
export interface MixingOrder {
  orderId: string;
  formulaId: string;
  formulaName: string;
  herdName: string | null;
  cattleCount: number;
  feedDate: string;
  status: string;
  totalKg: number;
  totalCost: number;
  version: number;
  items: OrderLine[];
}
export interface MixingExecution {
  executionId: string;
  orderId: string;
  executionTime: string;
  executorId: string;
  executorName: string | null;
  actualSummary: string;
  deviationNote: string | null;
  status: "EXECUTED" | "VOIDED";
  voidReason: string | null;
  voidedByName: string | null;
  voidedAt: string | null;
  orderVersion: number;
}
export interface MicronutrientRecommendation {
  productionStage: "GROWING" | "PREGNANT" | "LACTATING";
  dryMatterIntakeKg: number;
  cattleCount: number;
  items: {
    category: string;
    nutrientName: string;
    concentrationUnit: string;
    targetMin: number;
    targetMax: number;
    intakeUnit: string;
    dailyMinPerHead: number;
    dailyMaxPerHead: number;
    herdDailyMin: number;
    herdDailyMax: number;
    actualDailyPerHead: number | null;
    gapToMinPerHead: number | null;
    gapToMaxPerHead: number | null;
    supplyStatus: "OK" | "DEFICIENT" | "ABOVE_TARGET" | "UNAVAILABLE";
    maximumTolerableConcentration: string | null;
    deficiencySymptoms: string;
  }[];
  warnings: string[];
}
export interface BreedingNutrients {
  dryMatterIntakeKg: number;
  crudeProteinG: number;
  tdnKg: number;
  digestibleEnergyMcal: number;
  metabolizableEnergyMcal: number;
  calciumG: number;
  phosphorusG: number;
  vitaminAThousandIu: number;
}
export interface BreedingNutritionRecommendation {
  productionStage: "REPLACEMENT_GROWTH" | "MAINTENANCE" | "LATE_PREGNANCY" | "LACTATION";
  referenceWeightKg: number;
  cattleCount: number;
  perHeadDaily: BreedingNutrients;
  herdDaily: BreedingNutrients;
  crudeProteinPct: number;
  tdnPct: number;
  calciumPct: number;
  phosphorusPct: number;
  warnings: string[];
}
const key = () => ({ "X-Idempotency-Key": crypto.randomUUID() });
export async function getIngredients() {
  return (await http.get<ApiResponse<Ingredient[]>>("/feeding/ingredients"))
    .data.data;
}
export async function getIngredientPage(page=1,pageSize=50,keyword="",ingredientType="") {
  const params = new URLSearchParams({ page: String(page), pageSize: String(pageSize) });
  if (keyword.trim()) params.set("keyword", keyword.trim());
  if (ingredientType) params.set("ingredientType", ingredientType);
  return (await http.get<ApiResponse<IngredientPage>>(`/feeding/ingredients/page?${params}`)).data.data;
}
export async function createIngredient(p: Record<string, unknown>) {
  return (
    await http.post<ApiResponse<Ingredient>>("/feeding/ingredients", p, {
      headers: key(),
    })
  ).data.data;
}
export async function updateIngredient(id: string, p: Record<string, unknown>) {
  return (
    await http.put<ApiResponse<Ingredient>>(`/feeding/ingredients/${id}`, p, {
      headers: key(),
    })
  ).data.data;
}
export async function deleteIngredient(id: string) {
  return (await http.delete<ApiResponse<boolean>>(`/feeding/ingredients/${id}`, { headers: key() })).data.data;
}
export async function getFormulas() {
  return (await http.get<ApiResponse<Formula[]>>("/feeding/ration-formulas"))
    .data.data;
}
export async function createFormula(p: Record<string, unknown>) {
  return (
    await http.post<ApiResponse<Formula>>("/feeding/ration-formulas", p, {
      headers: key(),
    })
  ).data.data;
}
export async function recommendFormula(p: {
  bodySize:string;
  productionStage:string;
  currentWeightKg:number;
  targetWeightKg:number;
  feedingDays:number;
  roughageDryMatterPct:number;
  proteinFeedDryMatterPct?:number;
  ingredientIds:string[];
  ingredientRatios?: { ingredientId:string; dryMatterRatioPct:number }[];
}) {
  return (
    await http.post<ApiResponse<FormulaRecommendation>>(
      "/feeding/ration-formulas/recommend",
      p,
    )
  ).data.data;
}
export async function optimizeConcentrate(p: {
  ingredientIds:string[];
  targetCrudeProteinPct:number;
  minimumMetabolizableEnergy:number;
  maximumNdfPct:number;
  maximumCrudeFatPct:number;
  minimumStarchPct:number;
  maximumStarchPct:number;
}) {
  return (await http.post<ApiResponse<{
    ratios:{ ingredientId:string; dryMatterRatioPct:number }[];
    estimatedUnitPrice:number;
    warnings:string[];
  }>>("/feeding/concentrates/optimize", p)).data.data;
}
export async function recommendMicronutrients(p: {
  productionStage:string;
  dryMatterIntakeKg:number;
  cattleCount:number;
  formulaId?:string;
}) {
  return (
    await http.post<ApiResponse<MicronutrientRecommendation>>(
      "/feeding/micronutrients/recommend",
      p,
    )
  ).data.data;
}
export async function recommendBreedingNutrition(p: {
  productionStage: string;
  weightKg: number;
  milkKgPerDay?: number;
  cattleCount: number;
}) {
  return (
    await http.post<ApiResponse<BreedingNutritionRecommendation>>(
      "/feeding/breeding-nutrition/recommend",
      p,
    )
  ).data.data;
}
export async function updateFormula(id: string, p: Record<string, unknown>) {
  return (
    await http.put<ApiResponse<Formula>>(`/feeding/ration-formulas/${id}`, p, {
      headers: key(),
    })
  ).data.data;
}
export async function importFormula(file: File, formulaName: string) {
  const form = new FormData();
  form.append("file", file);
  form.append("formulaName", formulaName);
  return (
    await http.post<ApiResponse<Formula>>(
      "/feeding/ration-formulas/import",
      form,
      { headers: { ...key(), "Content-Type": "multipart/form-data" } },
    )
  ).data.data;
}
export async function activateFormula(id: string, version = 0) {
  return (
    await http.post<ApiResponse<Formula>>(
      `/feeding/ration-formulas/${id}/activate`,
      { reason: "审核通过并启用", version },
      { headers: key() },
    )
  ).data.data;
}
export async function deactivateFormula(id: string, version = 0) {
  return (
    await http.post<ApiResponse<Formula>>(
      `/feeding/ration-formulas/${id}/deactivate`,
      { reason: "人工停用配方", version },
      { headers: key() },
    )
  ).data.data;
}
export async function deleteFormula(id: string) {
  return (await http.delete<ApiResponse<boolean>>(`/feeding/ration-formulas/${id}`, { headers: key() })).data.data;
}
export async function getOrders() {
  return (await http.get<ApiResponse<MixingOrder[]>>("/feeding/mixing-orders"))
    .data.data;
}
export async function getOrderPage(page=1,pageSize=50) { return (await http.get<ApiResponse<FeedingPage<MixingOrder>>>(`/feeding/mixing-orders/page?page=${page}&pageSize=${pageSize}`)).data.data; }
export async function deleteOrder(id: string) {
  return (await http.delete<ApiResponse<boolean>>(`/feeding/mixing-orders/${id}`, { headers: key() })).data.data;
}
export async function createOrder(p: Record<string, unknown>) {
  return (
    await http.post<ApiResponse<MixingOrder>>("/feeding/mixing-orders", p, {
      headers: key(),
    })
  ).data.data;
}
export async function calculateOrder(p: Record<string, unknown>) {
  return (
    await http.post<ApiResponse<MixingOrder>>(
      "/feeding/mixing-orders/calculate",
      p,
      { headers: key() },
    )
  ).data.data;
}
export async function confirmOrder(o: MixingOrder) {
  return (
    await http.post<ApiResponse<MixingOrder>>(
      `/feeding/mixing-orders/${o.orderId}/confirm`,
      { reason: "配方与投料计划已核对", version: o.version },
      { headers: key() },
    )
  ).data.data;
}
export async function executeOrder(
  o: MixingOrder,
  items: { ingredientId: string; actualAmountKg: number }[],
  deviationNote: string,
) {
  return (
    await http.post<ApiResponse<MixingOrder>>(
      `/feeding/mixing-orders/${o.orderId}/execute`,
      {
        executionTime: new Date().toISOString().slice(0, 19),
        deviationNote,
        version: o.version,
        items,
      },
      { headers: key() },
    )
  ).data.data;
}
export async function cancelOrder(o: MixingOrder, reason: string) {
  return (
    await http.post<ApiResponse<MixingOrder>>(
      `/feeding/mixing-orders/${o.orderId}/cancel`,
      { reason, version: o.version },
      { headers: key() },
    )
  ).data.data;
}
export async function getExecutions() {
  return (
    await http.get<ApiResponse<MixingExecution[]>>("/feeding/mixing-executions")
  ).data.data;
}
export async function getExecutionPage(page=1,pageSize=50) { return (await http.get<ApiResponse<FeedingPage<MixingExecution>>>(`/feeding/mixing-executions/page?page=${page}&pageSize=${pageSize}`)).data.data; }
export async function voidExecution(id: string, reason: string, version: number) {
  return (await http.post<ApiResponse<boolean>>(`/feeding/mixing-executions/${id}/void`, { reason, version }, { headers: key() })).data.data;
}
