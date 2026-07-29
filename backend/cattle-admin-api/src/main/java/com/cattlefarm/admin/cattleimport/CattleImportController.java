package com.cattlefarm.admin.cattleimport;

import cn.dev33.satoken.annotation.*;
import com.cattlefarm.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Validated @RestController @RequestMapping("/api/v1/cattle/import")
@SaCheckRole(value={"ADMIN","FARM_MANAGER"},mode=SaMode.OR)
public class CattleImportController {private final CattleImportService service;public CattleImportController(CattleImportService service){this.service=service;}
 @GetMapping("/template")public ResponseEntity<ByteArrayResource>template(){var data=service.template();return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=cattle-import-template.xlsx").contentLength(data.contentLength()).body(data);}
 @PostMapping(value="/validate",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)public ApiResponse<ImportDtos.Result>validate(@RequestHeader("X-Idempotency-Key")@NotBlank String key,@RequestPart MultipartFile file){return ApiResponse.success(service.validate(file,key));}
 @PostMapping("/confirm")public ApiResponse<ImportDtos.Result>confirm(@RequestHeader("X-Idempotency-Key")@NotBlank String key,@Valid@RequestBody ImportDtos.Confirm r){return ApiResponse.success(service.confirm(r,key));}
}
