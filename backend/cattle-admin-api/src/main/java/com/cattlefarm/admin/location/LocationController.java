package com.cattlefarm.admin.location;
import cn.dev33.satoken.annotation.*; import com.cattlefarm.common.api.ApiResponse; import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank; import org.springframework.web.bind.annotation.*; import java.util.List;
@RestController @RequestMapping("/api/v1")
public class LocationController {
 private final LocationService service; public LocationController(LocationService service){this.service=service;}
 @GetMapping("/barns") public ApiResponse<List<BarnResponse>> barns(@RequestParam(name="status",required=false) String status){return ApiResponse.success(service.barns(status));}
 @PostMapping("/barns") @SaCheckRole(value={"ADMIN","FARM_MANAGER"},mode=SaMode.OR)
 public ApiResponse<BarnResponse> createBarn(@RequestHeader("X-Idempotency-Key")@NotBlank String key,@Valid @RequestBody CreateBarnRequest r){return ApiResponse.success(service.createBarn(r,key));}
 @PutMapping("/barns/{id}") @SaCheckRole(value={"ADMIN","FARM_MANAGER"},mode=SaMode.OR) public ApiResponse<BarnResponse>updateBarn(@PathVariable("id")long id,@RequestHeader("X-Idempotency-Key")@NotBlank String key,@Valid@RequestBody UpdateBarnRequest r){return ApiResponse.success(service.updateBarn(id,r,key));}
 @GetMapping("/herds") public ApiResponse<List<HerdResponse>> herds(@RequestParam(name="status",required=false) String status){return ApiResponse.success(service.herds(status));}
 @PostMapping("/herds") @SaCheckRole(value={"ADMIN","FARM_MANAGER"},mode=SaMode.OR)
 public ApiResponse<HerdResponse> createHerd(@RequestHeader("X-Idempotency-Key")@NotBlank String key,@Valid @RequestBody CreateHerdRequest r){return ApiResponse.success(service.createHerd(r,key));}
 @PutMapping("/herds/{id}") @SaCheckRole(value={"ADMIN","FARM_MANAGER"},mode=SaMode.OR) public ApiResponse<HerdResponse>updateHerd(@PathVariable("id")long id,@RequestHeader("X-Idempotency-Key")@NotBlank String key,@Valid@RequestBody UpdateHerdRequest r){return ApiResponse.success(service.updateHerd(id,r,key));}
}
