import { http, type ApiResponse } from "./http";
export interface Barn {
  barnId: string;
  barnCode: string;
  barnName: string;
  barnType: string | null;
  capacity: number | null;
  status: string;
  remark: string | null;
  cattleCount: number;
  version: number;
}
export interface Herd {
  herdId: string;
  herdCode: string;
  herdName: string;
  herdType: string | null;
  barnId: string | null;
  barnName: string | null;
  status: string;
  remark: string | null;
  cattleCount: number;
  version: number;
}
export interface TransferPayload {
  cattleId: string;
  toBarnId: string;
  toHerdId?: string;
  transferDate: string;
  reason: string;
  version: number;
}
export interface TransferResult {
  batchId: string;
  transferId: string;
  cattleId: string;
  toBarnId: string;
  toHerdId: string | null;
  capacityExceeded: boolean;
  warning: string | null;
  cattleVersion: number;
}
export async function getBarns(status?: string) {
  const r = await http.get<ApiResponse<Barn[]>>("/barns", {
    params: { status },
  });
  return r.data.data;
}
export async function createBarn(payload: Record<string, unknown>) {
  const r = await http.post<ApiResponse<Barn>>("/barns", payload);
  return r.data.data;
}
export async function updateBarn(id: string, payload: Record<string, unknown>) {
  const r = await http.put<ApiResponse<Barn>>(`/barns/${id}`, payload, {
    headers: { "X-Idempotency-Key": crypto.randomUUID() },
  });
  return r.data.data;
}
export async function getHerds(status?: string) {
  const r = await http.get<ApiResponse<Herd[]>>("/herds", {
    params: { status },
  });
  return r.data.data;
}
export async function createHerd(payload: Record<string, unknown>) {
  const r = await http.post<ApiResponse<Herd>>("/herds", payload);
  return r.data.data;
}
export async function updateHerd(id: string, payload: Record<string, unknown>) {
  const r = await http.put<ApiResponse<Herd>>(`/herds/${id}`, payload, {
    headers: { "X-Idempotency-Key": crypto.randomUUID() },
  });
  return r.data.data;
}
export async function transferCattle(payload: TransferPayload, key: string) {
  const r = await http.post<ApiResponse<TransferResult>>(
    "/transfers",
    payload,
    { headers: { "X-Idempotency-Key": key } },
  );
  return r.data.data;
}
