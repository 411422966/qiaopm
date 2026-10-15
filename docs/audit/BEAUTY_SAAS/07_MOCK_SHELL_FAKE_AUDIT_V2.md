# 美业 SaaS Mock / Shell / Fake 代码 Reality Audit v2.0

**审计时间**: 2026-08-13
**审计版本**: v2.0
**审计原则**: CODE > CONFIG > API > TEST > UI
**审计范围**: 后端 Java（Spring Boot）+ 前端 Vue 3 + 全部支付/AI/营销渠道

---

## 0. 审计摘要 (TL;DR)

| 项目 | 数值 |
|------|------|
| 后端 Java 文件 | 47 Controller + 51 Service (+24 ServiceImpl) + 120 Mapper |
| 空壳 Service 接口 | **4 / 4**（100% 仅 `extends IService<T>`） |
| 空壳 ServiceImpl | **4 / 4**（100% 仅 `extends ServiceImpl<M, T>`） |
| P0 高危 Mock | **1**（DCEP 数字人民币 真实通道 = 完全未对接） |
| P1 高危 Mock | **1**（AI 视觉档案 = Random 伪特征） |
| P2 中危 Mock | **3**（SMS / MiniProgram / Pos 收银 DCEP） |
| Controller 直连 Mapper 案例 | **5 个 Controller / 21 处 @Autowired Mapper** |
| 前端 Mock 数据生成器 | **1 个**（pos/stats.vue `generateMockOrders()`） |
| 前端 TODO/FIXME | **2 处**（含 P2 风险） |
| 后端 TODO | **1 处**（`PosController.java:319` DCEP 银联对接） |

**总体结论**: 🟠 **PARTIAL** — 业务主流程可用，但 **支付通道 + AI 视觉** 仍为 Mock/Stub 状态，生产部署前必须补齐真实对接。

---

## 1. MOCK 检测全局扫描

### 1.1 后端 Mock/Fake/Stub 关键词扫描

| 关键词 | 命中文件数 | 命中数 | 风险等级 |
|--------|----------|--------|----------|
| `TODO` | 2 | 2 | P2 / P0 |
| `FIXME` | 0 | 0 | — |
| `XXX` | 0 | 0 | — |
| `Mock`（类/方法名）| 2 | 5 | P0 (`DcepService.mockPrepay`) / P1 (`VisualArchiveService.mockAiAnalyze`) |
| `DEMO`（日志/prefix）| 3 | 8 | P2 |
| `Demo` | 4 | 5 | P2 |
| `MOCK`（envMode 等）| 2 | 9 | P0 |
| `throw new RuntimeException` | 8 | 38 | 🟢 多数为参数校验（合法） |
| `System.out.println` | 0 | 0 | 🟢 |
| `new Random()` | 8 | ~15 | 🟠 P1（AI 视觉）+ 🟢 配置使用 |
| `Math.random()` | 1 | 1 | 🟠 P0（Pos 收银 DCEP 流水号） |
| `return []` / `return null` | 多个 | ~30 | 🟢 多为合规短路（无数据时返空）|

### 1.2 前端 Mock/Fake 关键词扫描

| 关键词 | 命中文件 |
|--------|----------|
| `mock` / `Mock` | `views/pos/stats.vue`（generateMockOrders + DEMO_MEMBERS 等演示数据） |
| `console.log` | `views/pos/checkout.vue`, `views/stock/stats.vue`, `components/SubmitButton.vue`, `views/biz/service-item.vue`, `utils/idempotency.ts`, `views/biz/treatment-plan.vue`, `views/system/payment.vue`, `views/stock/product.vue`, `views/commission/rules.vue` |
| `setTimeout` | 9 个文件（绝大多数为防抖/重置校验定时器，**非 mock**） |
| `TODO` | 2 处（`pos/checkout.vue:558`、`system/qualification.vue`） |

**结论**: 前端 mock 风险主要集中在 `pos/stats.vue` 的演示数据生成器（受控），其余 setTimeout 为 UI 防抖。

---

## 2. 高危 Mock 详细审计

### 2.1 P0 — DCEP 数字人民币（**真实通道完全未对接**）

**文件**: `D:\QiaoPM\backend\src\main\java\com\meiyetec\service\DcepService.java`

**严重程度**: 🔴 **P0** — 资金通道，绝不允许 Mock 上生产

**风险摘要**:
- 整个服务注释即承认 "**P3 · T-P05：数字人民币 2.0（真实通道联调 + Mock 兜底）**"
- `mockPrepay()` 在 `MOCK` 模式直接返回硬编码 QR；非 MOCK 模式 → 直接返回 `"NOT_IMPLEMENTED"` 失败
- 验签函数对 `MOCK` 通道放行（`sign.startsWith("MOCK-")` 即通过），不具备任何安全性
- 健康检查 `pingHealth()` 仅检查 URL 字符串前缀，连真 HTTP 请求都没发

**代码证据**:

```45:99:DcepService.java
    private static final Logger log = LoggerFactory.getLogger(DcepService.class);

    private final PayDcepChannelMapper channelMapper;
    private final PayDcepOrderMapper orderMapper;
    private final PayDcepNotifyMapper notifyMapper;

    @Autowired
    private JdbcTemplate jdbc;

    private static final ObjectMapper JSON = new ObjectMapper();

    // ====================================================================
    // 通道配置
    // ====================================================================

    @GetMapping("/channel/list")
    public Result<List<PayDcepChannel>> channelList() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(channelMapper.selectList(new LambdaQueryWrapper<PayDcepChannel>()
                .eq(PayDcepChannel::getTenantId, tenantId)
                .eq(PayDcepChannel::getStatus, 1)));
    }

    @PostMapping("/channel")
    public Result<PayDcepChannel> createChannel(@RequestBody PayDcepChannel c) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (c.getChannelCode() == null) return Result.error(BizCode.BAD_REQUEST, "channelCode 必填");
        if (c.getEnvMode() == null) c.setEnvMode("MOCK");          // <<< 默认 MOCK 模式
        if (c.getStatus() == null) c.setStatus(1);
        c.setTenantId(tenantId);
        channelMapper.insert(c);
        return Result.success("已创建", c);
    }

    @PostMapping("/channel/{id}/health")
    public Result<Map<String, Object>> healthCheck(@PathVariable Long id) {
        PayDcepChannel c = channelMapper.selectById(id);
        if (c == null) return Result.error(BizCode.NOT_FOUND, "通道不存在");
        // 生产：调用对应银行 API
        boolean ok = "MOCK".equalsIgnoreCase(c.getEnvMode()) || pingHealth(c.getApiBaseUrl());
        jdbc.update("UPDATE pay_dcep_channel SET last_health_at = NOW(), last_health_msg = ? WHERE id = ?",
                ok ? "OK" : "FAIL", id);
        Map<String, Object> data = new HashMap<>();
        data.put("healthy", ok);
        data.put("envMode", c.getEnvMode());
        return Result.success(data);
    }

    private boolean pingHealth(String url) {
        try {
            // 简化：实际替换为 HTTP 探测
            return url != null && (url.startsWith("https://") || url.startsWith("http://"));  // <<< 字符串前缀检查
        } catch (Exception e) {
            return false;
        }
    }
```

