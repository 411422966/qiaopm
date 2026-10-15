package com.meiyetec.exception;

import com.meiyetec.util.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 统一异常处理。业务异常透出 BizCode，方便前端按 code 处理；
 * 系统异常使用 500 但仍带 BizCode.SYSTEM_ERROR，避免 "系统繁忙" 模糊化。
 * <p>注：Spring Security 当前未启用，因此不依赖 AccessDeniedException。
 * 权限拦截由 {@link com.meiyetec.security.PermissionAspect} 抛 {@link BusinessException} 接管。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        int code = e.getCode();
        HttpStatus status = (code >= 400 && code < 500) ? HttpStatus.valueOf(code) : HttpStatus.OK;
        log.warn("[BizException] code={} msg={}", code, e.getMessage());
        return ResponseEntity.status(status).body(Result.error(code, e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getAllErrors().stream()
                .map(err -> err.getDefaultMessage() == null ? "参数错误" : err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.ok(Result.error(BizCode.BAD_REQUEST.code, msg));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Result<Void>> handleOptimistic(OptimisticLockingFailureException e) {
        log.warn("[OptimisticLock] {}", e.getMessage());
        return ResponseEntity.ok(Result.error(BizCode.CONCURRENCY_CONFLICT));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Result<Void>> handleDb(DataAccessException e) {
        log.error("[DBError]", e);
        return ResponseEntity.ok(Result.error(BizCode.DB_ERROR));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Result<Void>> handleRuntime(RuntimeException e) {
        log.warn("[RuntimeException] {}", e.getMessage());
        return ResponseEntity.ok(Result.error(BizCode.SYSTEM_ERROR.code, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleAny(Exception e) {
        log.error("[SystemException]", e);
        return ResponseEntity.ok(Result.error(BizCode.SYSTEM_ERROR));
    }
}
