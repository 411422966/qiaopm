package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.PosDevice;
import com.meiyetec.entity.PosTransaction;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.PosDeviceMapper;
import com.meiyetec.mapper.PosTransactionMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 海外 POS 终端对接（FEATURE_COMPARISON_MATRIX.md 268-278 P2-21）
 * 独立于 /pos 收银模块，专注于 Square / 海外硬件对接
 */
@Slf4j
@RestController
@RequestMapping("/pos-device")
@RequiredArgsConstructor
public class PosDeviceService {

    private final PosDeviceMapper deviceMapper;
    private final PosTransactionMapper txMapper;

    @PostMapping("/register")
    public Result<PosDevice> register(@RequestBody PosDevice d) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (d.getDeviceCode() == null) return Result.error(BizCode.BAD_REQUEST, "deviceCode 必填");
        if (d.getStatus() == null) d.setStatus("ACTIVE");
        d.setTenantId(tenantId);
        d.setId(null);
        deviceMapper.insert(d);
        return Result.success("已注册", d);
    }

    @GetMapping("/list")
    public Result<List<PosDevice>> listDevices() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(deviceMapper.selectList(new LambdaQueryWrapper<PosDevice>()
                .eq(PosDevice::getTenantId, tenantId)
                .orderByDesc(PosDevice::getId)));
    }

    @PostMapping("/{id}")
    public Result<PosDevice> updateDevice(@PathVariable Long id, @RequestBody PosDevice d) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        d.setId(id);
        d.setTenantId(tenantId);
        deviceMapper.updateById(d);
        return Result.success("已更新", deviceMapper.selectById(id));
    }

    @PostMapping("/transaction/sync")
    @Transactional(rollbackFor = Exception.class)
    public Result<PosTransaction> syncTransaction(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String deviceCode = (String) body.get("deviceCode");
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        PosDevice device = deviceMapper.selectOne(new LambdaQueryWrapper<PosDevice>()
                .eq(PosDevice::getTenantId, tenantId)
                .eq(PosDevice::getDeviceCode, deviceCode));
        if (device == null) return Result.error(BizCode.NOT_FOUND, "设备未注册");

        PosTransaction tx = new PosTransaction();
        tx.setTenantId(tenantId);
        tx.setPosId(device.getId());
        tx.setVendor(device.getVendor());
        tx.setExternalTxId((String) body.getOrDefault("externalId", "TX" + System.currentTimeMillis()));
        tx.setAmount(amount);
        tx.setCurrency((String) body.getOrDefault("currency", "USD"));
        tx.setStatus((String) body.getOrDefault("status", "COMPLETED"));
        tx.setCreatedAt(LocalDateTime.now());
        txMapper.insert(tx);
        return Result.success("已同步", tx);
    }

    @GetMapping("/transaction/list")
    public Result<List<PosTransaction>> listTransactions(@RequestParam(required = false) Long deviceId,
                                                        @RequestParam(required = false) String status) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<PosTransaction> qw = new LambdaQueryWrapper<PosTransaction>()
                .eq(PosTransaction::getTenantId, tenantId)
                .orderByDesc(PosTransaction::getId);
        if (deviceId != null) qw.eq(PosTransaction::getPosId, deviceId);
        if (status != null && !status.isEmpty()) qw.eq(PosTransaction::getStatus, status);
        return Result.success(txMapper.selectList(qw));
    }

    @GetMapping("/dashboard")
    public Result<Map<String, Object>> dashboard() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            data.put("totalDevices", deviceMapper.selectCount(new LambdaQueryWrapper<PosDevice>()
                    .eq(PosDevice::getTenantId, tenantId)));
            data.put("totalTransactions", txMapper.selectCount(new LambdaQueryWrapper<PosTransaction>()
                    .eq(PosTransaction::getTenantId, tenantId)));
        } catch (Exception ignore) {}
        return Result.success(data);
    }
}