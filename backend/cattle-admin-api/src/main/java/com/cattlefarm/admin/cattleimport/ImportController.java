package com.cattlefarm.admin.cattleimport;

import cn.dev33.satoken.annotation.*;
import com.cattlefarm.common.api.ApiResponse;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/v1/imports") @SaCheckRole(value={"ADMIN","FARM_MANAGER"},mode=SaMode.OR)
public class ImportController {private final CattleImportService service;public ImportController(CattleImportService service){this.service=service;}
 @GetMapping public ApiResponse<List<ImportDtos.LogItem>>list(){return ApiResponse.success(service.list());}
 @GetMapping("/{importId}/errors")public ApiResponse<List<ImportDtos.ErrorItem>>errors(@PathVariable("importId")long id){return ApiResponse.success(service.errors(id));}
}
