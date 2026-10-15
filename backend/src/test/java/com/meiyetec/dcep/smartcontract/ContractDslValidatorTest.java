package com.meiyetec.dcep.smartcontract;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DCEP-03 智能合约 DSL 校验器单元测试
 */
@DisplayName("DCEP-03 智能合约 DSL 校验器")
class ContractDslValidatorTest {

    private final ContractDslValidator validator = new ContractDslValidator();
    private final LocalDateTime now = LocalDateTime.of(2026, 9, 4, 12, 0);

    // ============================================================
    // 1. 合法场景
    // ============================================================

    @Test
    @DisplayName("合法 expire 合约：未来 deadline + 合理 conditions")
    void valid_expireContract() {
        String json = "{\n" +
                "  \"type\": \"expire\",\n" +
                "  \"deadline\": \"2027-12-31\",\n" +
                "  \"minSpend\": 100,\n" +
                "  \"conditions\": []\n" +
                "}";
        ContractDslValidator.ValidationResult r = validator.validate(json, now);
        assertTrue(r.valid, "合法 expire 合约应通过校验，实际 errors=" + r.errors);
    }

    @Test
    @DisplayName("合法 purchase 合约：金额表达式 + 触发动作")
    void valid_purchaseContract() {
        String json = "{\n" +
                "  \"type\": \"purchase\",\n" +
                "  \"minSpend\": 200,\n" +
                "  \"merchantCategories\": [\"beauty\", \"spa\"],\n" +
                "  \"conditions\": [\n" +
                "    {\"trigger\": \"purchase\", \"amount\": \">=100\", \"action\": \"release\"},\n" +
                "    {\"trigger\": \"review\", \"amount\": \"==50\", \"action\": \"coupon_grant\"}\n" +
                "  ]\n" +
                "}";
        ContractDslValidator.ValidationResult r = validator.validate(json, now);
        assertTrue(r.valid);
    }

    @Test
    @DisplayName("合法 hybrid 合约：≥2 个 conditions")
    void valid_hybridContract() {
        String json = "{\n" +
                "  \"type\": \"hybrid\",\n" +
                "  \"conditions\": [\n" +
                "    {\"trigger\": \"purchase\", \"amount\": \">=100\", \"action\": \"release\"},\n" +
                "    {\"trigger\": \"expire\", \"deadline\": \"2027-06-30\", \"action\": \"refund\"}\n" +
                "  ]\n" +
                "}";
        ContractDslValidator.ValidationResult r = validator.validate(json, now);
        assertTrue(r.valid);
    }

    @Test
    @DisplayName("合法 milestone：含 count 字段")
    void valid_milestoneContract() {
        String json = "{\n" +
                "  \"type\": \"milestone\",\n" +
                "  \"conditions\": [\n" +
                "    {\"trigger\": \"purchase\", \"count\": 5, \"action\": \"coupon_grant\"}\n" +
                "  ]\n" +
                "}";
        ContractDslValidator.ValidationResult r = validator.validate(json, now);
        assertTrue(r.valid);
    }

    // ============================================================
    // 2. 错误场景
    // ============================================================

    @Test
    @DisplayName("空字符串：errors 应包含'不能为空'")
    void invalid_empty() {
        ContractDslValidator.ValidationResult r = validator.validate("", now);
        assertFalse(r.valid);
        assertTrue(r.errors.stream().anyMatch(e -> e.contains("不能为空")));
    }

    @Test
    @DisplayName("非法 JSON：errors 应包含 'JSON 解析失败'")
    void invalid_json() {
        ContractDslValidator.ValidationResult r = validator.validate("{invalid json", now);
        assertFalse(r.valid);
        assertTrue(r.errors.stream().anyMatch(e -> e.contains("JSON 解析失败")));
    }

    @Test
    @DisplayName("JSON 不是对象（是数组）：errors 应包含 '必须是 JSON 对象'")
    void invalid_jsonNotObject() {
        ContractDslValidator.ValidationResult r = validator.validate("[1,2,3]", now);
        assertFalse(r.valid);
    }

    @Test
    @DisplayName("type 缺失：必填")
    void invalid_missingType() {
        String json = "{\"deadline\": \"2027-12-31\"}";
        ContractDslValidator.ValidationResult r = validator.validate(json, now);
        assertFalse(r.valid);
        assertTrue(r.errors.stream().anyMatch(e -> e.contains("type")));
    }

    @Test
    @DisplayName("type=unknown 不支持")
    void invalid_unknownType() {
        String json = "{\"type\": \"unknown_type\"}";
        ContractDslValidator.ValidationResult r = validator.validate(json, now);
        assertFalse(r.valid);
        assertTrue(r.errors.stream().anyMatch(e -> e.contains("不支持的 type")));
    }

    @Test
    @DisplayName("expire 类型缺 deadline：必填")
    void invalid_expireMissingDeadline() {
        String json = "{\"type\": \"expire\"}";
        ContractDslValidator.ValidationResult r = validator.validate(json, now);
        assertFalse(r.valid);
        assertTrue(r.errors.stream().anyMatch(e -> e.contains("deadline")));
    }

    @Test
    @DisplayName("expire 类型 deadline 已过期：必填")
    void invalid_expirePastDeadline() {
        String json = "{\"type\": \"expire\", \"deadline\": \"2025-01-01\"}";
        ContractDslValidator.ValidationResult r = validator.validate(json, now);
        assertFalse(r.valid);
        assertTrue(r.errors.stream().anyMatch(e -> e.contains("deadline 必须在未来")));
    }

