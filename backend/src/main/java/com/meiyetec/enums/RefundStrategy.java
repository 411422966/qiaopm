package com.meiyetec.enums;

public enum RefundStrategy {
    /** 原路退回（现金 / 第三方 / 数字人民币） */
    ORIGINAL,
    /** 退至会员钱包 */
    WALLET,
    /** 部分原路 + 部分储值 */
    MIXED
}
