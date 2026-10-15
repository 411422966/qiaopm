package com.meiyetec.exception;

import lombok.Getter;

/**
 * 统一业务异常。GlobalExceptionHandler 会自动把它的 code / message 透出给前端。
 * 业务代码应优先抛出 BusinessException 而不是 RuntimeException，以便：
 * 1. 错误码可被前端按 code 精确处理；
 * 2. 单元测试和接口测试可通过 code 校验断言；
 * 3. 控制器层不再出现 500 / "系统繁忙" 等模糊错误。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(BizCode bizCode) {
        this(bizCode.code, bizCode.message);
    }

    public BusinessException(BizCode bizCode, String detail) {
        this(bizCode.code, bizCode.message + ": " + detail);
    }

    // ===== 静态工厂 =====

    public static BusinessException unauthorized(String message) {
        return new BusinessException(BizCode.UNAUTHORIZED.code, message);
    }

    public static BusinessException unauthorized() {
        return new BusinessException(BizCode.UNAUTHORIZED);
    }

    public static BusinessException forbidden(String message) {
        return new BusinessException(BizCode.FORBIDDEN.code, message);
    }

    public static BusinessException forbidden() {
        return new BusinessException(BizCode.FORBIDDEN);
    }

    public static BusinessException badRequest(String message) {
        return new BusinessException(BizCode.BAD_REQUEST.code, message);
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(BizCode.NOT_FOUND.code, message);
    }

    public static BusinessException conflict(String message) {
        return new BusinessException(BizCode.CONFLICT.code, message);
    }

    /**
     * 兼容老代码：传入 int code 与 message。
     */
    public static BusinessException of(int code, String message) {
        return new BusinessException(code, message);
    }
}
