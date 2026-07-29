import { http, type ApiResponse } from "./http";

export interface CattleRecord {
  cattleId: string;
  farmId: string;
  earTagNo: string;
  name: string | null;
  sex: "MALE" | "FEMALE";
  breedId: string | null;
  birthDate: string | null;
  sourceType: "BIRTH" | "PURCHASE";
  entryDate: string;
  lifecycleStage: "CALF" | "GROWING" | "RESERVE" | "COW" | "BULL";
  presenceStatus: string;
  healthStatus: string;
  breedingStatus: string | null;
  herdId: string | null;
  barnId: string | null;
  remark: string | null;
  createdAt: string;
  version: number;
}

export interface CattlePage {
  page: number;
  pageSize: number;
  total: number;
  items: CattleRecord[];
}

export interface CattleTimelineEvent {
  eventId: string;
  eventType: string;
  eventDate: string;
  summary: string;
  operatorId: string | null;
}
export interface CattlePedigree { cattleId:string; sireId:string|null; sireEarTagNo:string|null; sireText:string|null; damId:string|null; damEarTagNo:string|null; offspring:{cattleId:string;earTagNo:string;name:string|null;sex:string}[] }

export interface CattleQuery {
  page: number;
  pageSize: number;
  keyword?: string;
  presenceStatus?: string;
  lifecycleStage?: string;
  sex?: string;
  breedId?: string;
  sourceType?: string;
  healthStatus?: string;
  barnId?: string;
}

export interface CreateCattlePayload {
  earTagNo: string;
  name?: string;
  sex: "MALE" | "FEMALE";
  breedId?: string;
  birthDate?: string;
  sourceType: "BIRTH" | "PURCHASE";
  entryDate: string;
  lifecycleStage: "CALF" | "GROWING" | "RESERVE" | "COW" | "BULL";
  herdId?: string;
  barnId?: string;
  remark?: string;
}

export interface UpdateCattlePayload {
  earTagNo: string;
  name?: string;
  birthDate?: string;
  remark?: string;
  changeReason: string;
  version: number;
}

export interface ArchiveCattlePayload {
  exitType: "SALE" | "DEATH" | "CULL" | "OTHER";
  exitDate: string;
  reason: string;
  treatingRiskConfirmed: boolean;
  version: number;
}

export interface RestoreCattlePayload {
  reason: string;
  version: number;
}

export async function getCattlePage(query: CattleQuery) {
  const response = await http.get<ApiResponse<CattlePage>>("/cattle", {
    params: query,
  });
  return response.data.data;
}

export async function getAllCattle(
  query: Omit<CattleQuery, "page" | "pageSize"> = {},
) {
  const items: CattleRecord[] = [];
  let page = 1;
  while (true) {
    const result = await getCattlePage({ ...query, page, pageSize: 100 });
    items.push(...result.items);
    if (items.length >= result.total || !result.items.length) return items;
    page += 1;
  }
}

export async function createCattle(
  payload: CreateCattlePayload,
  idempotencyKey: string,
) {
  const response = await http.post<ApiResponse<CattleRecord>>(
    "/cattle",
    payload,
    {
      headers: { "X-Idempotency-Key": idempotencyKey },
    },
  );
  return response.data.data;
}

export async function getCattleDetail(cattleId: string) {
  const response = await http.get<ApiResponse<CattleRecord>>(
    `/cattle/${cattleId}`,
  );
  return response.data.data;
}

export async function getCattleTimeline(cattleId: string) {
  const response = await http.get<ApiResponse<CattleTimelineEvent[]>>(
    `/cattle/${cattleId}/timeline`,
  );
  return response.data.data;
}
export async function getCattlePedigree(cattleId:string){const response=await http.get<ApiResponse<CattlePedigree>>(`/cattle/${cattleId}/pedigree`);return response.data.data}

export async function updateCattle(
  cattleId: string,
  payload: UpdateCattlePayload,
  idempotencyKey: string,
) {
  const response = await http.put<ApiResponse<CattleRecord>>(
    `/cattle/${cattleId}`,
    payload,
    {
      headers: { "X-Idempotency-Key": idempotencyKey },
    },
  );
  return response.data.data;
}

export async function archiveCattle(
  cattleId: string,
  payload: ArchiveCattlePayload,
  idempotencyKey: string,
) {
  const response = await http.post<ApiResponse<CattleRecord>>(
    `/cattle/${cattleId}/archive`,
    payload,
    {
      headers: { "X-Idempotency-Key": idempotencyKey },
    },
  );
  return response.data.data;
}

export async function restoreCattle(
  cattleId: string,
  payload: RestoreCattlePayload,
  idempotencyKey: string,
) {
  const response = await http.post<ApiResponse<CattleRecord>>(
    `/cattle/${cattleId}/restore`,
    payload,
    {
      headers: { "X-Idempotency-Key": idempotencyKey },
    },
  );
  return response.data.data;
}
