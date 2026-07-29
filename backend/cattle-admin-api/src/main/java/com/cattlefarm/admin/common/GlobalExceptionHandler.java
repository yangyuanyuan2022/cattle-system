package com.cattlefarm.admin.common;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import com.cattlefarm.admin.auth.AuthException;
import com.cattlefarm.admin.auth.RefreshTokenException;
import com.cattlefarm.admin.cattle.CattleNotFoundException;
import com.cattlefarm.common.api.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RefreshTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleRefreshToken(RefreshTokenException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.failure("40102", exception.getMessage()));
    }

    @ExceptionHandler({NotLoginException.class, AuthException.class})
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(Exception exception) {
        String message = exception instanceof AuthException ? exception.getMessage() : "登录已过期，请重新登录";
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.failure("40101", message));
    }

    @ExceptionHandler(NotRoleException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(NotRoleException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.failure("40301", "无权执行该操作"));
    }

    @ExceptionHandler(DataScopeForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataScopeForbidden(DataScopeForbiddenException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.failure("40302", exception.getMessage()));
    }

    @ExceptionHandler({DuplicateKeyException.class, DataConflictException.class})
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(Exception exception) {
        String message = exception instanceof DataConflictException ? exception.getMessage() : "数据已存在或请求重复";
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.failure("40901", message));
    }

    @ExceptionHandler(CattleNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleCattleNotFound(CattleNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.failure("40401", exception.getMessage()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(ConstraintViolationException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.failure("VALIDATION_ERROR", exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleBodyValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst().map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("请求参数校验失败");
        return ResponseEntity.badRequest().body(ApiResponse.failure("VALIDATION_ERROR", message));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodValidation(HandlerMethodValidationException exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.failure("VALIDATION_ERROR", "请求参数校验失败"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(ApiResponse.failure("VALIDATION_ERROR", "请求内容格式错误"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        log.error("Unhandled API exception", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure("INTERNAL_ERROR", "系统内部错误"));
    }
}
