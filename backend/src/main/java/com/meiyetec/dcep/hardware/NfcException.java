package com.meiyetec.dcep.hardware;

/**
 * DCEP-01：NFC 操作异常
 *
 * <p>对应真实 NFC 读卡器返回的 SW（Status Word）错误码：
 * <ul>
 *   <li>6982：余额不足（Security status not satisfied）</li>
 *   <li>6A82：AID 未找到（File not found）</li>
 *   <li>6A86：参数错误（Incorrect parameters P1-P2）</li>
 *   <li>6700：长度错误（Wrong length）</li>
 *   <li>6303：PIN 校验失败（PIN tries remaining = 3）</li>
 * </ul>
 */
public class NfcException extends RuntimeException {

    /** ISO 7816-4 状态码 */
    private final String sw;

    public NfcException(String message) {
        super(message);
        this.sw = "6F00";  // 未指定错误
    }

    public NfcException(String sw, String message) {
        super(message);
        this.sw = sw;
    }

    public String getSw() {
        return sw;
    }

    /** 余额不足 */
    public static NfcException insufficientBalance(String message) {
        return new NfcException("6982", message);
    }

    /** AID 未找到 */
    public static NfcException aidNotFound(String aid) {
        return new NfcException("6A82", "AID not found: " + aid);
    }
}
