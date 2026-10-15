package com.meiyetec.booking;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * EXT-19：预约确认模板（渲染后）
 *
 * <p>{@code sms} 是单字符串（≤70 字 / 运营商分页规则）；
 * {@code miniProgram} 是微信订阅消息 keyword1..N 的 value 映射；
 * {@code emailSubject} / {@code emailBody} 是邮件主题与正文。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingConfirmationTemplate {

    /** 实际命中的 locale（fallback 后） */
    private String resolvedLocale;
    /** 是否命中 fallback（请求的 locale 不存在） */
    private Boolean usedFallback;
    /** 模板主题（兼容所有渠道） */
    private String subject;
    /** 短信正文（已渲染） */
    private String sms;
    /** 小程序 keyword1..N（已渲染） */
    private Map<String, String> miniProgram = new LinkedHashMap<>();
    /** 小程序卡片标题（已渲染） */
    private String miniProgramTitle;
    /** 邮件主题（已渲染） */
    private String emailSubject;
    /** 邮件正文（已渲染） */
    private String emailBody;
}
