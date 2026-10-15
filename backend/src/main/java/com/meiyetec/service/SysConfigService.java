package com.meiyetec.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.meiyetec.entity.SysConfig;

public interface SysConfigService extends IService<SysConfig> {

    /** 按 config_key 读取整型值，缺失/非法时返回 defaultValue */
    int getInt(String key, int defaultValue);

    /** 按 config_key 写入整型值，存在则更新，不存在则创建（group=system, sort_order=0） */
    void putInt(String key, int value, String description);
}