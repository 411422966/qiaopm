package com.meiyetec.service.channel;

import com.meiyetec.entity.MktChannelTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 修复 I076：PUSH 渠道 sender 骨架实现
 *
 * <p>PUSH 渠道（手机推送/App 推送）在当前阶段未对接具体推送服务（个推/极光/小米推送）。
 * <p>当前实现：channelType() 返回 "PUSH"；send() 返回失败结果（写 log）；healthCheck() 返回 true（不影响 healthCheckAll 总览）。
 * <p>后续接入真实推送服务时，重写 send() 即可，调用方契约不变。
 */
@Slf4j
@Component
public class PushSender implements ChannelSender {

    @Override
    public String channelType() {
        return "PUSH";
    }

    @Override
    public SendResult send(MktChannelTemplate tpl, String receiver, Map<String, Object> vars) {
        log.warn("[PushSender] PUSH channel not implemented yet (tplId={}, receiver={})",
                tpl == null ? null : tpl.getId(), receiver);
        return SendResult.fail("PUSH 渠道暂未实现，请接入真实推送服务（个推/极光/小米推送等）");
    }

    @Override
    public boolean healthCheck() {
        // 未实现的渠道不参与 healthCheckAll 总览的失败计数
        return true;
    }
}
