package com.cattlefarm.admin.attachment;

import com.cattlefarm.common.api.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@Validated
@RestController
@RequestMapping("/api/v1/attachments")
public class AttachmentController {
    private final AttachmentService service;

    public AttachmentController(AttachmentService service) { this.service = service; }

    @GetMapping
    public ApiResponse<java.util.List<AttachmentItem>> list(@RequestParam String businessType,
                                                             @RequestParam String businessId) {
        return ApiResponse.success(service.list(businessType, businessId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AttachmentItem> upload(
            @RequestHeader("X-Idempotency-Key") @NotBlank String key,
            @RequestParam String businessType,
            @RequestParam String businessId,
            @RequestPart MultipartFile file) {
        return ApiResponse.success(service.upload(businessType, businessId, file, key));
    }

    @GetMapping("/{attachmentId}")
    public ApiResponse<AttachmentItem> metadata(@PathVariable("attachmentId") long id) { return ApiResponse.success(service.find(id)); }

    @GetMapping("/{attachmentId}/content")
    public ResponseEntity<Resource> content(@PathVariable("attachmentId") long id) {
        AttachmentItem item = service.find(id);
        Resource resource = service.content(id);
        ContentDisposition disposition = ContentDisposition.attachment().filename(item.fileName(), StandardCharsets.UTF_8).build();
        MediaType type;
        try { type = MediaType.parseMediaType(item.fileType()); } catch (Exception ignored) { type = MediaType.APPLICATION_OCTET_STREAM; }
        return ResponseEntity.ok().contentType(type).contentLength(item.fileSize()).header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString()).body(resource);
    }

    @DeleteMapping("/{attachmentId}")
    public ApiResponse<Void> delete(@PathVariable("attachmentId") long id,
                                    @RequestHeader("X-Idempotency-Key") @NotBlank String key) {
        service.delete(id, key);
        return ApiResponse.success(null);
    }
}