```144:183:DcepService.java
        // 调用通道 prePay（Mock）
        Map<String, Object> prepayResp = mockPrepay(channel, order);

        order.setPrepayId((String) prepayResp.get("prepayId"));
        order.setPrepayAt(LocalDateTime.now());
        if (!"OK".equals(prepayResp.get("status"))) {
            order.setStatus(5);
            order.setErrorCode((String) prepayResp.get("errorCode"));
            order.setErrorMsg((String) prepayResp.get("errorMsg"));
        }
        orderMapper.updateById(order);

        Map<String, Object> data = new HashMap<>();
        data.put("dcepOrderId", order.getId());
        data.put("dcepOrderNo", order.getDcepOrderNo());
        data.put("prepayId", order.getPrepayId());
        data.put("status", order.getStatus());
        data.put("qrCode", prepayResp.get("qrCode"));
        data.put("expireAt", order.getExpireAt());
        return Result.success(data);
    }

    /**
     * 模拟通道 prepay（MOCK 模式直接成功）
     * 生产：调用银行 API：HTTP POST {api_base_url}/prepay
     */
    private Map<String, Object> mockPrepay(PayDcepChannel channel, PayDcepOrder order) {
        Map<String, Object> res = new HashMap<>();
        if ("MOCK".equalsIgnoreCase(channel.getEnvMode())) {
            res.put("status", "OK");
            res.put("prepayId", "PREPAY-" + order.getDcepOrderNo());
            res.put("qrCode", "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=");  // <<< 1x1 像素硬编码 PNG
            return res;
        }
        // 真实通道：使用 HTTP 调用（Demo：失败兜底）
        res.put("status", "FAIL");
        res.put("errorCode", "NOT_IMPLEMENTED");
        res.put("errorMsg", "SANDBOX/PROD 通道尚未对接");
        return res;
    }
```

```333:352:DcepService.java
    /**
     * 验签（Demo 简化）
     * 生产：根据 channelCode 选 RSA/SM2 公钥验签
     */
    private boolean verifySign(String channelCode, Map<String, Object> payload, String sign) {
        if (sign == null) return false;
        // Demo：sign = SHA-256(channelCode + notifyId + dcepOrderNo)
        try {
            String src = channelCode + (payload.get("notifyId") == null ? "" : payload.get("notifyId"))
                    + (payload.get("dcepOrderNo") == null ? "" : payload.get("dcepOrderNo"));
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(src.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            // Mock 通道：客户端 sign 等于服务端计算结果即通过
            return sign.equalsIgnoreCase(sb.toString()) || sign.startsWith("MOCK-");   // <<< 任何 MOCK- 开头的签名都通过
        } catch (Exception e) {
            return false;
        }
    }
```

**另一个 DCEP 入口（PosController.processOnePayment）TODO 注释**:

```316:325:PosController.java
                case "package" -> {
                    // 次卡核销：从 memberCardId 或第一笔 order_item 关联的套餐扣次数
                    Long cardId = it.getMemberCardId();
                    if (cardId == null) {
                        // 自动取订单中第一个有 packageOrderId 的明细对应的套餐卡
                        List<PosOrderItem> orderItems = itemMapper.selectList(
                                new LambdaQueryWrapper<PosOrderItem>().eq(PosOrderItem::getOrderId, order.getId()));
                        PosOrderItem pkgItem = orderItems.stream()
                                .filter(x -> x.getPackageOrderId() != null).findFirst().orElse(null);
                        if (pkgItem == null) throw new RuntimeException("订单未关联套餐，无法核销");
                        cardId = pkgItem.getPackageOrderId();
                    }
                    redeemPackage(order, cardId);
                    p.setAmount(BigDecimal.ZERO); // 次卡本身免费
                }
                case "e_cny_soft", "e_cny_hard" -> {
                    // 数字人民币：模拟对接（实际接入央行/运营机构网关 TODO）  <<< THIS IS THE TODO
                    if (it.getTransactionId() == null) {
                        p.setTransactionId("ECNY-" + System.currentTimeMillis() + "-" + (int)(Math.random()*10000));  // <<< 真随机数当 transactionId
                    }
                }
```

**整改要求**:
1. 选定 1 家银行（建行/工行/农行）对接真实 SDK
2. `mockPrepay` 改为 `bankPrepay` 接口 + 真实 HTTP 调用
3. 验签改为 RSA/SM2 真实公钥验签
4. 删除 `sign.startsWith("MOCK-")` 的失效兜底
5. healthCheck 真实 HTTP POST `/health`

---

### 2.2 P1 — AI 视觉档案（**Random 伪特征，毫无 AI 能力**）

**文件**: `D:\QiaoPM\backend\src\main\java\com\meiyetec\service\VisualArchiveService.java`

**严重程度**: 🟠 **P1** — 业务功能存在但结果完全随机，等于"假 AI"

**风险摘要**:
- `mockAiAnalyze()` 用 `new Random(hash.hashCode())` 生成伪特征值
- `mockAiCompare()` 同样基于 hash 的随机数生成相似度/差异分
- 没有调用任何真实视觉模型 SDK
- `modelVersion = "AI-VISION-V1-DEMO"` 字段明确标注是 Demo

**代码证据**:

```97:109:VisualArchiveService.java
        // AI 自动分析（Demo：基于类型模拟标签）
        if (Boolean.TRUE.equals(body.get("analyzeNow"))) {
            Map<String, Object> aiResult = mockAiAnalyze(archiveType, imageHash);
            a.setAiAnalyzed(1);
            a.setAiFeaturesJson(aiResult.get("features").toString());
            a.setAiTags((String) aiResult.get("tags"));
            a.setAiScore((BigDecimal) aiResult.get("score"));
        }
```

