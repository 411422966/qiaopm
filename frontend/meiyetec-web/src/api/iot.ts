import request from './request'

/**
 * IoT 设备联动 API
 */
export const iotApi = {
  // 设备管理
  devices: (params: { deviceType?: string; status?: string; roomId?: number }) =>
    request.get('/iot/device/list', { params }),
  deviceDetail: (id: number) => request.get(`/iot/device/${id}`),
  deviceByCode: (code: string) => request.get(`/iot/device/by-code/${code}`),
  createDevice: (data: any) => request.post('/iot/device', data),
  updateDevice: (data: any) => request.put(`/iot/device/${data.id}`, data),
  deleteDevice: (id: number) => request.delete(`/iot/device/${id}`),
  deviceStats: (storeId?: number) => request.get('/iot/device/statistics', { params: { storeId } }),
  deviceHealthCheck: (id: number) => request.post(`/iot/device/${id}/health-check`, {}),
  sendCommand: (id: number, data: any) => request.post(`/iot/device/${id}/command`, data),
  receiveReport: (id: number, payload: any) => request.post(`/iot/device/${id}/report`, payload),
  activateDevice: (data: { deviceCode: string; macAddress: string }) =>
    request.post('/iot/device/activate', data),

  // 数据采集
  deviceData: (params: { deviceId?: number; memberId?: number; dataType?: string; startTime?: string; endTime?: string }) =>
    request.get('/iot/data/list', { params }),
  memberRecentData: (memberId: number) => request.get(`/iot/data/member/${memberId}/recent`),
  memberTrend: (memberId: number, params: { dataType: string; startTime: string; endTime: string }) =>
    request.get(`/iot/data/member/${memberId}/trend`, { params }),

  // 触发规则
  rules: () => request.get('/iot/rule/list'),
  ruleDetail: (id: number) => request.get(`/iot/rule/${id}`),
  createRule: (data: any) => request.post('/iot/rule', data),
  updateRule: (data: any) => request.put(`/iot/rule/${data.id}`, data),
  deleteRule: (id: number) => request.delete(`/iot/rule/${id}`),
  activeRules: (params: { deviceType?: string; dataType?: string }) =>
    request.get('/iot/rule/active', { params }),

  // 告警
  alerts: (params: { deviceId?: number; alertLevel?: string; status?: string }) =>
    request.get('/iot/alert/list', { params }),
  createAlert: (data: any) => request.post('/iot/alert', data),
  handleAlert: (id: number, data: any) => request.put(`/iot/alert/${id}/handle`, data),

  // 仪表板 / 设备列表（index.vue）
  dashboard: () => request.get('/iot/device/statistics'),
  listDevices: (params?: any) => request.get('/iot/device/list', { params }),
  register: (data: any) => request.post('/iot/device', data),
  bindMember: (id: number, data: any) => request.put(`/iot/device/${id}`, data),

  // 设备遥测（index.vue）
  telemetry: (deviceId: number, limit?: number) =>
    request.get('/iot/data/list', { params: { deviceId, startTime: undefined, endTime: undefined } }),
  latestTelemetry: (deviceId: number) =>
    request.get('/iot/data/list', { params: { deviceId } }),
  telemetryHttp: (data: any) => request.post('/iot/data', data),
  telemetryMqtt: (data: any) => request.post('/iot/data', data),

  // 设备联动（management.vue）
  links: (masterDeviceId?: number) =>
    request.get('/iot/device/link/list', { params: { masterDeviceId } }),
  createLink: (data: any) => request.post('/iot/device/link', data),
  updateLink: (id: number, data: any) => request.put(`/iot/device/link/${id}`, data),
  deleteLink: (id: number) => request.delete(`/iot/device/link/${id}`),
  toggleLink: (id: number, enabled: number) =>
    request.post(`/iot/device/link/${id}/toggle`, { enabled })
}

/**
 * 设备类型
 */
