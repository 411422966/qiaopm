package com.meiyetec.crm;

import com.meiyetec.entity.CrmIntegration;

import java.util.List;
import java.util.Map;

/**
 * EXT-15：CRM 适配器抽象（与 OverseasPaymentAdapter 同源设计）
 *
 * <p>屏蔽 Salesforce / Hubspot / Mock 实现差异。运行时由 {@link CrmAdapterRouter} 按 platform 选实现。
 *
 * <p>核心职责：
 * <ol>
 *   <li>OAuth2 / API Key 鉴权 → 返回 access_token</li>
 *   <li>Contact / Lead 等对象双向同步</li>
 *   <li>增量同步：基于 syncToken 或 lastModifiedSince 游标</li>
 *   <li>健康检查 + 同步统计</li>
 * </ol>
 */
public interface CrmAdapter {

    /**
     * 平台代码：SALESFORCE / HUBSPOT / MOCK
     */
    String platformCode();

    /**
     * OAuth2 / API Key 鉴权，返回 access_token + 过期时间（秒）
     */
    AuthResult authenticate(CrmIntegration config);

    /**
     * 拉取增量 Contact 列表（基于 lastSyncAt / syncToken 游标）
     *
     * @param config 集成配置（含 lastSyncAt / syncToken）
     * @return 平台侧的 Contact 列表
     */
    List<ExternalContact> fetchContactsIncremental(CrmIntegration config, SyncCursor cursor);

    /**
     * 推送本地 Contact 到平台（upsert by externalId）
     */
    PushResult upsertContact(CrmIntegration config, Map<String, Object> contact);

    /**
     * 健康检查
     */
    HealthStatus healthCheck(CrmIntegration config);

    // ==================== DTO ====================

    /**
     * 鉴权结果
     */
    class AuthResult {
        public boolean success;
        public String accessToken;
        public String refreshToken;
        /** 过期时间戳（秒） */
        public Long expiresIn;
        /** 实例 URL（Salesforce 等动态端点） */
        public String instanceUrl;
        public String errorCode;
        public String errorMessage;

        public static AuthResult ok(String token, String refresh, Long expiresIn, String instanceUrl) {
            AuthResult r = new AuthResult();
            r.success = true;
            r.accessToken = token;
            r.refreshToken = refresh;
            r.expiresIn = expiresIn;
            r.instanceUrl = instanceUrl;
            return r;
        }

        public static AuthResult fail(String code, String msg) {
            AuthResult r = new AuthResult();
            r.success = false;
            r.errorCode = code;
            r.errorMessage = msg;
            return r;
        }
    }

    /**
     * 增量同步游标
     */
    class SyncCursor {
        /** 上次同步时间戳（毫秒） */
        public Long sinceEpochMs;
        /** 平台 sync token（Salesforce 用；可空表示首次全量） */
        public String syncToken;
        /**
         * 已存在的 externalId 集合（EXT-24 +：增量去重 — 适配器返回差集）。
         * <p>Mock / 简单适配器可直接用此做 IN-MEMORY 去重；
         * 复杂适配器（Salesforce）按需决定是否落到 SOQL {@code WHERE Id NOT IN (...)}。
         */
        public java.util.Set<String> knownExternalIds;

        public static SyncCursor empty() {
            return new SyncCursor();
        }

        public boolean isEmpty() {
            return sinceEpochMs == null && (syncToken == null || syncToken.isEmpty());
        }
    }

    /**
     * 平台侧 Contact
     */
    class ExternalContact {
        public String externalId;
        public String firstName;
        public String lastName;
        public String email;
        public String phone;
        public String company;
        public String title;
        public Map<String, Object> customFields;
        public String etag;
        /** 平台侧最近修改时间戳（毫秒） */
        public Long updatedAtMs;
    }

    /**
     * 推送结果
     */
    class PushResult {
        public boolean success;
        /** 平台返回的外部 ID（新建时必有） */
        public String externalId;
        public String etag;
        public String errorCode;
        public String errorMessage;

        public static PushResult ok(String externalId, String etag) {
            PushResult r = new PushResult();
            r.success = true;
            r.externalId = externalId;
            r.etag = etag;
            return r;
        }

        public static PushResult fail(String code, String msg) {
            PushResult r = new PushResult();
            r.success = false;
            r.errorCode = code;
            r.errorMessage = msg;
            return r;
        }
    }

    /**
     * 健康检查
     */
    class HealthStatus {
        public boolean healthy;
        public String stage;     // AUTH_OK / QUERY_OK / INSTANCE_OK / NETWORK_ERROR
        public String message;
        public Long elapsedMs;
        public String apiBaseUrl;
    }
}
