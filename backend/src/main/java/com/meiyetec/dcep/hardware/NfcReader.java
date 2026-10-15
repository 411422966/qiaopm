package com.meiyetec.dcep.hardware;

/**
 * DCEP-01 真机对接：NFC 读卡器抽象
 *
 * <p>支持四种物理通道：
 * <ul>
 *   <li>NFC-SIM 卡（运营商 SWP-SIM）</li>
 *   <li>SE 安全芯片（嵌入式/可穿戴）</li>
 *   <li>IoT 硬件钱包</li>
 *   <li>生物识别设备（指纹/3D 人脸）</li>
 * </ul>
 *
 * <p>本接口是上层业务调用的统一入口；底层实现可替换为：
 * <ul>
 *   <li>{@code SimulatedNfcReader}（开发联调）</li>
 *   <li>运营商 SDK 实现（移动 NFC-SIM / 联通 / 电信）</li>
 *   <li>设备厂商 SDK 实现（华为 Pay / Apple Wallet）</li>
 * </ul>
 */
public interface NfcReader {

    /**
     * 检测是否有可用的 NFC/SE 设备
     */
    boolean isPresent(String deviceSerial);

    /**
     * 选择应用（AID 选择，PC/SC SELECT 命令）
     *
     * @param deviceSerial 设备序列号（钱包编号或 SE 芯片 ID）
     * @param aid 应用标识符（数字人民币钱包 AID）
     * @return 选中成功返回 appletId
     */
    String selectApplet(String deviceSerial, String aid);

    /**
     * 读取余额（GET DATA 命令）
     */
    NfcBalance readBalance(String deviceSerial);

    /**
     * 发起离线扣款（DEBIT 命令 + 双签名）
     *
     * @param deviceSerial 设备序列号
     * @param amount 扣款金额
     * @param merchantId 商户号
     * @return 离线交易凭证（含双签名）
     */
    NfcDebitReceipt debitOffline(String deviceSerial, java.math.BigDecimal amount, String merchantId);

    /**
     * 关闭连接
     */
    void close(String deviceSerial);
}
