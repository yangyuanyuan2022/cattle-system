package com.cattlefarm.admin.report;
import cn.dev33.satoken.annotation.*;import com.cattlefarm.common.api.ApiResponse;import jakarta.validation.Valid;import jakarta.validation.constraints.NotBlank;import org.springframework.core.io.Resource;import org.springframework.http.*;import org.springframework.web.bind.annotation.*;import org.springframework.web.util.UriUtils;import java.nio.charset.StandardCharsets;import java.util.List;
@RestController @RequestMapping("/api/v1/reports/exports") @SaCheckRole(value={"ADMIN","FARM_MANAGER"},mode=SaMode.OR)
public class ReportExportController{
 private final ReportExportService service;public ReportExportController(ReportExportService service){this.service=service;}
 @PostMapping public ApiResponse<ExportDtos.Item>create(@RequestHeader("X-Idempotency-Key")@NotBlank String key,@Valid@RequestBody ExportDtos.Create r){return ApiResponse.success(service.create(r,key));}
 @GetMapping public ApiResponse<List<ExportDtos.Item>>list(){return ApiResponse.success(service.list());}
 @GetMapping("/{id}")public ApiResponse<ExportDtos.Item>find(@PathVariable("id")long id){return ApiResponse.success(service.find(id));}
 @GetMapping("/{id}/download")public ResponseEntity<Resource>download(@PathVariable("id")long id){Resource file=service.download(id);String name=UriUtils.encode(file.getFilename(),StandardCharsets.UTF_8);return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename*=UTF-8''"+name).contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).body(file);}
}
