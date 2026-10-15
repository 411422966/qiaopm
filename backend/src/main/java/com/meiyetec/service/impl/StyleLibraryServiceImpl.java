package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.*;
import com.meiyetec.exception.BizCode;
import com.meiyetec.exception.BusinessException;
import com.meiyetec.mapper.*;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StyleLibraryServiceImpl extends ServiceImpl<StyleLibraryMapper, StyleLibrary>
        implements StyleLibraryService {

    private final StyleLibraryMapper styleLibraryMapper;
    private final MemberStylePreferenceMapper preferenceMapper;
    private final StyleBrowseLogMapper browseLogMapper;
    private final MaterialSubstituteService materialSubstituteService;
    private final BookingService bookingService;
    private final EmployeeService employeeService;
    private final JdbcTemplate jdbc;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DT_FMT  = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public List<StyleLibrary> queryList(String styleType, String styleTag, Integer difficultyLevel,
                                       Integer status, Integer isNew, Integer isHot) {
        return styleLibraryMapper.queryList(styleType, styleTag, difficultyLevel, status, isNew, isHot);
    }

    @Override
    public List<StyleLibrary> findHotStyles() {
        return styleLibraryMapper.findHotStyles();
    }

    @Override
    public List<Map<String, Object>> statisticsByType(Long storeId) {
        return styleLibraryMapper.statisticsByType(storeId);
    }

    @Override
    public void recordView(Long styleId) {
        styleLibraryMapper.incrementViewCount(styleId);
    }

    @Override
    public void recordFavorite(Long styleId) {
        styleLibraryMapper.incrementFavoriteCount(styleId);
    }

    @Override
    public List<StyleLibrary> recommend(Long memberId, String styleType, int limit) {
        MemberStylePreference pref = preferenceMapper.findByMember(memberId);

        List<StyleLibrary> allStyles = styleLibraryMapper.queryList(styleType, null, null, 1, null, null);
        if (allStyles.isEmpty()) return new ArrayList<>();

        List<Map<String, Object>> browseHistory = browseLogMapper.topBrowsedStyles(memberId, 20);
        Map<Long, Integer> historyMap = new HashMap<>();
        for (Map<String, Object> b : browseHistory) {
            Long sid = ((Number) b.get("style_id")).longValue();
            Integer cnt = ((Number) b.get("view_count")).intValue();
            historyMap.put(sid, cnt);
        }

        Set<String> preferredStyles = new HashSet<>();
        Set<String> preferredColors = new HashSet<>();
        if (pref != null) {
            if (pref.getPreferredStyles() != null) {
                preferredStyles.addAll(Arrays.asList(pref.getPreferredStyles().split(",")));
            }
            if (pref.getPreferredColors() != null) {
                preferredColors.addAll(Arrays.asList(pref.getPreferredColors().split(",")));
            }
        }

        return allStyles.stream()
                .sorted((a, b) -> {
                    double scoreA = calculateScore(a, preferredStyles, preferredColors, historyMap);
                    double scoreB = calculateScore(b, preferredStyles, preferredColors, historyMap);
                    return Double.compare(scoreB, scoreA);
                })
                .limit(limit)
                .collect(Collectors.toList());
    }

    private double calculateScore(StyleLibrary style, Set<String> prefStyles,
                                  Set<String> prefColors, Map<Long, Integer> historyMap) {
        double score = 0;
        if (style.getStyleTag() != null && prefStyles.contains(style.getStyleTag())) {
            score += 50;
        }
        if (style.getColorScheme() != null && !prefColors.isEmpty()) {
            for (String c : prefColors) {
                if (style.getColorScheme().contains(c)) {
                    score += 30;
                    break;
                }
            }
        }
        if (style.getUseCount() != null) {
            score += Math.min(style.getUseCount() / 50.0, 30);
        }
        if (style.getIsNew() != null && style.getIsNew() == 1) {
            score += 15;
        }
        if (historyMap.containsKey(style.getId())) {
            score -= 10;
        }
        if (style.getRatingScore() != null) {
            score += style.getRatingScore().doubleValue() * 5;
        }
        return score;
    }

    @Override
    public int toggleLike(Long styleId) {
        StyleLibrary style = styleLibraryMapper.selectById(styleId);
        if (style == null) {
            return 0;
        }
        int likes = style.getFavoriteCount() != null ? style.getFavoriteCount() : 0;
        styleLibraryMapper.incrementLikeCount(styleId, 1);
        return likes + 1;
    }

    @Override
    public int initDefault() {
        int existing = styleLibraryMapper.selectCount(null).intValue();
        if (existing > 0) {
            return 0;
        }
        Long tenantId = TenantContextHolder.get().getTenantId();
        Long storeId = TenantContextHolder.get().getStoreId();

        List<StyleLibrary> seeds = Arrays.asList(
            make(tenantId, storeId, "NA-001", "经典法式白边", "NAIL_ART", "简约", "[\"白色\",\"粉色\"]", 2, 60, "FRENCH", "[\"指甲锉\",\"底胶\",\"白色彩绘胶\",\"封层\"]", true, true),
            make(tenantId, storeId, "NA-002", "璀璨星空渐变", "NAIL_ART", "仙气", "[\"紫色\",\"蓝色\",\"银色\"]", 3, 90, "GRADIENT", "[\"星空猫眼胶\",\"渐变海绵\",\"亮片\",\"封层\"]", true, false),
            make(tenantId, storeId, "NA-003", "晕染琥珀石", "NAIL_ART", "复古", "[\"橙色\",\"棕色\",\"金色\"]", 3, 75, "MARBLE", "[\"琥珀色甲油胶\",\"晕染笔\",\"金箔\",\"封层\"]", false, true),
            make(tenantId, storeId, "NA-004", "纯欲裸色系", "NAIL_ART", "甜美", "[\"裸粉\",\"奶茶色\",\"玫瑰粉\"]", 1, 45, "MATTE", "[\"裸色系甲油胶\",\"磨砂封层\"]", true, true),
            make(tenantId, storeId, "NA-005", "镜面银魅", "NAIL_ART", "酷感", "[\"银色\",\"黑色\"]", 2, 50, "CHROME", "[\"镜面银\",\"镜面黑\",\"加固底胶\",\"封层\"]", false, true),
            make(tenantId, storeId, "NA-006", "手绘卡通小熊", "NAIL_ART", "可爱", "[\"棕色\",\"粉色\",\"白色\"]", 4, 120, "ART", "[\"彩绘笔\",\"棕色/粉色/白色甲油胶\",\"眼睛笔\"]", false, false),
            make(tenantId, storeId, "NA-007", "小香风格纹", "NAIL_ART", "高级", "[\"黑色\",\"白色\",\"金色\"]", 3, 80, "TARTAN", "[\"黑色甲油胶\",\"白色画笔\",\"格纹贴纸\",\"金箔\",\"封层\"]", true, false),
            make(tenantId, storeId, "NA-008", "腮红微渐变", "NAIL_ART", "纯欲", "[\"粉色\",\"裸色\",\"珊瑚色\"]", 1, 55, "BLUSH", "[\"腮红甲油胶\",\"海绵\",\"渐变笔\",\"封层\"]", true, true)
        );
        for (StyleLibrary s : seeds) {
            styleLibraryMapper.insert(s);
        }
        return seeds.size();
    }

    private StyleLibrary make(Long tenantId, Long storeId, String code, String name,
                             String type, String tag, String colors, int difficulty,
                             int minutes, String techLevel, String tools,
                             boolean isHot, boolean isNew) {
        StyleLibrary s = new StyleLibrary();
        s.setTenantId(tenantId);
        s.setStoreId(storeId);
        s.setStyleCode(code);
        s.setStyleName(name);
        s.setStyleType(type);
        s.setStyleTag(tag);
        s.setColorScheme(colors);
        s.setDifficultyLevel(difficulty);
        s.setDurationMinutes(minutes);
        s.setBasePrice(new BigDecimal("68.00"));
        s.setVipPrice(new BigDecimal("88.00"));
        s.setMainImage("");
        s.setDescription("默认初始化款式：" + name);
        s.setOperationSteps("标准服务流程，参考" + name);
        s.setRequiredTools(tools);
        s.setTechnicianLevel(techLevel);
        s.setViewCount(0);
        s.setFavoriteCount(0);
        s.setUseCount(0);
        s.setRatingScore(new BigDecimal("5.0"));
        s.setStatus(1);
        s.setIsHot(isHot ? 1 : 0);
        s.setIsNew(isNew ? 1 : 0);
        s.setPublishEmployeeId(1L);
        s.setPublishEmployeeName("系统管理员");
        s.setPublishTime(LocalDateTime.now());
        return s;
    }

    @Override
    public Map<String, Object> quickOrder(Long styleId, Long memberId, Long technicianId, Long storeId) {
        Map<String, Object> result = new HashMap<>();
        result.put("orderUrl", "/booking/booking-form?styleId=" + styleId + "&memberId=" + memberId);
        result.put("styleId", styleId);
        result.put("memberId", memberId);
        return result;
    }

    /**
     * P2-12 IND-12：完整快速下单
     *
     * <p>链路：
     * <ol>
     *   <li>校验款式存在且上架</li>
     *   <li>解析 requiredTools → MaterialSubstituteService.resolveMaterials()</li>
     *   <li>若传入 bookingDate + startTime → 创建 Booking（含技师归属校验）</li>
     *   <li>返回 款式信息 + 技师信息 + 耗材列表 + 预约信息</li>
     * </ol>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> quickOrderFull(Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();

        Long tenantId = TenantContextHolder.get().getTenantId();
        if (tenantId == null) {
            throw new BusinessException(BizCode.UNAUTHORIZED, "未识别租户");
        }

        // 1. 解析请求参数
        Object styleIdObj = body.get("styleId");
        Object memberIdObj = body.get("memberId");
        Object technicianIdObj = body.get("technicianId");
        Object storeIdObj = body.get("storeId");
        String bookingDateStr = body.get("bookingDate") != null
                ? String.valueOf(body.get("bookingDate")) : null;
        String startTimeStr = body.get("startTime") != null
                ? String.valueOf(body.get("startTime")) : null;
        String remark = body.get("remark") != null
                ? String.valueOf(body.get("remark")) : "";

        if (styleIdObj == null) {
            throw new BusinessException(BizCode.BAD_REQUEST, "styleId 必填");
        }
        Long styleId = ((Number) styleIdObj).longValue();
        Long memberId = memberIdObj != null ? ((Number) memberIdObj).longValue() : null;
        Long technicianId = technicianIdObj != null ? ((Number) technicianIdObj).longValue() : null;
        Long storeId = storeIdObj != null ? ((Number) storeIdObj).longValue() : null;

        // 2. 查款式
        StyleLibrary style = styleLibraryMapper.selectById(styleId);
        if (style == null) {
            throw new BusinessException(BizCode.NOT_FOUND, "款式不存在");
        }
        if (style.getStatus() == null || style.getStatus() != 1) {
            throw new BusinessException(BizCode.BAD_REQUEST, "该款式已下架，请选择其他款式");
        }
        result.put("styleId", style.getId());
        result.put("styleName", style.getStyleName());
        result.put("styleCode", style.getStyleCode());
        result.put("basePrice", style.getBasePrice());
        result.put("vipPrice", style.getVipPrice());
        result.put("durationMinutes", style.getDurationMinutes());
        result.put("colorScheme", style.getColorScheme());
        result.put("category", style.getStyleType());
        result.put("requiredTools", style.getRequiredTools());

        // 3. 解析工具名称 → 库存商品 → 耗材（含替代）
        List<Map<String, Object>> materials = materialSubstituteService.getMaterialsForStyle(styleId, tenantId);
        result.put("materials", materials);
        result.put("materialCount", materials.size());
        boolean hasMaterialWarning = materials.stream()
                .anyMatch(m -> m.get("isSubstitute") == Boolean.TRUE
                        || m.get("substituteReason") != null);
        result.put("materialWarning", hasMaterialWarning ? "部分耗材库存不足，已启用替代方案或需补货" : null);

        // 4. 技师信息（如果指定了 technicianId）
        String technicianName = null;
        if (technicianId != null) {
            Employee emp = employeeService.getById(technicianId);
            if (emp != null) {
                technicianName = emp.getEmployeeName();
                // 技师所属门店校验（参考 P0-3）
                Store store = null;
                try {
                    store = jdbc.queryForObject(
                            "SELECT id, tenant_id FROM pos_store WHERE id = ?",
                            (rs, rowNum) -> {
                                Store s = new Store();
                                s.setId(rs.getLong("id"));
                                s.setTenantId(rs.getLong("tenant_id"));
                                return s;
                            },
                            emp.getStoreId());
                } catch (Exception ignored) {}
                if (store != null && !tenantId.equals(store.getTenantId())) {
                    throw new BusinessException(BizCode.FORBIDDEN,
                            "该技师所属门店不属于当前租户");
                }
                result.put("technicianId", emp.getId());
                result.put("technicianName", emp.getEmployeeName());
                result.put("technicianLevel", emp.getPosition());
            }
        }

        // 5. 若传了预约时间 → 创建预约
        Booking createdBooking = null;
        if (bookingDateStr != null && startTimeStr != null) {
            if (memberId == null) {
                throw new BusinessException(BizCode.BAD_REQUEST,
                        "创建预约需要传入 memberId");
            }
            // 解析时间
            LocalDate bookingDate = LocalDate.parse(bookingDateStr);
            LocalTime startTime = LocalTime.parse(startTimeStr, TIME_FMT);
            int durationMinutes = style.getDurationMinutes() != null ? style.getDurationMinutes() : 60;
            LocalDateTime startDateTime = LocalDateTime.of(bookingDate, startTime);
            LocalDateTime endDateTime = startDateTime.plusMinutes(durationMinutes);

            // storeId 兜底
            if (storeId == null) {
                storeId = style.getStoreId() != null ? style.getStoreId()
                        : TenantContextHolder.getStoreIdOrNull();
            }

            // 查会员信息（用于填充预约姓名/电话）
            String memberName = null;
            String memberPhone = null;
            try {
                Map<String, Object> memberInfo = jdbc.queryForMap(
                        "SELECT member_name, phone FROM crm_member WHERE id = ? AND deleted = 0 LIMIT 1",
                        memberId);
                memberName = (String) memberInfo.get("member_name");
                memberPhone = (String) memberInfo.get("phone");
            } catch (Exception ignored) {}

            // 构建预约对象（复用 BookingService.createWithConflictCheck）
            Booking b = new Booking();
            b.setTenantId(tenantId);
            b.setStoreId(storeId);
            b.setMemberId(memberId);
            b.setMemberName(memberName);
            b.setMemberPhone(memberPhone);
            b.setEmployeeId(technicianId);
            b.setEmployeeName(technicianName);
            b.setServiceItem(style.getStyleName());
            b.setBookingDate(bookingDate);
            b.setStartTime(startDateTime);
            b.setEndTime(endDateTime);
            b.setStatus(1);  // 待确认
            b.setSource(1);  // 会员自助
            b.setRemark("[款式下单] " + style.getStyleName() + " " + remark);
            b.setOperatorId(TenantContextHolder.get().getUserId());
            b.setBookingNo("BK" + System.currentTimeMillis());

            try {
                var bookingResult = bookingService.createWithConflictCheck(b);
                createdBooking = (Booking) bookingResult.getData();
            } catch (BusinessException be) {
                // 冲突 → 转化为警告，不阻断返回
                result.put("bookingWarning", be.getMessage());
                log.warn("[StyleLibrary] 快速下单预约冲突 styleId={} memberId={}: {}",
                        styleId, memberId, be.getMessage());
            }

            if (createdBooking != null) {
                result.put("bookingId", createdBooking.getId());
                result.put("bookingNo", createdBooking.getBookingNo());
                result.put("bookingDate", bookingDateStr);
                result.put("startTime", startTimeStr);
                result.put("endTime", endDateTime.format(TIME_FMT));
                result.put("bookingStatus", createdBooking.getStatus());
            }
        }

        // 6. 更新款式使用次数
        if (createdBooking != null) {
            jdbc.update("UPDATE biz_style_library SET use_count = use_count + 1 WHERE id = ?", styleId);
        }

        result.put("message", createdBooking != null
                ? "预约成功（待确认）"
                : "款式信息已加载，请在选择预约时间后提交");
        return result;
    }
}