```421:473:VisualArchiveService.java
    /** 模拟 AI 特征分析（生产替换为真实模型调用） */
    private Map<String, Object> mockAiAnalyze(String type, String hash) {
        Random rnd = new Random(hash.hashCode());                                                 // <<< 伪随机种子
        Map<String, Object> res = new HashMap<>();
        String[] skinTags = {"色斑", "毛孔", "细纹", "痤疮", "光泽", "弹性", "水润", "暗沉"};
        String[] faceTags = {"轮廓", "对称", "眼袋", "法令纹", "苹果肌", "嘴角"};
        String[] bodyTags = {"均匀", "疤痕", "色差", "松弛", "紧致"};
        String pool = type.contains("SKIN") ? String.join(",", skinTags)
                : type.contains("FACE") ? String.join(",", faceTags)
                : String.join(",", bodyTags);
        String[] tagArr = pool.split(",");
        StringBuilder tags = new StringBuilder();
        int tagCount = 3 + rnd.nextInt(3);
        for (int i = 0; i < tagCount; i++) {
            if (i > 0) tags.append(",");
            tags.append(tagArr[rnd.nextInt(tagArr.length)]);                                         // <<< 随机标签
        }
        BigDecimal score = BigDecimal.valueOf(50 + rnd.nextInt(50)).setScale(2, RoundingMode.HALF_UP);  // <<< 50-99 随机分

        Map<String, Object> features = new HashMap<>();
        features.put("brightness", rnd.nextDouble());                                              // <<< 0-1 随机特征
        features.put("saturation", rnd.nextDouble());
        features.put("symmetry", rnd.nextDouble());
        features.put("texture_score", rnd.nextDouble());

        res.put("features", features);
        res.put("tags", tags.toString());
        res.put("score", score);
        return res;
    }

    /** 模拟 AI 对比（生产替换为特征向量比对） */
    private Map<String, Object> mockAiCompare(MhVisualArchive before, MhVisualArchive after) {
        Random rnd = new Random((before.getImageHash() + after.getImageHash()).hashCode());         // <<< 伪随机种子
        BigDecimal similarity = BigDecimal.valueOf(0.5 + rnd.nextDouble() * 0.4).setScale(4, RoundingMode.HALF_UP);  // <<< 0.5-0.9 随机相似度
        BigDecimal diff = BigDecimal.valueOf(10 + rnd.nextDouble() * 30).setScale(2, RoundingMode.HALF_UP);          // <<< 10-40 随机差异

        Map<String, Object> skin = new LinkedHashMap<>();
        skin.put("spot", 1.5 + rnd.nextDouble() * 5);
        skin.put("pore", 1.5 + rnd.nextDouble() * 5);
        skin.put("wrinkle", 1.5 + rnd.nextDouble() * 5);
        skin.put("brightness", rnd.nextDouble() * 5);

        Map<String, Object> res = new HashMap<>();
        res.put("similarity", similarity);
        res.put("diffScore", diff);
        res.put("skinDiff", skin.toString());
        res.put("improvement", "毛孔细化、色斑淡化、皮肤光泽度提升");                                // <<< 硬编码"改善"文案
        res.put("regression", "暂无明显退步项");                                                    // <<< 硬编码"无退步"文案
        res.put("summary", String.format("整体改善 %.1f 分，相似度 %.2f，建议继续当前疗程", diff, similarity));
        return res;
    }
```

**同期假数据**:
- `c.setModelVersion("AI-VISION-V1-DEMO");`（line 199）— 字段值即承认是 Demo
- `imageHash` 默认值 `"sha256-" + Integer.toHexString(imageUrl.hashCode()) + "-" + System.currentTimeMillis()`（line 67-68）— 不是真 SHA-256

**整改要求**:
1. 接入真实 CV 服务（阿里云视觉智能 / 腾讯云人脸分析 / 自研模型）
2. 删除 `mockAiAnalyze` / `mockAiCompare` 方法
3. `modelVersion` 改为真实模型版本号
4. `imageHash` 改为文件上传后真正的 SHA-256

---

### 2.3 P2 — SMS 短信（**Log 兜底 Demo**）

**文件**: `D:\QiaoPM\backend\src\main\java\com\meiyetec\service\channel\SmsSender.java`

**严重程度**: 🟠 **P2** — 影响营销触达有效性，不会丢钱

**风险摘要**:
- 注释明确写 "**生产环境替换为阿里云 SDK：com.aliyun:dysmsapi20170525**"
- 实际只 `log.info` 然后返回 `SMS-DEMO-{timestamp}`，根本没发短信
- `healthCheck()` 永远返回 `true`

**代码证据**:

```17:53:SmsSender.java
@Component
public class SmsSender implements ChannelSender {

    private static final Logger log = LoggerFactory.getLogger(SmsSender.class);
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    @Override
    public String channelType() {
        return "SMS";
    }

    @Override
    public SendResult send(MktChannelTemplate tpl, String receiver, Map<String, Object> vars) {
        try {
            String content = render(tpl.getContent(), vars);
            // ============ 生产环境替换此处 ============
            // DefaultProfile profile = DefaultProfile.getProfile("cn-hangzhou", ACCESS_KEY, SECRET);
            // IAcsClient client = new DefaultAcsClient(profile);
            // SendSmsRequest req = new SendSmsRequest();
            // req.setPhoneNumbers(receiver);
            // req.setSignName("美业科技");
            // req.setTemplateCode(tpl.getExternalTemplateId());
            // req.setTemplateParam(content);
            // SendSmsResponse resp = client.getAcsResponse(req);
            // return SendResult.ok(resp.getBizId());
            // ===========================================
            log.info("[SMS-DEMO] to={} content={}", receiver, content);         // <<< 只是日志
            return SendResult.ok("SMS-DEMO-" + System.currentTimeMillis());      // <<< 假 externalMsgId
        } catch (Exception e) {
            log.warn("[SMS] send fail: {}", e.getMessage());
            return SendResult.fail(e.getMessage());
        }
    }

    @Override
    public boolean healthCheck() {
        return true;                                                             // <<< 假健康
    }
}
```

**整改要求**:
1. 接入阿里云/腾讯云短信 SDK
2. `healthCheck()` 改成实际发送测试短信或调用 API
3. 失败时 `SendResult.fail` 含真实错误码

---

### 2.4 P2 — MiniProgram 小程序（**Log 兜底 Demo**）

**文件**: `D:\QiaoPM\backend\src\main\java\com\meiyetec\service\channel\MiniProgramSender.java`

**严重程度**: 🟠 **P2** — 小程序订阅消息全部丢失

**代码证据**:

