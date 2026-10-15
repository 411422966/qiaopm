package com.meiyetec.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.meiyetec.entity.MemberMessage;
import com.meiyetec.mapper.MemberMessageMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.PageResult;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * M09-01：站内信管理 API
 * 提供站内信查询、标记已读、未读数统计
 */
@RestController
@RequestMapping("/member/message")
@RequiredArgsConstructor
public class MemberMessageController {

    private final MemberMessageMapper messageMapper;
    private final JdbcTemplate jdbc;

    private Long tenantId() {
        try { return TenantContextHolder.get().getTenantId(); } catch (Exception e) { return null; }
    }

    /**
     * GET /member/message/page
     * 会员站内信分页列表
     */
    @GetMapping("/page")
    public Result<PageResult<MemberMessage>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Long memberId,
            @RequestParam(required = false) Integer isRead,
            @RequestParam(required = false) String messageType,
            @RequestParam(required = false) String keyword) {

        Long tid = tenantId();
        Page<MemberMessage> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<MemberMessage> q = new LambdaQueryWrapper<>();
        if (tid != null) q.eq(MemberMessage::getTenantId, tid);
        if (memberId != null) q.eq(MemberMessage::getMemberId, memberId);
        if (isRead != null) q.eq(MemberMessage::getIsRead, isRead == 1);
        if (messageType != null && !messageType.isEmpty()) q.eq(MemberMessage::getMessageType, messageType);
        if (keyword != null && !keyword.isEmpty()) {
            q.and(w -> w.like(MemberMessage::getTitle, keyword).or().like(MemberMessage::getContent, keyword));
        }
        // 未过期的优先
        q.and(w -> w.isNull(MemberMessage::getExpireAt).or().ge(MemberMessage::getExpireAt, LocalDateTime.now()));
        q.orderByDesc(MemberMessage::getIsRead, MemberMessage::getCreatedAt);

        Page<MemberMessage> result = messageMapper.selectPage(page, q);
        return Result.success(PageResult.of(result));
    }

    /**
     * GET /member/message/unread-count
     * 未读消息数量
     */
    @GetMapping("/unread-count")
    public Result<Integer> unreadCount(@RequestParam Long memberId) {
        Long tid = tenantId();
        LambdaQueryWrapper<MemberMessage> q = new LambdaQueryWrapper<>();
        q.eq(MemberMessage::getMemberId, memberId)
         .eq(MemberMessage::getIsRead, false)
         .and(w -> w.isNull(MemberMessage::getExpireAt).or().ge(MemberMessage::getExpireAt, LocalDateTime.now()));
        if (tid != null) q.eq(MemberMessage::getTenantId, tid);
        long count = messageMapper.selectCount(q);
        return Result.success((int) count);
    }

    /**
     * POST /member/message/{id}/read
     * 标记已读
     */
    @PostMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        MemberMessage msg = messageMapper.selectById(id);
        if (msg == null) return Result.error("消息不存在");
        if (msg.getIsRead() != null && msg.getIsRead()) {
            return Result.success("已读", null); // 幂等
        }
        msg.setIsRead(true);
        msg.setReadAt(LocalDateTime.now());
        messageMapper.updateById(msg);
        return Result.success("已标记已读", null);
    }

    /**
     * POST /member/message/read-all
     * 标记全部已读
     */
    @PostMapping("/read-all")
    public Result<Integer> markAllRead(@RequestParam Long memberId) {
        Long tid = tenantId();
        StringBuilder sql = new StringBuilder("UPDATE member_message SET is_read=1, read_at=NOW() WHERE member_id=? AND is_read=0 AND deleted=0");
        if (tid != null) sql.append(" AND tenant_id=").append(tid);
        int affected = jdbc.update(sql.toString(), memberId);
        return Result.success("已标记 " + affected + " 条已读", null);
    }

    /**
     * DELETE /member/message/{id}
     * 删除消息
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        messageMapper.deleteById(id);
        return Result.success("已删除", null);
    }
}
