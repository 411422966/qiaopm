package com.meiyetec.booking;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * EXT-19：sendConfirmation 聚合结果
 *
 * <p>记录每个渠道的发送结果（成功/失败/外部 ID/错误信息）+ 实际使用的 locale。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BookingConfirmationResult {

    /** 整体成功标志（任一渠道成功即 true） */
    private boolean overallSuccess;
    /** 实际命中的 locale */
    private String resolvedLocale;
    /** 是否使用了 fallback（请求的 locale 模板不存在） */
    private Boolean usedFallback;
    /** 预约 ID */
    private Long bookingId;
    /** 各渠道结果：channel → {success, externalMsgId, errorMsg} */
    private Map<String, Map<String, Object>> channelResults = new LinkedHashMap<>();

    public void putChannel(String channel, boolean success, String externalMsgId, String errorMsg) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("success", success);
        r.put("externalMsgId", externalMsgId);
        r.put("errorMsg", errorMsg);
        channelResults.put(channel, r);
        if (success) overallSuccess = true;
    }
}
