import { http, type ApiResponse } from "./http";
export interface BreedingEvent {
  businessId: string;
  cattleId: string;
  earTagNo: string;
  eventDate: string;
  eventType: string;
  summary: string;
  breedingStatus: string | null;
}
export type BreedingRecordKind = "heats" | "inseminations" | "pregnancy-checks" | "calvings";
export interface BreedingRecord {
  heatId?: string;
  inseminationId?: string;
  checkId?: string;
  calvingId?: string;
  heatTime?: string;
  inseminationDate?: string;
  checkDate?: string;
  calvingDate?: string;
  symptoms?: string;
  semenOrBull?: string;
  result?: string;
  difficultyLevel?: string;
  version: number;
}
export async function getBreedingRecords(kind: BreedingRecordKind, cattleId: string) {
  const r = await http.get<ApiResponse<BreedingRecord[]>>(`/breeding/${kind}`, { params: { cattleId } });
  return r.data.data;
}
export async function voidBreedingRecord(kind: BreedingRecordKind, id: string, version: number, reason: string) {
  const r = await http.post(`/breeding/${kind}/${id}/void`, { version, reason }, { headers: { "X-Idempotency-Key": crypto.randomUUID() } });
  return r.data.data;
}
export async function getBreedingEvents(cattleId?: string) {
  const r = await http.get<ApiResponse<BreedingEvent[]>>("/breeding/events", {
    params: { cattleId },
  });
  return r.data.data;
}
export interface DueCow {
  cattleId: string;
  earTagNo: string;
  cattleName: string | null;
  barnName: string | null;
  herdName: string | null;
  checkId: string;
  expectedCalvingDate: string;
  daysUntilCalving: number;
  breedingStatus: string;
}
export async function getDueCows(days = 30) {
  return (
    await http.get<ApiResponse<DueCow[]>>("/breeding/due-cows", {
      params: { days },
    })
  ).data.data;
}
async function post(
  path: string,
  payload: Record<string, unknown>,
  key: string,
) {
  const r = await http.post(`/breeding/${path}`, payload, {
    headers: { "X-Idempotency-Key": key },
  });
  return r.data.data;
}
export const recordEstrus = (p: Record<string, unknown>, k: string) =>
  post("estrus", p, k);
export const recordBreeding = (p: Record<string, unknown>, k: string) =>
  post("inseminations", p, k);
export const recordPregnancy = (p: Record<string, unknown>, k: string) =>
  post("pregnancy-checks", p, k);
export const recordCalving = (p: Record<string, unknown>, k: string) =>
  post("calvings", p, k);
