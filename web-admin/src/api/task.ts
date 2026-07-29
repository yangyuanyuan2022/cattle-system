import{http,type ApiResponse}from'./http'
export interface TaskItem{taskId:string;sourceType:string;sourceId:string|null;taskType:string;title:string;cattleId:string|null;earTagNo:string|null;planDate:string;dueDate:string;priority:string;status:string;result:string|null;assigneeName:string|null;version:number}
export async function getTasks(status?:string){const r=await http.get<ApiResponse<TaskItem[]>>('/tasks',{params:{status}});return r.data.data}
async function write(path:string,payload:Record<string,unknown>,key:string){const r=await http.post<ApiResponse<TaskItem>>(path,payload,{headers:{'X-Idempotency-Key':key}});return r.data.data}
export const createTask=(p:Record<string,unknown>,k:string)=>write('/tasks',p,k)
export const completeTask=(id:string,p:Record<string,unknown>,k:string)=>write(`/tasks/${id}/complete`,p,k)
export const rescheduleTask=(id:string,p:Record<string,unknown>,k:string)=>write(`/tasks/${id}/reschedule`,p,k)
export const cancelTask=(id:string,p:Record<string,unknown>,k:string)=>write(`/tasks/${id}/cancel`,p,k)
