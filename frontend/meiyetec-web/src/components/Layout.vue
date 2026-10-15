<template>
  <el-container class="layout-container">
    <el-aside width="210px">
      <div class="logo">
        <span>美业SaaS</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        class="el-menu-vertical"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
      >
        <template v-for="item in displayedMenus" :key="item.path">
          <el-sub-menu v-if="hasChildren(item)" :index="item.path">
            <template #title>
              <el-icon><component :is="resolveIcon(item.icon) || item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </template>
            <el-menu-item
              v-for="c in item.children"
              :key="resolvePath(item.path, c.path)"
              :index="resolvePath(item.path, c.path)"
            >
              <el-icon><component :is="(c.icon && (typeof c.icon === 'string' ? resolveIcon(c.icon) : c.icon)) || 'Document'" /></el-icon>
              <span>{{ c.title }}</span>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="item.path">
            <el-icon><component :is="resolveIcon(item.icon) || item.icon" /></el-icon>
            <span>{{ item.title }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header>
        <div class="header-content">
          <span class="username">{{ username }}</span>
          <span v-if="store.session?.dataScope" class="scope">{{ scopeLabel }}</span>
          <el-button type="danger" size="small" @click="handleLogout">退出</el-button>
        </div>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  HomeFilled, User, Wallet, Document, Setting, Tickets, Calendar, Money,
  DataAnalysis, Box, TrendCharts, PriceTag, Present, DataLine, DataBoard,
  Download, Shop, OfficeBuilding, Bell, ChatLineRound, Connection, CreditCard,
  Filter, Goods, Histogram, Lock, Management, Medal, Notification, Operation,
  Phone, Promotion, Refresh, School, Search, Share, Star, Switch,
  Tools, Trophy, UserFilled, Van, VideoCamera, Watch, Aim, Brush, Collection,
  Coordinate, Crop, Flag, GoodsFilled, Headset, Key, Link,
  List, Location, MapLocation, Microphone, Picture, PieChart, Platform,
  QuestionFilled, Reading, Refrigerator, Service, ShoppingBag, ShoppingCart,
  Sunny, Timer, Unlock, Upload, View, Warning, ZoomIn,
  Postcard, FirstAidKit, MagicStick, Monitor, AlarmClock, SetUp
} from '@element-plus/icons-vue'
import { usePermissionStore } from '@/stores/permission'
import { rbacApi, type MenuNode } from '@/api/rbac'
import { logout as apiLogout, getUserInfo } from '@/api/auth'

const route = useRoute()
const router = useRouter()
const store = usePermissionStore()

interface MenuChild {
  path: string
  title: string
  icon?: any
}
interface MenuItem {
  path: string
  title: string
  icon?: any
  children?: MenuChild[]
}

const ICONS: Record<string, any> = {
  HomeFilled, User, Wallet, Document, Setting, Tickets, Calendar, Money,
  DataAnalysis, Box, TrendCharts, PriceTag, Present, DataLine, DataBoard,
  Download, Shop, OfficeBuilding, Bell, ChatLineRound, Connection, CreditCard,
  Filter, Goods, Histogram, Lock, Management, Medal, Notification, Operation,
  Phone, Promotion, Refresh, School, Search, Share, Star, Switch,
  Trophy, UserFilled, Van, VideoCamera, Watch, Aim, Brush, Collection,
  Coordinate, Crop, Flag, GoodsFilled, Headset, Key, Link,
  List, Location, MapLocation, Microphone, Picture, PieChart, Platform,
  QuestionFilled, Reading, Refrigerator, Service, ShoppingBag, ShoppingCart,
  Sunny, Timer, Unlock, Upload, View, Warning, ZoomIn,
  Postcard, FirstAidKit, MagicStick, Monitor
}

