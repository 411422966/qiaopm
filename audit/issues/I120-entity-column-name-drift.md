# I120 — Entity 字段与 DB 字段命名漂移（MyBatis-Plus 驼峰转换不一致）

## 现象
MyBatis-Plus 默认将 Java 字段驼峰转下划线时，**连续大写字母会拆成单字母 + 下划线**：
- `controlABRatio` → `control_a_b_ratio`（DB 实际是 `control_ab_ratio`）
- `variantCDRatio` → `variant_c_d_ratio`（DB 实际是 `variant_cd_ratio`）

## 已发现
| Entity | 字段 | 错误转换 | DB 实际 | 状态 |
|---|---|---|---|---|
| PricingExperiment | controlABRatio | control_a_b_ratio | control_ab_ratio | 已修 @TableField |
| PricingExperiment | variantCDRatio | variant_c_d_ratio | variant_cd_ratio | 已修 @TableField |

## 潜在风险
所有含连续大写字母的 Entity 字段都可能踩坑（xxxID / xxxURL / xxxAB / xxxCD 等），需全量扫描 444 个 Entity。

## 处置
- [ ] 扫描所有 Entity，找出「字段名含连续大写且无 @TableField」的情况
- [ ] 逐个加 @TableField
- [ ] 加启动时 Entity ↔ DB schema 校验测试

## 关联
- 来源：I118 排查中的附带发现
- 前置：I119

## 状态
待办
