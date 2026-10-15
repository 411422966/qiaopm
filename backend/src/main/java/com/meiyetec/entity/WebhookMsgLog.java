package com.meiyetec.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * EXT-24：webhook_msg_log 表（webhook 推送去重 + 审计）
 * <p>写入入口：{@code YouzanWebhookController}。msg_id + source 唯一键判重。
 */
@Data
@TableName("webhook_msg_log")
public class WebhookMsgLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** YOUZAN / WECHAT / STRIPE / ... */
    private String source;
    /** webhook 携带的唯一消息 ID（去重键） */
    private String msgId;
    /** TRADE_ORDER / TRADE_PAID / ... */
    private String msgType;
    /** 外部订单号（辅助索引，非唯一） */
    private String externalOrderId;
    /** RECEIVED / PROCESSED / FAILED / DUPLICATE */
    private String status;
    /** 原始 body（去重场景下可空） */
    private String rawPayload;
    /** X-Youzan-Sign 头值，便于审计 */
    private String signature;
    private String errorMessage;
    private LocalDateTime processedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
