package com.cattlefarm.admin.task;
import cn.dev33.satoken.annotation.*;import com.cattlefarm.common.api.ApiResponse;import jakarta.validation.Valid;import jakarta.validation.constraints.NotBlank;import org.springframework.web.bind.annotation.*;import java.util.List;
@RestController @RequestMapping("/api/v1/tasks")
public class TaskController{private final TaskService service;public TaskController(TaskService service){this.service=service;}
 @GetMapping public ApiResponse<List<TaskDtos.Item>>list(@RequestParam(name="status",required=false)String status){return ApiResponse.success(service.list(status));}
 @GetMapping("/{id}")public ApiResponse<TaskDtos.Detail>detail(@PathVariable("id")long id){return ApiResponse.success(service.detail(id));}
 @PostMapping @SaCheckRole(value={"ADMIN","FARM_MANAGER"},mode=SaMode.OR)public ApiResponse<TaskDtos.Item>create(@RequestHeader("X-Idempotency-Key")@NotBlank String key,@Valid @RequestBody TaskDtos.Create r){return ApiResponse.success(service.create(r,key));}
 @PostMapping("/{id}/complete")public ApiResponse<TaskDtos.Item>complete(@PathVariable("id")long id,@RequestHeader("X-Idempotency-Key")@NotBlank String key,@Valid @RequestBody TaskDtos.Complete r){return ApiResponse.success(service.complete(id,r,key));}
 @PostMapping("/{id}/reschedule")public ApiResponse<TaskDtos.Item>reschedule(@PathVariable("id")long id,@RequestHeader("X-Idempotency-Key")@NotBlank String key,@Valid @RequestBody TaskDtos.Reschedule r){return ApiResponse.success(service.reschedule(id,r,key));}
 @PostMapping("/{id}/cancel")@SaCheckRole(value={"ADMIN","FARM_MANAGER"},mode=SaMode.OR)public ApiResponse<TaskDtos.Item>cancel(@PathVariable("id")long id,@RequestHeader("X-Idempotency-Key")@NotBlank String key,@Valid @RequestBody TaskDtos.Cancel r){return ApiResponse.success(service.cancel(id,r,key));}
}
