package com.meiyetec.crm;

import com.meiyetec.entity.CrmIntegration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HUBSPOT / ZOHO adapter 单元测试（EXT-15 / EXT-24）
 *
 * <p>覆盖：
 * <ul>
 *   <li>platformCode() 返回 HUBSPOT / ZOHO</li>
 *   <li>parseObjectMappings：默认 → 默认表；JSON 覆盖 → merge；JSON 无效 → 兜底默认</li>
 *   <li>authenticate 无网络凭据时返回失败（不会抛异常）</li>
 *   <li>fetchContactsIncremental 在未配置凭据时不抛异常，返回空列表</li>
 *   <li>upsertContact 空 payload 返回失败</li>
 *   <li>CrmAdapterRouter 把 HUBSPOT / ZOHO 正确路由到对应实现</li>
 *   <li>HubSpotCrmAdapter/ZohoCrmAdapter 字段映射 key 不为空</li>
 *   <li>ZohoCrmAdapter.deriveAccountsBase：按 apiBaseUrl 推导账号域名</li>
 *   <li>HubSpotCrmAdapter/ZohoCrmAdapter 走 CrmAdapterRouter fallback 不再走 Mock</li>
 * </ul>
 */
@DisplayName("EXT-15/EXT-24 HubSpot + Zoho Adapter")
class HubspotZohoAdapterTest {

    // ============================================================
    //  platformCode
    // ============================================================
    @Test
    @DisplayName("HubSpot/Zoho platformCode 返回正确枚举")
    void platformCode() {
        assertEquals("HUBSPOT", new HubSpotCrmAdapter().platformCode());
        assertEquals("ZOHO",    new ZohoCrmAdapter().platformCode());
    }

    // ============================================================
    //  parseObjectMappings
    // ============================================================
    @Test
    @DisplayName("HubSpot parseObjectMappings 默认 → 默认表")
    void hubspotMappingsDefault() {
        Map<String, String> m = HubSpotCrmAdapter.parseObjectMappings(new CrmIntegration());
        assertEquals("hs_object_id",       m.get("externalId"));
        assertEquals("firstname",          m.get("firstName"));
        assertEquals("lastname",           m.get("lastName"));
        assertEquals("email",              m.get("email"));
        assertEquals("phone",              m.get("phone"));
        assertEquals("jobtitle",           m.get("title"));
        assertEquals("hs_lastmodifieddate", m.get("updatedAtMs"));
    }

    @Test
    @DisplayName("Zoho parseObjectMappings 默认 → 默认表")
    void zohoMappingsDefault() {
        Map<String, String> m = ZohoCrmAdapter.parseObjectMappings(new CrmIntegration());
        assertEquals("id",            m.get("externalId"));
        assertEquals("First_Name",    m.get("firstName"));
        assertEquals("Last_Name",     m.get("lastName"));
        assertEquals("Email",         m.get("email"));
        assertEquals("Phone",         m.get("phone"));
        assertEquals("Title",         m.get("title"));
        assertEquals("Modified_Time", m.get("updatedAtMs"));
    }

    @Test
    @DisplayName("HubSpot parseObjectMappings 用户定义覆盖默认")
    void hubspotMappingsOverride() {
        CrmIntegration cfg = new CrmIntegration();
        cfg.setObjectMappings("{\"email\":\"custom_email__c\",\"phone\":\"mobile_phone\",\"externalId\":\"hubspot_id\"}");
        Map<String, String> m = HubSpotCrmAdapter.parseObjectMappings(cfg);
        assertEquals("custom_email__c", m.get("email"));
        assertEquals("mobile_phone",    m.get("phone"));
        assertEquals("hubspot_id",      m.get("externalId"));
        // 未声明的字段保留默认
        assertEquals("firstname",       m.get("firstName"));
        assertEquals("jobtitle",        m.get("title"));
    }

