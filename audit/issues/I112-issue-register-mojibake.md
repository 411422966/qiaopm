# I112 — issue-register.md 中文永久损坏

## 现象
- `audit/issues/issue-register.md` 中文字符全部变成 `????`
- 显式 UTF-8 读取仍为 `????`，说明字节已被替换为 0x3F
- 英文/数字/符号保留，仅中文丢失

## 影响
- 154 KB 权威登记表中文语义全部损失
- 后续审计无法引用其中的中文描述

## 可能根因
- 某次 UTF-8 → GBK → UTF-8 的不可逆编码转换
- 与 I096（4 个 Java 源文件 UTF-8 字节损坏）可能同源

## 处置
- [ ] 从 Git 历史找完好版本
- [ ] 从远程仓库/备份找完好版本
- [ ] 若全部损坏，评估从 issue 单文件重建登记表
- [ ] 排查触发编码损坏的脚本/流程

## 状态
待办

## 追加（2026-10-16）
- git log --all 只有 a414f95 一条，HEAD~1 版本亦为 ????，历史不可恢复
- 0x3F 计数 17400 / 154457 字节，损坏约 11%
- 恢复路径改为：从 I001~I108 单文件重建，或从 PROJECT_REALITY_AUDIT.md 间接补

## 扫描结论（2026-10-16）
- 全仓库 audit/ + docs/ 下 .md 扫描，仅 2 个文件有 0x3F 异常
- issue-register.md：17400 / 154457 = 11.27%（大面积）
- baseline/preflight.md：409 / 21575 = 1.90%（局部）
- 结论：非系统性损坏，为重建前（a414f95 之前）的遗留污染
- 相关：backend/compile_impls.ps1 使用 -Encoding ascii，同类风险（另开 I113）

## preflight.md 复核（2026-10-16）
- .NET UTF8.GetString 读取正常 → 文件本体未损坏
- 409 个 0x3F 为文本内正常 ASCII 问号，非损坏特征
- 结论：preflight.md 移出 I112 范围

## preflight.md 复核（2026-10-16）
- .NET UTF8.GetString 读取仍乱码 → 文件确认损坏
- 损坏模式与 issue-register 不同（前者 mojibake 可逆，后者 0x3F 不可逆）
- 结论：I112 范围扩大为 2 个文件

## 更正（2026-10-16）
- 上一节「范围扩大至 preflight.md」为误判，作废
- 原因：.NET ReadAllBytes 不认 PowerShell 的 cd，相对路径读取失败，$b 保留了旧值
- 用绝对路径重读 preflight.md：UTF-8 解码正常，非损坏
- 最终结论：I112 范围仅 issue-register.md 单文件
