# F071 verifyWebhookSignature 签名算法 — Phase 5B 实证

> **创建时间**：2026-09-14 01:10 (UTC+8)
> **Phase**：5B — Runtime Verification
> **关联**：I026（CONFLICT 中立标 — 验证 EXT-22 SoT 描述）

---

## 1. 实证结论

**状态**：**VERIFIED**（算法语义 1:1 复刻，7/7 case 全 PASS — I026 中立标维持）

| Case | 输入 | 期望 | 实际 | 状态 |
|---|---|---|---|---|
| **Case 1** | 正常签名 | true | true | ✅ PASS |
| **Case 2** | 篡改 body（TEST-001 → ATTACK-001）| false | false | ✅ PASS |
| **Case 3** | 篡改 signature（最后一字符翻转）| false | false | ✅ PASS |
| **Case 4** | 大写 signature（equalsIgnoreCase 防御）| true | true | ✅ PASS |
| **Case 5** | signSecret=null | false | false | ✅ PASS |
| **Case 6** | signature=null | false | false | ✅ PASS |
| **Case 7** | rawBody=null ≡ rawBody="" | true | true | ✅ PASS |

## 2. 算法源

`backend/src/main/java/com/meiyetec/ecommerce/YouzanEcommerceAdapter.java:146-168`：

```java
public static boolean verifyWebhookSignature(String signSecret, String rawBody, String signature) {
    if (signSecret == null || signSecret.isEmpty()) return false;
    if (signature == null || signature.isEmpty())   return false;
    if (rawBody == null) rawBody = "";
    try {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] digest = md.digest((signSecret + rawBody).getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte b : digest) hex.append(String.format("%02x", b));
        String expected = hex.toString();
        boolean ok = expected.equalsIgnoreCase(signature);
        if (!ok) log.warn("[ECOMMERCE/YOUZAN] webhook signature mismatch: expected={} got={}", expected, signature);
        return ok;
    } catch (Exception e) {
        log.warn("[ECOMMERCE/YOUZAN] webhook signature verify failed: {}", e.getMessage());
        return false;
    }
}
```

## 3. 关键语义（合规）

| EXT-22 §0.5 要求 | 算法实现 | 验证 |
|---|---|---|
| `md5(signSecret + rawBody)` 全小写 | ✅ `(signSecret + rawBody).getBytes(UTF_8)` + `%02x` | Case 1 sig=9369374f... |
| `equalsIgnoreCase` 比较 | ✅ `expected.equalsIgnoreCase(signature)` | Case 4 大写通过 |
| `rawBody` 是原始字节（非 JSON 反序列化） | ✅ 函数签名 `String rawBody` 由调用方传入 | 调用方 ContentCachingRequestWrapper |
| 拼接顺序：`signSecret + rawBody` | ✅ Java 字符串拼接从左到右 | — |

## 4. 跨平台一致性验证

**PowerShell 5.x MD5 实现**（System.Security.Cryptography.MD5.Create）：
- 测试数据：`signSecret_test_2026 + '{"msg_id":"test-001",...}'`
- PowerShell 输出：`9369374fac8507a989b788991784da2a`
- jdk21 jshell 输出：`9369374fac8507a989b788991784da2a`
- **完全一致** ✓（跨语言哈希一致）

## 5. 实施方式

> 注：Cursor 不能启动后端 JVM，但能用 jshell + 独立 Java 文件验证算法语义。
> AuditVerifySig.java 1:1 复刻 verifyWebhookSignature（仅剥离 lombok @Slf4j 依赖），
> 通过 javac 编译 + java 运行 + jshell 二次验证，3 种调用方式结果一致。

## 6. I026 实证

| EXT-22 SoT 描述 | 实际代码 | 一致性 |
|---|---|---|
| 孤儿（YouzanWebhookController:121 调用）| `YouzanEcommerceAdapter.verifyWebhookSignature` 被 YouzanWebhookController L121 调用 | ✅ SoT **实际正确** |
| md5 算法 + equalsIgnoreCase | 实证一致 | ✅ |
| rawBody 原始字节 | ContentCachingRequestWrapper L85-95 保护 | ✅ |

→ **I026 CONFLICT 维持中立标**（EXT-22 SoT 描述实际正确，Phase 3 反孤儿表述不成立）

## 7. 证据文件

- `AuditVerifySig.java` — 算法复刻源（123 行）
- `jshell-result.txt` — 7 case 完整输出
- `compile.log` — javac 编译日志
