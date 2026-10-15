package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.entity.SysConfig;
import com.meiyetec.mapper.SysConfigMapper;
import com.meiyetec.service.SysConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class SysConfigServiceImpl extends ServiceImpl<SysConfigMapper, SysConfig> implements SysConfigService {

    @Override
    public int getInt(String key, int defaultValue) {
        try {
            SysConfig c = getOne(new QueryWrapper<SysConfig>().eq("config_key", key));
            if (c == null || c.getConfigValue() == null || c.getConfigValue().isBlank()) {
                return defaultValue;
            }
            return Integer.parseInt(c.getConfigValue().trim());
        } catch (NumberFormatException e) {
            log.warn("[SysConfig] 解析 {} 失败，使用默认值 {}", key, defaultValue);
            return defaultValue;
        }
    }

    @Override
    public void putInt(String key, int value, String description) {
        SysConfig existing = getOne(new QueryWrapper<SysConfig>().eq("config_key", key));
        if (existing == null) {
            SysConfig c = new SysConfig();
            c.setConfigKey(key);
            c.setConfigValue(String.valueOf(value));
            c.setConfigGroup("system");
            c.setDescription(description);
            c.setSortOrder(0);
            save(c);
            log.info("[SysConfig] 新增 {} = {}", key, value);
        } else {
            existing.setConfigValue(String.valueOf(value));
            updateById(existing);
            log.info("[SysConfig] 更新 {} = {}", key, value);
        }
    }
}