package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.meiyetec.entity.I18nLocale;
import com.meiyetec.entity.I18nTranslation;
import com.meiyetec.exception.BizCode;
import com.meiyetec.mapper.I18nLocaleMapper;
import com.meiyetec.mapper.I18nTranslationMapper;
import com.meiyetec.security.TenantContextHolder;
import com.meiyetec.util.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * R3.0-H P2.4：i18n 多语言服务
 *
 * <p>提供语言管理 + 翻译查询 + 内存缓存。
 */
@Slf4j
@RestController
@RequestMapping("/i18n")
@RequiredArgsConstructor
public class I18nService {

    private final I18nLocaleMapper localeMapper;
    private final I18nTranslationMapper translationMapper;

    @Autowired
    private JdbcTemplate jdbc;

    // 翻译内存缓存（localeCode → messageKey → value）
    private static final Map<String, Map<String, String>> CACHE = new ConcurrentHashMap<>();

    // ====================================================================
    // 语言管理
    // ====================================================================

    @GetMapping("/locale/list")
    public Result<List<I18nLocale>> listLocales() {
        Long tenantId = TenantContextHolder.get().getTenantId();
        return Result.success(localeMapper.selectList(
                new LambdaQueryWrapper<I18nLocale>()
                        .eq(I18nLocale::getTenantId, tenantId)
                        .eq(I18nLocale::getEnabled, 1)
                        .orderByAsc(I18nLocale::getSortOrder)));
    }

    @PostMapping("/locale")
    public Result<I18nLocale> createLocale(@RequestBody I18nLocale l) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        l.setTenantId(tenantId);
        if (l.getEnabled() == null) l.setEnabled(1);
        if (l.getRtl() == null) l.setRtl(0);
        if (l.getSortOrder() == null) l.setSortOrder(99);

        // 唯一性
        I18nLocale exist = localeMapper.selectOne(
                new LambdaQueryWrapper<I18nLocale>()
                        .eq(I18nLocale::getTenantId, tenantId)
                        .eq(I18nLocale::getLocaleCode, l.getLocaleCode()));
        if (exist != null) return Result.error(BizCode.CONFLICT, "localeCode 已存在");

        // 如设为默认，其他取消默认
        if (l.getIsDefault() != null && l.getIsDefault() == 1) {
            jdbc.update("UPDATE i18n_locale SET is_default=0 WHERE tenant_id=?",
                    tenantId);
        }
        localeMapper.insert(l);
        invalidateCache(tenantId, l.getLocaleCode());
        return Result.success("已创建", l);
    }

    @PostMapping("/locale/{id}/set-default")
    public Result<Void> setDefault(@PathVariable Long id) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        I18nLocale l = localeMapper.selectById(id);
        if (l == null) return Result.error(BizCode.NOT_FOUND, "语言不存在");
        jdbc.update("UPDATE i18n_locale SET is_default=0 WHERE tenant_id=?", tenantId);
        jdbc.update("UPDATE i18n_locale SET is_default=1 WHERE id=?", id);
        invalidateCache(tenantId, l.getLocaleCode());
        return Result.success("已设为默认", null);
    }

    // ====================================================================
    // 翻译条目
    // ====================================================================

    @PostMapping("/translation")
    @Transactional(rollbackFor = Exception.class)
    public Result<I18nTranslation> upsertTranslation(@RequestBody I18nTranslation t) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        t.setTenantId(tenantId);
        if (t.getEnabled() == null) t.setEnabled(1);

        I18nTranslation exist = translationMapper.selectOne(
                new LambdaQueryWrapper<I18nTranslation>()
                        .eq(I18nTranslation::getTenantId, tenantId)
                        .eq(I18nTranslation::getLocaleCode, t.getLocaleCode())
                        .eq(I18nTranslation::getMessageKey, t.getMessageKey()));
        if (exist != null) {
            exist.setMessageValue(t.getMessageValue());
            exist.setModule(t.getModule());
            exist.setDescription(t.getDescription());
            exist.setEnabled(t.getEnabled());
            translationMapper.updateById(exist);
            invalidateCache(tenantId, t.getLocaleCode());
            return Result.success("已更新", exist);
        }
        translationMapper.insert(t);
        invalidateCache(tenantId, t.getLocaleCode());
        return Result.success("已创建", t);
    }

    @PostMapping("/translation/batch")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> batchImport(@RequestBody Map<String, Object> body) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        String locale = (String) body.get("locale");
        @SuppressWarnings("unchecked")
        List<Map<String, String>> items = (List<Map<String, String>>) body.get("items");
        if (locale == null || items == null)
            return Result.error(BizCode.BAD_REQUEST, "locale / items 必填");

        int created = 0, updated = 0;
        for (Map<String, String> item : items) {
            String key = item.get("key");
            String value = item.get("value");
            String module = item.get("module");
            if (key == null || value == null) continue;

            I18nTranslation exist = translationMapper.selectOne(
                    new LambdaQueryWrapper<I18nTranslation>()
                            .eq(I18nTranslation::getTenantId, tenantId)
                            .eq(I18nTranslation::getLocaleCode, locale)
                            .eq(I18nTranslation::getMessageKey, key));
            if (exist != null) {
                exist.setMessageValue(value);
                if (module != null) exist.setModule(module);
                translationMapper.updateById(exist);
                updated++;
            } else {
                I18nTranslation t = new I18nTranslation();
                t.setTenantId(tenantId);
                t.setLocaleCode(locale);
                t.setMessageKey(key);
                t.setMessageValue(value);
                t.setModule(module);
                t.setEnabled(1);
                translationMapper.insert(t);
                created++;
            }
        }
        invalidateCache(tenantId, locale);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("created", created);
        data.put("updated", updated);
        data.put("total", items.size());
        return Result.success("批量导入完成", data);
    }

    /**
     * 批量查询翻译（前端 i18n 初始化用）
     */
    @GetMapping("/bundle")
    public Result<Map<String, String>> bundle(@RequestParam String locale) {
        Long tenantId = TenantContextHolder.get().getTenantId();
        // 查缓存
        Map<String, String> cached = CACHE.get(tenantId + ":" + locale);
        if (cached != null) return Result.success(cached);

        List<I18nTranslation> list = translationMapper.selectList(
                new LambdaQueryWrapper<I18nTranslation>()
                        .eq(I18nTranslation::getTenantId, tenantId)
                        .eq(I18nTranslation::getLocaleCode, locale)
                        .eq(I18nTranslation::getEnabled, 1));
        Map<String, String> bundle = new LinkedHashMap<>();
        for (I18nTranslation t : list) bundle.put(t.getMessageKey(), t.getMessageValue());

        CACHE.put(tenantId + ":" + locale, bundle);
        return Result.success(bundle);
    }

    /**
     * 单条翻译查询
     */
    public static String translate(Long tenantId, String locale, String key, String fallback) {
        Map<String, String> map = CACHE.get(tenantId + ":" + locale);
        if (map != null && map.containsKey(key)) return map.get(key);
        return fallback != null ? fallback : key;
    }

    private static void invalidateCache(Long tenantId, String locale) {
        if (locale != null) CACHE.remove(tenantId + ":" + locale);
    }
}