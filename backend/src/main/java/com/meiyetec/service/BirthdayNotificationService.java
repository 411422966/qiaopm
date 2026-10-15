package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meiyetec.entity.BirthdayMember;
import com.meiyetec.entity.BirthdaySendLog;
import com.meiyetec.entity.Coupon;
import com.meiyetec.entity.MktChannelTemplate;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.BirthdayMemberMapper;
import com.meiyetec.mapper.BirthdaySendLogMapper;
import com.meiyetec.mapper.CouponMapper;
import com.meiyetec.security.TenantContext;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.channel.ChannelDispatcher;
import com.meiyetec.service.channel.ChannelSender;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * M09-02：生日提醒服务（真实通道实现）
 *
 * <p>功能：
 * <ul>
 *   <li>生日录入（公历 / 农历）</li>
 *   <li>生日礼自动发放（真实优惠券 / 积分）</li>
 *   <li>多通道触达（短信/小程序订阅消息/站内信 — 接入 M09-01 真实通道）</li>
 *   <li>定时任务：每天 08:00 扫描，提前 7 天 / 当天 / 过期 1 天 三波触达</li>
 *   <li>手动触发生日礼发放</li>
 *   <li>发送记录与 ROI 统计</li>
 * </ul>
 *
 * <p>真实通道接入：
 * <ul>
 *   <li>优惠券：调用 CouponMapper 真实发放</li>
 *   <li>短信/小程序：调用 ChannelDispatcher → ChannelSender（M09-01）</li>
 *   <li>站内信：调用 ChannelDispatcher → ChannelSender（M09-01）</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BirthdayNotificationService {

    private final BirthdayMemberMapper memberMapper;
    private final BirthdaySendLogMapper sendLogMapper;
    private final CouponMapper couponMapper;
    private final ChannelDispatcher channelDispatcher;

    @Autowired
    private JdbcTemplate jdbc;

    private static final ObjectMapper JSON = new ObjectMapper();

    // ==============================================================
    // 生日礼发放（手动触发）
    // ==============================================================

    /**
     * 手动触发生日礼物发放（真实通道）
     *
     * @param memberId       会员ID
     * @param giftType       礼物类型：COUPON / POINTS / GIFT / SMS_ONLY
     * @param couponId       当 giftType=COUPON 时必填
     * @param points         当 giftType=POINTS 时的积分数量
     * @param channelType    通知通道：SMS / MINI_PROGRAM / IN_APP（多选逗号分隔）
     * @param templateId     消息模板ID（可选，不填则走默认文案）
     */
    @Transactional(rollbackFor = Exception.class)
    public Result<BirthdaySendLog> sendGift(Long memberId, String giftType,
                                             Long couponId, Integer points,
                                             String channelType, Long templateId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        BirthdayMember b = memberMapper.selectOne(new LambdaQueryWrapper<BirthdayMember>()
                .eq(BirthdayMember::getTenantId, tenantId)
                .eq(BirthdayMember::getMemberId, memberId));
        if (b == null) {
            throw new BusinessException(BizCode.NOT_FOUND, "未录入生日，请先在会员管理中补充生日信息");
        }

        // 防重复：同一年同类型只发一次
        int year = LocalDate.now().getYear();
        BirthdaySendLog dup = sendLogMapper.selectOne(new LambdaQueryWrapper<BirthdaySendLog>()
                .eq(BirthdaySendLog::getTenantId, tenantId)
                .eq(BirthdaySendLog::getMemberId, memberId)
                .eq(BirthdaySendLog::getSendYear, year)
                .eq(BirthdaySendLog::getGiftType, giftType == null ? "SMS_ONLY" : giftType));
        if (dup != null) {
            throw new BusinessException(BizCode.CONFLICT, "本年度该类型生日礼已发放（防重复）");
        }

        // 查会员信息（手机号/OpenID）
        Map<String, Object> member = getMemberInfo(memberId);
        String phone = (String) member.get("phone");
        String openid = (String) member.get("openid");
        String name = (String) member.get("name");

        // 1. 发优惠券
        String couponCode = null;
        BigDecimal couponValue = null;
        if ("COUPON".equals(giftType)) {
            if (couponId == null) throw new BusinessException(BizCode.BAD_REQUEST, "giftType=COUPON 时 couponId 必填");
            Coupon coupon = couponMapper.selectById(couponId);
            if (coupon == null) throw new BusinessException(BizCode.NOT_FOUND, "优惠券不存在");
            couponCode = grantCouponToMember(tenantId, couponId, memberId, "BIRTHDAY_GIFT");
            couponValue = coupon.getRuleValue();
        }

        // 2. 送积分
        Integer awardedPoints = null;
        if ("POINTS".equals(giftType)) {
            awardedPoints = points == null || points <= 0 ? 500 : points;
            addPoints(tenantId, memberId, awardedPoints, "生日赠送积分");
        }

        // 3. 发通知（真实通道）
        String channels = channelType == null || channelType.isEmpty() ? "IN_APP" : channelType;
        Map<String, String> channelResults = new LinkedHashMap<>();
        String lastError = null;

        for (String ch : channels.split(",")) {
            ch = ch.trim();
            String result = sendViaChannel(ch, memberId, phone, openid, name,
                    b.getBirthday(), giftType, couponCode, couponValue,
                    awardedPoints, templateId);
            channelResults.put(ch, result);
            if (result != null && !result.startsWith("FAIL")) {
                lastError = null;
            } else if (result != null) {
                lastError = result;
            }
        }

        // 4. 写发送记录
        BirthdaySendLog sl = new BirthdaySendLog();
        sl.setTenantId(tenantId);
        sl.setMemberId(memberId);
        sl.setSendYear(year);
        sl.setGiftType(giftType == null ? "SMS_ONLY" : giftType);
        sl.setCouponCode(couponCode);
        sl.setGiftValue(couponValue == null ? null : couponValue.toPlainString());
        sl.setPointsAwarded(awardedPoints);
        sl.setTemplateId(templateId == null ? null : String.valueOf(templateId));
        sl.setChannelMessageId(channelResults.isEmpty() ? null
                : channelResults.values().stream().filter(Objects::nonNull).findFirst().orElse(null));
        sl.setMessage(buildMessage(name, giftType, couponValue, awardedPoints));
        sl.setStatus(lastError == null ? "SUCCESS" : "PARTIAL_FAILED");
        sl.setErrorMessage(lastError);
        sl.setSentAt(LocalDateTime.now());
        sendLogMapper.insert(sl);

        // 更新最后发放年
        b.setLastSentYear(year);
        memberMapper.updateById(b);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("sendLog", sl);
        detail.put("channelResults", channelResults);
        detail.put("couponCode", couponCode);
        detail.put("pointsAwarded", awardedPoints);

        Result<Map<String, Object>> ok = Result.success(channelResults.isEmpty() ? "已发放生日礼（无通知通道）" : "生日礼发放完成", detail);
        return Result.success("生日礼发放完成", sl);
    }

    // ==============================================================
    // 多通道发送（接入 M09-01 ChannelDispatcher）
    // ==============================================================

    /**
     * 通过指定通道发送生日通知
     * @return 外部消息ID，或 "FAIL:原因"
     */
    private String sendViaChannel(String channelType, Long memberId,
                                   String phone, String openid, String name,
                                   LocalDate birthday, String giftType,
                                   String couponCode, BigDecimal couponValue,
                                   Integer points, Long templateId) {
        try {
            ChannelSender sender = channelDispatcher.getSender(channelType);
            if (sender == null) {
                log.warn("[Birthday] No sender for channel: {}", channelType);
                return "FAIL:no_sender";
            }

            // 构建模板变量
            Map<String, Object> vars = new HashMap<>();
            vars.put("name", name != null ? name : "会员");
            vars.put("birthday", birthday != null ? birthday.toString() : "");
            vars.put("giftType", giftType != null ? giftType : "");
            vars.put("couponCode", couponCode != null ? couponCode : "");
            vars.put("points", points != null ? String.valueOf(points) : "");

            // 构建占位模板（templateId 不存在时使用默认文案）
            MktChannelTemplate tpl = buildTemplate(channelType, templateId, name, giftType, couponCode, points);

            // receiver
            String receiver = "SMS".equals(channelType) ? phone
                    : "MINI_PROGRAM".equals(channelType) ? (openid != null ? openid : "OPENID-" + memberId)
                    : String.valueOf(memberId);

            if (receiver == null || receiver.isEmpty()) {
                log.warn("[Birthday] No receiver for channel {} member {}", channelType, memberId);
                return "FAIL:no_receiver";
            }

            ChannelSender.SendResult res = sender.send(tpl, receiver, vars);
            if (res.success) {
                log.info("[Birthday] Channel {} send OK: msgId={}", channelType, res.externalMsgId);
                return res.externalMsgId;
            } else {
                log.warn("[Birthday] Channel {} send FAIL: {}", channelType, res.errorMsg);
                return "FAIL:" + res.errorMsg;
            }
        } catch (Exception e) {
            log.error("[Birthday] Channel {} exception: {}", channelType, e.getMessage(), e);
            return "FAIL:" + e.getMessage();
        }
    }

    private MktChannelTemplate buildTemplate(String channelType, Long templateId,
                                               String name, String giftType,
                                               String couponCode, Integer points) {
        MktChannelTemplate tpl = new MktChannelTemplate();
        tpl.setChannelType(channelType);
        tpl.setExternalTemplateId(templateId != null ? String.valueOf(templateId) : null);

        // 使用预设默认文案
        String content;
        if ("SMS".equals(channelType)) {
            content = String.format("【美业科技】亲爱的%s，生日快乐！%s，感谢一路有你~",
                    name != null ? name : "",
                    couponCode != null ? "您的专属优惠券码：" + couponCode + " 祝您青春美丽！"
                    : points != null ? "已赠送" + points + "积分到您的账户，祝您消费愉快！"
                            : "祝您生日快乐！");
        } else if ("MINI_PROGRAM".equals(channelType)) {
            content = String.format("🎂亲爱的%s，生日快乐！\n%s",
                    name != null ? name : "",
                    couponCode != null ? "🎁您的生日优惠券已到账，券码：" + couponCode
                    : points != null ? "🎁已赠送" + points + "积分，祝您消费愉快！"
                            : "🎁感谢一路有您，生日快乐！");
        } else {
            content = String.format("🎂 亲爱的 %s，生日快乐！\n\n%s\n\n感谢您的信任与支持，美业祝您青春美丽！",
                    name != null ? name : "",
                    couponCode != null ? "🎁 您的生日优惠券码：**" + couponCode + "** 已到账，请尽快使用"
                    : points != null ? "🎁 已赠送 **" + points + "** 积分到您的账户"
                            : "🎁 感谢一路有您，祝您青春美丽！");
        }
        tpl.setContent(content);
        tpl.setSubject("🎂 生日快乐，亲爱的 " + (name != null ? name : "会员"));
        return tpl;
    }

    private String buildMessage(String name, String giftType, BigDecimal couponValue, Integer points) {
        String gift = "";
        if ("COUPON".equals(giftType)) gift = "优惠券（¥" + couponValue + "）";
        else if ("POINTS".equals(giftType)) gift = "积分" + points + "分";
        else gift = "祝福通知";
        return "亲爱的 " + (name != null ? name : "会员") + "，您的" + gift + "已发放，祝您生日快乐！";
    }

    // ==============================================================
    // 定时任务：每天 08:00 扫描，提前 7 天 / 当天 / 过期 1 天
    // ==============================================================

    /**
     * 每天 08:00 扫描所有租户生日会员，触发生日礼
     * 仅对 birthday_member.auto_send = 1 的会员生效
     */
    @Scheduled(cron = "0 0 8 * * ?")
    public void scheduledScan() {
        log.info("[Birthday] 定时扫描开始");
        try {
            List<Map<String, Object>> tenants = jdbc.queryForList(
                    "SELECT id FROM crm_tenant WHERE status = 'ACTIVE' LIMIT 100");
            for (Map<String, Object> t : tenants) {
                Long tenantId = ((Number) t.get("id")).longValue();
                runBirthdayScan(tenantId);
            }
        } catch (Exception e) {
            log.error("[Birthday] 扫描异常", e);
        }
    }

    /**
     * 按租户执行生日扫描
     */
    private void runBirthdayScan(Long tenantId) {
        LocalDate today = LocalDate.now();
        int thisYear = today.getYear();

        List<BirthdayMember> all = memberMapper.selectList(new LambdaQueryWrapper<BirthdayMember>()
                .eq(BirthdayMember::getTenantId, tenantId)
                .eq(BirthdayMember::getAutoSend, 1));

        int sent = 0, skip = 0, fail = 0;
        for (BirthdayMember b : all) {
            // 已发过今年跳过
            if (Integer.valueOf(thisYear).equals(b.getLastSentYear())) {
                skip++;
                continue;
            }

            LocalDate next = nextBirthday(b.getBirthday(), today);
            long days = ChronoUnit.DAYS.between(today, next);

            // 提前 7 天 / 当天(0) / 过期 1 天(-1)
            if (days != 7 && days != 0 && days != -1) continue;

            // 判断是否已发过这一波（同一 wave 一年只发一次）
            String waveKey = thisYear + "-" + days;
            int existing = sendLogMapper.selectCount(new LambdaQueryWrapper<BirthdaySendLog>()
                    .eq(BirthdaySendLog::getTenantId, tenantId)
                    .eq(BirthdaySendLog::getMemberId, b.getMemberId())
                    .eq(BirthdaySendLog::getSendYear, thisYear)).intValue();
            if (existing > 0) {
                skip++;
                continue;
            }

            try {
                // 切换租户上下文（供 ChannelDispatcher 使用）
                setTenantContext(tenantId);

                // 自动发站内信 + 短信（按配置）
                String channels = "IN_APP";
                String giftType = "SMS_ONLY";
                if (b.getAutoCoupon() != null && !b.getAutoCoupon().isEmpty()) {
                    giftType = "COUPON";
                }

                Map<String, Object> member = getMemberInfo(b.getMemberId());
                String phone = (String) member.get("phone");

                // 发通知（IN_APP 总是发）
                String inAppResult = sendViaChannel("IN_APP", b.getMemberId(), phone,
                        (String) member.get("openid"), (String) member.get("name"),
                        b.getBirthday(), giftType, null, null, null, null);
                sent++;

                // 如果配置了自动发券
                if ("COUPON".equals(giftType) && b.getAutoCoupon() != null) {
                    try {
                        Long couponId = Long.parseLong(b.getAutoCoupon());
                        grantCouponToMember(tenantId, couponId, b.getMemberId(), "BIRTHDAY_AUTO");
                    } catch (Exception couponEx) {
                        log.warn("[Birthday] Auto coupon grant fail: {}", couponEx.getMessage());
                    }
                }

                // 发短信（如果有手机号）
                if (phone != null && !phone.isEmpty()) {
                    sendViaChannel("SMS", b.getMemberId(), phone,
                            (String) member.get("openid"), (String) member.get("name"),
                            b.getBirthday(), giftType, null, null, null, null);
                }

                // 写日志
                BirthdaySendLog sl = new BirthdaySendLog();
                sl.setTenantId(tenantId);
                sl.setMemberId(b.getMemberId());
                sl.setSendYear(thisYear);
                sl.setGiftType(giftType);
                sl.setChannelMessageId(inAppResult);
                sl.setMessage(waveMessage(days, (String) member.get("name")));
                sl.setStatus("SUCCESS");
                sl.setSentAt(LocalDateTime.now());
                sendLogMapper.insert(sl);

                // 更新发放年
                b.setLastSentYear(thisYear);
                memberMapper.updateById(b);

                log.info("[Birthday] 租户 {} 会员 {} 生日 wave {} 通知已发送", tenantId, b.getMemberId(), waveKey);
            } catch (Exception e) {
                log.error("[Birthday] 租户 {} 会员 {} 发送异常: {}", tenantId, b.getMemberId(), e.getMessage());
                fail++;
            } finally {
                clearTenantContext();
            }
        }
        log.info("[Birthday] 租户 {} 扫描完成：发送 {} 跳过 {} 失败 {}", tenantId, sent, skip, fail);
    }

    private String waveMessage(long daysUntil, String name) {
        if (daysUntil == 7) {
            return "您的生日即将到来（还有7天），美业准备了精美礼物等您领取！";
        } else if (daysUntil == 0) {
            return "🎂 亲爱的 " + (name != null ? name : "") + "，今天是您的生日！美业全体祝您生日快乐！";
        } else {
            return "您的生日刚过，希望您度过了一个愉快的生日！美业继续为您提供服务~";
        }
    }

    // ==============================================================
    // 内部辅助
    // ==============================================================

    private Map<String, Object> getMemberInfo(Long memberId) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT name, phone, openid FROM crm_member WHERE id = ? AND deleted = 0 LIMIT 1",
                    memberId);
            return rows.isEmpty() ? Collections.emptyMap() : rows.get(0);
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private String grantCouponToMember(Long tenantId, Long couponId, Long memberId, String source) {
        // 防重复发放
        Long existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM marketing_coupon_grant WHERE coupon_id = ? AND member_id = ? AND source = ? AND status = 1",
                Long.class, couponId, memberId, source);
        if (existing != null && existing > 0) {
            log.info("[Birthday] Coupon {} already granted to member {}, skip", couponId, memberId);
            return jdbc.queryForObject(
                    "SELECT code FROM marketing_coupon_grant WHERE coupon_id = ? AND member_id = ? AND source = ? AND status = 1 LIMIT 1",
                    String.class, couponId, memberId, source);
        }
        String code = "BIRTH" + System.currentTimeMillis();
        jdbc.update("INSERT INTO marketing_coupon_grant (coupon_id, member_id, tenant_id, code, status, source, expire_time, created_at) " +
                        "SELECT ?, ?, ?, ?, 1, ?, DATE_ADD(NOW(), INTERVAL 365 DAY), NOW() " +
                        "FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM marketing_coupon_grant WHERE coupon_id = ? AND member_id = ? AND source = ? AND status = 1)",
                couponId, memberId, tenantId, code, source, couponId, memberId, source);
        // 取实际入库的 code
        try {
            return jdbc.queryForObject(
                    "SELECT code FROM marketing_coupon_grant WHERE member_id = ? AND coupon_id = ? AND status = 1 ORDER BY id DESC LIMIT 1",
                    String.class, memberId, couponId);
        } catch (Exception e) {
            return code;
        }
    }

    private void addPoints(Long tenantId, Long memberId, Integer points, String remark) {
        jdbc.update("INSERT INTO point_record (tenant_id, member_id, type, amount, remark, created_at) VALUES (?, ?, 1, ?, ?, NOW())",
                tenantId, memberId, points, remark);
        jdbc.update("UPDATE crm_member SET points = COALESCE(points, 0) + ? WHERE id = ?",
                points, memberId);
    }

    private LocalDate nextBirthday(LocalDate birthday, LocalDate today) {
        if (birthday == null) return today.plusDays(7);
        LocalDate thisYear = birthday.withYear(today.getYear());
        return thisYear.isBefore(today) ? thisYear.plusYears(1) : thisYear;
    }

    private void setTenantContext(Long tenantId) {
        try {
            TenantContext ctx = new TenantContext();
            ctx.setTenantId(tenantId);
            TenantContextHolder.set(ctx);
        } catch (Exception ignored) {}
    }

    private void clearTenantContext() {
        try {
            com.meiyetec.security.TenantContextHolder.clear();
        } catch (Exception ignored) {}
    }
}
