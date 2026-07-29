import{http,type ApiResponse}from'./http'
export interface Metric{code:string;label:string;value:number;unit:string}export interface Breakdown{code:string;label:string;value:number}
export interface ReportOverview{startDate:string;endDate:string;inventory:Metric[];lifecycleStages:Breakdown[];herds:Breakdown[];movements:Metric[];breeding:Metric[];healthVaccination:Metric[];feeding:Metric[];tasks:Metric[]}
export interface InventoryReport{startDate:string;endDate:string;metrics:Metric[];lifecycleStages:Breakdown[];herds:Breakdown[];movements:Metric[]}
export interface SectionReport{startDate:string;endDate:string;metrics:Metric[]}
export interface ExportItem{exportId:string;module:string;status:string;fileName:string|null;rowCount:number|null;failReason:string|null;expiredAt:string|null;createdAt:string}
export async function getReportOverview(startDate:string,endDate:string){return(await http.get<ApiResponse<ReportOverview>>('/reports/overview',{params:{startDate,endDate}})).data.data}
export async function getInventoryReport(startDate:string,endDate:string){return(await http.get<ApiResponse<InventoryReport>>('/reports/inventory',{params:{startDate,endDate}})).data.data}
export async function getBreedingReport(startDate:string,endDate:string){return(await http.get<ApiResponse<SectionReport>>('/reports/breeding',{params:{startDate,endDate}})).data.data}
export async function getHealthReport(startDate:string,endDate:string){return(await http.get<ApiResponse<SectionReport>>('/reports/health',{params:{startDate,endDate}})).data.data}
export async function getTaskReport(startDate:string,endDate:string){return(await http.get<ApiResponse<SectionReport>>('/reports/tasks',{params:{startDate,endDate}})).data.data}
export async function createReportExport(startDate:string,endDate:string){return(await http.post<ApiResponse<ExportItem>>('/reports/exports',{startDate,endDate},{headers:{'X-Idempotency-Key':crypto.randomUUID()}})).data.data}
export async function getReportExports(){return(await http.get<ApiResponse<ExportItem[]>>('/reports/exports')).data.data}
export async function downloadReportExport(item:ExportItem){const r=await http.get(`/reports/exports/${item.exportId}/download`,{responseType:'blob'});const url=URL.createObjectURL(r.data);const a=document.createElement('a');a.href=url;a.download=item.fileName||'cattle-report.xlsx';a.click();URL.revokeObjectURL(url)}
