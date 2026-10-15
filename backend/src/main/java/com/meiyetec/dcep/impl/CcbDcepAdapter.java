package com.meiyetec.dcep.impl;

import com.meiyetec.dcep.AbstractDcepAdapter;
import com.meiyetec.dcep.DcepChannelAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 建设银行（CCB）DCEP 适配器
 *
 * <p>对接方式：SaaS直联 / 对公网银_外联平台（V14.1）
 * <p>API 文档：{@code https://store.ccb.com/} 申请后下载
 * <p>核心交易码：NWXM13（预填单维护）/ NWXM14（查询）/ NWXM15（删除）/ NWXM16（结果查询）
 *
 * <p><b>当前状态：占位实现，等待真实 SDK 接入</b>
 * <p>替换步骤：
 * <ol>
 *   <li>BD 完成对公数字钱包开户（通过汇元）</li>
 *   <li>银行下发 SDK（CCB-Java-SDK）+ 客户端证书 + 商户号</li>
 *   <li>BE 引入 SDK jar，注入到 Spring 容器</li>
 *   <li>在 {@link #prepay(PrepayRequest)} 中调用 CCB NWXM13</li>
 *   <li>在 {@link #parseAndVerifyNotify(String, Map)} 中用银行公钥校验签名</li>
 * </ol>
 */
@Component("ccbDcepAdapter")
public class CcbDcepAdapter extends AbstractDcepAdapter implements DcepChannelAdapter {

    private static final Logger log = LoggerFactory.getLogger(CcbDcepAdapter.class);

    @Override
    public String channelCode() {
        return "DCEP-CCB";
    }

    @Override
    public PrepayResult prepay(PrepayRequest req) {
        // TODO(BD-2 + SDK-2)：调用 CCB NWXM13 发送预填单
        // 步骤：
        //   1. 构造 NWXM13 请求：walletNo + prepayNo + amountCents + expireAt + notifyUrl
        //   2. 用 .pfx 客户端证书 + 私钥签名
        //   3. HTTPS POST 到 CCB 沙箱/prod 网关
        //   4. 解析响应：prepayId、qrCode、expiresAt
        log.warn("[DCEP-CCB] prepay() not yet implemented, channel={} order={}",
                req.getChannelCode(), req.getDcepOrderNo());
        return failPrepay("NOT_IMPLEMENTED", "建行 DCEP 适配器尚未对接真实 SDK，等待 BD-2 完成开户");
    }

    @Override
    public QueryResult query(OrderQueryRequest req) {
        // TODO(SDK-2)：调用 CCB NWXM14 查询订单状态
        log.warn("[DCEP-CCB] query() not yet implemented, order={}", req.getDcepOrderNo());
        return QueryResult.builder()
            .success(false)
            .errorCode("NOT_IMPLEMENTED")
            .build();
    }

    @Override
    public RefundResult refund(RefundRequest req) {
        // TODO(SDK-2)：调用 CCB 退款接口（原路退对公钱包）
        log.warn("[DCEP-CCB] refund() not yet implemented, order={}", req.getDcepOrderNo());
        return RefundResult.builder()
            .success(false)
            .errorCode("NOT_IMPLEMENTED")
            .build();
    }

    @Override
    public CloseResult closeOrder(CloseRequest req) {
        // TODO(SDK-2)：调用 CCB NWXM15 删除预填单
        log.warn("[DCEP-CCB] closeOrder() not yet implemented, order={}", req.getDcepOrderNo());
        return CloseResult.builder()
            .success(false)
            .errorCode("NOT_IMPLEMENTED")
            .build();
    }

    @Override
    public NotifyResult parseAndVerifyNotify(String rawBody, java.util.Map<String, String> headers) {
        // TODO(SDK-2)：用 CCB 公钥验证签名
        // 步骤：
        //   1. 解析 body 为 Map
        //   2. 取 sign + signAlgo + notifyId + timestamp
        //   3. 用 bankPublicKey 验证 sign
        //   4. 校验 timestamp 在 ±5 分钟内
        //   5. 返回 NotifyResult
        //
        // 防重放：维护 notifyId 缓存（Redis TTL 30 天）
        log.warn("[DCEP-CCB] parseAndVerifyNotify() not yet implemented");
        return NotifyResult.builder()
            .signatureValid(false)
            .timestampValid(false)
            .rawBody(rawBody)
            .build();
    }
}
