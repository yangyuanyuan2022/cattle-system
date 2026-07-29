package com.cattlefarm.admin.auth;

import cn.dev33.satoken.stp.StpUtil;
import com.cattlefarm.admin.auth.service.AuthService;
import com.cattlefarm.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final com.cattlefarm.admin.auth.service.MiniappAuthService miniappAuthService;

    public AuthController(AuthService authService, com.cattlefarm.admin.auth.service.MiniappAuthService miniappAuthService) {
        this.authService = authService;
        this.miniappAuthService = miniappAuthService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                            HttpServletRequest servletRequest) {
        return ApiResponse.success(authService.login(request, servletRequest));
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me() {
        return ApiResponse.success(authService.currentUser());
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.success(authService.refresh(request));
    }

    @PostMapping("/miniapp/bind")
    public ApiResponse<LoginResponse> bind(
            @jakarta.validation.constraints.NotBlank @org.springframework.web.bind.annotation.RequestHeader("X-Idempotency-Key") String key,
            @Valid @RequestBody MiniappBindRequest request,
            HttpServletRequest servletRequest) {
        return ApiResponse.success(miniappAuthService.bind(request, key, servletRequest));
    }

    @PostMapping("/miniapp/unbind")
    public ApiResponse<Void> unbind(
            @jakarta.validation.constraints.NotBlank @org.springframework.web.bind.annotation.RequestHeader("X-Idempotency-Key") String key) {
        miniappAuthService.unbind(key);
        return ApiResponse.success(null);
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authService.logout();
        return ApiResponse.success(null);
    }
}
