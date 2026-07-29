import { http,type ApiResponse } from './http'
export interface HealthCase{caseId:string;caseNo:string;cattleId:string;earTagNo:string;cattleName:string|null;discoverDate:string;symptom:string;severity:string;caseStatus:string;healthStatus:string;treatmentCount:number;withdrawalUntil:string|null;version:number}
export interface TreatmentItem{treatmentId:string;treatmentDate:string;diagnosis:string;treatmentPlan:string|null;needFollowUp:boolean;followUpDate:string|null;vetName:string|null;version:number}
export interface FollowUpItem{followUpId:string;followUpDate:string;result:string;description:string|null;operatorName:string|null;version:number}
export interface HealthCaseDetail{caseInfo:HealthCase;treatments:TreatmentItem[];followUps:FollowUpItem[]}
export async function getHealthCases(status?:string){const r=await http.get<ApiResponse<HealthCase[]>>('/health/abnormalities',{params:{status}});return r.data.data}
export async function getHealthCaseDetail(caseId:string){const r=await http.get<ApiResponse<HealthCaseDetail>>(`/health/cases/${caseId}`);return r.data.data}
export async function voidHealthRecord(kind:'cases'|'treatments'|'follow-ups',id:string,version:number,reason:string){const r=await http.post(`/health/${kind}/${id}/void`,{version,reason},{headers:{'X-Idempotency-Key':crypto.randomUUID()}});return r.data.data}
export async function reportAbnormality(payload:Record<string,unknown>,key:string){const r=await http.post('/health/abnormalities',payload,{headers:{'X-Idempotency-Key':key}});return r.data.data}
export async function createTreatment(payload:Record<string,unknown>,key:string){const r=await http.post('/health/treatments',payload,{headers:{'X-Idempotency-Key':key}});return r.data.data}
export async function createFollowUp(payload:Record<string,unknown>,key:string){const r=await http.post('/health/follow-ups',payload,{headers:{'X-Idempotency-Key':key}});return r.data.data}
