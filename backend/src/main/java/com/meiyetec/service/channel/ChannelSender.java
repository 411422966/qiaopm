package com.meiyetec.service.channel;

import com.meiyetec.entity.MktChannelTemplate;

import java.util.Map;

/**
 * T-D21：消息渠道适配层
 * 各渠道实现本接口，可插拔。
 */
public interface ChannelSender {

    /** 渠道类型 */
    String channelType();

    /** 发送结果 */
    SendResult send(MktChannelTemplate tpl, String receiver, Map<String, Object> vars);

    /** 健康检查 */
    boolean healthCheck();

    class SendResult {
        public boolean success;
        public String externalMsgId;
        public String errorMsg;
        public static SendResult ok(String externalMsgId) {
            SendResult r = new SendResult();
            r.success = true;
            r.externalMsgId = externalMsgId;
            return r;
        }
        public static SendResult fail(String msg) {
            SendResult r = new SendResult();
            r.success = false;
            r.errorMsg = msg;
            return r;
        }
    }
}
