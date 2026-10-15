package com.meiyetec.dcep.impl;

import com.meiyetec.dcep.AbstractDcepAdapter;
import com.meiyetec.dcep.DcepChannelAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 农业银行（ABC）DCEP 适配器
 *
 * <p>对接方式：商户收单（线下）+ 网络支付接口（线上）
 * <p>特色：银行卡、条码、数字人民币一体化受理
 * <p>注意：必须现场调研（农行客户经理上门）
 *
 * <p><b>当前状态：占位实现，等待真实 SDK 接入</b>
 */
@Component("abcDcepAdapter")
public class AbcDcepAdapter extends AbstractDcepAdapter implements DcepChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(AbcDcepAdapter.class);

    @Override
    public String channelCode() {
        return "DCEP-ABC";
    }

    @Override
    public PrepayResult prepay(PrepayRequest req) {
        // TODO(SDK-4)：调用 ABC 网络支付接口
        // 步骤：
        //   1. 构造请求：merchantId + orderNo + amountCents + goodsName + notifyUrl
        //   2. RSA 签名
        //   3. HTTPS POST 到 ABC 网关
        //   4. 解析响应：prepayId、qrCode
        log.warn("[DCEP-ABC] prepay() not yet implemented, order={}", req.getDcepOrderNo());
        return failPrepay("NOT_IMPLEMENTED", "农行 DCEP 适配器尚未对接真实 SDK");
    }

    @Override
    public QueryResult query(OrderQueryRequest req) {
        // TODO(SDK-4)：调用 ABC 订单查询
        log.warn("[DCEP-ABC] query() not yet implemented, order={}", req.getDcepOrderNo());
        return QueryResult.builder()
            .success(false)
            .errorCode("NOT_IMPLEMENTED")
            .build();
    }

    @Override
    public RefundResult refund(RefundRequest req) {
        // TODO(SDK-4)：调用 ABC 退款
        log.warn("[DCEP-ABC] refund() not yet implemented, order={}", req.getDcepOrderNo());
        return RefundResult.builder()
            .success(false)
            .errorCode("NOT_IMPLEMENTED")
            .build();
    }

    @Override
    public CloseResult closeOrder(CloseRequest req) {
        // TODO(SDK-4)：调用 ABC 关闭订单
        log.warn("[DCEP-ABC] closeOrder() not yet implemented, order={}", req.getDcepOrderNo());
        return CloseResult.builder()
            .success(false)
            .errorCode("NOT_IMPLEMENTED")
            .build();
    }

    @Override
    public NotifyResult parseAndVerifyNotify(String rawBody, Map<String, String> headers) {
        // TODO(SDK-4)：用 ABC 公钥验证签名
        log.warn("[DCEP-ABC] parseAndVerifyNotify() not yet implemented");
        return NotifyResult.builder()
            .signatureValid(false)
            .timestampValid(false)
            .rawBody(rawBody)
            .build();
    }
}
