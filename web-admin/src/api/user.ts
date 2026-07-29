import{http,type ApiResponse}from'./http'
export interface DataScope{scopeType:string;scopeObjectId:string|null}
export interface UserItem{userId:string;username:string;realName:string;phone:string|null;status:string;roles:string[];dataScopes:DataScope[];lastLoginAt:string|null;version:number}
export interface RoleItem{roleId:string;roleCode:string;roleName:string;status:string;permissionCodes:string[];version:number}
export interface PermissionGroup{code:string;name:string;roles:string[]}
export async function getUsers(){return(await http.get<ApiResponse<UserItem[]>>('/users')).data.data}export async function getRoles(){return(await http.get<ApiResponse<RoleItem[]>>('/roles')).data.data}export async function getPermissions(){return(await http.get<ApiResponse<PermissionGroup[]>>('/permissions/tree')).data.data}export async function createUser(p:Record<string,unknown>){return(await http.post<ApiResponse<UserItem>>('/users',p,{headers:{'X-Idempotency-Key':crypto.randomUUID()}})).data.data}export async function updateUser(id:string,p:Record<string,unknown>){return(await http.put<ApiResponse<UserItem>>(`/users/${id}`,p,{headers:{'X-Idempotency-Key':crypto.randomUUID()}})).data.data}
export async function createRole(p:Record<string,unknown>){return(await http.post<ApiResponse<RoleItem>>('/roles',p,{headers:{'X-Idempotency-Key':crypto.randomUUID()}})).data.data}
export async function updateRole(id:string,p:Record<string,unknown>){return(await http.put<ApiResponse<RoleItem>>(`/roles/${id}`,p,{headers:{'X-Idempotency-Key':crypto.randomUUID()}})).data.data}
