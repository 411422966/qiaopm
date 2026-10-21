package com.meiyetec.controller;

import com.meiyetec.annotation.RequirePermission;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.StyleGallery;
import com.meiyetec.entity.StyleViewLog;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.StyleGalleryMapper;
import com.meiyetec.mapper.StyleViewLogMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/gallery")
@RequiredArgsConstructor
public class StyleGalleryController {

    private final StyleGalleryMapper styleMapper;
    private final StyleViewLogMapper viewLogMapper;

    @Autowired
    private JdbcTemplate jdbc;

    @PostMapping
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("style:gallery:edit")
    public Result<StyleGallery> create(@RequestBody StyleGallery s) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        if (s.getStyleCode() == null || s.getStyleName() == null)
            return Result.error(BizCode.BAD_REQUEST, "编码/名称必填");
        if (s.getStatus() == null) s.setStatus("ON_SHELF");
        if (s.getDifficulty() == null) s.setDifficulty(1);
        if (s.getEstimatedMinutes() == null) s.setEstimatedMinutes(60);
        s.setTenantId(tenantId);
        s.setId(null);
        s.setViewCount(0);
        s.setOrderCount(0);
        s.setLikeCount(0);
        styleMapper.insert(s);
        return Result.success("已创建", s);
    }

    @GetMapping("/{id}")
    @RequirePermission("style:gallery:list")
    public Result<StyleGallery> get(@PathVariable Long id) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StyleGallery s = styleMapper.selectById(id);
        if (s == null || !s.getTenantId().equals(tenantId))
            return Result.error(BizCode.NOT_FOUND, "款式不存在");
        jdbc.update("UPDATE style_gallery SET view_count = view_count + 1 WHERE id = ?", id);
        s.setViewCount(s.getViewCount() + 1);
        return Result.success(s);
    }

    @PostMapping("/{id}/view")
    @RequirePermission("style:gallery:edit")
    public Result<Void> logView(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StyleViewLog viewLog = new StyleViewLog();
        viewLog.setTenantId(tenantId);
        viewLog.setStyleId(id);
        if (body != null) {
            viewLog.setMemberId(body.get("memberId") == null ? null : ((Number) body.get("memberId")).longValue());
            viewLog.setOpenId((String) body.get("openId"));
            viewLog.setSource((String) body.getOrDefault("source", "H5"));
        }
        viewLogMapper.insert(viewLog);
        return Result.success("已记录", null);
    }

    @PostMapping("/{id}/like")
    @RequirePermission("style:gallery:edit")
    public Result<Void> like(@PathVariable Long id) {
        jdbc.update("UPDATE style_gallery SET like_count = like_count + 1 WHERE id = ?", id);
        return Result.success("已点赞", null);
    }

    @GetMapping("/list")
    @RequirePermission("style:gallery:list")
    public Result<List<StyleGallery>> list(@RequestParam(required = false) String category,
                                            @RequestParam(required = false) String tag,
                                            @RequestParam(defaultValue = "HOT") String sort) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        LambdaQueryWrapper<StyleGallery> qw = new LambdaQueryWrapper<StyleGallery>()
                .eq(StyleGallery::getTenantId, tenantId)
                .eq(StyleGallery::getStatus, "ON_SHELF");
        if (category != null && !category.isEmpty()) qw.eq(StyleGallery::getCategory, category);
        if (tag != null && !tag.isEmpty()) qw.like(StyleGallery::getTags, tag);
        if ("VIEW".equals(sort)) qw.orderByDesc(StyleGallery::getViewCount);
        else if ("NEW".equals(sort)) qw.orderByDesc(StyleGallery::getCreatedAt);
        else qw.orderByDesc(StyleGallery::getViewCount).orderByDesc(StyleGallery::getLikeCount);
        return Result.success(styleMapper.selectList(qw));
    }

    @PostMapping("/{id}/order")
    @RequirePermission("style:gallery:edit")
    public Result<Map<String, Object>> quickOrder(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        StyleGallery s = styleMapper.selectById(id);
        if (s == null || !s.getTenantId().equals(tenantId))
            return Result.error(BizCode.NOT_FOUND, "款式不存在");
        jdbc.update("UPDATE style_gallery SET order_count = order_count + 1 WHERE id = ?", id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("styleId", id);
        data.put("styleName", s.getStyleName());
        data.put("price", s.getBasePrice());
        data.put("estimatedMinutes", s.getEstimatedMinutes());
        data.put("orderUrl", "/booking/create?styleId=" + id);
        return Result.success("已跳转", data);
    }

    @PostMapping("/init-default")
    @Transactional(rollbackFor = Exception.class)
    @RequirePermission("style:gallery:edit")
    public Result<Map<String, Object>> initDefault() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        List<Map<String, Object>> defaults = List.of(
                Map.of("code", "FRENCH_CLASSIC", "name", "法式经典", "category", "FRENCH", "difficulty", 2, "minutes", 60, "price", 168, "tag", "HOT,CLASSIC", "desc", "经典法式白边，永不过时"),
                Map.of("code", "GLITTER_PARTY", "name", "闪粉派对", "category", "GLITTER", "difficulty", 3, "minutes", 75, "price", 238, "tag", "HOT,GLITTER", "desc", "Bling Bling 闪粉"),
                Map.of("code", "MATTE_OLIVE", "name", "哑光橄榄绿", "category", "MATTE", "difficulty", 2, "minutes", 50, "price", 138, "tag", "NEW", "desc", "高级哑光质感"),
                Map.of("code", "CHROME_MIRROR", "name", "镜面铬色", "category", "CHROME", "difficulty", 4, "minutes", 90, "price", 288, "tag", "RECOMMEND", "desc", "未来感镜面"),
                Map.of("code", "CAT_3D", "name", "3D 猫咪", "category", "3D", "difficulty", 5, "minutes", 120, "price", 388, "tag", "HOT,ART", "desc", "立体造型猫咪"),
                Map.of("code", "GRADIENT_PINK", "name", "渐变樱花粉", "category", "ART", "difficulty", 4, "minutes", 75, "price", 218, "tag", "SEASONAL,SPRING", "desc", "渐变樱花"),
                Map.of("code", "SPRING_FLORAL", "name", "春意小花", "category", "ART", "difficulty", 3, "minutes", 70, "price", 198, "tag", "SEASONAL,SPRING", "desc", "手绘小花")
        );
        int created = 0;
        for (Map<String, Object> d : defaults) {
            StyleGallery exist = styleMapper.selectOne(new LambdaQueryWrapper<StyleGallery>()
                    .eq(StyleGallery::getTenantId, tenantId)
                    .eq(StyleGallery::getStyleCode, d.get("code")));
            if (exist != null) continue;
            StyleGallery s = new StyleGallery();
            s.setTenantId(tenantId);
            s.setStyleCode((String) d.get("code"));
            s.setStyleName((String) d.get("name"));
            s.setCategory((String) d.get("category"));
            s.setDifficulty((Integer) d.get("difficulty"));
            s.setEstimatedMinutes((Integer) d.get("minutes"));
            s.setBasePrice(new java.math.BigDecimal(d.get("price").toString()));
            s.setTags((String) d.get("tag"));
            s.setDescription((String) d.get("desc"));
            if (((String) d.get("tag")).contains("HOT")) s.setIsHot(1);
            if (((String) d.get("tag")).contains("NEW")) s.setIsNew(1);
            if (((String) d.get("tag")).contains("RECOMMEND")) s.setIsRecommend(1);
            s.setSeason("ALL");
            s.setGender("FEMALE");
            s.setStatus("ON_SHELF");
            styleMapper.insert(s);
            created++;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("created", created);
        return Result.success("已初始化", data);
    }
}