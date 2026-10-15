package com.meiyetec.entity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
/**
 * 数字人民币硬钱包
 * 支持 NFC-SIM卡 / 可穿戴设备 SE芯片 / 物联网硬件钱包
 */
@Data
@TableName("biz_dcep_hardware_wallet")
public class DcepHardwareWallet {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long memberId;
    private String memberName;
    /** 钱包名称 */
    private String walletName;
    /** 钱包类型: nfc_sim/se_chip/iot_wallet/biometric */
    private String walletType;
    /** 钱包编号(全局唯一) */
    private String walletCode;
    /** SE芯片ID */
    private String seChipId;
    /** NFC SIM卡号 */
    private String nfcSimId;
    /** 设备型号: 如华为Mate60/Apple Watch SE */
    private String deviceModel;
    /** 设备品牌 */
    private String brand;
    /** 钱包公钥 */
    private String publicKey;
    /** 钱包地址(数字人民币地址) */
    private String walletAddress;
    /** 运营机构: 工商银行/建设银行/中国银行 */
    private String operatorOrg;
    /** 余额 */
    private BigDecimal balance;
    /** 累计充值 */
    private BigDecimal totalRecharge;
    /** 累计消费 */
    private BigDecimal totalConsumption;
    /** 单笔限额 */
    private BigDecimal maxAmountPerTransaction;
    /** 单日限额 */
    private BigDecimal maxAmountPerDay;
    /** 单日剩余 */
    private BigDecimal dailyRemaining;
    /** 支持离线: 0-否, 1-是 */
    private Integer offlineSupport;
    /** 离线余额上限 */
    private BigDecimal offlineBalanceLimit;
    /** 当前离线余额 */
    private BigDecimal offlineBalance;
    /** 生物识别: 0-否, 1-指纹/人脸 */
    private Integer biometricEnabled;
    /** PIN 加密状态 (0-未设置 1-已设置) */
    private Integer pinSet;
    /** PIN BCrypt 哈希 (DCEP-01 新增) */
    private String pinHash;
    /** 私钥 AES 加密 (DCEP-01 新增) */
    private String privateKeyEnc;
    /** 当日累计消费 (DCEP-01 新增) */
    private BigDecimal dailyUsed;
    /** 当日限额重置时间 (DCEP-01 新增) */
    private LocalDateTime dailyResetAt;
    /** 最后同步时间 */
    private LocalDateTime lastSyncTime;
    /** 状态: active/lost/stolen/frozen/expired */
    private String status;
    /** 激活时间 */
    private LocalDateTime activatedTime;
    /** 过期时间 */
    private LocalDateTime expiredTime;
    private String remark;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