```14:47:MiniProgramSender.java
@Component
public class MiniProgramSender implements ChannelSender {

    private static final Logger log = LoggerFactory.getLogger(MiniProgramSender.class);

    @Override
    public String channelType() {
        return "MINI_PROGRAM";
    }

    @Override
    public SendResult send(MktChannelTemplate tpl, String receiver, Map<String, Object> vars) {
        try {
            String content = SmsSender.render(tpl.getContent(), vars);
            // ============ 生产环境替换此处 ============
            // WxMaService wxService = ...;
            // WxMaSubscribeMessage msg = new WxMaSubscribeMessage();
            // msg.setTemplateId(tpl.getExternalTemplateId());
            // msg.setToUser(receiver);
            // msg.addData(new WxMaSubscribeMessage.MsgData("thing1", "..."));
            // wxService.getMsgService().sendSubscribeMsg(msg);
            // ===========================================
            log.info("[MP-DEMO] openId={} template={} content={}", receiver, tpl.getExternalTemplateId(), content);  // <<< 日志兜底
            return SendResult.ok("MP-DEMO-" + System.currentTimeMillis());                            // <<< 假 externalMsgId
        } catch (Exception e) {
            return SendResult.fail(e.getMessage());
        }
    }

    @Override
    public boolean healthCheck() {
        return true;                                                                                  // <<< 假健康
    }
}
```

**整改要求**:
1. 接入 `me.chanjar:weixin-java-miniapp`
2. 配置 AppID / AppSecret（在配置中心/数据库）
3. `healthCheck` 接入 access_token 健康检查

---

### 2.5 P2 — InAppSender 站内信（**只 log，没写入任何存储**）

**文件**: `D:\QiaoPM\backend\src\main\java\com\meiyetec\service\channel\InAppSender.java`

**严重程度**: 🟠 **P2** — 站内消息丢，用户无感知

```13:39:InAppSender.java
@Component
public class InAppSender implements ChannelSender {

    private static final Logger log = LoggerFactory.getLogger(InAppSender.class);

    @Override
    public String channelType() {
        return "IN_APP";
    }

    @Override
    public SendResult send(MktChannelTemplate tpl, String receiver, Map<String, Object> vars) {
        try {
            String content = SmsSender.render(tpl.getContent(), vars);
            // ============ 写站内信表/Redis ============   // <<< 注释承认 TODO
            log.info("[IN_APP] userId={} content={}", receiver, content);
            return SendResult.ok("INAPP-" + System.currentTimeMillis());
        } catch (Exception e) {
            return SendResult.fail(e.getMessage());
        }
    }

    @Override
    public boolean healthCheck() {
        return true;
    }
}
```

**整改要求**:
1. 写入 `sys_in_app_message` 表或 Redis List
2. 提供 `/sys/in-app/unread` 接口供前端轮询

---

## 3. 空壳 Service (Shell Service) 全量审计

### 3.1 空壳 Service 接口

| Service | 文件 | 接口行数 | 实际方法 | 风险等级 |
|---------|------|----------|----------|----------|
| `BookingService` | `service/BookingService.java` | 6 | **0** | 🟠 P1 |
| `WalletService` | `service/WalletService.java` | 7 | **0** | 🔴 P0 |
| `PackageRedemptionService` | `service/PackageRedemptionService.java` | 7 | **0** | 🟠 P1 |
| `MemberService` | `service/MemberService.java` | 7 | **0** | 🔴 P0 |

**代码证据**:

```1:6:BookingService.java
package com.meiyetec.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.Booking;

public interface BookingService extends IService<Booking> {
}
```

```1:7:WalletService.java
package com.meiyetec.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.Wallet;

public interface WalletService extends IService<Wallet> {
}
```

```1:7:PackageRedemptionService.java
package com.meiyetec.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.PackageRedemption;

public interface PackageRedemptionService extends IService<PackageRedemption> {
}
```

```1:7:MemberService.java
package com.meiyetec.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.Member;

public interface MemberService extends IService<Member> {
}
```

### 3.2 空壳 ServiceImpl

| ServiceImpl | 行数 | 实际方法 | 风险等级 |
|-------------|------|----------|----------|
| `BookingServiceImpl` | 10 | 0 | 🟠 P1 |
| `WalletServiceImpl` | 11 | 0 | 🔴 P0 |
| `PackageRedemptionServiceImpl` | 11 | 0 | 🟠 P1 |
| `MemberServiceImpl` | 11 | 0 | 🔴 P0 |

**代码证据**:

```1:10:BookingServiceImpl.java
@Service
public class BookingServiceImpl extends ServiceImpl<BookingMapper, Booking> implements BookingService {
}
```

```1:11:WalletServiceImpl.java
@Service
public class WalletServiceImpl extends ServiceImpl<WalletMapper, Wallet> implements WalletService {
}
```

```1:11:PackageRedemptionServiceImpl.java
@Service
public class PackageRedemptionServiceImpl extends ServiceImpl<PackageRedemptionMapper, PackageRedemption> implements PackageRedemptionService {
}
```

```1:11:MemberServiceImpl.java
@Service
public class MemberServiceImpl extends ServiceImpl<MemberMapper, Member> implements MemberService {
}
```

### 3.3 空壳 Service 反模式影响

**问题**: 当 Service 是空壳时，业务逻辑全部渗透到 Controller 中导致：

1. **代码重复**: 同一逻辑在多处 Controller 中复制（钱包扣余额逻辑在 `WalletController` 和 `PackageController` 重复）
2. **测试困难**: 无法对 Service 做单元测试，只能测 Controller
3. **事务边界混乱**: `@Transactional` 注解在 Controller 上（违反 Spring 最佳实践）
4. **缓存/审计/AOP 失效**: 业务方法无法被 AOP 拦截

**示例 — WalletController 中 doRecharge 完整业务逻辑（应在 WalletService）**:

```115:163:WalletController.java
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> doRecharge(WalletOpDTO dto, Long tenantId) {
        if (memberService.getById(dto.getMemberId()) == null) {
            throw new BusinessException(BizCode.MEMBER_NOT_FOUND);
        }
        BigDecimal gift = dto.getGiftAmount() == null ? BigDecimal.ZERO : dto.getGiftAmount();
        if (gift.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(BizCode.BAD_REQUEST, "赠送金额不能为负");
        }
        BigDecimal add = dto.getAmount().add(gift);
        if (add.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(BizCode.BAD_REQUEST, "充值金额必须大于 0");
        }

        Wallet wallet = ensureWallet(dto.getMemberId());
        BigDecimal before = wallet.getBalance();
        int rows = casUpdate(dto.getMemberId(), add, dto.getAmount(), BigDecimal.ZERO, wallet.getVersion());
        if (rows == 0) {
            throw new BusinessException(BizCode.CONCURRENCY_CONFLICT, "钱包并发更新失败");
        }

        WalletTransaction txn = new WalletTransaction();
        txn.setTenantId(tenantId);
        txn.setMemberId(dto.getMemberId());
        txn.setType(TYPE_RECHARGE);
        txn.setAmount(dto.getAmount());
        txn.setBalanceBefore(before);
        txn.setBalanceAfter(before.add(add));
        txn.setGiftAmount(gift);
        txn.setPayMethod(dto.getPayMethod() == null ? "现金" : dto.getPayMethod());
        txn.setOperatorId(dto.getOperatorId());
        txn.setRemark(dto.getRemark());
        txnService.save(txn);

        // 同步 crm_member.balance
        Member m = memberService.getById(dto.getMemberId());
        m.setBalance(before.add(add));
        memberService.updateById(m);

        Map<String, Object> data = new HashMap<>();
        data.put("memberId", dto.getMemberId());
        data.put("rechargeAmount", dto.getAmount());
        data.put("giftAmount", gift);
        data.put("totalAdded", add);
        data.put("balanceBefore", before);
        data.put("balanceAfter", before.add(add));
        data.put("txnId", txn.getId());
        return Result.success("充值成功", data);
    }
```

