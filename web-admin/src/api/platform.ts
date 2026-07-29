import { http, type ApiResponse } from './http'

export interface FarmInfo {
  farmId: string
  farmName: string
  farmCode: string
  farmType: string
  contactName: string | null
  contactPhone: string | null
  status: string
  remark: string | null
  version: number
}

export interface BusinessRule {
  code: string
  name: string
  value: string
  valueType: string
  remark: string | null
}

export interface DashboardOverview {
  inField: number
  pendingTasks: number
  overdueTasks: number
  healthAlerts: number
  pregnantCows: number
  dueSoon: number
  withdrawalRestricted: number
  todayMixingOrders: number
}

export interface DictionaryType { typeId: string; typeCode: string; typeName: string; status: string }
export interface DictionaryItem { itemId: string; typeCode: string; itemCode: string; itemName: string; sortNo: number; status: string; remark: string | null }

const key = () => ({ 'X-Idempotency-Key': crypto.randomUUID() })
export async function getFarm() { return (await http.get<ApiResponse<FarmInfo>>('/farm')).data.data }
export async function updateFarm(payload: Record<string, unknown>) { return (await http.put<ApiResponse<FarmInfo>>('/farm', payload, { headers: key() })).data.data }
export async function getBusinessRules() { return (await http.get<ApiResponse<BusinessRule[]>>('/settings/business-rules')).data.data }
export async function updateBusinessRules(values: Record<string, string>) { return (await http.put<ApiResponse<BusinessRule[]>>('/settings/business-rules', { values }, { headers: key() })).data.data }
export async function getDashboardOverview() { return (await http.get<ApiResponse<DashboardOverview>>('/dashboard/overview')).data.data }
export async function getDictionaryTypes() { return (await http.get<ApiResponse<DictionaryType[]>>('/dictionaries/types')).data.data }
export async function getDictionaryItems(typeCode: string) { return (await http.get<ApiResponse<DictionaryItem[]>>('/dictionaries/entries', { params: { typeCode } })).data.data }
export async function createDictionaryItem(payload: Record<string, unknown>) { return (await http.post<ApiResponse<DictionaryItem>>('/dictionaries/entries', payload, { headers: key() })).data.data }
export async function updateDictionaryItem(id: string, payload: Record<string, unknown>) { return (await http.put<ApiResponse<DictionaryItem>>(`/dictionaries/entries/${id}`, payload, { headers: key() })).data.data }
