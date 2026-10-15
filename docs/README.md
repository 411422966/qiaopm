# docs/ — 项目文档目录

> 本 README 是 v3 验收体系下的 docs/ 顶层指引，**与** `README-文档体系使用指南.md`（历史文档体系指南）**并存**。
> 关系：本文件只补充 v3 新增的 SoT 指引；历史文档使用请看 `README-文档体系使用指南.md`。

---

## 1. v3 新增指引：EXT-* 单一来源（SoT）

EXT-19 / EXT-22 / EXT-23 / EXT-24 的**唯一来源**（Single Source of Truth）在 Cursor rules：

```text
.cursor/rules/
├── ext-19-operational-notes.mdc       ← EXT-19（多语言预约确认）
├── ext-22-google-maps-and-ecommerce.mdc ← EXT-22（Place Details + 有赞签名）
├── ext-23-plugin-runtime.mdc          ← EXT-23（pf4j plugin runtime）
└── ext-24-crm-integration.mdc         ← EXT-24（Salesforce CRM 同步）
```

### 为什么不复制到 `docs/EXT-*.md`

- `docs/` 与 `.cursor/rules/` 双份维护 → 易漂移（Cursor 改 .mdc 时漏改 docs）
- `.mdc` 是 Cursor AI 直接消费的格式；`.md` 给人类读
- 用户在 v3 Q3 中明确选择：**以 `.cursor/rules/ext-*.mdc` 为唯一来源**

### 团队查阅建议

- **Cursor AI 引用**：直接读 `.mdc`（已被 Cursor 加载到上下文）
- **团队成员查阅**：用 IDE 打开 `.cursor/rules/ext-*.mdc`（中文 markdown，语法兼容）
- **外部审计查阅**：可临时导出 PDF/HTML，但**不存** `docs/EXT-*.md` 副本

### 何时需要新增 EXT-*.mdc

按 v3 任务清单 §A5：
- 出现新扩展（如 EXT-25）→ 在 `.cursor/rules/ext-XX-*.mdc` 写一份
- 不写 `docs/EXT-XX.md`
- 在本 README 的清单里追加一行

---

## 2. 历史文档

详见：`README-文档体系使用指南.md`（中文名）

涵盖：
- PRD / TECH / METHOD / MODULE 等文档体系
- EXEC-R2.0 / EXEC-R2.1 / EXEC-R3.0 批次执行清单
- RELEASE-GATE / VERIFICATION / REFUND 等专项文档

---

## 3. 历史审计沉淀

历史审计报告**不在本目录顶层**，统一在 `docs/audit/`：

```text
docs/audit/
├── BEAUTY_SAAS/          ← 28+ 个历史矩阵报告
├── *.sql / *.txt / *.md  ← 历史修复总结 + raw 扫描
└── ...
```

详见：`audit/README.md` §2（说明为何本目录与 docs/audit/ 共存）

---

**本文件为 v3 第一批（A1.3）落地，2026-09-12。**