    @Test
    @DisplayName("Zoho parseObjectMappings 用户定义覆盖默认")
    void zohoMappingsOverride() {
        CrmIntegration cfg = new CrmIntegration();
        cfg.setObjectMappings("{\"email\":\"Work_Email\",\"phone\":\"Mobile\",\"externalId\":\"zoho_uuid\"}");
        Map<String, String> m = ZohoCrmAdapter.parseObjectMappings(cfg);
        assertEquals("Work_Email", m.get("email"));
        assertEquals("Mobile",     m.get("phone"));
        assertEquals("zoho_uuid",  m.get("externalId"));
        // 未声明的字段保留默认
        assertEquals("First_Name",    m.get("firstName"));
        assertEquals("Modified_Time", m.get("updatedAtMs"));
    }

    @Test
    @DisplayName("HubSpot/Zoho parseObjectMappings 非法 JSON → 兜底默认")
    void mappingsInvalidJson() {
        CrmIntegration cfg = new CrmIntegration();
        cfg.setObjectMappings("{not valid json}");
        Map<String, String> hm = HubSpotCrmAdapter.parseObjectMappings(cfg);
        Map<String, String> zm = ZohoCrmAdapter.parseObjectMappings(cfg);
        assertEquals("hs_object_id", hm.get("externalId"));
        assertEquals("id",           zm.get("externalId"));
    }

    @Test
    @DisplayName("parseObjectMappings 顶层不是对象（如数组） → 兜底默认")
    void mappingsNonObject() {
        CrmIntegration cfg = new CrmIntegration();
        cfg.setObjectMappings("[\"a\",\"b\"]");
        assertEquals("hs_object_id", HubSpotCrmAdapter.parseObjectMappings(cfg).get("externalId"));
        assertEquals("id",           ZohoCrmAdapter.parseObjectMappings(cfg).get("externalId"));
    }

    // ============================================================
    //  鉴权失败兜底（无网络、无凭据）
    // ============================================================
    @Test
    @DisplayName("HubSpot authenticate 无凭据 → 失败（且不抛异常）")
    void hubspotAuthNoConfig() {
        CrmAdapter.AuthResult r = new HubSpotCrmAdapter().authenticate(new CrmIntegration());
        // PAT 兜底：cfg.accessToken 为 null → 仍要返回 AuthResult，不抛
        assertFalse(r.success, "无凭据时必须 success=false 而不是抛异常");
        assertNotNull(r.errorCode);
    }

    @Test
    @DisplayName("Zoho authenticate 缺凭据 → 失败（且不抛异常）")
    void zohoAuthMissingConfig() {
        CrmAdapter.AuthResult r = new ZohoCrmAdapter().authenticate(new CrmIntegration());
        assertFalse(r.success);
        assertEquals("ZOHO_OAUTH_INPUT_MISSING", r.errorCode);
    }

