package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.CrmComplaintTicket;
import com.meiyetec.entity.CrmFollowUpTask;
import com.meiyetec.entity.CrmReview;
import com.meiyetec.mapper.CrmComplaintTicketMapper;
import com.meiyetec.mapper.CrmFollowUpTaskMapper;
import com.meiyetec.mapper.CrmReviewMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.CrmCareOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CrmCareOverviewServiceImpl implements CrmCareOverviewService {

    private final CrmFollowUpTaskMapper taskMapper;
    private final CrmReviewMapper reviewMapper;
    private final CrmComplaintTicketMapper ticketMapper;

    @Override
    public Map<String, Object> overview(Long storeId, Long ownerId) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        Map<String, Object> result = new LinkedHashMap<>();

        LambdaQueryWrapper<CrmFollowUpTask> taskWrapper = new LambdaQueryWrapper<>();
        taskWrapper.eq(CrmFollowUpTask::getDeleted, 0);
        if (storeId != null) taskWrapper.eq(CrmFollowUpTask::getStoreId, storeId);
        if (ownerId != null) taskWrapper.eq(CrmFollowUpTask::getOwnerId, ownerId);

        long pendingFollow = taskMapper.selectCount(taskWrapper.clone()
                .eq(CrmFollowUpTask::getStatus, 1));
        long overdueFollow = taskMapper.selectCount(taskWrapper.clone()
                .eq(CrmFollowUpTask::getStatus, 1)
                .lt(CrmFollowUpTask::getPlannedAt, LocalDateTime.now()));
        long doneFollow = taskMapper.selectCount(taskWrapper.clone()
                .in(CrmFollowUpTask::getStatus, 3, 4));

        result.put("pendingFollow", pendingFollow);
        result.put("overdueFollow", overdueFollow);

        LambdaQueryWrapper<CrmComplaintTicket> ticketWrapper = new LambdaQueryWrapper<>();
        ticketWrapper.eq(CrmComplaintTicket::getDeleted, 0);
        if (storeId != null) ticketWrapper.eq(CrmComplaintTicket::getStoreId, storeId);
        if (ownerId != null) ticketWrapper.eq(CrmComplaintTicket::getHandlerId, ownerId);

        long pendingTicket = ticketMapper.selectCount(ticketWrapper.clone()
                .in(CrmComplaintTicket::getStatus, 1, 2));
        long overdueTicket = ticketMapper.selectCount(ticketWrapper.clone()
                .in(CrmComplaintTicket::getStatus, 1, 2)
                .lt(CrmComplaintTicket::getSlaExpireAt, LocalDateTime.now()));

        result.put("pendingTicket", pendingTicket);
        result.put("overdueTicket", overdueTicket);

        LambdaQueryWrapper<CrmReview> reviewWrapper = new LambdaQueryWrapper<>();
        reviewWrapper.eq(CrmReview::getDeleted, 0);
        if (storeId != null) reviewWrapper.eq(CrmReview::getStoreId, storeId);
        List<CrmReview> reviews = reviewMapper.selectList(reviewWrapper);

        long totalReviews = reviews.size();
        double avgRating = 0;
        long lowRating = 0;
        List<String> lowRatingKeywords = new ArrayList<>();

        if (totalReviews > 0) {
            double sum = reviews.stream()
                    .filter(r -> r.getOverallRating() != null)
                    .mapToInt(CrmReview::getOverallRating)
                    .sum();
            long rated = reviews.stream().filter(r -> r.getOverallRating() != null).count();
            avgRating = rated > 0 ? sum / rated : 0;
            lowRating = reviews.stream()
                    .filter(r -> r.getOverallRating() != null && r.getOverallRating() <= 2)
                    .count();
            reviews.stream()
                    .filter(r -> r.getOverallRating() != null && r.getOverallRating() <= 2)
                    .filter(r -> r.getTags() != null && !r.getTags().isEmpty())
                    .limit(10)
                    .forEach(r -> {
                        for (String tag : r.getTags().split(",")) {
                            if (!lowRatingKeywords.contains(tag.trim())) {
                                lowRatingKeywords.add(tag.trim());
                            }
                        }
                    });
        }

        result.put("avgRating", BigDecimal.valueOf(avgRating).setScale(1, RoundingMode.HALF_UP).doubleValue());
        result.put("totalReviews", totalReviews);
        result.put("lowRating", lowRating);
        result.put("lowRatingKeywords", lowRatingKeywords);

        Map<String, Object> funnel = new LinkedHashMap<>();
        funnel.put("total", pendingFollow + overdueFollow + doneFollow);
        funnel.put("pending", pendingFollow);
        funnel.put("overdue", overdueFollow);
        funnel.put("doneFollow", doneFollow);
        result.put("funnel", funnel);

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1);
        List<Map<String, Object>> todayFollow = new ArrayList<>();
        taskMapper.selectList(taskWrapper.clone()
                        .eq(CrmFollowUpTask::getStatus, 1)
                        .ge(CrmFollowUpTask::getPlannedAt, todayStart)
                        .lt(CrmFollowUpTask::getPlannedAt, todayEnd)
                        .orderByAsc(CrmFollowUpTask::getPlannedAt)
                        .last("LIMIT 20"))
                .forEach(t -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", t.getId());
                    item.put("taskNo", t.getTaskNo());
                    item.put("memberName", t.getMemberName());
                    item.put("memberPhone", t.getMemberPhone());
                    item.put("subject", t.getSubject());
                    item.put("plannedAt", t.getPlannedAt());
                    item.put("ownerName", t.getOwnerName());
                    item.put("status", t.getStatus());
                    todayFollow.add(item);
                });
        result.put("todayFollow", todayFollow);

        List<Map<String, Object>> todayTicket = new ArrayList<>();
        ticketMapper.selectList(ticketWrapper.clone()
                        .in(CrmComplaintTicket::getStatus, 1, 2)
                        .ge(CrmComplaintTicket::getSlaExpireAt, todayStart)
                        .lt(CrmComplaintTicket::getSlaExpireAt, todayEnd)
                        .orderByAsc(CrmComplaintTicket::getSlaExpireAt)
                        .last("LIMIT 20"))
                .forEach(t -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", t.getId());
                    item.put("ticketNo", t.getTicketNo());
                    item.put("memberName", t.getMemberName());
                    item.put("complaintType", t.getComplaintType());
                    item.put("slaExpireAt", t.getSlaExpireAt());
                    item.put("handlerName", t.getHandlerName());
                    item.put("status", t.getStatus());
                    todayTicket.add(item);
                });
        result.put("todayTicket", todayTicket);

        List<Map<String, Object>> lowRatingList = new ArrayList<>();
        reviewMapper.selectList(reviewWrapper.clone()
                        .le(CrmReview::getOverallRating, 2)
                        .orderByDesc(CrmReview::getCreateTime)
                        .last("LIMIT 20"))
                .forEach(r -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", r.getId());
                    item.put("memberName", r.getMemberName());
                    item.put("overallRating", r.getOverallRating());
                    item.put("content", r.getContent());
                    item.put("tags", r.getTags());
                    item.put("createTime", r.getCreateTime());
                    lowRatingList.add(item);
                });
        result.put("lowRatingList", lowRatingList);

        Map<String, Object> matrix = new LinkedHashMap<>();
        if (storeId == null) {
            for (Long sId : Arrays.asList(1L, 2L, 3L)) {
                Map<String, Object> cell = new LinkedHashMap<>();
                LambdaQueryWrapper<CrmFollowUpTask> sw = new LambdaQueryWrapper<CrmFollowUpTask>()
                        .eq(CrmFollowUpTask::getDeleted, 0)
                        .eq(CrmFollowUpTask::getStoreId, sId);
                if (ownerId != null) sw.eq(CrmFollowUpTask::getOwnerId, ownerId);
                long pending = taskMapper.selectCount(sw.clone().eq(CrmFollowUpTask::getStatus, 1));
                long overdue = taskMapper.selectCount(sw.clone()
                        .eq(CrmFollowUpTask::getStatus, 1)
                        .lt(CrmFollowUpTask::getPlannedAt, LocalDateTime.now()));
                cell.put("pendingFollow", pending);
                cell.put("overdueFollow", overdue);
                cell.put("doneFollow", taskMapper.selectCount(sw.clone().in(CrmFollowUpTask::getStatus, 3, 4)));
                matrix.put("store_" + sId, cell);
            }
        }
        result.put("matrix", matrix);

        List<String> dates = new ArrayList<>();
        List<Integer> followTrend = new ArrayList<>();
        List<Integer> ticketTrend = new ArrayList<>();
        List<Double> reviewTrend = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");

        for (int i = 6; i >= 0; i--) {
            LocalDate d = LocalDate.now().minusDays(i);
            String label = d.format(fmt);
            dates.add(label);

            LocalDateTime dayStart = d.atStartOfDay();
            LocalDateTime dayEnd = dayStart.plusDays(1);

            long done = taskMapper.selectCount(new LambdaQueryWrapper<CrmFollowUpTask>()
                    .eq(CrmFollowUpTask::getDeleted, 0)
                    .in(CrmFollowUpTask::getStatus, 3, 4)
                    .ge(CrmFollowUpTask::getFinishedAt, dayStart)
                    .lt(CrmFollowUpTask::getFinishedAt, dayEnd));
            if (storeId != null) done = taskMapper.selectCount(new LambdaQueryWrapper<CrmFollowUpTask>()
                    .eq(CrmFollowUpTask::getDeleted, 0).eq(CrmFollowUpTask::getStoreId, storeId)
                    .in(CrmFollowUpTask::getStatus, 3, 4)
                    .ge(CrmFollowUpTask::getFinishedAt, dayStart)
                    .lt(CrmFollowUpTask::getFinishedAt, dayEnd));
            followTrend.add((int) done);

            long newTicket = ticketMapper.selectCount(new LambdaQueryWrapper<CrmComplaintTicket>()
                    .eq(CrmComplaintTicket::getDeleted, 0)
                    .ge(CrmComplaintTicket::getCreateTime, dayStart)
                    .lt(CrmComplaintTicket::getCreateTime, dayEnd));
            if (storeId != null) newTicket = ticketMapper.selectCount(new LambdaQueryWrapper<CrmComplaintTicket>()
                    .eq(CrmComplaintTicket::getDeleted, 0).eq(CrmComplaintTicket::getStoreId, storeId)
                    .ge(CrmComplaintTicket::getCreateTime, dayStart)
                    .lt(CrmComplaintTicket::getCreateTime, dayEnd));
            ticketTrend.add((int) newTicket);

            long newReview = reviewMapper.selectCount(new LambdaQueryWrapper<CrmReview>()
                    .eq(CrmReview::getDeleted, 0)
                    .ge(CrmReview::getCreateTime, dayStart)
                    .lt(CrmReview::getCreateTime, dayEnd));
            if (storeId != null) newReview = reviewMapper.selectCount(new LambdaQueryWrapper<CrmReview>()
                    .eq(CrmReview::getDeleted, 0).eq(CrmReview::getStoreId, storeId)
                    .ge(CrmReview::getCreateTime, dayStart)
                    .lt(CrmReview::getCreateTime, dayEnd));
            reviewTrend.add((double) newReview);
        }

        result.put("followTrend", followTrend);
        result.put("ticketTrend", ticketTrend);
        result.put("reviewTrend", reviewTrend);

        result.put("unreadMsg", 0);
        return result;
    }
}