**整改要求**:
- `WalletService` / `MemberService` 至少要包含 `ensureWallet` / `setBalance` / `getBalance` 等核心业务方法
- `BookingService` 至少要包含 `checkConflict` / `changeStatus` / `createBooking` 等
- `PackageRedemptionService` 至少要包含 `redeemPackage` / `splitRedeem` 等

---

## 4. Controller → Mapper 直连反模式

### 4.1 概览

通过 `@Autowired Mapper` / `@RequiredArgsConstructor` 注入 Mapper 后**直接调用** Mapper 方法（绕过 Service）的 Controller 列表：

| Controller | Mapper 数量 | 风险等级 |
|------------|-------------|----------|
| `BookingController` | 0 (但用 `JdbcTemplate` 直接 SQL 查询统计) | 🟠 |
| `WalletController` | 1 (`WalletMapper`) | 🟠 |
| `MemberController` | 0 (但用 `JdbcTemplate` 直接 SQL 删除) | 🟠 |
| `PackageController` | 1 (`PackageOrderMapper.getBaseMapper()` 强转) | 🔴 |
| `PosController` | 4 (`PosOrderMapper`, `PosOrderItemMapper`, `PosPaymentMapper`, `BizServiceMapper`) | 🔴 |
| `StockController` | 6 (`StockProductMapper`, `StockInventoryMapper`, `StockRecordMapper`, `StockCheckMapper`, `BizServiceMaterialMapper`, `PosOrderConsumptionMapper`) | 🔴 |
| `ReportController` | 7+ (`PosOrderMapper`, `PosOrderItemMapper`, `PosPaymentMapper`, `MemberMapper`, `EmployeeMapper`, `ReportConfigMapper`, `ReportExportLogMapper`, `ReportSnapshotMapper`) | 🔴 |
| `CommissionController` | 5+ (`CommissionRuleMapper`, `CommissionRecordMapper`, `CommissionSalaryMapper`, `EmployeeMapper`, `CommissionSettlementMapper`) | 🔴 |
| `DcepMerchantQualificationController` | 1+ | 🟠 |

### 4.2 典型反模式代码

**BookingController — JDBC 直查**:

```247:281:BookingController.java
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LocalDate today = LocalDate.now();

        String sql =
            "SELECT " +
            "  COALESCE(SUM(CASE WHEN status IN (2,3,4) THEN 1 ELSE 0 END),0) AS confirmed, " +
            "  COALESCE(SUM(CASE WHEN status IN (3,4) THEN 1 ELSE 0 END),0) AS checkedIn, " +
            "  COALESCE(SUM(CASE WHEN status = 6 THEN 1 ELSE 0 END),0) AS noShow, " +
            "  COALESCE(SUM(CASE WHEN status = 5 THEN 1 ELSE 0 END),0) AS cancelled " +
            "FROM biz_booking WHERE deleted = 0 AND tenant_id = ? AND booking_date = ?";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, tenantId, today);     // <<< Controller 内嵌 SQL
        ...
```

**WalletController — Mapper 直调**:

```269:288:WalletController.java
    private int casUpdate(Long memberId, BigDecimal delta, BigDecimal rechargeDelta,
                          BigDecimal consumeDelta, Integer version) {
        for (int i = 0; i < MAX_OPTIMISTIC_RETRY; i++) {
            int rows = walletMapper.updateBalance(memberId, delta, rechargeDelta, consumeDelta, version);  // <<< 直接调 Mapper
            if (rows > 0) return rows;
            // 重新拉取最新数据并重试
            Wallet latest = walletService.getOne(
                    new LambdaQueryWrapper<Wallet>().eq(Wallet::getMemberId, memberId));
            if (latest == null) {
                throw new BusinessException(BizCode.NOT_FOUND, "钱包不存在");
            }
            version = latest.getVersion();
            // 二次校验：余额是否够
            if (delta.signum() < 0 && latest.getBalance().add(delta).signum() < 0) {
                throw new BusinessException(BizCode.WALLET_BALANCE_NOT_ENOUGH,
                        "余额不足，当前余额: " + latest.getBalance());
            }
        }
        throw new BusinessException(BizCode.CONCURRENCY_CONFLICT, "钱包 CAS 失败，已达最大重试次数");
    }
```

```379:386:WalletController.java
    private Wallet ensureWallet(Long memberId) {
        Wallet w = walletService.getOne(new LambdaQueryWrapper<Wallet>().eq(Wallet::getMemberId, memberId));
        if (w == null) {
            walletMapper.insertIfAbsent(memberId);                                     // <<< 直接调 Mapper
            w = walletService.getOne(new LambdaQueryWrapper<Wallet>().eq(Wallet::getMemberId, memberId));
        }
        return w;
    }
```

**PackageController — 强转 BaseMapper**:

```224:236:PackageController.java
        PackageOrderMapper orderMapper = (PackageOrderMapper) orderService.getBaseMapper();   // <<< 强转 BaseMapper
        int rows = orderMapper.casRedeem(orderId, order.getVersion(), times);
        if (rows == 0) {
            // 版本过期或次数不足：抛具体错误
            PackageOrder now = orderService.getById(orderId);
            if (now == null) throw new BusinessException(BizCode.NOT_FOUND, "套餐订单不存在");
            if (now.getRemainingTimes() == null || now.getRemainingTimes() < times) {
                throw new BusinessException(BizCode.PACKAGE_TIMES_NOT_ENOUGH,
                        "剩余次数不足，当前剩余 " + now.getRemainingTimes());
            }
            throw new BusinessException(BizCode.CONCURRENCY_CONFLICT, "套餐核销并发冲突，请重试");
        }
```

**PosController — 4 个 Mapper 全注入 + 实付款金额次卡扣减直接写 SQL**:

