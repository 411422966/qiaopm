package com.meiyetec.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.meiyetec.dto.TenantStatsDTO;
import com.meiyetec.entity.SysTenant;
import com.meiyetec.entity.SysUser;
import com.meiyetec.mapper.SysTenantMapper;
import com.meiyetec.mapper.SysUserMapper;
import com.meiyetec.service.SysTenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SysTenantServiceImpl extends ServiceImpl<SysTenantMapper, SysTenant> implements SysTenantService {

    private final SysUserMapper userMapper;

    @Override
    public void checkAvailable(Long tenantId) {
        if (tenantId == null) return;
        SysTenant t = getById(tenantId);
        if (t == null) return;
        if (t.getStatus() != null && t.getStatus() == 0) {
            throw new RuntimeException("租户已被停用，请联系管理员");
        }
        if (t.getExpireAt() != null && !t.getExpireAt().isEmpty()) {
            LocalDateTime expire;
            try {
                expire = LocalDateTime.parse(t.getExpireAt(),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (Exception e) {
                // 时间格式异常时忽略，按未过期处理（兼容历史脏数据）
                return;
            }
            if (expire.isBefore(LocalDateTime.now())) {
                throw new RuntimeException("租户已过期，请续费后登录");
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int freezeExpiredTenant(Long tenantId) {
        SysTenant t = getById(tenantId);
        if (t == null || t.getExpireAt() == null || t.getExpireAt().isEmpty()) {
            return 0;
        }
        try {
            LocalDateTime expire = LocalDateTime.parse(t.getExpireAt(),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            if (expire.isAfter(LocalDateTime.now())) {
                return 0;
            }
        } catch (Exception e) {
            return 0;
        }

        // 已过期：停用租户
        if (t.getStatus() == null || t.getStatus() != 0) {
            t.setStatus(0);
            updateById(t);
        }

        // 冻结该租户下所有用户
        List<SysUser> users = userMapper.listByTenantId(tenantId);
        int frozen = 0;
        for (SysUser u : users) {
            if (u.getStatus() != null && u.getStatus() != 0) {
                u.setStatus(0);
                userMapper.updateById(u);
                frozen++;
            }
        }
        return frozen;
    }

    @Override
    public TenantStatsDTO getStats(Long tenantId) {
        SysTenant t = getById(tenantId);
        if (t == null) return null;
        TenantStatsDTO dto = new TenantStatsDTO();
        dto.setTenantId(t.getId());
        dto.setTenantName(t.getTenantName());
        dto.setTenantCode(t.getTenantCode());
        dto.setStatus(t.getStatus());
        dto.setExpireAt(t.getExpireAt());

        // 过期判断
        boolean expired = false;
        if (t.getExpireAt() != null && !t.getExpireAt().isEmpty()) {
            try {
                LocalDateTime expire = LocalDateTime.parse(t.getExpireAt(),
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                expired = expire.isBefore(LocalDateTime.now());
            } catch (Exception ignored) {}
        }
        dto.setExpired(expired);

        List<SysUser> users = userMapper.listByTenantId(tenantId);
        dto.setUserTotal(users.size());
        dto.setUserActive((int) users.stream().filter(u -> u.getStatus() != null && u.getStatus() == 1).count());
        dto.setUserInactive((int) users.stream().filter(u -> u.getStatus() != null && u.getStatus() == 0).count());
        return dto;
    }
}