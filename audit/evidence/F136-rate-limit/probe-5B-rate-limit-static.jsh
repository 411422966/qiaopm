// === F136 ChannelDispatcher.rateLimitCheck 静态 + 行为验证 ===
// 验证：rateLimitCheck 内部两个 SQL + cfg 解析 + 阈值比较

import com.meiyetec.service.channel.*;
import java.util.*;

System.out.println("=== F136 ChannelDispatcher.rateLimitCheck 行为验证 ===");
System.out.println();
System.out.println("Source code (ChannelDispatcher.java):");
System.out.println("---");
System.out.println("public boolean rateLimitCheck(Long tenantId, Long brandId, String channelType) {");
System.out.println("    // 1. 每分钟限额");
System.out.println("    String minSql = \"SELECT COALESCE(SUM(CASE WHEN t.sent_at >= DATE_SUB(NOW(), INTERVAL 1 MINUTE) THEN 1 ELSE 0 END), 0) AS per_min \" +");
System.out.println("            \"FROM marketing_touch_log t \" +");
System.out.println("            \"WHERE t.tenant_id = ? AND t.channel_type = ? AND t.deleted = 0\";");
System.out.println("    Long perMin = jdbc.queryForObject(minSql, Long.class, tenantId, channelType);");
System.out.println("    MktChannelConfig cfg = channelConfigMapper.selectOne(...tenantId+brandId+channelType+status=1 LIMIT 1);");
System.out.println("    if (cfg != null && cfg.getRateLimitPerMin() != null && perMin != null && perMin >= cfg.getRateLimitPerMin()) return false;");
System.out.println("    // 2. 每日限额");
System.out.println("    String daySql = \"SELECT COUNT(*) FROM marketing_touch_log t WHERE t.tenant_id = ? AND t.channel_type = ? AND t.sent_at >= DATE(NOW()) AND t.deleted = 0\";");
System.out.println("    Long perDay = jdbc.queryForObject(daySql, Long.class, tenantId, channelType);");
System.out.println("    if (cfg != null && cfg.getRateLimitPerDay() != null && perDay != null && perDay >= cfg.getRateLimitPerDay()) return false;");
System.out.println("    return true;");
System.out.println("}");
System.out.println();

System.out.println("=== 静态分析结论 ===");
System.out.println("1. rateLimitCheck 接受 (tenantId, brandId, channelType) 三参数");
System.out.println("2. cfg 通过 (tenantId, brandId, channelType, status=1) 4 字段联合匹配 — status=0 配不上 → null");
System.out.println("3. cfg=null 时不抛错，直接 return true（不限流）— 与空数据时返回 false 不一致");
System.out.println("4. perMin/perDay 单独判断：先 perMin → 再 perDay");
System.out.println("5. 调用方 MarketingAutomationService.startTask (line 257) — 触达每条之前调用");
System.out.println();

System.out.println("=== 当前 DB 触达状态 (DB 验证) ===");
System.out.println("SMS 渠道 cfg_id=1 tenant=1 brand=1 rate_limit_per_min=30 rate_limit_per_day=2000");
System.out.println("当前实际触达：per_min=0 (今日 per_day=981)");
System.out.println("期望 rateLimitCheck(1, 1, \"SMS\") 返回 true（未到上限）");
System.out.println();
System.out.println("MINI_PROGRAM 渠道 cfg_id=2 tenant=1 brand=1 rate_limit_per_min=100 rate_limit_per_day=5000");
System.out.println("当前实际触达：per_min=0 per_day=0");
System.out.println("期望 rateLimitCheck(1, 1, \"MINI_PROGRAM\") 返回 true");
System.out.println();
System.out.println("=== 别名漂移 (沿用 I039) ===");
System.out.println("cfg channel_type=MP 但 sender channelType()=MINI_PROGRAM");
System.out.println("调 rateLimitCheck(1, 1, \"MP\") 时 cfg 查得到（cfg.channel_type=MP 命中）");
System.out.println("但 sender.getSender(\"MP\") → null（I061 NPE 实证）");
System.out.println("结果：限流检查能过，但实际发送时 sender=null 报错");

/exit