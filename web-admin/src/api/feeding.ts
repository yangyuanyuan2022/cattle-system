import { http, type ApiResponse } from "./http";
export interface Ingredient {
  ingredientId: string;
  ingredientName: string;
  ingredientType: string;
  dryMatterPct: number;
  crudeProteinPct: number;
  energyValue: number | null;
  ndfPct: number;
  unitPrice: number;
  status: string;
  remark: string | null;
  version: number;
}
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
  dryMatterKg: number;
  crudeProteinPct: number;
  ndfPct: number;
  dailyCost: number;
  items: FormulaLine[];
  rowVersion: number;
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
}
const key = () => ({ "X-Idempotency-Key": crypto.randomUUID() });
export async function getIngredients() {
  return (await http.get<ApiResponse<Ingredient[]>>("/feeding/ingredients"))
    .data.data;
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
export async function getOrders() {
  return (await http.get<ApiResponse<MixingOrder[]>>("/feeding/mixing-orders"))
    .data.data;
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
