package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.entity.StockDualVerifyRecord;
import com.meiyetec.entity.StockRecord;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.StockDualVerifyRecordMapper;
import com.meiyetec.mapper.StockRecordMapper;
import com.meiyetec.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * P2-11 IND-05 双人核销服务
 *
 * <p>提供阈值判断、待复核记录创建、复核完成等核心操作。
 * <p>复核不是"阻止操作"，而是"补签名审计"——
 * 库存已在复核前扣减，复核不通过时通过冲正接口补偿。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockDualVerifyService {

    private static final String KEY_THRESHOLD = "stock_dual_verify_threshold";
    private static final BigDecimal DEFAULT_THRESHOLD = new BigDecimal("1000");

    private final StockDualVerifyRecordMapper dualVerifyMapper;
    private final StockRecordMapper recordMapper;
    private final SysConfigService sysConfigService;
    private final RbacService rbacService;

    // ==================== 阈值判断 ====================

    /**
     * 判断给定金额是否超过双人核销阈值。
     * 阈值从 sys_config 读取，按租户可覆盖。
     */
    public boolean isDualVerifyRequired(Long tenantId, BigDecimal totalAmount) {
        if (totalAmount == null) return false;
        BigDecimal threshold = getThreshold(tenantId);
        return totalAmount.compareTo(threshold) > 0;
    }

    /**
     * 获取双人核销阈值（元）。
     */
    public BigDecimal getThreshold(Long tenantId) {
        try {
            int val = sysConfigService.getInt(KEY_THRESHOLD, DEFAULT_THRESHOLD.intValue());
            return new BigDecimal(val);
        } catch (Exception e) {
            log.warn("[DualVerify] get threshold failed, use default 1000", e);
            return DEFAULT_THRESHOLD;
        }
    }

    // ==================== 创建待复核记录 ====================

    /**
     * 出库/消耗完成后，如果超过阈值则写一条 PENDING 双人核销记录。
     *
     * @param recordId      stock_record.id
     * @param verifyType    CHECK / OUTBOUND / CONSUMPTION
     * @param recordType    2=出库 4=领用 5=报损 3=盘点
     * @param mainVerifierId  主核销人ID
     * @param mainVerifierName 主核销人姓名
     * @param totalAmount   核销金额
     */
    public void createPendingRecordIfNeeded(Long recordId, Long stockCheckId,
                                            String verifyType, String recordType,
                                            Long mainVerifierId, String mainVerifierName,
                                            Long storeId, BigDecimal totalAmount) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) tenantId = 1L;

        if (!isDualVerifyRequired(tenantId, totalAmount)) {
            // 不超阈值，更新 stock_record.dual_verify_status = NOT_REQUIRED
            updateRecordStatus(recordId, "NOT_REQUIRED");
            return;
        }

        StockDualVerifyRecord r = new StockDualVerifyRecord();
        r.setTenantId(tenantId);
        r.setStoreId(storeId != null ? storeId : 1L);
        r.setVerifyType(verifyType);
        r.setRecordType(recordType);
        r.setStockRecordId(recordId);
        r.setStockCheckId(stockCheckId);
        r.setMainVerifierId(mainVerifierId);
        r.setMainVerifierName(mainVerifierName);
        r.setVerifyStatus("PENDING");
        r.setTotalAmount(totalAmount);
        r.setThreshold(getThreshold(tenantId));
        r.setCreateTime(LocalDateTime.now());
        r.setUpdateTime(LocalDateTime.now());
        dualVerifyMapper.insert(r);

        updateRecordStatus(recordId, "PENDING");
        log.info("[DualVerify] created PENDING record id={} type={} recordId={} amount={} threshold={}",
                r.getId(), verifyType, recordId, totalAmount, r.getThreshold());
    }

    private void updateRecordStatus(Long recordId, String status) {
        if (recordId == null) return;
        StockRecord rec = recordMapper.selectById(recordId);
        if (rec != null) {
            rec.setDualVerifyStatus(status);
            recordMapper.updateById(rec);
        }
    }

    // ==================== 复核完成 ====================

    /**
     * 复核人提交复核。
     *
     * @param dualVerifyId  双人核销记录ID
     * @param reviewerId    复核人ID（从员工下拉选）
     * @param reviewerName  复核人姓名
     * @param reviewerGps   复核人GPS/指纹JSON
     */
    @Transactional(rollbackFor = Exception.class)
    public StockDualVerifyRecord completeVerify(Long dualVerifyId,
                                                Long reviewerId, String reviewerName,
                                                String reviewerGps) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) tenantId = 1L;

        StockDualVerifyRecord rec = dualVerifyMapper.selectById(dualVerifyId);
        if (rec == null || !tenantId.equals(rec.getTenantId())) {
            throw new BusinessException(BizCode.NOT_FOUND, "双人核销记录不存在");
        }
        if (!"PENDING".equals(rec.getVerifyStatus())) {
            throw new BusinessException(BizCode.CONFLICT,
                    "该记录状态为 " + rec.getVerifyStatus() + "，无法复核");
        }
        // 复核人 ≠ 主核销人
        if (reviewerId != null && reviewerId.equals(rec.getMainVerifierId())) {
            throw new BusinessException(BizCode.BAD_REQUEST, "复核人不能与主核销人为同一人");
        }
        // 复核人必须有 stock:check 权限
        if (reviewerId != null) {
            boolean hasPermission = rbacService.loadPermissionsByUser(reviewerId)
                    .contains("stock:check");
            if (!hasPermission) {
                throw new BusinessException(BizCode.PERMISSION_DENIED,
                        "复核人没有「stock:check」权限码，无法复核");
            }
        }

        rec.setReviewerId(reviewerId);
        rec.setReviewerName(reviewerName);
        rec.setReviewerGps(reviewerGps);
        rec.setReviewTime(LocalDateTime.now());
        rec.setVerifyStatus("VERIFIED");
        rec.setUpdateTime(LocalDateTime.now());
        dualVerifyMapper.updateById(rec);

        // 更新 stock_record
        if (rec.getStockRecordId() != null) {
            updateRecordStatus(rec.getStockRecordId(), "VERIFIED");
        }

        log.info("[DualVerify] verified dualVerifyId={} reviewerId={}", dualVerifyId, reviewerId);
        return rec;
    }

    /**
     * 复核人驳回复核（需要走冲正补偿库存）。
     */
    @Transactional(rollbackFor = Exception.class)
    public StockDualVerifyRecord rejectVerify(Long dualVerifyId,
                                              Long reviewerId, String reviewerName,
                                              String reviewerGps, String remark) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) tenantId = 1L;

        StockDualVerifyRecord rec = dualVerifyMapper.selectById(dualVerifyId);
        if (rec == null || !tenantId.equals(rec.getTenantId())) {
            throw new BusinessException(BizCode.NOT_FOUND, "双人核销记录不存在");
        }
        if (!"PENDING".equals(rec.getVerifyStatus())) {
            throw new BusinessException(BizCode.CONFLICT,
                    "该记录状态为 " + rec.getVerifyStatus() + "，无法驳回");
        }

        rec.setReviewerId(reviewerId);
        rec.setReviewerName(reviewerName);
        rec.setReviewerGps(reviewerGps);
        rec.setReviewTime(LocalDateTime.now());
        rec.setVerifyStatus("REJECTED");
        rec.setRemark(remark);
        rec.setUpdateTime(LocalDateTime.now());
        dualVerifyMapper.updateById(rec);

        if (rec.getStockRecordId() != null) {
            updateRecordStatus(rec.getStockRecordId(), "REJECTED");
        }

        log.info("[DualVerify] rejected dualVerifyId={} reviewerId={} reason={}",
                dualVerifyId, reviewerId, remark);
        return rec;
    }

    // ==================== 查询 ====================

    /**
     * 分页查询双人核销记录（支持按状态/类型筛选）
     */
    public Page<StockDualVerifyRecord> page(Long tenantId, int pageNum, int pageSize,
                                             String verifyType, String verifyStatus) {
        LambdaQueryWrapper<StockDualVerifyRecord> q = new LambdaQueryWrapper<>();
        q.eq(StockDualVerifyRecord::getTenantId, tenantId);
        if (verifyType != null && !verifyType.isEmpty()) {
            q.eq(StockDualVerifyRecord::getVerifyType, verifyType);
        }
        if (verifyStatus != null && !verifyStatus.isEmpty()) {
            q.eq(StockDualVerifyRecord::getVerifyStatus, verifyStatus);
        }
        q.orderByDesc(StockDualVerifyRecord::getId);
        Page<StockDualVerifyRecord> page = dualVerifyMapper.selectPage(
                new Page<>(pageNum, pageSize), q);
        return page;
    }

    /**
     * 按 stock_record.id 查双人核销记录（用于展示是否待复核）
     */
    public StockDualVerifyRecord findByRecordId(Long stockRecordId) {
        if (stockRecordId == null) return null;
        return dualVerifyMapper.selectOne(new LambdaQueryWrapper<StockDualVerifyRecord>()
                .eq(StockDualVerifyRecord::getStockRecordId, stockRecordId)
                .orderByDesc(StockDualVerifyRecord::getId)
                .last("LIMIT 1"));
    }

    /**
     * 按 stock_check.id 查双人核销记录
     */
    public StockDualVerifyRecord findByCheckId(Long stockCheckId) {
        if (stockCheckId == null) return null;
        return dualVerifyMapper.selectOne(new LambdaQueryWrapper<StockDualVerifyRecord>()
                .eq(StockDualVerifyRecord::getStockCheckId, stockCheckId)
                .orderByDesc(StockDualVerifyRecord::getId)
                .last("LIMIT 1"));
    }

    /**
     * 查询当前租户所有 PENDING 记录（待复核列表）
     */
    public List<StockDualVerifyRecord> listPending(Long tenantId) {
        return dualVerifyMapper.selectList(new LambdaQueryWrapper<StockDualVerifyRecord>()
                .eq(StockDualVerifyRecord::getTenantId, tenantId)
                .eq(StockDualVerifyRecord::getVerifyStatus, "PENDING")
                .eq(StockDualVerifyRecord::getDeleted, 0)
                .orderByDesc(StockDualVerifyRecord::getId));
    }
}
