import { http, type ApiResponse } from "./http";

export interface ImportError {
  errorId: string;
  rowNo: number;
  fieldName: string;
  rawValue: string;
  errorCode: string;
  errorMessage: string;
}
export interface ImportResult {
  importId: string;
  status: string;
  totalCount: number;
  successCount: number;
  failCount: number;
  errors: ImportError[];
}
export interface ImportLog {
  importId: string;
  module: string;
  fileName: string;
  status: string;
  totalCount: number;
  successCount: number;
  failCount: number;
  errorSummary: string | null;
  operatorId: string;
  createdAt: string;
}
export interface TransferItem {
  transferId: string;
  batchId: string;
  cattleId: string;
  earTagNo: string;
  fromBarnName: string | null;
  fromHerdName: string | null;
  toBarnName: string;
  toHerdName: string | null;
  transferDate: string;
  reason: string;
  operatorName: string;
  voided: boolean;
}
export interface ExitItem {
  exitId: string;
  cattleId: string;
  earTagNo: string;
  exitType: string;
  exitDate: string;
  reason: string;
  operatorName: string;
  restoredAt: string | null;
  restoreReason: string | null;
  voided: boolean;
  voidReason: string | null;
}

const key = () => ({ "X-Idempotency-Key": crypto.randomUUID() });
function downloadBlob(blob: Blob, name: string) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = name;
  a.click();
  URL.revokeObjectURL(url);
}

export async function downloadImportTemplate() {
  const r = await http.get("/cattle/import/template", { responseType: "blob" });
  downloadBlob(r.data, "cattle-import-template.xlsx");
}
export async function validateCattleImport(file: File) {
  const form = new FormData();
  form.append("file", file);
  return (
    await http.post<ApiResponse<ImportResult>>(
      "/cattle/import/validate",
      form,
      { headers: { ...key(), "Content-Type": "multipart/form-data" } },
    )
  ).data.data;
}
export async function confirmCattleImport(importId: string) {
  return (
    await http.post<ApiResponse<ImportResult>>(
      "/cattle/import/confirm",
      { importId },
      { headers: key() },
    )
  ).data.data;
}
export async function getImportLogs() {
  return (await http.get<ApiResponse<ImportLog[]>>("/imports")).data.data;
}
export async function getImportErrors(importId: string) {
  return (
    await http.get<ApiResponse<ImportError[]>>(`/imports/${importId}/errors`)
  ).data.data;
}
export async function getTransfers() {
  return (await http.get<ApiResponse<TransferItem[]>>("/transfers")).data.data;
}
export async function batchTransfer(payload: Record<string, unknown>) {
  return (await http.post("/transfers/batch", payload, { headers: key() })).data
    .data;
}
export async function voidTransfer(
  transferId: string,
  reason: string,
  cattleVersion: number,
) {
  return (
    await http.post<ApiResponse<TransferItem>>(
      `/transfers/${transferId}/void`,
      { reason, cattleVersion },
      { headers: key() },
    )
  ).data.data;
}
export async function getExits() {
  return (await http.get<ApiResponse<ExitItem[]>>("/exits")).data.data;
}
export async function voidExit(
  exitId: string,
  reason: string,
  cattleVersion: number,
) {
  return (
    await http.post<ApiResponse<ExitItem>>(
      `/exits/${exitId}/void`,
      { reason, cattleVersion },
      { headers: key() },
    )
  ).data.data;
}
export async function getCattleQr(cattleId: string) {
  return (
    await http.get(`/cattle/${cattleId}/qrcode`, { responseType: "blob" })
  ).data as Blob;
}
