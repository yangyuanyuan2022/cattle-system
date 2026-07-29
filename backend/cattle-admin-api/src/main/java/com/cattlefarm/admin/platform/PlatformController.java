package com.cattlefarm.admin.platform;
import cn.dev33.satoken.annotation.*;import com.cattlefarm.common.api.ApiResponse;import jakarta.validation.Valid;import jakarta.validation.constraints.NotBlank;import org.springframework.web.bind.annotation.*;import java.util.*;
@RestController @RequestMapping("/api/v1")
public class PlatformController{private final PlatformService service;public PlatformController(PlatformService service){this.service=service;}
 @GetMapping("/farm")public ApiResponse<PlatformDtos.Farm>farm(){return ApiResponse.success(service.farm());}
 @PutMapping("/farm")@SaCheckRole("ADMIN")public ApiResponse<PlatformDtos.Farm>updateFarm(@RequestHeader("X-Idempotency-Key")@NotBlank String key,@Valid@RequestBody PlatformDtos.UpdateFarm r){return ApiResponse.success(service.updateFarm(r,key));}
 @GetMapping("/settings/business-rules")@SaCheckRole(value={"ADMIN","FARM_MANAGER"},mode=SaMode.OR)public ApiResponse<List<PlatformDtos.Rule>>rules(){return ApiResponse.success(service.rules());}
 @PutMapping("/settings/business-rules")@SaCheckRole("ADMIN")public ApiResponse<List<PlatformDtos.Rule>>updateRules(@RequestHeader("X-Idempotency-Key")@NotBlank String key,@Valid@RequestBody PlatformDtos.UpdateRules r){return ApiResponse.success(service.updateRules(r,key));}
 @GetMapping("/dashboard/overview")public ApiResponse<PlatformDtos.Dashboard>dashboard(){return ApiResponse.success(service.dashboard());}
 @GetMapping("/dictionaries/types")public ApiResponse<List<PlatformDtos.DictType>>types(){return ApiResponse.success(service.types());}
 @GetMapping("/dictionaries/entries")public ApiResponse<List<PlatformDtos.DictItem>>items(@RequestParam("typeCode")String type){return ApiResponse.success(service.items(type));}
 @PostMapping("/dictionaries/entries")@SaCheckRole("ADMIN")public ApiResponse<PlatformDtos.DictItem>createItem(@RequestHeader("X-Idempotency-Key")@NotBlank String key,@Valid@RequestBody PlatformDtos.CreateDictItem r){return ApiResponse.success(service.createItem(r,key));}
 @PutMapping("/dictionaries/entries/{id}")@SaCheckRole("ADMIN")public ApiResponse<PlatformDtos.DictItem>updateItem(@PathVariable("id")long id,@RequestHeader("X-Idempotency-Key")@NotBlank String key,@Valid@RequestBody PlatformDtos.UpdateDictItem r){return ApiResponse.success(service.updateItem(id,r,key));}
 @GetMapping("/system/enums")public ApiResponse<Map<String,List<Map<String,String>>>>enums(){return ApiResponse.success(Map.ofEntries(Map.entry("sex",e("MALE","公牛","FEMALE","母牛")),Map.entry("sourceType",e("BIRTH","场内出生","PURCHASE","外购")),Map.entry("presenceStatus",e("IN_FIELD","在场","EXITED","已离场")),Map.entry("healthStatus",e("NORMAL","正常","OBSERVING","观察中","TREATING","治疗中")),Map.entry("breedingStatus",e("WAIT_BREED","待配","BRED_WAIT_CHECK","已配待检","PREGNANT","妊娠","NEAR_CALVING","临产","POSTPARTUM","产后恢复")),Map.entry("taskStatus",e("PENDING","待处理","IN_PROGRESS","进行中","DONE","已完成","OVERDUE","已逾期","CANCELLED","已取消")),Map.entry("priority",e("NORMAL","普通","IMPORTANT","重要","URGENT","紧急"))));}
 private static List<Map<String,String>>e(String...v){List<Map<String,String>>out=new ArrayList<>();for(int i=0;i<v.length;i+=2)out.add(Map.of("code",v[i],"name",v[i+1]));return out;}
}
