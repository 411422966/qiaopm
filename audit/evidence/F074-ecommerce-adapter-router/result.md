# F074 EcommerceAdapterRouter — Phase 5B 实证

> **创建时间**：2026-09-14 01:10 (UTC+8)
> **Phase**：5B — Runtime Verification

---

## 1. 实证结论

**状态**：**MOCK-INTENTIONAL**（与 CrmAdapterRouter 同源设计，Mock fallback）

## 2. 静态证据

`backend/src/main/java/com/meiyetec/ecommerce/EcommerceAdapterRouter.java` 全文 46 行：

| 维度 | 内容 |
|---|---|
| **注入方式** | `@Autowired` List&lt;EcommerceAdapter&gt; 自动收集所有实现 |
| **路由表** | HashMap&lt;platformCode, EcommerceAdapter&gt; |
| **fallback** | miss platform → `adapters.get("MOCK")`（L33）|
| **null 安全** | `platform == null` → fall through 到 MOCK |
| **大小写** | `platform.toUpperCase()` 统一 |
| **辅助方法** | `hasAdapter(platform)` + `listPlatforms()` |

## 3. 运行时验证

```bash
GET /ecommerce/platform/list → 期望 listPlatforms() 返回 ["MOCK", "YOUZAN"]
```

实际：I029 PARTIAL-MISSING 阻断（GET 500），但 router 本身代码层 PASS。

## 4. 与 CrmAdapterRouter 同源

| 维度 | CrmAdapterRouter | EcommerceAdapterRouter |
|---|---|---|
| 设计模式 | HashMap + fallback | 同 |
| 默认 fallback | MOCK | MOCK |
| 关键方法 | getAdapter / hasAdapter | 同 |
| listPlatforms | 不存在 | 新增（同源便利方法） |

## 5. Issue 状态

无新增 Issue。F074 设计合规，与 Batch 1.1 CrmAdapterRouter 一致。
