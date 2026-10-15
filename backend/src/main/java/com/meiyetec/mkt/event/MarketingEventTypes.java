package com.meiyetec.mkt.event;

/**
 * M16 营销事件类型常量
 *
 * <p>所有业务事件统一编码，便于触发器监听。
 * 命名约定：{@code <域>_<动作>_<对象>}，全大写 + 下划线。
 *
 * <p>支持的事件类别：
 * <ul>
 *   <li>会员生命周期：MEMBER_REGISTER / MEMBER_LEVEL_UP / MEMBER_INACTIVE</li>
 *   <li>交易：ORDER_PLACED / ORDER_PAID / ORDER_REFUNDED / CONSUME / RECHARGE</li>
 *   <li>预约：BOOKING_CREATED / BOOKING_CONFIRMED / BOOKING_CHECKIN</li>
 *   <li>关怀：BIRTHDAY / MEMBER_DAY</li>
 *   <li>裂变：INVITE_SENT / INVITE_REGISTERED</li>
 *   <li>互动：REVIEW_SUBMITTED / SHARED</li>
 * </ul>
 */
public final class MarketingEventTypes {

    private MarketingEventTypes() {}

    // ===== 会员生命周期 =====
    public static final String MEMBER_REGISTER       = "MEMBER_REGISTER";
    public static final String MEMBER_LEVEL_UP       = "MEMBER_LEVEL_UP";
    public static final String MEMBER_INACTIVE       = "MEMBER_INACTIVE";

    // ===== 交易 =====
    public static final String ORDER_PLACED          = "ORDER_PLACED";
    public static final String ORDER_PAID            = "ORDER_PAID";
    public static final String ORDER_REFUNDED        = "ORDER_REFUNDED";
    public static final String CONSUME               = "CONSUME";
    public static final String RECHARGE              = "RECHARGE";

    // ===== 预约 =====
    public static final String BOOKING_CREATED       = "BOOKING_CREATED";
    public static final String BOOKING_CONFIRMED     = "BOOKING_CONFIRMED";
    public static final String BOOKING_CHECKIN       = "BOOKING_CHECKIN";

    // ===== 关怀 =====
    public static final String BIRTHDAY              = "BIRTHDAY";
    public static final String MEMBER_DAY            = "MEMBER_DAY";
    /** M09-ext · 次卡即将到期（7 天内到期的有效次卡） */
    public static final String PACKAGE_PRE_EXPIRY   = "PACKAGE_PRE_EXPIRY";

    // ===== 裂变 / 互动 =====
    public static final String INVITE_SENT           = "INVITE_SENT";
    public static final String INVITE_REGISTERED     = "INVITE_REGISTERED";
    public static final String REVIEW_SUBMITTED      = "REVIEW_SUBMITTED";
    public static final String SHARED                = "SHARED";
}