```48:62:PosController.java
    private final PosOrderMapper orderMapper;
    private final PosOrderItemMapper itemMapper;
    private final PosPaymentMapper paymentMapper;
    private final BizServiceMapper serviceMapper;
    private final MemberService memberService;
    private final WalletTransactionService txnService;
    private final CommissionCalculationService commissionCalculationService;
    private final InventoryConsumptionService inventoryConsumptionService;
    private final JwtUtil jwtUtil;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final ApplicationEventPublisher eventPublisher;
```

```337:361:PosController.java
    private void redeemPackage(PosOrder order, Long packageOrderId) {
        Map<String, Object> card = jdbc.queryForMap(
                "SELECT id, member_id, remaining_times, status FROM crm_package_order WHERE id=? AND deleted=0",
                packageOrderId);
        ...
        int updated = jdbc.update(
                "UPDATE crm_package_order SET remaining_times = remaining_times - 1, update_time = NOW() " +
                "WHERE id = ? AND remaining_times > 0",
                packageOrderId);
        if (updated == 0) throw new RuntimeException("套餐扣减失败");

        jdbc.update(
                "INSERT INTO crm_package_redemption(order_id, member_id, package_id, times, service_item, operator_id, redeem_time) " +
                "VALUES(?, ?, (SELECT package_id FROM crm_package_order WHERE id=?), 1, ?, ?, NOW())",
                packageOrderId, memberIdOfCard, packageOrderId,
                "POS 订单 " + order.getOrderNo(), order.getCashierId());
    }
```

**StockController — 6 个 Mapper 全注入**:

```45:56:StockController.java
@RestController
@RequestMapping("/stock")
@SysOpLog(module = "库存")
public class StockController {

    @Autowired private StockProductMapper productMapper;
    @Autowired private StockInventoryMapper inventoryMapper;
    @Autowired private StockRecordMapper recordMapper;
    @Autowired private StockCheckMapper checkMapper;
    @Autowired private BizServiceMaterialMapper bomMapper;
    @Autowired private PosOrderConsumptionMapper orderConsumptionMapper;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private IdempotencyService idem;
    @Autowired private InventoryConsumptionService consumptionService;
```

### 4.3 反模式危害

1. **业务逻辑全在 Controller**: 2000+ 行的 `StockController`、`ReportController` 难以维护
2. **AOP 失效**: `@Transactional` 标注在 Controller 私有方法上无效（Spring AOP 限制）
3. **测试障碍**: 必须 Mock 整个 Mapper 链
4. **缓存 / 租户隔离 / 审计 AOP 失效**: 业务方法不在 Service 层，无法被拦截

---

## 5. 支付通道完整审计

| 支付方式 | 实现位置 | 真实 / Mock | 备注 |
|----------|----------|-------------|------|
| **现金 (cash)** | `PosController.processOnePayment` `case "cash"` | ✅ **真实** | 记录 `PosPayment` 表即可 |
| **银行卡 (unionpay)** | `PosController.processOnePayment` `case "unionpay"` | 🟠 **部分 Mock** | 仅记录，无银联 SDK；但 `transactionId` 由前端传入，所以业务逻辑可走，生产需对接银联电子签名 |
| **微信支付 (wechat)** | `PosController.processOnePayment` `case "wechat"` | 🟠 **部分 Mock** | 同 unionpay；无微信支付 SDK |
| **支付宝 (alipay)** | `PosController.processOnePayment` `case "alipay"` | 🟠 **部分 Mock** | 同 unionpay；无支付宝 SDK |
| **储值 (wallet)** | `WalletController` + `WalletService` | ✅ **真实** | 内部钱包余额管理 + 乐观锁 + 幂等 |
| **数字人民币 (e_cny_soft/hard)** | `PosController` line 318-323 + `DcepService` | 🔴 **完全 Mock** | 见 P0 |
| **次卡 (package)** | `PosController` + `PackageController` | ✅ **真实** | CAS 扣次数 + 写 redemption 记录 |
| **混合支付 (mixed)** | `PosController.processOnePayment` 循环 | ✅ **真实** | 支持多笔 payment 累加 |

### 5.1 支付反模式代码（PosController）

```284:335:PosController.java
        try {
            switch (method) {
                case "wallet" -> {
                    if (order.getMemberId() == null) throw new RuntimeException("非会员不能用储值支付");
                    Member m = memberService.getById(order.getMemberId());
                    if (m == null || m.getBalance() == null || m.getBalance().compareTo(amount) < 0) {
                        throw new RuntimeException("会员余额不足");
                    }
                    BigDecimal before = m.getBalance();
                    BigDecimal after = before.subtract(amount);
                    m.setBalance(after);
                    m.setTotalConsume(m.getTotalConsume() == null ? amount : m.getTotalConsume().add(amount));
                    memberService.updateById(m);
                    jdbc.update(                                                            // <<< Controller 内嵌 SQL
                        "INSERT INTO crm_wallet_transaction(tenant_id, member_id, type, amount, balance_before, balance_after, pay_method, order_id, operator_id, remark, created_at) " +
                        "VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                        1L, m.getId(), 2, amount, before, after, "wallet", order.getId(), order.getCashierId(),
                        "订单消费:" + order.getOrderNo(), LocalDateTime.now());
                }
                case "package" -> {
                    ...
                }
                case "e_cny_soft", "e_cny_hard" -> {
                    // 数字人民币：模拟对接（实际接入央行/运营机构网关 TODO）
                    if (it.getTransactionId() == null) {
                        p.setTransactionId("ECNY-" + System.currentTimeMillis() + "-" + (int)(Math.random()*10000));
                    }
                }
                case "cash", "wechat", "alipay", "unionpay" -> { /* 现金/第三方，记录即可 */ }    // <<< 第三方支付只记录
                default -> throw new RuntimeException("不支持的支付方式: " + method);
            }
        } catch (RuntimeException ex) {
            p.setStatus(2);
            p.setErrorMsg(ex.getMessage());
            paymentMapper.insert(p);
            return null;
        }
```

**问题**:
- `wechat` / `alipay` / `unionpay` 只是打 log 就算支付成功，没有任何渠道回调校验
- 没有渠道验签、金额校验、对账

**整改要求**:
1. 微信支付：接入 `com.github.wechatpay-apiv3:wechatpay-java` SDK
2. 支付宝：接入 `com.alipay.sdk:alipay-sdk-java`
3. 银联：接入 `com.unionpay.acp.sdk:acp-sdk`
4. 数字人民币：见 P0 整改
5. Callback handler 必须验签 + 幂等 + 查单 + 状态机

---

## 6. 静态硬编码数据审计

