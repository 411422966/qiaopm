# I121 — GrayReleaseService 命名与职责不符（Controller 伪装成 Service）

## 现象
`GrayReleaseService.java`：
- 类名 `XxxService`，后缀为 Service
- 实际注解：`@RestController` + `@RequestMapping("/gray")`
- 暴露 12 个 REST 端点（/gray/flag/* /gray/experiment/* /gray/ab-test/*）

## 影响
- 静态扫描时容易被误判为 Service（本次 I120 排查就踩过）
- 违反 Spring 分层约定（Controller 层与 Service 层混淆）
- 名字叫 Service 但直接返回 `Result<...>`，不像标准 Service 的分层抽象

## 处置
- [ ] 重命名为 `GrayReleaseController`，移至 `controller/` 包
- [ ] 或拆分为 Controller + Service 两层
- [ ] 检查是否还有其他同类命名（扫 *Service.java 里的 @RestController）

## 关联
- 来源：I120 排查中的附带发现

## 状态
待办

## 修复完成（2026-10-21）
- GrayReleaseService → GrayReleaseController，移至 com.meiyetec.controller 包
- stableHash 静态方法抽到 com.meiyetec.util.GrayReleaseHasher（纯 SHA-256 工具，无实例依赖）
- 保留 @ConditionalOnProperty（feature.legacy-missing-ddl.enabled，默认 false）
- 引用更新：
  - FeatureFlagEvaluator.java javadoc {@link} 改为 com.meiyetec.controller.GrayReleaseController
  - P26CloudNativeStressGraySecurityTest.java 调用 GrayReleaseHasher.stableHash + 加 import
- 主代码编译 EXIT=0
- 测试编译：P26CloudNativeStressGraySecurityTest 无错误（PluginRuntimeTest 历史错误 I052 无关）
- 踩坑记录：PowerShell 5.1 `Out-File -Encoding utf8` 会写 BOM，Java 编译报 "非法字符 '\ufeff'"，需用 `[System.IO.File]::WriteAllText` + `New-Object System.Text.UTF8Encoding $false`

## 状态
已修复