    @Test
    @DisplayName("deadline 格式错误：必须 yyyy-MM-dd")
    void invalid_deadlineFormat() {
        String json = "{\"type\": \"expire\", \"deadline\": \"2027/12/31\"}";
        ContractDslValidator.ValidationResult r = validator.validate(json, now);
        assertFalse(r.valid);
        assertTrue(r.errors.stream().anyMatch(e -> e.contains("deadline 格式错误")));
    }

    @Test
    @DisplayName("minSpend 为负数")
    void invalid_negativeMinSpend() {
        String json = "{\"type\": \"purchase\", \"minSpend\": -100}";
        ContractDslValidator.ValidationResult r = validator.validate(json, now);
        assertFalse(r.valid);
        assertTrue(r.errors.stream().anyMatch(e -> e.contains("minSpend 不能为负数")));
    }

    @Test
    @DisplayName("purchase 缺 conditions：必填")
    void invalid_purchaseMissingConditions() {
        String json = "{\"type\": \"purchase\"}";
        ContractDslValidator.ValidationResult r = validator.validate(json, now);
        assertFalse(r.valid);
        assertTrue(r.errors.stream().anyMatch(e -> e.contains("conditions 数组")));
    }

    @Test
    @DisplayName("hybrid 仅 1 个 condition：必填 ≥2")
    void invalid_hybridTooFewConditions() {
        String json = "{\n" +
                "  \"type\": \"hybrid\",\n" +
                "  \"conditions\": [{\"trigger\": \"purchase\", \"action\": \"release\"}]\n" +
                "}";
        ContractDslValidator.ValidationResult r = validator.validate(json, now);
        assertFalse(r.valid);
        assertTrue(r.errors.stream().anyMatch(e -> e.contains("hybrid")));
    }

    @Test
    @DisplayName("condition.trigger 不支持")
    void invalid_unknownTrigger() {
        String json = "{\n" +
                "  \"type\": \"purchase\",\n" +
                "  \"conditions\": [{\"trigger\": \"unknown_event\", \"action\": \"release\"}]\n" +
                "}";
        ContractDslValidator.ValidationResult r = validator.validate(json, now);
        assertFalse(r.valid);
    }

    @Test
    @DisplayName("condition.action 不支持")
    void invalid_unknownAction() {
        String json = "{\n" +
                "  \"type\": \"purchase\",\n" +
                "  \"conditions\": [{\"trigger\": \"purchase\", \"action\": \"unknown_action\"}]\n" +
                "}";
        ContractDslValidator.ValidationResult r = validator.validate(json, now);
        assertFalse(r.valid);
    }

    @Test
    @DisplayName("condition.amount 表达式非法")
    void invalid_amountExpr() {
        String json = "{\n" +
                "  \"type\": \"purchase\",\n" +
                "  \"conditions\": [{\"trigger\": \"purchase\", \"amount\": \"abc\", \"action\": \"release\"}]\n" +
                "}";
        ContractDslValidator.ValidationResult r = validator.validate(json, now);
        assertFalse(r.valid);
        assertTrue(r.errors.stream().anyMatch(e -> e.contains("amount 表达式非法")));
    }

    @Test
    @DisplayName("amount 区间上界 < 下界")
    void invalid_amountRange() {
        String json = "{\n" +
                "  \"type\": \"purchase\",\n" +
                "  \"conditions\": [{\"trigger\": \"purchase\", \"amount\": \"500~100\", \"action\": \"release\"}]\n" +
                "}";
        ContractDslValidator.ValidationResult r = validator.validate(json, now);
        assertFalse(r.valid);
    }

    @Test
    @DisplayName("merchantCategories 未知类目：仅警告，不阻塞")
    void warning_unknownCategory() {
        String json = "{\n" +
                "  \"type\": \"purchase\",\n" +
                "  \"merchantCategories\": [\"unknown_category\"],\n" +
                "  \"conditions\": [{\"trigger\": \"purchase\", \"action\": \"release\"}]\n" +
                "}";
        ContractDslValidator.ValidationResult r = validator.validate(json, now);
        assertTrue(r.valid, "未知类目仅警告，不阻塞");
        assertTrue(r.warnings.stream().anyMatch(w -> w.contains("未知商户类目")));
    }

    @Test
    @DisplayName("escrowAmount 超大：仅警告")
    void warning_oversizedEscrow() {
        String json = "{\n" +
                "  \"type\": \"expire\",\n" +
                "  \"deadline\": \"2027-12-31\",\n" +
                "  \"escrowAmount\": 99999999\n" +
                "}";
        ContractDslValidator.ValidationResult r = validator.validate(json, now);
        assertTrue(r.valid);
        assertTrue(r.warnings.stream().anyMatch(w -> w.contains("escrowAmount")));
    }

    @Test
    @DisplayName("condition.count 必须 > 0")
    void invalid_zeroCount() {
        String json = "{\n" +
                "  \"type\": \"milestone\",\n" +
                "  \"conditions\": [{\"trigger\": \"purchase\", \"count\": 0, \"action\": \"coupon_grant\"}]\n" +
                "}";
        ContractDslValidator.ValidationResult r = validator.validate(json, now);
        assertFalse(r.valid);
        assertTrue(r.errors.stream().anyMatch(e -> e.contains("count 必须 > 0")));
    }

    @Test
    @DisplayName("summary 输出格式正确")
    void summaryFormat() {
        String json = "{\"type\": \"expire\", \"deadline\": \"2025-01-01\"}";
        ContractDslValidator.ValidationResult r = validator.validate(json, now);
        String s = r.summary();
        assertTrue(s.contains("INVALID"));
        assertTrue(s.contains("错误"));
    }
}
