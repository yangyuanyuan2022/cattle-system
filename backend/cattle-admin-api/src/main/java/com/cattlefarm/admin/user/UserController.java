package com.cattlefarm.admin.user;
import cn.dev33.satoken.annotation.*;import com.cattlefarm.common.api.ApiResponse;import jakarta.validation.Valid;import jakarta.validation.constraints.NotBlank;import org.springframework.web.bind.annotation.*;import java.util.List;
@RestController
public class UserController{private final UserService service;public UserController(UserService service){this.service=service;}
 @GetMapping("/api/v1/users")@SaCheckRole(value={"ADMIN","FARM_MANAGER"},mode=SaMode.OR)public ApiResponse<List<UserDtos.Item>>users(){return ApiResponse.success(service.users());}
 @PostMapping("/api/v1/users")@SaCheckRole("ADMIN")public ApiResponse<UserDtos.Item>create(@RequestHeader("X-Idempotency-Key")@NotBlank String key,@Valid@RequestBody UserDtos.Create r){return ApiResponse.success(service.create(r,key));}
 @PutMapping("/api/v1/users/{id}")@SaCheckRole("ADMIN")public ApiResponse<UserDtos.Item>update(@PathVariable("id")long id,@RequestHeader("X-Idempotency-Key")@NotBlank String key,@Valid@RequestBody UserDtos.Update r){return ApiResponse.success(service.update(id,r,key));}
 @GetMapping("/api/v1/roles")@SaCheckRole(value={"ADMIN","FARM_MANAGER"},mode=SaMode.OR)public ApiResponse<List<UserDtos.Role>>roles(){return ApiResponse.success(service.roles());}
 @PostMapping("/api/v1/roles")@SaCheckRole("ADMIN")public ApiResponse<UserDtos.Role>createRole(@RequestHeader("X-Idempotency-Key")@NotBlank String key,@Valid@RequestBody UserDtos.CreateRole r){return ApiResponse.success(service.createRole(r,key));}
 @PutMapping("/api/v1/roles/{id}")@SaCheckRole("ADMIN")public ApiResponse<UserDtos.Role>updateRole(@PathVariable("id")long id,@RequestHeader("X-Idempotency-Key")@NotBlank String key,@Valid@RequestBody UserDtos.UpdateRole r){return ApiResponse.success(service.updateRole(id,r,key));}
 @GetMapping("/api/v1/permissions/tree")@SaCheckRole(value={"ADMIN","FARM_MANAGER"},mode=SaMode.OR)public ApiResponse<List<UserDtos.PermissionGroup>>permissions(){return ApiResponse.success(service.permissions());}
}
