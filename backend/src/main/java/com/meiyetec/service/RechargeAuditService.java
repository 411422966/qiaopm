package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.dto.WalletOpDTO;
import com.meiyetec.entity.Member;
import com.meiyetec.entity.RechargeAudit;
import com.meiyetec.entity.RechargeAuditLog;
import com.meiyetec.entity.Wallet;
import com.meiyetec.entity.WalletTransaction;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.RechargeAuditLogMapper;
import com.meiyetec.mapper.RechargeAuditMapper;
import com.meiyetec.mapper.WalletMapper;
import com.meiyetec.mapper.WalletTransactionMapper;
import com.meiyetec.security.TenantContext;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Keys;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class RechargeAuditService {

    private final RechargeAuditMapper auditMapper;
    private final RechargeAuditLogMapper auditLogMapper;
    private final WalletMapper walletMapper;
    private final WalletTransactionMapper txnMapper;
    private final WalletService walletService;

    @Autowired
    private JdbcTemplate jdbc;

    private String currentUserName(TenantContext ctx) {
        if (ctx.getUserId() == null) return "系统";
        try {
            String name = jdbc.queryForObject("SELECT real_name FROM sys_user WHERE id = ?",
                    String.class, ctx.getUserId());
            return name == null ? "系统" : name;
        } catch (Exception e) {
            return "系统";
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<RechargeAudit> applyRecharge(RechargeAudit req) {
        TenantContext ctx = TenantContextHolder.get();
        Long tenantId = ctx.getTenantId();
        if (tenantId == null) throw new BusinessException(BizCode.UNAUTHORIZED);

        if (req.getMemberId() == null) throw new BusinessException(BizCode.BAD_REQUEST, "会员 id 必填");
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessException(BizCode.BAD_REQUEST, "充值金额必须大于 0");
        if (req.getPayMethod() == null || req.getPayMethod().isEmpty())
            throw new BusinessException(BizCode.BAD_REQUEST, "支付方式必填");

        Member m = jdbc.queryForObject(
            "SELECT id, name, phone FROM crm_member WHERE id = ? AND deleted = 0",
            (rs, rowNum) -> {
                Member mm = new Member();
                mm.setId(rs.getLong("id"));
                mm.setName(rs.getString("name"));
                mm.setPhone(rs.getString("phone"));
                return mm;
            }, req.getMemberId());
        if (m == null) throw new BusinessException(BizCode.MEMBER_NOT_FOUND);

        RechargeAudit audit = new RechargeAudit();
        audit.setTenantId(tenantId);
        audit.setStoreId(req.getStoreId());
        audit.setAuditNo("RA" + System.currentTimeMillis());
        audit.setMemberId(req.getMemberId());
        audit.setMemberName(m.getName());
        audit.setMemberPhone(m.getPhone());
        audit.setAmount(req.getAmount());
        audit.setGiftAmount(req.getGiftAmount() == null ? BigDecimal.ZERO : req.getGiftAmount());
        audit.setTotalAmount(audit.getAmount().add(audit.getGiftAmount()));
        audit.setPayMethod(req.getPayMethod());
        audit.setTradeNo(req.getTradeNo());
        audit.setStatus(1);
        audit.setApplyUserId(ctx.getUserId());
        audit.setApplyUserName(currentUserName(ctx));
        audit.setSource(req.getSource() == null ? 1 : req.getSource());
        audit.setRemark(req.getRemark());
        audit.setVersion(0);

        auditMapper.insert(audit);
        saveLog(audit.getId(), 1, ctx.getUserId(), currentUserName(ctx),
                null, "1", "提交充值审核申请");

        return Result.success("已提交审核", audit);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<RechargeAudit> approve(Long id, String remark) {
        TenantContext ctx = TenantContextHolder.get();
        RechargeAudit audit = auditMapper.selectById(id);
        if (audit == null) throw new BusinessException(BizCode.NOT_FOUND, "审核记录不存在");
        if (audit.getStatus() != 1) throw new BusinessException(BizCode.BAD_REQUEST,
                "当前状态不允许审核，status=" + audit.getStatus());

        WalletOpDTO dto = new WalletOpDTO();
        dto.setMemberId(audit.getMemberId());
        dto.setAmount(audit.getAmount());
        dto.setGiftAmount(audit.getGiftAmount());
        dto.setPayMethod(audit.getPayMethod());
        dto.setRemark("审核发放：「" + audit.getAuditNo() + "」");
        walletService.doRecharge(dto, audit.getTenantId());

        audit.setStatus(2);
        audit.setAuditUserId(ctx.getUserId());
        String auditorName = currentUserName(ctx);
        audit.setAuditUserName(auditorName);
        audit.setAuditTime(LocalDateTime.now());
        audit.setAuditRemark(remark);
        audit.setVersion(audit.getVersion() + 1);
        auditMapper.updateById(audit);

        saveLog(audit.getId(), 2, ctx.getUserId(), auditorName,
                "1", "2", "审核通过" + (remark != null ? "：" + remark : ""));

        return Result.success("审核通过，已发放", audit);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<RechargeAudit> reject(Long id, String remark) {
        TenantContext ctx = TenantContextHolder.get();
        RechargeAudit audit = auditMapper.selectById(id);
        if (audit == null) throw new BusinessException(BizCode.NOT_FOUND, "审核记录不存在");
        if (audit.getStatus() != 1) throw new BusinessException(BizCode.BAD_REQUEST,
                "当前状态不允许审核，status=" + audit.getStatus());

        audit.setStatus(3);
        audit.setAuditUserId(ctx.getUserId());
        String auditorName = currentUserName(ctx);
        audit.setAuditUserName(auditorName);
        audit.setAuditTime(LocalDateTime.now());
        audit.setAuditRemark(remark);
        audit.setVersion(audit.getVersion() + 1);
        auditMapper.updateById(audit);

        saveLog(audit.getId(), 3, ctx.getUserId(), auditorName,
                "1", "3", "审核拒绝" + (remark != null ? "：" + remark : ""));

        return Result.success("已拒绝", audit);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Void> cancel(Long id, String reason) {
        TenantContext ctx = TenantContextHolder.get();
        RechargeAudit audit = auditMapper.selectById(id);
        if (audit == null) throw new BusinessException(BizCode.NOT_FOUND, "审核记录不存在");
        if (audit.getStatus() != 1) throw new BusinessException(BizCode.BAD_REQUEST,
                "仅待审核状态可以取消，status=" + audit.getStatus());

        audit.setStatus(4);
        audit.setVersion(audit.getVersion() + 1);
        auditMapper.updateById(audit);

        saveLog(audit.getId(), 4, ctx.getUserId(),
                currentUserName(ctx),
                "1", "4", "取消申请" + (reason != null ? "：" + reason : ""));

        return Result.success("已取消", null);
    }

    public Result<Page<Map<String, Object>>> page(
            Integer status, String keyword, String startDate, String endDate,
            long current, long size) {
        TenantContext ctx = TenantContextHolder.get();
        Long tenantId = ctx.getTenantId();
        if (tenantId == null) throw new BusinessException(BizCode.UNAUTHORIZED);

        StringBuilder sql = new StringBuilder(
            "SELECT id, audit_no, member_name, member_phone, amount, gift_amount, total_amount, "
            + "pay_method, status, source, apply_user_name, audit_user_name, audit_time, audit_remark, "
            + "create_time FROM crm_recharge_audit WHERE deleted = 0 AND tenant_id = ? ");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);

        if (status != null) { sql.append("AND status = ? "); args.add(status); }
        if (keyword != null && !keyword.isEmpty()) {
            sql.append("AND (member_name LIKE ? OR member_phone LIKE ? OR audit_no LIKE ?) ");
            args.add("%" + keyword + "%"); args.add("%" + keyword + "%"); args.add("%" + keyword + "%");
        }
        if (startDate != null && !startDate.isEmpty()) {
            sql.append("AND create_time >= ? "); args.add(LocalDate.parse(startDate).atStartOfDay());
        }
        if (endDate != null && !endDate.isEmpty()) {
            sql.append("AND create_time <= ? "); args.add(LocalDate.parse(endDate).atTime(23, 59, 59));
        }

        sql.append("ORDER BY create_time DESC LIMIT ?, ?");
        args.add((current - 1) * size);
        args.add(size);

        List<Map<String, Object>> records = jdbc.queryForList(sql.toString(), args.toArray());

        StringBuilder cntSql = new StringBuilder(
            "SELECT COUNT(*) FROM crm_recharge_audit WHERE deleted = 0 AND tenant_id = ? ");
        List<Object> cntArgs = new ArrayList<>();
        cntArgs.add(tenantId);
        if (status != null) { cntSql.append("AND status = ? "); cntArgs.add(status); }
        if (keyword != null && !keyword.isEmpty()) {
            cntSql.append("AND (member_name LIKE ? OR member_phone LIKE ? OR audit_no LIKE ?) ");
            cntArgs.add("%" + keyword + "%"); cntArgs.add("%" + keyword + "%"); cntArgs.add("%" + keyword + "%");
        }
        long total = jdbc.queryForObject(cntSql.toString(), Long.class, cntArgs.toArray());

        records = Keys.camel(records);

        Page<Map<String, Object>> page = new Page<>(current, size);
        page.setRecords(records);
        page.setTotal(total);
        return Result.success(page);
    }

    public Result<RechargeAudit> detail(Long id) {
        RechargeAudit audit = auditMapper.selectById(id);
        if (audit == null) throw new BusinessException(BizCode.NOT_FOUND, "审核记录不存在");
        return Result.success(audit);
    }

    public Result<List<RechargeAuditLog>> logList(Long auditId) {
        LambdaQueryWrapper<RechargeAuditLog> q = new LambdaQueryWrapper<>();
        q.eq(RechargeAuditLog::getAuditId, auditId).orderByAsc(RechargeAuditLog::getCreateTime);
        return Result.success(auditLogMapper.selectList(q));
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Void> freezeAmount(Long memberId, BigDecimal amount, String reason) {
        if (memberId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessException(BizCode.BAD_REQUEST, "参数不合法");
        TenantContext ctx = TenantContextHolder.get();
        Long tenantId = ctx.getTenantId();

        Wallet wallet = walletMapper.selectOne(
            new LambdaQueryWrapper<Wallet>().eq(Wallet::getMemberId, memberId));
        if (wallet == null) throw new BusinessException(BizCode.BAD_REQUEST, "钱包不存在");
        if (wallet.getBalance().compareTo(amount) < 0)
            throw new BusinessException(BizCode.BAD_REQUEST, "余额不足，无法冻结");

        wallet.setBalance(wallet.getBalance().subtract(amount));
        wallet.setFrozenAmount(
            (wallet.getFrozenAmount() == null ? BigDecimal.ZERO : wallet.getFrozenAmount()).add(amount));
        wallet.setVersion(wallet.getVersion() + 1);
        walletMapper.updateById(wallet);

        WalletTransaction txn = new WalletTransaction();
        txn.setTenantId(tenantId);
        txn.setMemberId(memberId);
        txn.setType(5);
        txn.setAmount(amount);
        txn.setBalanceBefore(wallet.getBalance().add(amount));
        txn.setBalanceAfter(wallet.getBalance());
        txn.setRemark(reason);
        txnMapper.insert(txn);

        return Result.success("冻结成功", null);
    }

    @Transactional(rollbackFor = Exception.class)
    public Result<Void> unfreezeAmount(Long memberId, BigDecimal amount, String reason) {
        if (memberId == null || amount == null || amount.compareTo(BigDecimal.ZERO) <= 0)
            throw new BusinessException(BizCode.BAD_REQUEST, "参数不合法");
        TenantContext ctx = TenantContextHolder.get();
        Long tenantId = ctx.getTenantId();

        Wallet wallet = walletMapper.selectOne(
            new LambdaQueryWrapper<Wallet>().eq(Wallet::getMemberId, memberId));
        if (wallet == null) throw new BusinessException(BizCode.BAD_REQUEST, "钱包不存在");
        BigDecimal frozen = wallet.getFrozenAmount() == null ? BigDecimal.ZERO : wallet.getFrozenAmount();
        if (frozen.compareTo(amount) < 0)
            throw new BusinessException(BizCode.BAD_REQUEST, "冻结余额不足，无法解冻");

        wallet.setFrozenAmount(frozen.subtract(amount));
        wallet.setBalance(wallet.getBalance().add(amount));
        wallet.setVersion(wallet.getVersion() + 1);
        walletMapper.updateById(wallet);

        WalletTransaction txn = new WalletTransaction();
        txn.setTenantId(tenantId);
        txn.setMemberId(memberId);
        txn.setType(6);
        txn.setAmount(amount);
        txn.setBalanceBefore(frozen);
        txn.setBalanceAfter(wallet.getFrozenAmount());
        txn.setRemark(reason);
        txnMapper.insert(txn);

        return Result.success("解冻成功", null);
    }

    private void saveLog(Long auditId, Integer action, Long opId, String opName,
                         String beforeStatus, String afterStatus, String remark) {
        try {
            RechargeAuditLog log = new RechargeAuditLog();
            log.setAuditId(auditId);
            RechargeAudit audit = auditMapper.selectById(auditId);
            if (audit != null) log.setAuditNo(audit.getAuditNo());
            log.setAction(action);
            log.setOperatorId(opId);
            log.setOperatorName(opName);
            log.setBeforeStatus(beforeStatus);
            log.setAfterStatus(afterStatus);
            log.setRemark(remark);
            auditLogMapper.insert(log);
        } catch (Exception e) {
            this.log.warn("记录审核日志失败: {}", e.getMessage());
        }
    }
}
