import{http,type ApiResponse}from'./http'
export interface WeightItem{weightId:string;cattleId:string;earTagNo:string;cattleName:string|null;measureDate:string;weightKg:number;measureMethod:string|null;changeKg:number|null;averageDailyGain:number|null;abnormal:boolean;warning:string|null;version:number}
export interface BodyConditionItem{bodyConditionId:string;cattleId:string;earTagNo:string;cattleName:string|null;scoreDate:string;score:number;remark:string|null;version:number}
export interface GrowthTrend{cattleId:string;earTagNo:string;weights:WeightItem[];bodyConditions:BodyConditionItem[]}
export interface HerdGrowthTrend{herdId:string;herdName:string;weights:{measureDate:string;averageWeightKg:number;cattleCount:number}[]}
export async function getGrowthTrend(cattleId:string){const r=await http.get<ApiResponse<GrowthTrend>>('/growth/trends',{params:{cattleId}});return r.data.data}
export async function getHerdGrowthTrend(herdId:string){const r=await http.get<ApiResponse<HerdGrowthTrend>>('/growth/herd-trends',{params:{herdId}});return r.data.data}
export async function recordWeight(payload:Record<string,unknown>,key:string){const r=await http.post<ApiResponse<WeightItem>>('/growth/weights',payload,{headers:{'X-Idempotency-Key':key}});return r.data.data}
export async function recordBodyCondition(payload:Record<string,unknown>,key:string){const r=await http.post<ApiResponse<BodyConditionItem>>('/growth/body-conditions',payload,{headers:{'X-Idempotency-Key':key}});return r.data.data}
export async function voidGrowthRecord(kind:'weights'|'body-conditions',id:string,version:number,reason:string){const r=await http.post(`/growth/${kind}/${id}/void`,{version,reason},{headers:{'X-Idempotency-Key':crypto.randomUUID()}});return r.data.data}
