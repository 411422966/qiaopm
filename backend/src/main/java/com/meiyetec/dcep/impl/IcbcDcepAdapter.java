package com.meiyetec.dcep.impl;

import com.meiyetec.dcep.AbstractDcepAdapter;
import com.meiyetec.dcep.DcepChannelAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 工商银行（ICBC）DCEP 适配器
 *
 * <p>对接方式：工行开放平台（{@code https://open.icbc.com.cn}）
 * <p>API 形态：RESTful + JSON
 * <p>签名：RSA-2048 + SHA-256（支持 SM2/SM3 国密）
 *
 * <p>特色：数字人民币智能合约平台（DLCC + 工银玺链）支持可编程消费券
 *
 * <p><b>当前状态：占位实现，等待真实 SDK 接入</b>
 */
@Component("icbcDcepAdapter")
public class IcbcDcepAdapter extends AbstractDcepAdapter implements DcepChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(IcbcDcepAdapter.class);

    @Override
    public String channelCode() {
        return "DCEP-ICBC";
    }

    @Override
    public PrepayResult prepay(PrepayRequest req) {
        // TODO(SDK-3)：调用 ICBC 开放平台 /pay/prepay
        // 步骤：
        //   1. 构造 REST 请求：merchantId + appId + orderAmount + goodsName + notifyUrl
        //   2. 用 RSA-2048 私钥签名
        //   3. HTTPS POST 到 https://open.icbc.com.cn/api/pay/prepay
        //   4. 解析响应：prepayId、qrCode、h5Url
        log.warn("[DCEP-ICBC] prepay() not yet implemented, order={}", req.getDcepOrderNo());
        return failPrepay("NOT_IMPLEMENTED", "工行 DCEP 适配器尚未对接真实 SDK");
    }

    @Override
    public QueryResult query(OrderQueryRequest req) {
        // TODO(SDK-3)：调用 ICBC /pay/query
        log.warn("[DCEP-ICBC] query() not yet implemented, order={}", req.getDcepOrderNo());
        return QueryResult.builder()
            .success(false)
            .errorCode("NOT_IMPLEMENTED")
            .build();
    }

    @Override
    public RefundResult refund(RefundRequest req) {
        // TODO(SDK-3)：调用 ICBC /pay/refund
        log.warn("[DCEP-ICBC] refund() not yet implemented, order={}", req.getDcepOrderNo());
        return RefundResult.builder()
            .success(false)
            .errorCode("NOT_IMPLEMENTED")
            .build();
    }

    @Override
    public CloseResult closeOrder(CloseRequest req) {
        // TODO(SDK-3)：调用 ICBC /pay/close
        log.warn("[DCEP-ICBC] closeOrder() not yet implemented, order={}", req.getDcepOrderNo());
        return CloseResult.builder()
            .success(false)
            .errorCode("NOT_IMPLEMENTED")
            .build();
    }

    @Override
    public NotifyResult parseAndVerifyNotify(String rawBody, Map<String, String> headers) {
        // TODO(SDK-3)：用 ICBC 公钥验证签名
        log.warn("[DCEP-ICBC] parseAndVerifyNotify() not yet implemented");
        return NotifyResult.builder()
            .signatureValid(false)
            .timestampValid(false)
            .rawBody(rawBody)
            .build();
    }
}