const fallbackMenus: MenuItem[] = [
  { path: '/dashboard', title: '首页', icon: HomeFilled },
  {
    path: '/biz', title: '基础资料', icon: Document,
    children: [
      { path: 'service-item', title: '服务项目', icon: Document },
      { path: 'treatment-plan', title: '疗程方案', icon: Document },
      { path: 'treatment-report', title: '效果报告', icon: DataLine },
      { path: '/dev/pricelist', title: '电子价目表卡项套餐', icon: PriceTag },
      { path: '/dev/care-plan', title: '护肤方案共享套餐', icon: Present },
      { path: '/care', title: '配方护理建议', icon: Brush }
    ]
  },
  { path: '/member', title: '会员管理', icon: User },
  {
    path: '/wallet', title: '储值管理', icon: Wallet,
    children: [
      { path: 'recharge', title: '储值充值', icon: Document },
      { path: 'transactions', title: '储值流水', icon: Document },
      { path: '/gift-card', title: '礼品卡', icon: Present }
    ]
  },
  {
    path: '/package', title: '套餐次卡', icon: Tickets,
    children: [
      { path: 'list', title: '套餐管理', icon: Document },
      { path: 'member-orders', title: '会员套餐查询', icon: Document },
      { path: 'share', title: '多人共享套餐', icon: Share }
    ]
  },
  {
    path: '/booking', title: '预约管理', icon: Calendar,
    children: [
      { path: 'calendar', title: '预约日历', icon: Calendar },
      { path: 'list', title: '预约列表', icon: Document },
      { path: 'schedule', title: '排班管理', icon: Calendar },
      { path: 'resource', title: '预约资源', icon: Management },
      { path: 'waitlist', title: '候补队列', icon: List },
      { path: 'no-show', title: '爽约管理', icon: Warning },
      { path: '/lbs', title: '上门服务调度', icon: Van },
      { path: '/spa-scene', title: 'SPA场景模式', icon: MagicStick },
      { path: '/dev/forecast', title: '客流预测与智能排班', icon: TrendCharts }
    ]
  },
  {
    path: '/pos', title: '收银结算', icon: Money,
    children: [
      { path: 'checkout', title: '收银台', icon: Money },
      { path: 'orders', title: '订单列表', icon: Document },
      { path: 'stats', title: '收银统计', icon: DataAnalysis },
      { path: 'refund', title: '退款单', icon: Refresh },
      { path: 'reconciliation', title: '支付对账', icon: CreditCard },
      { path: '/pay/dcep', title: '数字人民币', icon: Money }
    ]
  },
  {
    path: '/stock', title: '库存管理', icon: Box,
    children: [
      { path: 'list', title: '库存列表', icon: Document },
      { path: 'product', title: '商品耗材', icon: Goods },
      { path: 'record', title: '出入库记录', icon: Document },
      { path: 'consumption', title: '消耗记录', icon: Document },
      { path: 'check', title: '盘点管理', icon: Document },
      { path: 'alert', title: '库存预警', icon: Warning },
      { path: 'stats', title: '库存统计', icon: DataAnalysis },
      { path: 'cross-store', title: '跨店调度', icon: Connection },
      { path: 'trace/:id', title: '批次追溯', icon: Connection }
    ]
  },
  {
    path: '/commission', title: '员工提成', icon: TrendCharts,
    children: [
      { path: 'rules', title: '提成规则', icon: Document },
      { path: 'records', title: '提成记录', icon: Document },
      { path: 'salary', title: '工资报表', icon: DataAnalysis },
      { path: 'settlement', title: '结算管理', icon: Money },
      { path: 'batch', title: '提成结算批次', icon: Document },
      { path: 'reversal', title: '提成冲正', icon: Refresh }
    ]
  },
  {
    path: '/market', title: '营销工具', icon: PriceTag,
    children: [
      { path: 'coupon', title: '优惠券', icon: Present },
      { path: 'points', title: '积分商城', icon: Medal },
      { path: 'activity', title: '营销活动', icon: Promotion },
      { path: '/member-day', title: '会员日自动化', icon: Present },
      { path: '/subscription', title: '订阅会员制', icon: Medal },
      { path: '/mkt/effect', title: '营销效果', icon: DataAnalysis },
      { path: '/mkt/channel', title: '渠道配置', icon: Connection },
      { path: '/mkt/template', title: '消息模板', icon: ChatLineRound },
      { path: '/mkt/touch-task', title: '触达任务', icon: Bell },
      { path: '/mkt/journey', title: '营销旅程', icon: Star },
      { path: '/mkt/conversion', title: '触达效果', icon: DataAnalysis },
      { path: '/mkt/pricing', title: '动态定价', icon: PriceTag },
      { path: '/lifecycle', title: '生命周期看板', icon: DataLine },
      { path: '/style-gallery', title: '款式图库', icon: Picture }
    ]
  },
  {
    path: '/crm', title: '客户运营', icon: Service,
    children: [
      { path: 'care/overview', title: '客户运营看板', icon: DataBoard },
      { path: 'care/follow-task', title: '回访任务', icon: Document },
      { path: 'care/follow-plan', title: '回访计划', icon: Calendar },
      { path: '/crm/care/follow-rule', title: '回访任务规则', icon: SetUp },
      { path: 'care/suggestion', title: '护理建议', icon: MagicStick },
      { path: '/crm/detection', title: '肤质档案', icon: MagicStick },
      { path: '/crm/style-package', title: '款式偏好', icon: Picture },
      { path: 'care/complaint', title: '投诉工单', icon: Warning },
      { path: 'care/review', title: '客户评价', icon: Star },
      { path: 'care/review-tpl', title: '评价模板', icon: Document },
      { path: '/crm/cross-rule', title: '跨店规则', icon: Connection },
      { path: '/crm/cross-record', title: '跨店核销', icon: Document },
      { path: '/intel/rfm', title: 'RFM 分群', icon: Filter },
      { path: '/intel/ltv', title: 'LTV 预测', icon: TrendCharts }
    ]
  },
  {
    path: '/org', title: '组织架构', icon: OfficeBuilding,
    children: [
      { path: 'hq', title: '总部看板', icon: DataBoard },
      { path: 'brand', title: '品牌管理', icon: Shop },
      { path: 'store', title: '门店管理', icon: Shop }
    ]
  },
  {
    path: '/report', title: '数据报表', icon: DataLine,
    children: [
      { path: 'dashboard', title: '数据看板', icon: DataBoard },
      { path: 'biz', title: '营业报表', icon: TrendCharts },
      { path: 'export', title: '导出管理', icon: Download },
      { path: 'task', title: '导出任务', icon: Document },
      { path: 'caliber', title: '指标口径', icon: Document }
    ]
  },
  {
    path: '/mh', title: '医美合规', icon: Medal,
    children: [
      { path: 'visual', title: '视觉档案', icon: Picture },
      { path: 'consent', title: '合规记录', icon: Document }
    ]
  },
  {
    path: '/employee', title: '考勤管理', icon: Timer,
    children: [
      { path: 'attendance/index', title: '考勤打卡', icon: AlarmClock },
      { path: 'attendance/records', title: '考勤记录', icon: Document },
      { path: 'attendance/leave', title: '请假管理', icon: Calendar },
      { path: 'attendance/statistics', title: '考勤统计', icon: DataAnalysis },
      { path: 'attendance/config', title: '考勤规则', icon: Setting }
    ]
  },
  {
    path: '/technician', title: '技师管理', icon: Medal,
    children: [
      { path: '/technician/level', title: '技师等级', icon: Medal },
      { path: '/technician/benefit', title: '等级权益', icon: Present },
      { path: '/technician/evaluation', title: '等级评定', icon: Trophy }
    ]
  },
  {
    path: '/material', title: '耗材管理', icon: Box,
    children: [
      { path: '/material/binding', title: '耗材绑定', icon: Connection },
      { path: '/material/statistics', title: '消耗统计', icon: PieChart },
      { path: '/material/expiry', title: '效期追踪', icon: AlarmClock },
      { path: '/material/service-material', title: '项目耗材', icon: SetUp },
      { path: '/resource/schedule', title: '双资源排程', icon: Calendar },
      { path: '/payment/overseas', title: '海外支付', icon: CreditCard },
      { path: '/iot/management', title: 'IoT设备', icon: Connection },
      { path: '/payment/dcep-advanced', title: '数字人民币2.0', icon: Wallet }
    ]
  },
  {
    path: '/sys', title: '系统设置', icon: Setting,
    children: [
      { path: 'setting', title: '店铺配置', icon: Shop },
      { path: 'tenant', title: '租户管理', icon: OfficeBuilding },
      { path: 'employee', title: '员工管理', icon: User },
      { path: 'log', title: '操作日志', icon: Document },
      { path: '/sys/login-log', title: '登录日志', icon: Document },
      { path: 'payment', title: '支付配置', icon: Money },
      { path: '/sys/qualification', title: '商户资质材料', icon: Document }
    ]
  },
  {
    path: '/dev', title: '研发工具', icon: Tools,
    children: [
      { path: 'gray-release', title: '灰度发布控制台', icon: Switch },
      { path: 'ltv-training', title: 'LTV流失预测', icon: DataAnalysis },
      { path: 'datahub', title: '数据中台', icon: Connection },
      { path: 'care-memberday', title: '配方护理会员日', icon: Present },
      { path: '/ai-vision', title: 'AI 视觉识别', icon: View },
      { path: '/ltv-churn', title: 'LTV/Churn 分析', icon: DataLine },
      { path: '/iot', title: 'IoT 设备管理', icon: Monitor },
      { path: '/kiosk', title: '自助 Kiosk', icon: Postcard },
      { path: '/complication', title: '并发症 SOP', icon: FirstAidKit },
      { path: '/cohort', title: 'Cohort 队列', icon: DataBoard },
      { path: '/gdpr', title: 'GDPR 合规', icon: Lock },
      { path: '/hipaa', title: 'HIPAA 加固', icon: Lock },
      { path: '/pos-device', title: '海外 POS', icon: CreditCard },
      { path: '/plugin', title: '插件市场', icon: MagicStick },
      { path: '/fiscal', title: '财税直连', icon: Money },
      { path: '/crm-integration', title: 'CRM 集成', icon: Connection },
      { path: '/ecommerce', title: '电商集成', icon: ShoppingCart },
      { path: '/intl-payment', title: '国际支付', icon: CreditCard },
      { path: '/intl-booking', title: '国际预约', icon: Calendar },
      { path: '/ai-scheduler', title: 'AI 智能排班', icon: TrendCharts },
      { path: '/risk-control', title: '资金风控', icon: Warning },
      { path: '/google-maps', title: 'Google Maps', icon: MapLocation },
      { path: 'p1-suite', title: 'P1套件合集', icon: Star },
      { path: 'p2-suite', title: 'P2套件合集', icon: Star },
      { path: 'p3-suite', title: 'P3套件合集', icon: Star },
      { path: 'gap-suite', title: '补齐套件', icon: Star }
    ]
  }
]

