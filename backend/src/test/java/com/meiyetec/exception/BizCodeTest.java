package com.meiyetec.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R2.0-E · 业务错误码基础断言（纯单元测试）
 * 设计：code 字段是 200/4xx/5xxx 复合编码（HTTP 状态码 + 业务码）
 * 消息字段非空
 */
class BizCodeTest {

    @Test
    @DisplayName("所有错误码 code 合法（范围 200~6000）")
    void allCodesHaveValidCodeRange() {
        for (BizCode b : BizCode.values()) {
            int code = b.code;
            assertTrue(code >= 200 && code <= 6000,
                    "非法 code " + code + " for " + b.name());
        }
    }

    @Test
    @DisplayName("所有错误码消息非空")
    void allCodesHaveMessage() {
        for (BizCode b : BizCode.values()) {
            assertNotNull(b.message, "无消息 " + b.name());
            assertTrue(b.message.length() > 0, "空消息 " + b.name());
        }
    }

    @Test
    @DisplayName("R2.0-B 幂等 & 并发语义码都存在")
    void concurrencyAndIdempotencyCodes() {
        // 库存超卖 5303
        assertEquals(5303, BizCode.STOCK_OVERBOOK.code);
        // 退款超额 5503
        assertEquals(5503, BizCode.REFUND_EXCEED.code);
        // 幂等 409 三个
        assertEquals(409, BizCode.IDEMPOTENT_REPLAY.code);
        assertEquals(409, BizCode.IDEMPOTENT_KEY_CONFLICT.code);
        assertEquals(409, BizCode.IDEMPOTENT_PROCESSING.code);
        // 并发冲突 409
        assertEquals(409, BizCode.CONCURRENCY_CONFLICT.code);
    }

    @Test
    @DisplayName("R2.0-C 退款 & 对账语义码")
    void refundAndReconciliationCodes() {
        assertEquals(409, BizCode.CONFLICT.code);
        assertEquals(5501, BizCode.ORDER_STATUS_INVALID.code);
        assertEquals(5502, BizCode.REFUND_AMOUNT_INVALID.code);
        assertEquals(404, BizCode.NOT_FOUND.code);
    }

    @Test
    @DisplayName("HTTP 4xx 业务异常码")
    void badRequestCodes() {
        assertEquals(400, BizCode.BAD_REQUEST.code);
        assertEquals(403, BizCode.FORBIDDEN.code);
        assertEquals(401, BizCode.UNAUTHORIZED.code);
    }

    @Test
    @DisplayName("OK 仅用于内部，不暴露给前端接口")
    void okIsInternalOnly() {
        assertEquals(200, BizCode.OK.code);
    }
}
