package com.cattlefarm.admin.audit;
import cn.dev33.satoken.annotation.*;import com.cattlefarm.common.api.ApiResponse;import jakarta.validation.constraints.*;import org.springframework.format.annotation.DateTimeFormat;import org.springframework.web.bind.annotation.*;import java.time.LocalDate;
@RestController @RequestMapping("/api/v1/audit")
public class AuditController{
 private final AuditService service;public AuditController(AuditService service){this.service=service;}
 @GetMapping("/operations")@SaCheckRole(value={"ADMIN","FARM_MANAGER"},mode=SaMode.OR)
 public ApiResponse<AuditDtos.Page<AuditDtos.Operation>>operations(@RequestParam(name="page",defaultValue="1")@Min(1)int page,@RequestParam(name="pageSize",defaultValue="20")@Min(1)@Max(200)int pageSize,@RequestParam(name="module",required=false)String module,@RequestParam(name="startDate",required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate startDate,@RequestParam(name="endDate",required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate endDate){return ApiResponse.success(service.operations(page,pageSize,module,startDate,endDate));}
 @GetMapping("/logins")@SaCheckRole("ADMIN")
 public ApiResponse<AuditDtos.Page<AuditDtos.Login>>logins(@RequestParam(name="page",defaultValue="1")@Min(1)int page,@RequestParam(name="pageSize",defaultValue="20")@Min(1)@Max(200)int pageSize,@RequestParam(name="result",required=false)String result){return ApiResponse.success(service.logins(page,pageSize,result));}
 @GetMapping("/exports")@SaCheckRole(value={"ADMIN","FARM_MANAGER"},mode=SaMode.OR)
 public ApiResponse<AuditDtos.Page<AuditDtos.Export>>exports(@RequestParam(name="page",defaultValue="1")@Min(1)int page,@RequestParam(name="pageSize",defaultValue="20")@Min(1)@Max(200)int pageSize,@RequestParam(name="status",required=false)String status){return ApiResponse.success(service.exports(page,pageSize,status));}
}