const dynamicMenus = computed<MenuItem[]>(() => {
  if (!store.loaded || !store.menus?.length) return []
  return (store.menus as MenuNode[]).map((n) => toMenuItem(n)).filter((x) => !!x) as MenuItem[]
})

const displayedMenus = computed<MenuItem[]>(() => {
  // 优先使用后端动态菜单（store.menus），后端未返回时 fallback 兜底
  if (store.loaded && dynamicMenus.value.length > 0) {
    return dynamicMenus.value
  }
  return fallbackMenus
})

function toMenuItem(n: MenuNode): MenuItem | null {
  if (!n.path) return null
  const item: MenuItem = {
    path: n.path.startsWith('/') ? n.path : '/' + n.path,
    title: n.title,
    icon: ICONS[n.icon || ''] || Document
  }
    if (n.children && n.children.length) {
      item.children = n.children.map((c: MenuNode) => ({
        path: c.path,
        title: c.title,
        icon: ICONS[c.icon || ''] || Document
      }))
    }
  return item
}

function resolveIcon(name?: string) {
  if (!name) return null
  return ICONS[name] || null
}

function hasChildren(item: MenuItem) {
  return !!item.children && item.children.length > 0
}

const resolvePath = (parent: string, child: string) => {
  if (child.startsWith('/')) return child
  if (!child) return parent
  return `${parent}/${child}`
}

