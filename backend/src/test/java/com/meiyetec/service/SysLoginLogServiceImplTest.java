package com.meiyetec.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.meiyetec.entity.SysLoginLog;
import com.meiyetec.mapper.SysLoginLogMapper;
import com.meiyetec.service.impl.SysLoginLogServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AUTH-004 登录日志查询 — 单元测试
 *
 * 不启动 Spring：Mockito 替换 Mapper，验证：
 *  1) clearBeforeDays(days<=0) 必须返回 0（不删数据，本轮修复的 BUG 兜底）
 *  2) clearBeforeDays(days>0) 必须调用 mapper.delete
 *  3) trend() 必须返回 N 天数据（即使某天 0 也要占位）
 *  4) stats() 异常时不能 throw，必须返回安全 fallback
 *  5) anomaly() 异常时不能 throw，必须返回空列表
 */
class SysLoginLogServiceImplTest {

    private SysLoginLogServiceImpl newService(SysLoginLogMapper mapper) {
        SysLoginLogServiceImpl svc = new SysLoginLogServiceImpl();
        // 注入 mock mapper（ServiceImpl.baseMapper）
        try {
            java.lang.reflect.Field f = com.baomidou.mybatisplus.extension.service.impl.ServiceImpl.class
                    .getDeclaredField("baseMapper");
            f.setAccessible(true);
            f.set(svc, mapper);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return svc;
    }

    @Test
    void clearBeforeDays_zeroOrNegative_returnsZero_withoutCallingDelete() {
        SysLoginLogMapper mapper = mock(SysLoginLogMapper.class);
        SysLoginLogServiceImpl svc = newService(mapper);

        assertEquals(0, svc.clearBeforeDays(0));
        assertEquals(0, svc.clearBeforeDays(-1));
        assertEquals(0, svc.clearBeforeDays(-999));

        verify(mapper, never()).delete(any());
    }

    @Test
    void clearBeforeDays_positive_callsMapperDelete() {
        SysLoginLogMapper mapper = mock(SysLoginLogMapper.class);
        when(mapper.delete(any())).thenReturn(7);
        SysLoginLogServiceImpl svc = newService(mapper);

        int n = svc.clearBeforeDays(30);

        assertEquals(7, n);
        verify(mapper).delete(any());
    }

    @Test
    void trend_returnsNDaysEvenIfEmpty() {
        SysLoginLogMapper mapper = mock(SysLoginLogMapper.class);
        when(mapper.selectMaps(any())).thenReturn(new ArrayList<Map<String, Object>>());
        SysLoginLogServiceImpl svc = newService(mapper);

        var r = svc.trend(7);

        assertNotNull(r);
        assertEquals(7, r.size());
        for (var day : r) {
            assertEquals(0, day.get("total"));
            assertEquals(0, day.get("success"));
            assertEquals(0, day.get("fail"));
            assertNotNull(day.get("date"));
        }
    }

    @Test
    void stats_exceptionReturnsFallback() {
        SysLoginLogMapper mapper = mock(SysLoginLogMapper.class);
        when(mapper.selectCount(any())).thenThrow(new RuntimeException("DB down"));
        SysLoginLogServiceImpl svc = newService(mapper);

        var r = svc.stats(30);

        assertNotNull(r);
        assertEquals(0, r.get("totalCount"));
        assertEquals(0, r.get("todayCount"));
        assertEquals(100d, ((Number) r.get("totalSuccessRate")).doubleValue());
        assertEquals("—", r.get("topIp"));
    }

    @Test
    void anomaly_exceptionReturnsEmptyLists() {
        SysLoginLogMapper mapper = mock(SysLoginLogMapper.class);
        when(mapper.selectMaps(any())).thenThrow(new RuntimeException("DB down"));
        SysLoginLogServiceImpl svc = newService(mapper);

        var r = svc.anomaly(7, 5);

        assertNotNull(r);
        assertEquals(0, ((List<?>) r.get("topFailIps")).size());
        assertEquals(0, ((List<?>) r.get("topFailUsers")).size());
    }
}