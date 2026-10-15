package com.meiyetec.service;

import com.meiyetec.entity.SysTenantConfig;

import java.util.List;
import java.util.Map;

public interface SysTenantConfigService {

    Map<String, String> getConfig(Long tenantId);

    void saveConfig(Long tenantId, Map<String, String> configs);
}
