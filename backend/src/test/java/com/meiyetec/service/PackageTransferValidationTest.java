package com.meiyetec.service;

import com.meiyetec.dto.PackageTransferDTO;
import com.meiyetec.mapper.PackageOrderMapper;
import com.meiyetec.service.impl.PackageRedeemServiceImpl;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M04 · 套餐转赠完整链路 — 反射 / 静态契约回归测试（无 Spring 上下文）
 *
 * <p>职责：在不启动 Spring 的前提下，验证 transfer 链路的关键契约，防止回归：
 * <ul>
 *   <li>{@code PackageOrderMapper.casTransfer}：M04 完整链路核心 — 三重乐观锁守卫</li>
 *   <li>{@code PackageTransferDTO}：转赠请求契约（toMemberId / operatorId / remark）</li>
 *   <li>{@code PackageRedeemServiceImpl.STATUS_TRANSFER = 6}：CAS 互斥守卫的关键字典值</li>
 * </ul>
 *
 * <p>注：本测试**不**验证 {@code PackageController.transfer} 的逐行校验逻辑（11 项），
 * 那是运行时行为，由 {@code P211FullChainIntegrationTest} + 联调承担；这里只校验静态契约。
 */
class PackageTransferValidationTest {

    @Test
    void package_order_mapper_has_casTransfer_method() {
        Method m = findMethod(PackageOrderMapper.class, "casTransfer");
        assertNotNull(m, "PackageOrderMapper.casTransfer 必须存在（M04 完整链路核心）");
        assertEquals(int.class, m.getReturnType(),
                "casTransfer 返回值类型必须为 int（影响行数）");
        assertEquals(2, m.getParameterCount(),
                "casTransfer 应有 2 个参数：id + version");
    }

    @Test
    void cas_transfer_signature_id_and_version() {
        Method m = findMethod(PackageOrderMapper.class, "casTransfer");
        assertNotNull(m);
        assertEquals(Long.class, m.getParameterTypes()[0],
                "第 1 参数必须是 Long（套餐订单 ID）");
        assertEquals(Integer.class, m.getParameterTypes()[1],
                "第 2 参数必须是 Integer（乐观锁 version）");
    }

    @Test
    void cas_redeem_also_present() {
        Method m = findMethod(PackageOrderMapper.class, "casRedeem");
        assertNotNull(m, "casRedeem 必须保留（M04 不影响现有核销链路）");
        assertEquals(3, m.getParameterCount(),
                "casRedeem 应有 3 个参数：id + version + times");
    }

    @Test
    void package_transfer_dto_has_required_fields() {
        assertNotNull(findField(PackageTransferDTO.class, "toMemberId"),
                "PackageTransferDTO.toMemberId 必须存在（target 会员）");
        assertNotNull(findField(PackageTransferDTO.class, "operatorId"),
                "PackageTransferDTO.operatorId 必须存在（操作人）");
        assertNotNull(findField(PackageTransferDTO.class, "remark"),
                "PackageTransferDTO.remark 必须存在");
    }

    @Test
    void package_transfer_dto_toMemberId_has_validation() throws NoSuchFieldException {
        Field f = PackageTransferDTO.class.getDeclaredField("toMemberId");
        jakarta.validation.constraints.NotNull ann = f.getAnnotation(jakarta.validation.constraints.NotNull.class);
        assertNotNull(ann,
                "toMemberId 必须标 @NotNull（避免空指针导致 NPE）");
    }

    @Test
    void package_redeem_impl_status_transfer_constant_is_six() throws Exception {
        // STATUS_TRANSFER 是 casTransfer 互斥守卫的关键字典值：
        //   - casRedeem: status NOT IN (3,4,5,6)  ── 排除"已转赠"
        //   - casTransfer: status = 1              ── 排除"非正常"
        // 写完转赠：status = 6 (TRANSFER)、remaining=0
        // 二次转赠会因 status != 1 直接被 CAS 拒绝
        // 此处走源码文本校验而非反射（避免 Lombok Slf4j 初始化拉入整个服务依赖图）
        String src = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/java/com/meiyetec/service/impl/PackageRedeemServiceImpl.java"),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(src.contains("STATUS_TRANSFER"),
                "PackageRedeemServiceImpl 源码必须包含 STATUS_TRANSFER 常量");
        assertTrue(src.matches("(?s).*STATUS_TRANSFER\\s*=\\s*6.*"),
                "PackageRedeemServiceImpl 必须定义 STATUS_TRANSFER = 6（与 crm_package_order.status 字典一致）");
    }

    // ---------- helpers ----------

    private static Method findMethod(Class<?> clazz, String name) {
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(name)) return m;
        }
        return null;
    }

    private static Field findField(Class<?> clazz, String name) {
        try {
            return clazz.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }
}