export const deviceTypeOptions = [
  { value: 'skin_analyzer', label: '皮肤检测仪', icon: 'View', color: '#409EFF', description: 'VISIA等皮肤指标采集' },
  { value: 'smart_mirror', label: '智能美容镜', icon: 'Picture', color: '#9C27B0', description: 'AI试妆/护肤推荐' },
  { value: 'massage_bed', label: '智能按摩床', icon: 'Grid', color: '#67C23A', description: '力度/温度调节' },
  { value: 'smart_lock', label: '智能门锁', icon: 'Lock', color: '#E6A23C', description: '房间授权/技师权限' },
  { value: 'led_lamp', label: 'LED美容灯', icon: 'Sunny', color: '#F56C6C', description: '光疗波长调节' },
  { value: 'humidifier', label: '香薰加湿器', icon: 'Cloudy', color: '#1C1F23', description: '环境湿度控制' },
  { value: 'weighing_scale', label: '智能体重秤', icon: 'Odometer', color: '#2E8B57', description: 'BMI/体脂率' }
]

/**
 * 设备状态
 */
export const deviceStatusOptions = [
  { value: 'online', label: '在线', type: 'success' },
  { value: 'offline', label: '离线', type: 'info' },
  { value: 'error', label: '故障', type: 'danger' },
  { value: 'maintenance', label: '维护中', type: 'warning' }
]

/**
 * 通信协议
 */
export const protocolOptions = [
  { value: 'mqtt', label: 'MQTT', icon: 'Connection' },
  { value: 'http', label: 'HTTP/REST', icon: 'Link' },
  { value: 'modbus', label: 'Modbus', icon: 'SetUp' },
  { value: 'ble', label: 'BLE 蓝牙', icon: 'Cellphone' },
  { value: 'wifi', label: 'Wi-Fi', icon: 'Position' },
  { value: 'zigbee', label: 'Zigbee', icon: 'Share' }
]

/**
 * 数据类型
 */
export const dataTypeOptions = [
  { value: 'detection', label: '检测数据', icon: 'DataAnalysis', color: '#409EFF' },
  { value: 'treatment', label: '治疗数据', icon: 'FirstAidKit', color: '#67C23A' },
  { value: 'operation', label: '操作记录', icon: 'Operation', color: '#909399' },
  { value: 'error', label: '故障数据', icon: 'Warning', color: '#F56C6C' },
  { value: 'heartbeat', label: '心跳', icon: 'Lightning', color: '#9C27B0' }
]

/**
 * 触发动作类型
 */
export const actionTypeOptions = [
  { value: 'send_coupon', label: '发送优惠券', icon: 'Ticket' },
  { value: 'send_sms', label: '发送短信', icon: 'Message' },
  { value: 'create_followup', label: '创建跟进任务', icon: 'Bell' },
  { value: 'update_member_tag', label: '更新客户标签', icon: 'CollectionTag' },
  { value: 'notify_employee', label: '通知员工', icon: 'User' }
]

/**
 * 条件操作符
 */
export const operatorOptions = [
  { value: 'lt', label: '小于 (<)' },
  { value: 'le', label: '小于等于 (≤)' },
  { value: 'gt', label: '大于 (>)' },
  { value: 'ge', label: '大于等于 (≥)' },
  { value: 'eq', label: '等于 (=)' }
]

/**
 * 告警级别
 */
export const alertLevelOptions = [
  { value: 'critical', label: '严重', type: 'danger' },
  { value: 'warning', label: '警告', type: 'warning' },
  { value: 'info', label: '提示', type: 'info' }
]

/**
 * 告警类型
 */
export const alertTypeOptions = [
  { value: 'offline', label: '设备离线', icon: 'Connection' },
  { value: 'error', label: '设备故障', icon: 'Warning' },
  { value: 'data_anomaly', label: '数据异常', icon: 'WarningFilled' },
  { value: 'maintenance', label: '维护提醒', icon: 'Tools' },
  { value: 'firmware_update', label: '固件更新', icon: 'Upload' },
  { value: 'low_battery', label: '电量低', icon: 'Battery' }
]