package com.cattlefarm.admin.cattle;

import com.cattlefarm.common.api.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/cattle")
public class CattleQrController {
    private final CattleQrService service;
    public CattleQrController(CattleQrService service) { this.service = service; }

    @GetMapping(value = "/{cattleId}/qrcode", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qrcode(@PathVariable("cattleId") long id) {
        byte[] png = service.qrcode(id);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).contentLength(png.length)
                .cacheControl(CacheControl.noCache()).body(png);
    }

    @GetMapping("/scan")
    public ApiResponse<CattleResponse> scan(@RequestParam @NotBlank String token) {
        return ApiResponse.success(service.scan(token));
    }
}
