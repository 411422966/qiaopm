package com.meiyetec.crm;

import com.meiyetec.entity.CrmIntegration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * EXT-15：Mock CRM 适配器（默认 fallback）
 *
 * <p>本 Adapter 始终注册（无条件 {@code @Component}），
 * 按 {@code platform=MOCK} 路由时使用，供本地开发 + 联调，
 * 避免无真实凭据导致排程阻塞。
 *
 * <p>历史：曾标注 {@code @ConditionalOnMissingBean(name="salesforceCrmAdapter")}，
 * 但 SalesforceCrmAdapter 无条件 {@code @Component}，导致 Mock 永不注册、
 * {@code CrmAdapterRouter.getAdapter("MOCK")} 返回 null → NPE（详见 I012）。
 * 该注解已于 2026-09-23 移除（commit {@code a6e2d7d}）。
 */
@Slf4j
@Component
public class MockCrmAdapter implements CrmAdapter {

    private static final List<ExternalContact> SAMPLE = List.of(
            contact("003AX000001", "张", "三", "zhang3@example.com", "13800000001", "Manager"),
            contact("003AX000002", "李", "四", "li4@example.com",    "13800000002", "Staff"),
            contact("003AX000003", "王", "五", "wang5@example.com",  "13800000003", null)
    );

    /**
     * 模拟"业务系统侧持续生成"的新数据：
     * <ul>
     *   <li>每次调用 {@link #fetchContactsIncremental} 时，往队列头部加 1 条新样本（保证差集非空）</li>
     *   <li>整个进程生命周期内单调递增；可被 cursor.knownExternalIds 过滤</li>
     * </ul>
     */
    private final java.util.Deque<ExternalContact> rollingBuffer =
            new java.util.concurrent.ConcurrentLinkedDeque<>();

    @Override
    public String platformCode() {
        return "MOCK";
    }

    @Override
    public AuthResult authenticate(CrmIntegration config) {
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        AuthResult r = new AuthResult();
        r.success = true;
        r.accessToken = "MOCK-TOKEN-" + UUID.randomUUID();
        r.refreshToken = "MOCK-REFRESH-" + UUID.randomUUID();
        r.expiresIn = 7200L;
        r.instanceUrl = "https://mock-crm.local";
        return r;
    }

    @Override
    public List<ExternalContact> fetchContactsIncremental(CrmIntegration config, SyncCursor cursor) {
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}

        // 1. 计算"已知 ID 集合"（第一次为空 → 全量；之后过滤）
        java.util.Set<String> known = cursor == null || cursor.knownExternalIds == null
                ? java.util.Collections.emptySet()
                : cursor.knownExternalIds;

        boolean firstSync = cursor == null || cursor.isEmpty();

        // 2. 每次调用额外生成 1 条"新数据"（保证第二次起有差集可返回）
        long newSeed = System.nanoTime();
        rollingBuffer.addFirst(contact(
                "MOCK-" + Long.toHexString(newSeed),
                "新", String.valueOf(rollingBuffer.size() + 100),
                "new" + (rollingBuffer.size() + 100) + "@example.com",
                "1390000" + String.format("%04d", rollingBuffer.size() % 10000),
                "NewJoiner"
        ));
        if (rollingBuffer.size() > 50) rollingBuffer.pollLast();

        // 3. 合并"固定 3 条样本 + 滚动缓冲"，过滤已知 externalId
        java.util.List<ExternalContact> pool = new java.util.ArrayList<>(SAMPLE);
        pool.addAll(rollingBuffer);

        java.util.List<ExternalContact> out = new java.util.ArrayList<>();
        boolean isFirst = firstSync || known.isEmpty();
        for (ExternalContact c : pool) {
            // 首次同步：保留全部；后续同步：差集
            if (isFirst || !known.contains(c.externalId)) {
                out.add(c);
            }
        }

        log.info("[CRM/MOCK] fetchContacts: total={} new={} knownIds={} firstSync={}",
                pool.size(), out.size(), known.size(), isFirst);
        return out;
    }

    @Override
    public PushResult upsertContact(CrmIntegration config, Map<String, Object> contact) {
        try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        String externalId = contact.get("externalId") == null
                ? "MOCK-" + UUID.randomUUID().toString().substring(0, 8)
                : contact.get("externalId").toString();
        return PushResult.ok(externalId, "v1");
    }

    @Override
    public HealthStatus healthCheck(CrmIntegration config) {
        HealthStatus s = new HealthStatus();
        s.healthy = true;
        s.stage = "MOCK_OK";
        s.message = "Mock CRM 链路 OK";
        s.apiBaseUrl = "https://mock-crm.local";
        s.elapsedMs = 50L;
        return s;
    }

    private static ExternalContact contact(String id, String firstName, String lastName,
                                            String email, String phone, String title) {
        ExternalContact c = new ExternalContact();
        c.externalId = id;
        c.firstName = firstName;
        c.lastName = lastName;
        c.email = email;
        c.phone = phone;
        c.title = title;
        c.updatedAtMs = System.currentTimeMillis();
        return c;
    }
}
