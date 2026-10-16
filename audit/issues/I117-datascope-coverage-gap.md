# I117 — DataScopeInterceptor 覆盖路径不全（P0）

## 现象
- WebConfig.addInterceptors 中：
  - JwtInterceptor 覆盖 /**（全部路径）
  - DataScopeInterceptor 仅覆盖 15 个前缀
- 未覆盖路径直接进入 Controller，无跨租户/跨门店校验

## 影响
- 未覆盖路径下，任何登录用户可传任意 tenant_id / store_id 访问他租户/他门店数据
- 高风险未覆盖域：
  - 支付：/domestic/** /overseas-payment/** /payment/** /bnpl/** /huabei/**
  - 数字货币：/dcep/**
  - 电商：/ecommerce/**
  - 插件：/plugin/** /marketplace/**
  - 其他：/kiosk/** /invite/** /customer-care/** /price-list/**
    /price-sensitivity/** /review-analysis/** /dianping/** /lbs/**

## 根因
- DataScopeInterceptor 采用「显式白名单路径」策略（addPathPatterns 列 15 个）
- 而非「全路径 + 黑名单排除」（如 JwtInterceptor 的 excludePathPatterns）
- 新增 Controller 默认不受数据范围保护

## 处置建议
方案 A（推荐）：改为全路径 + 排除清单
  registry.addInterceptor(dataScopeInterceptor)
      .addPathPatterns("/**")
      .excludePathPatterns(
          "/auth/**", "/actuator/**", "/static/**", "/error",
          "/webhook/**", "/payment/webhook/**"
      );

方案 B（保守）：保留白名单策略，但补齐所有业务路径前缀

## 关联
- 来源：P0 核验清单 §1
- 权限域：I115（@RequirePermission 覆盖不足，与本 issue 叠加放大风险）
- 相关代码：security/DataScopeInterceptor.java + config/WebConfig.java

## 状态
待办
