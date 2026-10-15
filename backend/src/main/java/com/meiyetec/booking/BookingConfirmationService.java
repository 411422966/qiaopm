package com.meiyetec.booking;

import com.meiyetec.entity.MktChannelTemplate;
import com.meiyetec.entity.OverseasBooking;
import com.meiyetec.service.channel.ChannelSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * EXT-19：预约确认发送服务
 *
 * <p>{@link #sendConfirmation(OverseasBooking)} 流程：
 * <ol>
 *   <li>从 {@link BookingConfirmationTemplateResolver} 按 {@code booking.customerLocale} 加载并渲染模板</li>
 *   <li>按 booking 字段判断可用渠道：
 *       <ul>
 *         <li>customerPhone → SMS</li>
 *         <li>customerEmail → EMAIL</li>
 *         <li>miniProgramOpenId → MINI_PROGRAM</li>
 *       </ul>
 *   </li>
 *   <li>调对应 {@link ChannelSender#send(MktChannelTemplate, String, Map)}，构造 in-memory 模板对象</li>
 *   <li>聚合每渠道结果，返回 {@link BookingConfirmationResult}</li>
 * </ol>
 *
 * <p>门店信息（storeName / storePhone）通过 {@link StoreLookupService} 从 {@code pos_store} 实时查，
 * 替换之前的占位常量。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingConfirmationService {

    /** MINI_PROGRAM 渠道使用的 WeChat 模板 ID（占位；真实接入需在公众号后台注册模板） */
    private static final String MP_TEMPLATE_ID = "BOOKING_CONFIRMATION";
    /** MINI_PROGRAM 渠道 keyword1..N 映射的变量 */
    private static final String MP_VARIABLES_JSON =
            "[\"customerName\",\"serviceName\",\"bookingDate\",\"bookingTime\",\"bookingNo\"]";

    /** 兜底门店信息（当 booking.storeId 为空 / 查不到时使用） */
    private static final String FALLBACK_STORE_NAME  = "美业旗舰店";
    private static final String FALLBACK_STORE_PHONE = "400-888-8888";

    private final BookingConfirmationTemplateResolver resolver;
    private final StoreLookupService storeLookup;

    @Autowired(required = false)
    private List<ChannelSender> channelSenders;

    /** 渠道类型 → 实现（懒查 + 缓存） */
    private final Map<String, ChannelSender> senderMap = new HashMap<>();

    // ============================================================
    //  对外 API
    // ============================================================

    /**
     * 发送预约确认（核心入口）
     *
     * @param booking 预约实体
     * @return 聚合结果（含每渠道 success / externalMsgId / errorMsg）
     */
    public BookingConfirmationResult sendConfirmation(OverseasBooking booking) {
        if (booking == null) {
            BookingConfirmationResult empty = new BookingConfirmationResult();
            empty.setOverallSuccess(false);
            return empty;
        }
        BookingConfirmationResult result = new BookingConfirmationResult();
        result.setBookingId(booking.getId());

        // 1. 构造占位符（含门店信息）
        Map<String, Object> vars = buildVars(booking);

        // 2. 渲染模板
        BookingConfirmationTemplate tpl = resolver.resolve(booking.getCustomerLocale(), vars);
        result.setResolvedLocale(tpl.getResolvedLocale());
        result.setUsedFallback(tpl.getUsedFallback());

        // 3. 按可用渠道路由
        if (booking.getCustomerPhone() != null && !booking.getCustomerPhone().isEmpty()) {
            ChannelSender.SendResult r = sendViaChannel("SMS",
                    booking.getCustomerPhone(), tpl.getSubject(), tpl.getSms(), null);
            result.putChannel("SMS", r.success, r.externalMsgId, r.errorMsg);
        } else {
            result.putChannel("SMS", false, null, "customerPhone is empty — skipped");
        }

        if (booking.getCustomerEmail() != null && !booking.getCustomerEmail().isEmpty()) {
            ChannelSender.SendResult r = sendViaChannel("EMAIL",
                    booking.getCustomerEmail(), tpl.getEmailSubject(), tpl.getEmailBody(), null);
            result.putChannel("EMAIL", r.success, r.externalMsgId, r.errorMsg);
        } else {
            result.putChannel("EMAIL", false, null, "customerEmail is empty — skipped");
        }

        if (booking.getMiniProgramOpenId() != null && !booking.getMiniProgramOpenId().isEmpty()) {
            ChannelSender.SendResult r = sendMiniProgram(booking.getMiniProgramOpenId(), tpl, vars);
            result.putChannel("MINI_PROGRAM", r.success, r.externalMsgId, r.errorMsg);
        } else {
            result.putChannel("MINI_PROGRAM", false, null, "miniProgramOpenId is empty — skipped");
        }

        result.setOverallSuccess(
                anySuccess(result.getChannelResults()));
        log.info("[BOOKING-CONFIRM] bookingId={} locale={} (fallback={}) channels={} overall={}",
                booking.getId(), result.getResolvedLocale(), result.getUsedFallback(),
                result.getChannelResults().keySet(), result.isOverallSuccess());
        return result;
    }

    /**
     * 仅渲染模板（不发通道）— 用于前端预览 / 单元测试
     */
    public BookingConfirmationTemplate preview(OverseasBooking booking) {
        return resolver.resolve(booking == null ? null : booking.getCustomerLocale(),
                buildVars(booking));
    }

    // ============================================================
    //  内部实现
    // ============================================================

    /** 构造 in-memory MktChannelTemplate 用于 ChannelSender.send() (SMS / EMAIL 通用) */
    private ChannelSender.SendResult sendViaChannel(String channelType, String receiver,
                                                     String subject, String body, Map<String, Object> vars) {
        ChannelSender sender = lookupSender(channelType);
        if (sender == null) {
            return ChannelSender.SendResult.fail("No ChannelSender for type=" + channelType);
        }
        MktChannelTemplate tpl = new MktChannelTemplate();
        tpl.setSubject(subject);
        tpl.setContent(body);
        tpl.setExternalTemplateId("BOOKING_CONFIRMATION");
        return sender.send(tpl, receiver, vars);
    }

    /** 小程序渠道（用 keyword1..N 映射渲染后的关键字） */
    private ChannelSender.SendResult sendMiniProgram(String openId, BookingConfirmationTemplate tpl,
                                                      Map<String, Object> vars) {
        ChannelSender sender = lookupSender("MINI_PROGRAM");
        if (sender == null) {
            return ChannelSender.SendResult.fail("No ChannelSender for type=MINI_PROGRAM");
        }
        MktChannelTemplate mpTpl = new MktChannelTemplate();
        mpTpl.setSubject(tpl.getMiniProgramTitle() == null ? tpl.getSubject() : tpl.getMiniProgramTitle());
        // 把渲染后的 miniProgram 内容也写到 content（MiniProgramSender 会用作 keyword1 fallback）
        mpTpl.setContent(tpl.getMiniProgramTitle() == null ? "" : tpl.getMiniProgramTitle());
        mpTpl.setExternalTemplateId(MP_TEMPLATE_ID);
        mpTpl.setVariables(MP_VARIABLES_JSON);
        return sender.send(mpTpl, openId, vars);
    }

    /** 按 channelType 查 sender（懒查 + 缓存） */
    private ChannelSender lookupSender(String channelType) {
        ChannelSender s = senderMap.get(channelType);
        if (s != null) return s;
        if (channelSenders == null) return null;
        for (ChannelSender candidate : channelSenders) {
            if (channelType.equalsIgnoreCase(candidate.channelType())) {
                senderMap.put(channelType, candidate);
                return candidate;
            }
        }
        return null;
    }

    /** 占位符变量（含门店信息） */
    private Map<String, Object> buildVars(OverseasBooking booking) {
        Map<String, Object> vars = new HashMap<>();
        if (booking == null) return vars;
        vars.put("customerName", safeStr(booking.getCustomerName()));
        vars.put("customerPhone", safeStr(booking.getCustomerPhone()));
        vars.put("customerEmail", safeStr(booking.getCustomerEmail()));
        vars.put("serviceName",   safeStr(booking.getServiceName()));
        vars.put("bookingDate",   booking.getBookingDate() == null ? "" : booking.getBookingDate().toString());
        vars.put("bookingTime",   safeStr(booking.getBookingTime()));
        vars.put("bookingNo",     safeStr(booking.getBookingNo()));

        // 门店信息：从 pos_store 实时查（5 分钟缓存）
        StoreLookupService.StoreInfo store = storeLookup.lookup(booking.getStoreId());
        vars.put("storeName",  store == null || store.getStoreName()  == null ? FALLBACK_STORE_NAME  : store.getStoreName());
        vars.put("storePhone", store == null || store.getStorePhone() == null ? FALLBACK_STORE_PHONE : store.getStorePhone());
        return vars;
    }

    private static String safeStr(String s) { return s == null ? "" : s; }

    /** 任一渠道 success=true → overall true */
    private static boolean anySuccess(Map<String, Map<String, Object>> channels) {
        for (Map<String, Object> r : channels.values()) {
            Object s = r.get("success");
            if (s != null && Boolean.TRUE.equals(s)) return true;
        }
        return false;
    }
}
