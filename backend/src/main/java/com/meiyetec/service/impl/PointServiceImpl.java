package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.entity.Member;
import com.meiyetec.entity.PointRecord;
import com.meiyetec.entity.PointRule;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.MemberMapper;
import com.meiyetec.mapper.PointRecordMapper;
import com.meiyetec.mapper.PointRuleMapper;
import com.meiyetec.service.PointService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
public class PointServiceImpl implements PointService {

    private static final int MAX_OPTIMISTIC_RETRY = 100;

    @Autowired
    private PointRecordMapper pointRecordMapper;
    @Autowired
    private PointRuleMapper pointRuleMapper;
    @Autowired
    private MemberMapper memberMapper;

    @Override
    public Map<String, Object> getMemberPoints(Long memberId) {
        Member member = memberMapper.selectById(memberId);
        Map<String, Object> result = new HashMap<>();
        result.put("memberId", memberId);
        result.put("balance", member != null ? member.getPoints() : 0);
        result.put("totalEarned", member != null ? member.getPoints() : 0);
        
        LambdaQueryWrapper<PointRecord> q = new LambdaQueryWrapper<>();
        q.eq(PointRecord::getMemberId, memberId).eq(PointRecord::getType, 1);
        Long earned = pointRecordMapper.selectCount(q);
        q.eq(PointRecord::getType, 2);
        Long consumed = pointRecordMapper.selectCount(q);
        result.put("totalEarned", earned);
        result.put("totalConsumed", consumed);
        
        return result;
    }

    @Override
    public Map<String, Object> getPointRecords(Long memberId, int pageNum, int pageSize) {
        Page<PointRecord> page = new Page<>(pageNum, pageSize);
        IPage<PointRecord> result = pointRecordMapper.selectMemberPoints(page, memberId);
        
        Page<Map<String, Object>> mapPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        mapPage.setRecords(result.getRecords().stream().map(pr -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", pr.getId());
            m.put("type", pr.getType());
            m.put("typeText", pr.getType() == 1 ? "获得" : "消耗");
            m.put("pointValue", pr.getPointValue());
            m.put("balanceBefore", pr.getBalanceBefore());
            m.put("balanceAfter", pr.getBalanceAfter());
            m.put("source", pr.getSource());
            m.put("sourceText", sourceText(pr.getSource()));
            m.put("remark", pr.getRemark());
            m.put("createdAt", pr.getCreatedAt());
            return m;
        }).toList());
        
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("records", mapPage.getRecords());
        resultMap.put("total", mapPage.getTotal());
        resultMap.put("pages", mapPage.getPages());
        resultMap.put("current", mapPage.getCurrent());
        resultMap.put("size", mapPage.getSize());
        return resultMap;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addPoints(Long memberId, String source, Integer points, Long refId, String remark) {
        addPointsReturningId(memberId, source, points, refId, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addPointsReturningId(Long memberId, String source, Integer points, Long refId, String remark) {
        Member member = readMember(memberId);
        int before = member.getPoints() != null ? member.getPoints() : 0;
        int after = before + points;

        return casPointsWithRetryReturningId(memberId, points, before, after, 1, member.getVersion(),
                source, refId, remark);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void consumePoints(Long memberId, String source, Integer points, Long refId, String remark) {
        Member member = readMember(memberId);
        int before = member.getPoints() != null ? member.getPoints() : 0;
        if (before < points) throw new BusinessException(BizCode.MEMBER_POINTS_NOT_ENOUGH, "积分不足");
        int after = before - points;

        casPointsWithRetry(memberId, -points, before, after, 2, member.getVersion(),
                source, refId, remark);
    }

    /**
     * 积分乐观锁重试循环：最多 MAX_OPTIMISTIC_RETRY 次。
     * 成功则写入 PointRecord，返回新增记录 ID。
     * 失败返回 null。
     */
    private Long casPointsWithRetryReturningId(Long memberId, int delta, int before, int after,
                                               int recordType, Integer version,
                                               String source, Long refId, String remark) {
        for (int i = 0; i < MAX_OPTIMISTIC_RETRY; i++) {
            int rows = memberMapper.casPointsWithVersion(memberId, delta, version);
            if (rows > 0) {
                return writePointRecordReturningId(memberId, before, after, recordType, delta, source, refId, remark);
            }
            Member latest = readMember(memberId);
            version = latest.getVersion();
        }
        throw new BusinessException(BizCode.CONCURRENCY_CONFLICT, "积分并发更新失败，请重试");
    }

    private Long casPointsWithRetry(Long memberId, int delta, int before, int after,
                                    int recordType, Integer version,
                                    String source, Long refId, String remark) {
        casPointsWithRetryReturningId(memberId, delta, before, after, recordType, version, source, refId, remark);
        return null;
    }

    private Member readMember(Long memberId) {
        Member m = memberMapper.selectById(memberId);
        if (m == null) throw new BusinessException(BizCode.MEMBER_NOT_FOUND, "会员不存在");
        return m;
    }

    private void writePointRecord(Long memberId, int before, int after,
                                  int recordType, int pointValue,
                                  String source, Long refId, String remark) {
        writePointRecordReturningId(memberId, before, after, recordType, pointValue, source, refId, remark);
    }

    private Long writePointRecordReturningId(Long memberId, int before, int after,
                                             int recordType, int pointValue,
                                             String source, Long refId, String remark) {
        PointRecord record = new PointRecord();
        record.setStoreId(1L);
        record.setMemberId(memberId);
        record.setType(recordType);
        record.setPointValue(pointValue);
        record.setBalanceBefore(before);
        record.setBalanceAfter(after);
        record.setSource(source);
        record.setRefId(refId);
        record.setRemark(remark);
        pointRecordMapper.insert(record);
        return record.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processConsumePoints(Long memberId, Long orderId, Integer amount) {
        LambdaQueryWrapper<PointRule> q = new LambdaQueryWrapper<>();
        q.eq(PointRule::getEventKey, "consume").eq(PointRule::getStatus, 1);
        PointRule rule = pointRuleMapper.selectOne(q);
        if (rule == null) return;
        
        BigDecimal multiplier = rule.getMultiplier() != null ? rule.getMultiplier() : BigDecimal.ONE;
        int points = (int) (amount * multiplier.doubleValue() * rule.getPointValue());
        if (points > 0) {
            addPoints(memberId, "consume", points, orderId, "消费得积分");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processRechargePoints(Long memberId, Long transactionId, Integer amount) {
        LambdaQueryWrapper<PointRule> q = new LambdaQueryWrapper<>();
        q.eq(PointRule::getEventKey, "recharge").eq(PointRule::getStatus, 1);
        PointRule rule = pointRuleMapper.selectOne(q);
        if (rule == null) return;
        
        BigDecimal multiplier = rule.getMultiplier() != null ? rule.getMultiplier() : BigDecimal.ONE;
        int points = (int) (amount * multiplier.doubleValue() * rule.getPointValue());
        if (points > 0) {
            addPoints(memberId, "recharge", points, transactionId, "储值得积分");
        }
    }

    private String sourceText(String source) {
        return switch (source) {
            case "consume" -> "消费得积分";
            case "recharge" -> "储值得积分";
            case "signin" -> "签到得积分";
            case "redeem" -> "积分兑换";
            case "coupon_give" -> "优惠券赠送";
            case "admin" -> "手动调整";
            default -> source;
        };
    }
}