### 6.1 WalletController 充值赠送规则硬编码

```349:375:WalletController.java
    @GetMapping("/promotion/rules")
    public Result<List<Map<String, Object>>> promotionRules() {
        List<Map<String, Object>> rules = List.of(
                Map.of("rule", "充100送10", "recharge", 100, "gift", 10),
                Map.of("rule", "充500送60", "recharge", 500, "gift", 60),
                Map.of("rule", "充1000送150", "recharge", 1000, "gift", 150),
                Map.of("rule", "充2000送350", "recharge", 2000, "gift", 350)
        );
        return Result.success(rules);
    }

    @GetMapping("/promotion/calc")
    public Result<Map<String, Object>> calcPromotion(@RequestParam BigDecimal amount) {
        int amt = amount.intValue();
        int gift = 0;
        String rule = "无赠送";
        if (amt >= 2000)      { gift = 350; rule = "充2000送350"; }
        else if (amt >= 1000) { gift = 150; rule = "充1000送150"; }
        else if (amt >= 500)  { gift = 60;  rule = "充500送60"; }
        else if (amt >= 100)  { gift = 10;  rule = "充100送10"; }
        Map<String, Object> r = new HashMap<>();
        r.put("recharge", amount);
        r.put("gift", BigDecimal.valueOf(gift));
        r.put("rule", rule);
        r.put("total", amount.add(BigDecimal.valueOf(gift)));
        return Result.success(r);
    }
```

**风险**: 🟠 **P2** — 营销规则应配置化（`mkt_promotion_rule` 表），不可写死在 Controller

### 6.2 MemberController 会员编号生成

```281:284:MemberController.java
        if (m.getMemberNo() == null || m.getMemberNo().isEmpty()) {
            m.setMemberNo("M" + DateTimeFormatter.ofPattern("yyyyMMdd")
                    .format(LocalDate.now()) + String.format("%04d", new Random().nextInt(9999)));   // <<< Random 4 位编号
        }
```

```510:511:MemberController.java
                    m.setMemberNo("M" + DateTimeFormatter.ofPattern("yyyyMMdd")
                            .format(LocalDate.now()) + String.format("%04d", new Random().nextInt(9999)));
```

**风险**: 🟢 **可接受** — 4 位随机数每日可能少量冲突，但会员导入可手动指定编号

---

## 7. 前端 Mock 数据审计

### 7.1 pos/stats.vue 演示数据生成器

**文件**: `D:\QiaoPM\frontend\meiyetec-web\src\views\pos\stats.vue`

**严重程度**: 🟡 **P3**（受控演示）— 接口空数据时显示，避免页面空白

```335:355:pos/stats.vue
  try {
    // 后端 /orders/page 已批量补全 items + payments（IN 查询），无需前端并发拉详情
    const res: any = await orderApi.page({ pageNum: 1, pageSize: 200 })
    const real = res.data?.records || res.data?.list || res.records || res.list || []
    if (real.length === 0) {
      // 数据库暂无订单 → 注入演示数据（不污染后端）
      allOrders.value = generateMockOrders()
      isDemo.value = true
    } else {
      allOrders.value = real
      isDemo.value = false
    }
  } catch (e: any) {
    // 接口失败也走演示数据，避免页面空白
    allOrders.value = generateMockOrders()
    isDemo.value = true
    ElMessage.warning('后端数据加载失败，已显示演示数据：' + (e.message || ''))
  } finally {
    loading.value = false
  }
}
```

```357:396:pos/stats.vue
// ========== 演示数据模式 ==========
const isDemo = ref(false)
const DEMO_METHODS = ['wechat', 'alipay', 'cash', 'wallet', 'unionpay', 'points']
const DEMO_METHOD_WEIGHTS = [40, 35, 8, 7, 5, 5]   // 微信 40% 支付宝 35%...
const DEMO_SERVICES = [
  { name: '经典剪发',      price: 80,   weight: 25 },
  { name: '精剪造型',      price: 158,  weight: 18 },
  { name: '染发护理',      price: 480,  weight: 15 },
  { name: '烫发护理',      price: 580,  weight: 10 },
  { name: '深层护理 SPA',  price: 298,  weight: 12 },
  { name: '面部护理',      price: 380,  weight: 8 },
  { name: '美甲手护',      price: 128,  weight: 7 },
  { name: '肩颈按摩 60min',price: 168,  weight: 5 }
]
const DEMO_MEMBERS = [
  { name: '李小姐', phone: '138****1234' },
  { name: '王女士', phone: '139****5678' },
  ...
  { name: '散客',   phone: '' }
]
```

**正面**:
- 演示模式下有 `isDemo = true` 标记
- 文档明确写 "不污染后端"
- 有 `ElMessage.warning` 提示用户

**负面**:
- 演示数据生成器有 100+ 行（line 408-...），过多代码维护

**整改建议**:
- 拆成 `utils/mockData.ts` 公共模块
- 加一个 "演示模式" 开关，让用户主动选择而非自动触发

### 7.2 pos/checkout.vue TODO

```553:559:pos/checkout.vue
        amount: l.amount,
        transactionId: ['cash', 'package'].includes(l.method) ? null : `${l.method.toUpperCase()}-${Date.now()}-${Math.floor(Math.random()*1000)}`,
        memberCardId: l.method === 'package' ? 1 : null  // TODO 实际场景让用户选具体次卡
      }))
    }
```

**风险**: 🟠 **P2** — 次卡支付硬编码 `memberCardId = 1`，生产环境会扣错卡

**整改**: 增加卡选择器，让用户从名下有效次卡中选一张

---

## 8. 状态分类汇总表

