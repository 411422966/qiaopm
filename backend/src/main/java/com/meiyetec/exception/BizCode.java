package com.meiyetec.exception;

/**
 * 统一业务错误码。前端按 code 路由错误处理逻辑，message 仅用于展示。
 *
 * <p>设计原则：</p>
 * <ul>
 *   <li>1xx 业务通用；2xx 操作成功；3xx 客户端错误；4xx 权限；5xx 服务端</li>
 *   <li>已有错误码不删不修改，只能新增（向前兼容）</li>
 *   <li>每个错误码必须配备 TODO 测试用例（脚本覆盖）</li>
 * </ul>
 */
public enum BizCode {

    // 业务通用：内部使用 200，不要发回给前端
    OK(200, "操作成功"),

    // 客户端错误 4xx
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录"),
    FORBIDDEN(403, "无权访问"),
    NOT_FOUND(404, "资源不存在"),
    CONFLICT(409, "数据冲突"),
    CONCURRENCY_CONFLICT(409, "并发冲突，请重试"),
    IDEMPOTENT_REPLAY(409, "幂等命中，已返回上次结果"),
    IDEMPOTENT_KEY_CONFLICT(409, "幂等键已被使用，且请求体不同"),
    IDEMPOTENT_PROCESSING(409, "相同幂等键正在处理中，请稍后再试"),
    TENANT_DENIED(403, "跨租户访问被拒绝"),
    STORE_DENIED(403, "无权访问该门店数据"),
    PERMISSION_DENIED(403, "权限不足"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后重试"),

    // 业务错误 5xxx
    MEMBER_NOT_FOUND(5001, "会员不存在"),
    MEMBER_DUPLICATE_PHONE(5002, "手机号已存在"),
    MEMBER_FROZEN(5003, "会员已被冻结"),
    MEMBER_POINTS_NOT_ENOUGH(5004, "积分不足"),
    WALLET_BALANCE_NOT_ENOUGH(5101, "储值余额不足"),
    WALLET_TX_DUPLICATE(5102, "钱包流水重复"),
    PACKAGE_TIMES_NOT_ENOUGH(5201, "套餐次数不足"),
    PACKAGE_EXPIRED(5202, "套餐已过期"),
    STOCK_NOT_ENOUGH(5301, "库存不足"),
    STOCK_BATCH_EXPIRED(5302, "批次已过期"),
    STOCK_OVERBOOK(5303, "库存超卖，请重试"),
    BOOKING_CONFLICT(5401, "预约冲突"),
    BOOKING_STATUS_INVALID(5402, "预约状态不允许此操作"),
    ORDER_STATUS_INVALID(5501, "订单状态不允许此操作"),
    REFUND_AMOUNT_INVALID(5502, "退款金额不合法"),
    REFUND_EXCEED(5503, "退款金额超过可退"),
    PAYMENT_FAILED(5601, "支付失败"),
    AUTH_TOKEN_EXPIRED(5801, "登录已过期"),
    AUTH_USER_DISABLED(5802, "账号已禁用"),
    AUTH_TENANT_DISABLED(5803, "租户已停用"),
    AUTH_TENANT_EXPIRED(5804, "租户已过期"),
    PASSWORD_NOT_MATCH(5810, "密码错误"),
    USERNAME_NOT_FOUND(5811, "用户不存在"),
    CAPTCHA_INVALID(5812, "验证码错误"),
    IMPORT_HEADER_MISSING(5813, "导入模板表头不匹配"),
    EXPORT_TOO_LARGE(5814, "导出数据超过 10 万行"),
    ASYNC_TASK_NOT_FOUND(5815, "异步任务不存在"),

    // FISCAL-001：财税直连
    CONFIG_MISSING(5901, "渠道配置缺失"),
    FISCAL_GATEWAY_ERROR(5902, "财税网关调用失败"),
    FISCAL_STATUS_INVALID(5903, "当前发票状态不允许此操作"),

    // 服务器 5xx
    SYSTEM_ERROR(500, "系统繁忙，请稍后重试"),
    DB_ERROR(500, "数据库异常"),
    REDIS_ERROR(500, "缓存异常"),
    THIRD_PARTY_ERROR(500, "第三方服务异常"),
    CONCURRENT_RETRY_EXHAUSTED(500, "并发重试已用尽");

    public final int code;
    public final String message;

    BizCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
