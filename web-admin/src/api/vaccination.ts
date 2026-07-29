import{http,type ApiResponse}from'./http'
export interface VaccinationPlan{planId:string;planName:string;vaccineItem:string;planDate:string;dueDate:string;status:string;targetSummary:string;targetCount:number;executedCount:number;remark:string|null;version:number}
export interface VaccinationExecution{executionId:string;planId:string|null;planName:string|null;executionDate:string;vaccineItem:string;batchNo:string|null;executorName:string|null;remark:string|null;cattleCount:number;cattleSummary:string;version:number}
export async function getVaccinationPlans(status?:string){const r=await http.get<ApiResponse<VaccinationPlan[]>>('/vaccinations/plans',{params:{status}});return r.data.data}
export async function getVaccinationExecutions(planId?:string){const r=await http.get<ApiResponse<VaccinationExecution[]>>('/vaccinations/executions',{params:{planId}});return r.data.data}
export async function cancelVaccinationPlan(id:string,version:number,reason:string){const r=await http.post(`/vaccinations/plans/${id}/cancel`,{version,reason},{headers:{'X-Idempotency-Key':crypto.randomUUID()}});return r.data.data}
export async function voidVaccinationExecution(id:string,version:number,reason:string){const r=await http.post(`/vaccinations/executions/${id}/void`,{version,reason},{headers:{'X-Idempotency-Key':crypto.randomUUID()}});return r.data.data}
export async function createVaccinationPlan(payload:Record<string,unknown>,key:string){const r=await http.post<ApiResponse<VaccinationPlan>>('/vaccinations/plans',payload,{headers:{'X-Idempotency-Key':key}});return r.data.data}
export async function executeVaccination(payload:Record<string,unknown>,key:string){const r=await http.post('/vaccinations/executions',payload,{headers:{'X-Idempotency-Key':key}});return r.data.data}
