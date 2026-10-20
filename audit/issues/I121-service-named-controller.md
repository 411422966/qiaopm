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
