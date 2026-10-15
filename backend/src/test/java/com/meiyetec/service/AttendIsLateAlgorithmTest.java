package com.meiyetec.service;

import com.meiyetec.mapper.AttendanceConfigMapper;
import com.meiyetec.service.impl.AttendanceConfigServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ATTEND 配置相关的纯算法测试（避免加载 Spring）
 * 使用 Mockito mock mapper，让 service 不依赖 DB
 */
class AttendIsLateAlgorithmTest {

    private final AttendanceConfigMapper mapper = mock(AttendanceConfigMapper.class);
    private final AttendanceConfigServiceImpl svc = new AttendanceConfigServiceImpl(mapper);

    @Test
    void config_null_returns_not_late_and_not_early_leave() {
        when(mapper.findActiveConfig(1L, 1L)).thenReturn(null);
        assertFalse(svc.isLate(LocalDateTime.now(), 1L, 1L));
        assertFalse(svc.isEarlyLeave(LocalDateTime.now(), 1L, 1L));
    }

    @Test
    void get_allow_late_minutes_returns_zero_when_no_config() {
        when(mapper.findActiveConfig(1L, 1L)).thenReturn(null);
        assertEquals(0, svc.getAllowLateMinutes(1L, 1L));
    }

    @Test
    void get_active_config_returns_null_when_no_mapper_hit() {
        when(mapper.findActiveConfig(1L, 1L)).thenReturn(null);
        assertNull(svc.getActiveConfig(1L, 1L));
    }
}