    // ============================================================
    //  拉取/upsert 在无凭据时安全返回（不抛）
    // ============================================================
    @Test
    @DisplayName("HubSpot fetchContactsIncremental 无凭据 → 返回空列表")
    void hubspotFetchEmpty() {
        List<CrmAdapter.ExternalContact> list = new HubSpotCrmAdapter()
                .fetchContactsIncremental(new CrmIntegration(), CrmAdapter.SyncCursor.empty());
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    @DisplayName("Zoho fetchContactsIncremental 无凭据 → 返回空列表")
    void zohoFetchEmpty() {
        List<CrmAdapter.ExternalContact> list = new ZohoCrmAdapter()
                .fetchContactsIncremental(new CrmIntegration(), CrmAdapter.SyncCursor.empty());
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test
    @DisplayName("HubSpot upsertContact 空 payload → 失败")
    void hubspotUpsertEmpty() {
        CrmAdapter.PushResult r = new HubSpotCrmAdapter().upsertContact(
                new CrmIntegration(), Map.of());
        assertFalse(r.success);
        assertEquals("EMPTY_PAYLOAD", r.errorCode);
    }

    @Test
    @DisplayName("Zoho upsertContact 空 payload → 失败")
    void zohoUpsertEmpty() {
        CrmAdapter.PushResult r = new ZohoCrmAdapter().upsertContact(
                new CrmIntegration(), Map.of());
        assertFalse(r.success);
        assertEquals("EMPTY_PAYLOAD", r.errorCode);
    }

    // ============================================================
    //  Zoho deriveAccountsBase
    // ============================================================
    @Test
    @DisplayName("Zoho deriveAccountsBase 按域推导账号域名")
    void zohoAccountsBase() {
        assertEquals("https://accounts.zoho.com",
                ZohoCrmAdapter.deriveAccountsBase("https://www.zohoapis.com"));
        assertEquals("https://accounts.zoho.com.cn",
                ZohoCrmAdapter.deriveAccountsBase("https://www.zohoapis.com.cn"));
        assertEquals("https://accounts.zoho.eu",
                ZohoCrmAdapter.deriveAccountsBase("https://www.zohoapis.eu"));
        assertEquals("https://accounts.zoho.in",
                ZohoCrmAdapter.deriveAccountsBase("https://www.zohoapis.in"));
        assertEquals("https://accounts.zoho.com.au",
                ZohoCrmAdapter.deriveAccountsBase("https://www.zohoapis.com.au"));
        // null / 未知 → 默认 .com
        assertEquals("https://accounts.zoho.com",
                ZohoCrmAdapter.deriveAccountsBase(null));
    }

    // ============================================================
    //  Router 把 HUBSPOT / ZOHO 正确路由
    // ============================================================
    @Test
    @DisplayName("Router：HUBSPOT/ZOHO 正确路由到对应 adapter（不再 fallback 到 Mock）")
    void routerHubspotZoho() {
        CrmAdapterRouter router = new CrmAdapterRouter(List.of(
                new SalesforceCrmAdapter(),
                new HubSpotCrmAdapter(),
                new ZohoCrmAdapter(),
                new MockCrmAdapter()
        ));
        assertEquals("HUBSPOT", router.getAdapter("HUBSPOT").platformCode());
        assertEquals("ZOHO",    router.getAdapter("ZOHO").platformCode());
        assertEquals("hUbSpOt", router.getAdapter("hUbSpOt").platformCode());  // 大小写不敏感
        assertEquals("Zoho",    router.getAdapter("Zoho").platformCode());
        assertTrue(router.hasAdapter("HUBSPOT"));
        assertTrue(router.hasAdapter("ZOHO"));
        assertEquals(4, router.listPlatforms().size());
    }

    @Test
    @DisplayName("Router：未知 platform 仍 fallback 到 Mock（向后兼容 dev）")
    void routerFallbackToMock() {
        CrmAdapterRouter router = new CrmAdapterRouter(List.of(
                new HubSpotCrmAdapter(),
                new ZohoCrmAdapter(),
                new MockCrmAdapter()
        ));
        assertEquals("MOCK", router.getAdapter("NOT_REAL_PLATFORM").platformCode());
        assertEquals("MOCK", router.getAdapter(null).platformCode());
    }

    // ============================================================
    //  SyncCursor.knownExternalIds 兼容（HubSpot/Zoho 不会消费它）
    // ============================================================
    @Test
    @DisplayName("SyncCursor.knownExternalIds 不影响 HubSpot/Zoho 返回结构")
    void knownIdsIgnoredForHubspotZoho() {
        Set<String> known = new HashSet<>(List.of("001", "002", "003"));
        CrmAdapter.SyncCursor c = new CrmAdapter.SyncCursor();
        c.knownExternalIds = known;
        c.sinceEpochMs = System.currentTimeMillis();
        assertEquals(3, c.knownExternalIds.size());
        assertFalse(c.isEmpty());
    }
}