| 模块 | 文件 | 方法 | Mock 类型 | 风险 | 证据（行号） |
|------|------|------|-----------|------|------------|
| **DCEP** | `DcepService.java` | `mockPrepay` | 硬编码成功 + 1×1 像素 QR | 🔴 P0 | 170-183 |
| **DCEP** | `DcepService.java` | `verifySign` | `MOCK-` 前缀全通过 | 🔴 P0 | 348 |
| **DCEP** | `DcepService.java` | `pingHealth` | URL 字符串前缀检查 | 🔴 P0 | 93-100 |
| **DCEP** | `PosController.java` | `processOnePayment` | `Math.random()` 当 transactionId | 🔴 P0 | 318-323 |
| **AI 视觉** | `VisualArchiveService.java` | `mockAiAnalyze` | Random 伪特征 | 🟠 P1 | 423-451 |
| **AI 视觉** | `VisualArchiveService.java` | `mockAiCompare` | Random 相似度 + 硬编码文案 | 🟠 P1 | 454-473 |
| **AI 视觉** | `VisualArchiveService.java` | `imageHash` 默认 | `hashCode()` + 时间戳 | 🟠 P1 | 67-68 |
| **AI 视觉** | `VisualArchiveService.java` | `modelVersion` | 字符串 `"AI-VISION-V1-DEMO"` | 🟠 P1 | 199 |
| **SMS** | `SmsSender.java` | `send` | log.info 兜底 | 🟠 P2 | 42-43 |
| **SMS** | `SmsSender.java` | `healthCheck` | 永远 `true` | 🟠 P2 | 51-52 |
| **MiniProgram** | `MiniProgramSender.java` | `send` | log.info 兜底 | 🟠 P2 | 36-37 |
| **MiniProgram** | `MiniProgramSender.java` | `healthCheck` | 永远 `true` | 🟠 P2 | 44-45 |
| **InApp** | `InAppSender.java` | `send` | 仅 log，没写存储 | 🟠 P2 | 28-29 |
| **Booking** | `BookingServiceImpl.java` | 整个类 | 仅 `extends ServiceImpl` | 🟠 P1 | 1-10 |
| **Wallet** | `WalletServiceImpl.java` | 整个类 | 仅 `extends ServiceImpl` | 🔴 P0 | 1-11 |
| **Package** | `PackageRedemptionServiceImpl.java` | 整个类 | 仅 `extends ServiceImpl` | 🟠 P1 | 1-11 |
| **Member** | `MemberServiceImpl.java` | 整个类 | 仅 `extends ServiceImpl` | 🔴 P0 | 1-11 |
| **Controller→Mapper** | `PosController` | 4 Mapper@Autowired | 业务渗透到 Controller | 🔴 P0 | 49-52 |
| **Controller→Mapper** | `StockController` | 6 Mapper@Autowired | 业务渗透到 Controller | 🔴 P0 | 47-55 |
| **Controller→Mapper** | `ReportController` | 7 Mapper@Autowired | 业务渗透到 Controller | 🔴 P0 | 47-55 |
| **Controller→Mapper** | `CommissionController` | 5 Mapper@Autowired | 业务渗透到 Controller | 🟠 P1 | 33-41 |
| **Controller→Mapper** | `PackageController` | 强转 BaseMapper | 业务渗透到 Controller | 🟠 P1 | 225-226 |
| **营销规则** | `WalletController.promotionRules` | 硬编码 List | 配置应入表 | 🟠 P2 | 351-356 |
| **次卡选择** | `pos/checkout.vue` | `memberCardId = 1` | 硬编码写死 | 🟠 P2 | 558 |
| **订单统计** | `BookingController.stats` | Controller 内嵌 SQL | 应下沉到 Service | 🟠 P1 | 252-258 |
| **前端 Mock** | `pos/stats.vue` | `generateMockOrders` | 受控演示数据 | 🟡 P3 | 408-... |

---

## 9. 整改优先级与路线图

### 9.1 P0 — 必须立即修复（资金 / 合规相关）

| # | 任务 | 估时 | 负责人 |
|---|------|------|--------|
| 1 | DCEP 真实银行通道对接（建行/工行/农行选一） | 2 周 | 后端架构师 |
| 2 | `WalletService` 注入业务方法（从 Controller 抽取） | 3 天 | 后端 |
| 3 | `MemberService` 注入业务方法 | 2 天 | 后端 |
| 4 | 微信支付 / 支付宝 / 银联 三方 SDK 接入 | 2 周 | 后端 |
| 5 | PosController 抽 Service | 5 天 | 后端 |
| 6 | StockController 抽 Service | 5 天 | 后端 |

### 9.2 P1 — 重要功能补齐

| # | 任务 | 估时 |
|---|------|------|
| 1 | AI 视觉真实模型接入（阿里云视觉智能 / 腾讯云） | 2 周 |
| 2 | BookingService 业务化（checkConflict / state machine） | 3 天 |
| 3 | PackageRedemptionService 业务化（redeem / split） | 2 天 |
| 4 | ReportController 抽 Service | 3 天 |
| 5 | CommissionController 抽 Service | 3 天 |

### 9.3 P2 — 用户体验提升

| # | 任务 | 估时 |
|---|------|------|
| 1 | SMS 真实通道（阿里云 / 腾讯云） | 3 天 |
| 2 | MiniProgram 真实通道（WxJava） | 2 天 |
| 3 | InApp 站内信真实落库 | 1 天 |
| 4 | 营销规则配置化（mkt_promotion_rule 表） | 2 天 |
| 5 | 次卡选择器（前端） | 1 天 |

### 9.4 P3 — 优化项

| # | 任务 | 估时 |
|---|------|------|
| 1 | pos/stats.vue 演示数据拆分公共模块 | 半天 |
| 2 | 演示模式开关 UI | 半天 |

---

## 10. 总结

### 10.1 真实度评分

| 模块 | 真实度 | 备注 |
|------|--------|------|
| 会员 CRUD | 70% | Service 是空壳，但 Controller 业务完整 |
| 预约管理 | 65% | Service 空壳，但状态机/冲突检测/导出完整 |
| 收银 / POS | 60% | 现金/储值/次卡真实；微信/支付宝/银联/数字人民币全 Mock |
| 钱包 | 75% | Service 空壳，但乐观锁 + 幂等 + 流水齐 |
| 套餐 | 75% | 购买/核销/退款/转赠/拆分完整；Service 部分空壳 |
| 库存 | 65% | CRUD + 入库/出库/盘点完整；7 个 Mapper 全注入 Controller |
| 提成 | 70% | 规则/记录/结算完整；但 Controller 直连 Mapper |
| 报表 | 60% | KPI/同比/导出/月报完整；但 1700+ 行大 Controller |
| 营销 | 75% | 优惠券/活动/积分/自动化齐 |
| DCEP | 10% | **完全 Mock，生产禁用** |
| AI 视觉 | 15% | Random 伪分析 |
| 短信 | 20% | 仅 log |
| 小程序 | 20% | 仅 log |
| 站内信 | 20% | 仅 log |

### 10.2 总体结论

🟠 **美业 SaaS v2.0 — PARTIAL READY**

- ✅ **业务主流程可用**：会员、预约、收银、库存、报表、提成在脱机环境下功能完整
- 🔴 **资金通道未真实**：DCEP / 微信 / 支付宝 / 银联生产前必须对接
- 🟠 **AI 与消息通道 Mock**：上线前可作为"功能占位"，不能作为"AI 能力"宣传
- 🟠 **架构不规范**：Service 大量空壳，业务逻辑集中在 Controller，后期维护成本高

**建议**: 优先完成 P0 整改后再启动客户试点；P1/P2 边迭代边补齐。