const activeMenu = computed(() => route.path)
const username = computed(() => {
  try {
    const user = localStorage.getItem('user')
    if (user) {
      const userObj = JSON.parse(user)
      return userObj.realName || userObj.username || '未登录'
    }
    return store.session?.username || '未登录'
  } catch {
    return store.session?.username || '未登录'
  }
})

const scopeLabel = computed(() => {
  const s = store.session?.dataScope
  if (!s) return ''
  switch (s) {
    case 'ALL': return '数据范围：全租户'
    case 'BRAND': return '数据范围：品牌'
    case 'STORE': return '数据范围：当前门店'
    case 'SELF': return '数据范围：本人'
    default: return ''
  }
})

const handleLogout = async () => {
  // 调用后端退出接口（写入 sys_login_log 审计记录）
  try { await apiLogout() } catch (_) { /* 后端不可达也允许退出，避免锁死在登录页 */ }
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  localStorage.removeItem('rbac.session.v1')
  store.clear()
  router.push('/login')
}

onMounted(async () => {
  // AUTH-003：拉取当前登录用户信息（真实后端 /auth/userinfo）
  // 失败（如后端暂不可达）不阻塞布局显示；本字段仅用于右上角展示
  try {
    const res: any = await getUserInfo()
    if (res?.data) {
      localStorage.setItem('user', JSON.stringify({
        id: res.data.id,
        username: res.data.username,
        realName: res.data.realName
      }))
    }
  } catch {
    // 静默忽略：登录响应已写 localStorage，UI 仍可读
  }

  if (!store.loaded) {
    try {
      const res: any = await rbacApi.permissions()
      const data = res.data
      store.session = data
      store.permissions = data.permissions || []
      store.menus = data.menus || []
      store.loaded = true
    } catch {
      // 拦截器已处理 401
    }
  }
})
</script>

<style scoped>
.layout-container {
  height: 100vh;
}
.el-aside {
  background: #304156;
  color: #fff;
}
.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  color: #fff;
  font-size: 18px;
  font-weight: bold;
  background: #2b3a4e;
}
.header-content {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  height: 100%;
  gap: 12px;
}
.username {
  color: #303133;
  font-size: 14px;
}
.scope {
  color: #909399;
  font-size: 12px;
  padding: 2px 8px;
  border: 1px solid #dcdfe6;
  border-radius: 10px;
}
</style>
