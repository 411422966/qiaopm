import request from './request'

/**
 * P1-11 自助签到 Kiosk
 *
 * 后端 KioskService @ /kiosk
 *
 * 完整链路：
 *  设备心跳  → POST /kiosk/device/{code}/heartbeat
 *  KPI 看板  → GET  /kiosk/stats
 *  签到      → POST /kiosk/checkin
 *  开始服务  → POST /kiosk/checkin/{id}/start-service
 *  结束服务  → POST /kiosk/checkin/{id}/finish-service
 */

export const kioskApi = {
  // ─────────────────────────────────────────────
  // 设备
  // ─────────────────────────────────────────────

  /** 注册新 Kiosk 设备 */
  registerDevice: (data: {
    deviceCode: string
    deviceName?: string
    location?: string
    ipAddress?: string
    storeId?: number
  }) => request.post('/kiosk/device', data),

  /** 设备列表 */
  listDevices: () => request.get<any[]>('/kiosk/device/list'),

  /** 更新设备信息 */
  updateDevice: (id: number, data: Partial<{
    deviceName: string
    location: string
    ipAddress: string
    status: 'ACTIVE' | 'INACTIVE'
    storeId: number
  }>) => request.post(`/kiosk/device/${id}`, data),

  // ─────────────────────────────────────────────
  // P0-1 · 设备心跳
  // ─────────────────────────────────────────────

  /**
   * 设备心跳（建议每 30~60s 调用一次）
   * @param code  设备编号
   * @param body  可选：{ ipAddress, location }
   */
  heartbeat: (code: string, body?: { ipAddress?: string; location?: string }) =>
    request.post<any>(`/kiosk/device/${code}/heartbeat`, body ?? {}),

  // ─────────────────────────────────────────────
  // P0-2 · KPI 统计
  // ─────────────────────────────────────────────

  /**
   * Kiosk 今日 KPI
   *
   * 响应字段：
   * - todayCheckins      今日总签到数
   * - waitingCount       等待中（CHECKED_IN）
   * - inServiceCount     服务中（IN_SERVICE）
   * - finishedCount      已完成（FINISHED）
   * - avgWaitMinutes     平均等待时长（分钟，1位小数）
   * - activeDevices      活跃设备数（5min 内有心跳）
   * - totalDevices       设备总数
   * - deviceStatusBreakdown  设备状态分布 { ACTIVE, INACTIVE, OFFLINE }
   * - checkinStatusBreakdown 签到状态分布 { CHECKED_IN, IN_SERVICE, FINISHED }
   */
  stats: () => request.get<any>('/kiosk/stats'),

  // ─────────────────────────────────────────────
  // 签到
  // ─────────────────────────────────────────────

  /**
   * 签到（增强版）
   *
   * @param body.deviceCode     设备编号（必填）
   * @param body.bookingId      预约 ID（可选）
   * @param body.memberId        会员 ID（可选，有限使用）
   * @param body.phone          手机号（自动匹配会员）
   * @param body.checkinMethod  签到方式：QRCODE | MANUAL | WALK_IN（默认 QRCODE）
   * @param body.notes          备注
   *
   * 幂等：同 deviceCode + bookingId 在 10 分钟内不重复签到
   */
  checkin: (body: {
    deviceCode: string
    bookingId?: number
    memberId?: number
    phone?: string
    checkinMethod?: 'QRCODE' | 'MANUAL' | 'WALK_IN'
    notes?: string
  }) => request.post<any>('/kiosk/checkin', body),

  /** 签到记录列表 */
  listCheckins: (params?: {
    deviceCode?: string
    date?: string       // yyyy-MM-dd
    status?: 'CHECKED_IN' | 'IN_SERVICE' | 'FINISHED'
  }) => request.get<any[]>('/kiosk/checkin/list', { params }),

  /** 签到详情 */
  getCheckin: (id: number) => request.get<any>(`/kiosk/checkin/${id}`),

  // ─────────────────────────────────────────────
  // P1-2 · 开始服务
  // ─────────────────────────────────────────────

  /**
   * 接待员点击「开始服务」
   * 仅允许 status = CHECKED_IN 的记录
   *
   * @param body.staffId  技师/接待员 ID（可选）
   */
  startService: (id: number, body?: { staffId?: number }) =>
    request.post<any>(`/kiosk/checkin/${id}/start-service`, body ?? {}),

  // ─────────────────────────────────────────────
  // P1-3 · 结束服务
  // ─────────────────────────────────────────────

  /**
   * 接待员点击「结束服务」
   * 仅允许 status = IN_SERVICE 的记录
   * 同时将关联的 booking.status → 4（已完成）
   *
   * @param body.staffId  技师 ID
   * @param body.notes    备注（如加单、手工调整）
   */
  finishService: (id: number, body?: { staffId?: number; notes?: string }) =>
    request.post<any>(`/kiosk/checkin/${id}/finish-service`, body ?? {}),

  // ============================================================
  // ⭐ P2 · Walk-in 散客签到（无预约直接到店）
  // ============================================================

  /**
   * POST /kiosk/checkin/walk-in
   *
   * 同时创建 booking + checkin（自动 status=3 已签到）。
   * 幂等：同 deviceCode + memberId 在 60 秒内不重复。
   *
   * @example
   *   const r = await kioskApi.walkIn({
   *     deviceCode: 'KIOSK-01',
   *     phone:      '13800138000',
   *     serviceItem: '基础护理'
   *   })
   */
  walkIn: (body: {
    deviceCode: string
    memberId?: number
    phone?: string
    serviceItem: string
    employeeId?: number
    notes?: string
  }) =>
    request.post<{
      checkinId: number
      bookingId: number
      bookingNo: string
      memberId: number
      memberName: string
      memberPhone: string
      serviceItem: string
      walkIn: boolean
    }>('/kiosk/checkin/walk-in', body),

  // ============================================================
  // ⭐ P2-分析 · 24h 签到分布 / 技师绩效 / 离线告警
  // ============================================================

  /**
   * 24 小时签到分布（按 hour 分桶）
   * @param date  yyyy-MM-dd，缺省当天
   * 返回：[ { hour, label, count, finished }, ... 24 项 ]
   */
  hourlyStats: (date?: string) =>
    request.get<Array<{
      hour: number
      label: string
      count: number
      finished: number
    }>>('/kiosk/stats/hourly', { params: date ? { date } : {} }),

  /**
   * 技师绩效对比（按 staffId 聚合）
   * 返回：[ { staffId, total, finished, finishRate, avgWaitMinutes }, ... ]
   */
  staffStats: (date?: string) =>
    request.get<Array<{
      staffId: number
      total: number
      finished: number
      finishRate: number
      avgWaitMinutes: number
    }>>('/kiosk/stats/staff', { params: date ? { date } : {} }),

  /**
   * 离线设备告警
   * 返回：[ { id, deviceCode, deviceName, location, lastHeartbeat,
   *           offlineMinutes, severity: NEVER|INFO|WARN|CRITICAL }, ... ]
   */
  offlineAlerts: () =>
    request.get<Array<{
      id: number
      deviceCode: string
      deviceName: string
      location: string
      lastHeartbeat: string | null
      offlineMinutes: number
      severity: 'NEVER' | 'INFO' | 'WARN' | 'CRITICAL'
      thresholdInfoMin: number
      thresholdWarnMin: number
      thresholdCriticalMin: number
    }>>('/kiosk/alerts/offline'),

  // ============================================================
  // ⭐ P2-扩展 · 客流目标 + 技师周/月趋势
  // ============================================================

  /**
   * 取当日客流目标（per-day 总数）
   */
  dailyTarget: (date?: string) =>
    request.get<{
      date: string
      tenantId: number
      target: number
      source: string
    }>('/kiosk/stats/daily-target', { params: date ? { date } : {} }),

  /**
   * 更新当日客流目标（仅 JVM 内存生效，重启恢复）
   * body: { target: 120 }
   */
  updateDailyTarget: (body: { target: number; date?: string }) =>
    request.post<{ target: number; scope: string }>(
      '/kiosk/stats/daily-target',
      body
    ),

  /**
   * 技师周/月趋势
   * @param staffIds  逗号分隔的技师 ID 字符串
   * @param period    week | month（默认 week）
   * @param endDate   截止日期（默认今天）
   */
  staffTrend: (params: {
    staffIds: string
    period?: 'week' | 'month'
    endDate?: string
  }) =>
    request.get<{
      period: 'week' | 'month'
      fromDate: string
      toDate: string
      staffIds: number[]
      rows: Array<{
        date: string
        total: number
        finished: number
        finishRate: number
        perStaff: Array<{
          staffId: number
          total: number
          finished: number
          avgWaitMinutes: number
        }>
      }>
    }>('/kiosk/stats/staff-trend', { params })
}

/**
 * 离线严重度 → tag 配色
 */
export const OFFLINE_SEVERITY_TAG: Record<string, { label: string; type: string }> = {
  NEVER:    { label: '从未心跳', type: 'info' },
  INFO:     { label: '轻度离线', type: 'warning' },
  WARN:     { label: '严重离线', type: 'danger' },
  CRITICAL: { label: '长时间无响应', type: 'danger' }
}

/**
 * 签到状态文案映射
 */
export const KIOSK_STATUS_LABEL: Record<string, { label: string; type: string }> = {
  CHECKED_IN:  { label: '等待中',  type: 'warning' },
  IN_SERVICE:  { label: '服务中',  type: 'primary' },
  FINISHED:    { label: '已完成',  type: 'success' }
}

/**
 * 设备状态文案映射
 */
export const DEVICE_STATUS_LABEL: Record<string, { label: string; type: string }> = {
  ACTIVE:    { label: '在线',   type: 'success' },
  INACTIVE:  { label: '停用',  type: 'info' },
  OFFLINE:   { label: '离线',  type: 'danger' }
}
