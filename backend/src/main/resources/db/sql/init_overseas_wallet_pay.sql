-- ============================================
-- 海外 Apple Pay / Google Pay 钱包支付网关种子数据
-- P3.6：海外钱包支付（2026-09-05）
-- 底层通道：Stripe PaymentIntents（payment_method_types=card）
-- ============================================

-- Apple Pay 网关（沙箱）
INSERT INTO biz_overseas_payment_gateway
    (tenant_id, gateway_code, gateway_name, api_base_url, publishable_key, secret_key,
     webhook_secret, merchant_id, fee_rate, fixed_fee, supported_currencies, payment_methods,
     environment, country_code, status, health_status, metadata, remark, deleted, create_time)
VALUES
    (NULL, 'apple_pay', 'Apple Pay (Stripe 沙箱)', 'https://api.stripe.com',
     'pk_test_PLACEHOLDER_REPLACE_IN_PROD',
     'sk_test_PLACEHOLDER_REPLACE_IN_PROD',
     'whsec_PLACEHOLDER_REPLACE_IN_PROD',
     'merchant.com.example.applepay', 0.029, 0.30,
     '["USD","EUR","GBP","CAD","AUD","JPY","SGD","HKD"]',
     '["apple_pay"]',
     'sandbox', 'GLOBAL', 0, 'unknown',
     '{"displayName":"Beauty Salon","supportedNetworks":["visa","masterCard","amex","discover"],"merchantCapabilities":["supports3DS"]}',
     'Apple Pay 网关占位 — 生产前请替换 publishableKey/secretKey/webhookSecret/merchantId',
     0, NOW())
ON DUPLICATE KEY UPDATE
    gateway_name = VALUES(gateway_name),
    metadata = VALUES(metadata),
    remark = VALUES(remark);

-- Google Pay 网关（沙箱）
INSERT INTO biz_overseas_payment_gateway
    (tenant_id, gateway_code, gateway_name, api_base_url, publishable_key, secret_key,
     webhook_secret, merchant_id, fee_rate, fixed_fee, supported_currencies, payment_methods,
     environment, country_code, status, health_status, metadata, remark, deleted, create_time)
VALUES
    (NULL, 'google_pay', 'Google Pay (Stripe 沙箱)', 'https://api.stripe.com',
     'pk_test_PLACEHOLDER_REPLACE_IN_PROD',
     'sk_test_PLACEHOLDER_REPLACE_IN_PROD',
     'whsec_PLACEHOLDER_REPLACE_IN_PROD',
     '01234567890123456789', 0.029, 0.30,
     '["USD","EUR","GBP","CAD","AUD","JPY","SGD","HKD"]',
     '["google_pay"]',
     'sandbox', 'GLOBAL', 0, 'unknown',
     '{"merchantName":"Beauty Salon","testCardNetworks":["VISA","MASTERCARD","AMEX"],"billingAddressRequired":false}',
     'Google Pay 网关占位 — 生产前请替换 publishableKey/secretKey/webhookSecret/merchantId',
     0, NOW())
ON DUPLICATE KEY UPDATE
    gateway_name = VALUES(gateway_name),
    metadata = VALUES(metadata),
    remark = VALUES(remark);

-- 钱包支付路由规则（payment_method 维度的网关定向）
INSERT INTO biz_payment_route_rule
    (tenant_id, rule_name, priority, currency, country_code, payment_method, gateway_code,
     fee_rate, priority_strategy, status, remark, deleted, create_time)
VALUES
    (NULL, 'Apple Pay 全局优先', 10, 'USD', NULL, 'apple_pay', 'apple_pay',
     0.029, 'speed', 1, '苹果设备用户一键支付', 0, NOW()),
    (NULL, 'Google Pay 全局优先', 10, 'USD', NULL, 'google_pay', 'google_pay',
     0.029, 'speed', 1, '安卓设备用户一键支付', 0, NOW()),
    (NULL, 'Apple Pay 欧洲', 9, 'EUR', NULL, 'apple_pay', 'apple_pay',
     0.029, 'coverage', 1, '欧元区 Apple Pay', 0, NOW()),
    (NULL, 'Apple Pay 英国', 9, 'GBP', 'GB', 'apple_pay', 'apple_pay',
     0.029, 'coverage', 1, '英国 Apple Pay', 0, NOW()),
    (NULL, 'Google Pay 日本', 9, 'JPY', 'JP', 'google_pay', 'google_pay',
     0.029, 'coverage', 1, '日本 Google Pay', 0, NOW())
ON DUPLICATE KEY UPDATE
    rule_name = VALUES(rule_name),
    priority = VALUES(priority);

-- RBAC 权限（钱包支付路由规则权限）
INSERT INTO sys_permission (code, name, module, type, status, deleted, create_time)
VALUES
    ('payment:overseas:wallet', '海外钱包支付', 'payment', 'menu', 1, 0, NOW())
ON DUPLICATE KEY UPDATE name = VALUES(name);
