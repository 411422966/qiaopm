package com.meiyetec.util;

import com.meiyetec.exception.BizCode;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
public class Result<T> {
    private int code;
    private String message;
    private T data;
    private String traceId;
    private String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("操作成功");
        result.setData(data);
        return result;
    }

    public static <T> Result<T> success(String message, T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage(message);
        result.setData(data);
        return result;
    }

    public static <T> Result<T> ok() {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("操作成功");
        return result;
    }

    public static <T> Result<T> ok(T data) {
        return success(data);
    }

    public static <T> Result<T> error(String message) {
        Result<T> result = new Result<>();
        result.setCode(500);
        result.setMessage(message);
        return result;
    }

    public static <T> Result<T> error(int code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    public static <T> Result<T> error(BizCode bizCode) {
        return error(bizCode.code, bizCode.message);
    }

    public static <T> Result<T> error(BizCode bizCode, String detail) {
        return error(bizCode.code, bizCode.message + ": " + detail);
    }

    public static <T> Result<T> error(BizCode bizCode, String message, T data) {
        Result<T> result = new Result<>();
        result.code = bizCode.code;
        result.message = message;
        result.data = data;
        return result;
    }

    public static <T> Result<T> error(BizCode bizCode, T data) {
        Result<T> result = new Result<>();
        result.code = bizCode.code;
        result.message = bizCode.message;
        result.data = data;
        return result;
    }

    public static <T> Result<T> success(String message, T data, T extra) {
        Result<T> result = new Result<>();
        result.code = 200;
        result.message = message;
        result.data = data;
        return result;
    }

    public static <T> Result<T> success(T data, T extra) {
        Result<T> result = new Result<>();
        result.code = 200;
        result.message = "操作成功";
        result.data = data;
        return result;
    }

    /**
     * 链路 traceId 注入（由拦截器或 GlobalExceptionHandler 调用）。
     */
    public Result<T> withTrace(String traceId) {
        this.traceId = traceId;
        return this;
    }
}
