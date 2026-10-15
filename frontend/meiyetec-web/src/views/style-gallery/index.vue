<template>
  <div class="sg-page">
    <!-- ============ 顶部品牌区 + 搜索 ============ -->
    <div class="sg-header">
      <div class="sg-header__top">
        <div class="sg-logo">💎 美业款式图库</div>
        <el-button
          size="small"
          :icon="Refresh"
          :loading="initLoading"
          @click="onInitDefault"
          plain>
          初始化样例
        </el-button>
        <el-button
          size="small"
          type="primary"
          :icon="ShoppingCart"
          @click="goQuickOrder"
          style="margin-left: 8px">
          快速下单
        </el-button>
      </div>
      <div class="sg-search">
        <el-input
          v-model="filter.keyword"
          placeholder="搜索款式名 / 标签（如 闪粉、3D）"
          clearable
          size="default"
          @keyup.enter="loadList"
          @clear="loadList">
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </div>
    </div>

    <!-- ⭐ 增强 4：ECharts 风格分布饼图 -->
    <div v-if="distribution.length > 0" class="sg-stats">
      <div class="sg-stats__title">
        📊 当前列表分布（按 {{ distributionGroupBy === 'category' ? '类别' : '风格' }}）
        <el-radio-group v-model="distributionGroupBy" size="small" @change="renderPie">
          <el-radio-button label="category">类别</el-radio-button>
          <el-radio-button label="style">风格</el-radio-button>
        </el-radio-group>
      </div>
      <div ref="pieRef" class="sg-stats__chart"></div>
    </div>

    <!-- ============ sticky filter bar ============ -->
    <div class="sg-filter">
      <!-- 类别 chips -->
      <div class="sg-filter__cats">
        <div
          v-for="c in categories"
          :key="c.code || 'all'"
          class="sg-chip"
          :class="{ active: filter.category === c.code }"
          @click="onCategoryClick(c.code)">
          <span class="sg-chip__emoji">{{ c.emoji }}</span>
          <span>{{ c.label }}</span>
        </div>
      </div>

      <!-- ⭐ 增强 3：风格 chips（与类别正交的第二个维度） -->
      <div class="sg-filter__cats sg-filter__cats--style">
        <div
          v-for="s in dimensions"
          :key="s.code || 'all-style'"
          class="sg-chip sg-chip--style"
          :class="{ active: filter.style === s.code }"
          @click="onStyleClick(s.code)">
          <span class="sg-chip__emoji">{{ s.emoji }}</span>
          <span>{{ s.label }}</span>
        </div>
      </div>

      <div class="sg-filter__sort">
        <span
          v-for="s in sortOptions"
          :key="s.code"
          class="sg-sort"
          :class="{ active: filter.sort === s.code }"
          @click="onSortClick(s.code)">
          {{ s.label }}
        </span>
      </div>
    </div>

    <!-- ============ 内容区 ============ -->
    <div class="sg-content" v-loading="loading">
      <el-empty
        v-if="!loading && list.length === 0"
        description="还没有款式数据，点击右上角「初始化样例」一键生成 7 条官方款式"
        :image-size="120" />

      <div v-else class="sg-masonry">
        <article
          v-for="item in list"
          :key="item.id"
          class="sg-card"
          @click="openDetail(item)">
          <!-- 封面 -->
          <div
            class="sg-card__cover"
            :style="{ background: coverBg(item.category) }">
            <img
              v-if="firstImage(item)"
              :src="firstImage(item)"
              :alt="item.styleName"
              loading="lazy"
              class="sg-card__img" />
            <span v-else class="sg-card__placeholder">{{ categoryEmoji(item.category) }}</span>
            <span class="sg-card__difficulty" v-if="item.difficulty">
              {{ renderDifficulty(item.difficulty) }}
            </span>

            <!-- ⭐ 增强 1：卡片右上角心形收藏按钮 -->
            <button
              class="sg-card__fav"
              :class="{ active: favSet.has(item.id) }"
              @click.stop="onToggleFavorite(item)"
              :title="favSet.has(item.id) ? '取消收藏' : '收藏'">
              {{ favSet.has(item.id) ? '❤️' : '🤍' }}
            </button>
          </div>

          <!-- 信息 -->
          <div class="sg-card__body">
            <div class="sg-card__title">{{ item.styleName }}</div>
            <div class="sg-card__tags">
              <el-tag
                v-for="t in parseTags(item.tags).slice(0, 3)"
                :key="t.code"
                :type="(t.tone as any)"
                size="small"
                effect="plain"
                round>
                {{ t.label }}
              </el-tag>
            </div>
            <div class="sg-card__meta">
              <span class="sg-card__time">⏱ {{ item.estimatedMinutes || 60 }} 分钟</span>
              <span class="sg-card__price">¥{{ item.basePrice ?? '—' }}</span>
            </div>
            <div class="sg-card__stats">
              <span>👁 {{ item.viewCount ?? 0 }}</span>
              <span>❤️ {{ item.likeCount ?? 0 }}</span>
              <span>🛒 {{ item.orderCount ?? 0 }}</span>
            </div>
            <div class="sg-card__actions">
              <el-button size="small" :icon="Pointer" plain @click.stop="onLike(item)">
                点赞
              </el-button>
              <el-button size="small" type="primary" :icon="ShoppingCart" @click.stop="onQuickOrder(item)">
                快速下单
              </el-button>
            </div>
          </div>
        </article>
      </div>
    </div>

    <!-- ============ 详情 Drawer ============ -->
    <el-drawer
      v-model="detailOpen"
      :size="isMobile ? '92%' : '480px'"
      direction="rtl"
      :with-header="false">
      <div v-if="detailItem" class="sg-detail">
        <div class="sg-detail__cover" :style="{ background: coverBg(detailItem.category) }">
          <img
            v-if="firstImage(detailItem)"
            :src="firstImage(detailItem)"
            :alt="detailItem.styleName"
            class="sg-detail__img" />
          <span v-else class="sg-detail__placeholder">{{ categoryEmoji(detailItem.category) }}</span>
          <el-button
            class="sg-detail__close"
            :icon="Close"
            circle
            plain
            size="small"
            @click="detailOpen = false" />
        </div>

        <div class="sg-detail__body">
          <div class="sg-detail__title-row">
            <h2 class="sg-detail__title">{{ detailItem.styleName }}</h2>
            <!-- ⭐ 增强 1：详情页心形按钮 -->
            <button
              class="sg-detail__fav"
              :class="{ active: favSet.has(detailItem.id) }"
              @click="onToggleFavorite(detailItem)">
              {{ favSet.has(detailItem.id) ? '❤️ 已收藏' : '🤍 收藏' }}
            </button>
          </div>

          <div class="sg-detail__tags">
            <el-tag
              v-for="t in parseTags(detailItem.tags)"
              :key="t.code"
              :type="(t.tone as any)"
              effect="plain"
              round>
              {{ t.label }}
            </el-tag>
            <el-tag type="info" effect="plain" round>
              🎭 {{ styleLabel(inferStyle(detailItem)) }}
            </el-tag>
          </div>

          <div class="sg-detail__price-row">
            <div class="sg-detail__price">
              <span class="cur">¥</span>{{ detailItem.basePrice ?? '—' }}
            </div>
            <div class="sg-detail__time">
              ⏱ 预计 {{ detailItem.estimatedMinutes || 60 }} 分钟
            </div>
          </div>

          <p class="sg-detail__desc" v-if="detailItem.description">
            {{ detailItem.description }}
          </p>

          <el-descriptions :column="2" border size="small" class="sg-detail__desc-table">
            <el-descriptions-item label="款式编码">{{ detailItem.styleCode }}</el-descriptions-item>
            <el-descriptions-item label="类别">{{ detailItem.category || '—' }}</el-descriptions-item>
            <el-descriptions-item label="风格">{{ styleLabel(inferStyle(detailItem)) }}</el-descriptions-item>
            <el-descriptions-item label="难度">{{ renderDifficulty(detailItem.difficulty) }}</el-descriptions-item>
            <el-descriptions-item label="季节">{{ detailItem.season || 'ALL' }}</el-descriptions-item>
            <el-descriptions-item label="浏览">{{ detailItem.viewCount ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="点赞">{{ detailItem.likeCount ?? 0 }}</el-descriptions-item>
            <el-descriptions-item label="成单">{{ detailItem.orderCount ?? 0 }}</el-descriptions-item>
          </el-descriptions>

          <div class="sg-detail__actions">
            <el-button :icon="Pointer" plain size="large" @click="onLike(detailItem)">
              点赞 {{ detailItem.likeCount ? `(${detailItem.likeCount})` : '' }}
            </el-button>
            <!-- ⭐ 增强 2：分享好友按钮 -->
            <el-button
              :icon="Share"
              plain
              size="large"
              @click="onShare(detailItem)">
              分享好友
            </el-button>
            <el-button
              type="primary"
              :icon="ShoppingCart"
              size="large"
              style="flex:1"
              @click="onQuickOrder(detailItem)">
              立即下单
            </el-button>
          </div>
        </div>
      </div>
    </el-drawer>

    <!-- ⭐ 增强 2：分享二维码 Dialog -->
    <el-dialog
      v-model="shareOpen"
      title="分享给好友"
      width="92%"
      :style="{ maxWidth: '380px' }"
      align-center
      :show-close="true"
      :close-on-click-modal="false">
      <div v-if="shareUrl" class="sg-share">
        <div class="sg-share__qr">
          <img :src="qrUrl" :alt="`QR code for ${shareUrl}`" />
        </div>
        <div class="sg-share__url">
          <el-input
            :model-value="shareUrl"
            readonly
            size="default">
            <template #append>
              <el-button :icon="CopyDocument" @click="onCopyLink">复制</el-button>
            </template>
          </el-input>
        </div>
        <p class="sg-share__hint">
          📱 微信扫一扫即可在 H5 打开<br>
          📋 或复制链接发给好友
        </p>
      </div>
    </el-dialog>

    <!-- ============ 底部浮动数据条 ============ -->
    <div class="sg-footer">
      共 <b>{{ list.length }}</b> 款 · 已收藏 <b>{{ favSet.size }}</b> 款 · 排序：<b>{{ sortLabel }}</b>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Search, Refresh, ShoppingCart, Pointer, Close, Share, CopyDocument
} from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import {
  styleGalleryApi,
  STYLE_CATEGORIES,
  STYLE_DIMENSIONS,
  resolveImageList,
  parseTags,
  renderDifficulty,
  coverBg,
  categoryEmoji,
  inferStyle,
  buildShareUrl,
  buildQrCodeUrl,
  getFavorites,
  toggleFavorite,
  getDistribution
} from '@/api/style-gallery'

const categories = STYLE_CATEGORIES
const dimensions = STYLE_DIMENSIONS
const sortOptions = [
  { code: 'HOT',  label: '🔥 热门' },
  { code: 'NEW',  label: '🆕 最新' },
  { code: 'VIEW', label: '👁 浏览最多' }
] as const

const router = useRouter()

const filter = reactive({
  category: '' as string,
  style:    '' as string,        // ⭐ 增强 3：风格筛选
  sort:     'HOT' as 'HOT' | 'NEW' | 'VIEW',
  keyword:  ''
})

const list   = ref<any[]>([])
const loading = ref(false)
const initLoading = ref(false)

const detailOpen   = ref(false)
const detailItem   = ref<any | null>(null)

const isMobile = ref(typeof window !== 'undefined' && window.innerWidth < 768)
const onResize = () => { isMobile.value = window.innerWidth < 768 }

// ⭐ 增强 1：收藏集
const favSet = ref<Set<number>>(new Set())

const sortLabel = computed(() =>
  sortOptions.find(s => s.code === filter.sort)?.label || filter.sort
)

const styleLabel = (code: string) =>
  STYLE_DIMENSIONS.find(s => s.code === code)?.label || code

const firstImage = (item: any) => resolveImageList(item)[0] || ''

const loadList = async () => {
  loading.value = true
  try {
    const params: any = { sort: filter.sort }
    if (filter.category) params.category = filter.category
    if (filter.keyword)  params.tag = filter.keyword
    const res: any = await styleGalleryApi.list(params)
    let arr = Array.isArray(res?.data) ? res.data : (Array.isArray(res) ? res : [])

    // 客户端二次过滤：keyword 走 name/code/tags/description
    if (filter.keyword) {
      const kw = filter.keyword.toLowerCase()
      arr = arr.filter(x =>
        (x.styleName    || '').toLowerCase().includes(kw) ||
        (x.styleCode    || '').toLowerCase().includes(kw) ||
        (x.tags         || '').toLowerCase().includes(kw) ||
        (x.description  || '').toLowerCase().includes(kw)
      )
    }

    // ⭐ 增强 3：客户端按风格过滤（后端无 style 维度）
    if (filter.style) {
      arr = arr.filter(x => inferStyle(x) === filter.style)
    }

    list.value = arr
  } catch (e: any) {
    ElMessage.error(e?.message || '加载款式失败')
    list.value = []
  } finally {
    loading.value = false
    nextTick(renderPie)
  }
}

const onCategoryClick = (code: string) => {
  filter.category = code
  loadList()
}

const onSortClick = (code: 'HOT' | 'NEW' | 'VIEW') => {
  filter.sort = code
  loadList()
}

// ⭐ 增强 3：风格筛选
const onStyleClick = (code: string) => {
  filter.style = code
  loadList()
}

// ⭐ 增强 1：切换收藏
const onToggleFavorite = (item: any) => {
  const now = toggleFavorite(item.id)
  // 触发响应式刷新
  if (now) favSet.value.add(item.id)
  else     favSet.value.delete(item.id)
  favSet.value = new Set(favSet.value)
  ElMessage.success(now ? '已收藏 ❤️' : '已取消收藏')
}

const onLike = async (item: any) => {
  try {
    await styleGalleryApi.like(item.id)
    item.likeCount = (item.likeCount || 0) + 1
    if (detailItem.value?.id === item.id) detailItem.value.likeCount = item.likeCount
    ElMessage.success('已点赞 ❤️')
  } catch (e: any) {
    ElMessage.error(e?.message || '点赞失败')
  }
}

const onQuickOrder = async (item: any) => {
  try {
    await ElMessageBox.confirm(
      `「${item.styleName}」预计 ¥${item.basePrice ?? '—'}，预计 ${item.estimatedMinutes || 60} 分钟，是否跳转预约？`,
      '确认下单',
      { type: 'success', confirmButtonText: '去预约', cancelButtonText: '再看看' }
    )
  } catch { return }
  try {
    const res: any = await styleGalleryApi.quickOrder(item.id, {})
    const data = res?.data || res
    item.orderCount = (item.orderCount || 0) + 1
    if (detailItem.value?.id === item.id) detailItem.value.orderCount = item.orderCount
    ElMessage.success(`已下单，跳转到 ${data.orderUrl || '/booking/create'}`)
    if (data.orderUrl && typeof window !== 'undefined') {
      window.location.href = data.orderUrl
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '下单失败')
  }
}

const openDetail = async (item: any) => {
  detailItem.value = item
  detailOpen.value = true
  try {
    const res: any = await styleGalleryApi.get(item.id)
    const fresh = res?.data || res
    if (fresh) {
      detailItem.value = { ...item, ...fresh }
      const idx = list.value.findIndex(x => x.id === item.id)
      if (idx >= 0) list.value[idx] = detailItem.value
    }
    styleGalleryApi.logView(item.id, { source: 'H5' }).catch(() => {})
  } catch (e: any) {
    ElMessage.warning(e?.message || '详情加载失败，显示本地副本')
  }
}

const goQuickOrder = () => {
  router.push('/style-gallery/quick-order')
}

const onInitDefault = async () => {
  try {
    await ElMessageBox.confirm(
      '将一键生成 7 条官方示例款式（每个租户只插入一次，重复点击不会重复插入）。继续？',
      '初始化样例',
      { type: 'warning' }
    )
  } catch { return }
  initLoading.value = true
  try {
    const res: any = await styleGalleryApi.initDefault()
    const data = res?.data || res
    ElMessage.success(`初始化完成，新增 ${data?.created ?? '?'} 条`)
    loadList()
  } catch (e: any) {
    ElMessage.error(e?.message || '初始化失败')
  } finally {
    initLoading.value = false
  }
}

// ====================================================================
// ⭐ 增强 2：分享给好友
// ====================================================================
const shareOpen  = ref(false)
const shareUrl   = ref('')
const qrUrl      = ref('')

const onShare = (item: any) => {
  shareUrl.value = buildShareUrl(item.id)
  qrUrl.value    = buildQrCodeUrl(shareUrl.value, 220)
  shareOpen.value = true
}

const onCopyLink = async () => {
  try {
    await navigator.clipboard.writeText(shareUrl.value)
    ElMessage.success('链接已复制 ✅')
  } catch {
    // 兜底：选中文本框
    const input = document.createElement('input')
    input.value = shareUrl.value
    document.body.appendChild(input)
    input.select()
    document.execCommand('copy')
    document.body.removeChild(input)
    ElMessage.success('链接已复制 ✅')
  }
}

// ====================================================================
// ⭐ 增强 4：ECharts 风格分布饼图
// ====================================================================
const pieRef = ref<HTMLDivElement | null>(null)
let pieChart: echarts.ECharts | null = null
const distributionGroupBy = ref<'category' | 'style'>('category')

const distribution = computed(() =>
  getDistribution(list.value, distributionGroupBy.value)
)

const renderPie = () => {
  if (!pieRef.value) return
  if (!pieChart) {
    pieChart = echarts.init(pieRef.value, undefined, { renderer: 'canvas' })
    window.addEventListener('resize', resizePie)
  }
  const data = distribution.value
  if (!data.length) {
    pieChart.clear()
    return
  }
  // 自定义色板（呼应品牌粉色调）
  const palette = [
    '#ff7eb3', '#ff758c', '#f0c674', '#a8b89c',
    '#9aa9c0', '#e3a6c8', '#c89dd6', '#7ec8a6',
    '#f7b267', '#ff9aa2'
  ]
  pieChart.setOption({
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} 款 ({d}%)'
    },
    legend: {
      orient: 'vertical',
      right: 8,
      top: 'middle',
      textStyle: { fontSize: 11, color: '#606266' }
    },
    series: [{
      type: 'pie',
      radius: ['38%', '70%'],
      center: ['38%', '50%'],
      avoidLabelOverlap: true,
      itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { show: false },
      emphasis: {
        scale: true,
        scaleSize: 6,
        label: { show: true, fontSize: 13, fontWeight: 600 }
      },
      data: data.map((d, i) => ({ ...d, itemStyle: { color: palette[i % palette.length] } }))
    }]
  })
}

const resizePie = () => { pieChart?.resize() }

// 列表变化或维度切换时重绘
watch(distribution, () => nextTick(renderPie))
watch(distributionGroupBy, () => nextTick(renderPie))

onMounted(() => {
  favSet.value = getFavorites()                              // ⭐ 读收藏
  loadList()
  window.addEventListener('resize', onResize)
  // 处理从分享链接进来的场景：?share=123 自动打开详情
  try {
    const params = new URLSearchParams(window.location.search)
    const shareId = params.get('share')
    if (shareId) {
      // 等列表加载后尝试定位
      setTimeout(() => {
        const target = list.value.find(x => String(x.id) === shareId)
        if (target) openDetail(target)
        else ElMessage.info(`分享款式 #${shareId} 已下架`)
      }, 800)
    }
  } catch { /* 忽略 */ }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  window.removeEventListener('resize', resizePie)
  pieChart?.dispose()
})
</script>

<style scoped>
.sg-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #fff5f7 0%, #fdfafc 30%, #ffffff 100%);
  padding-bottom: 64px;
}

/* ============ Header ============ */
.sg-header {
  padding: 16px 16px 12px;
  background: linear-gradient(135deg, #ff7eb3, #ff758c);
  color: #fff;
  border-radius: 0 0 18px 18px;
  box-shadow: 0 6px 18px rgba(255, 117, 140, 0.18);
}
.sg-header__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.sg-logo {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.5px;
}
.sg-search :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.92);
  border-radius: 12px;
  padding: 4px 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

/* ============ ⭐ 增强 4：分布图区 ============ */
.sg-stats {
  background: #fff;
  margin: 12px;
  border-radius: 14px;
  padding: 12px 14px;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.04);
}
.sg-stats__title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}
.sg-stats__chart {
  width: 100%;
  height: 200px;
}

/* ============ Filter Bar ============ */
.sg-filter {
  position: sticky;
  top: 0;
  z-index: 50;
  background: #fff;
  padding: 10px 12px 8px;
  border-bottom: 1px solid #f5f5f7;
  backdrop-filter: blur(8px);
}
.sg-filter__cats {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  padding-bottom: 6px;
  scrollbar-width: none;
}
.sg-filter__cats::-webkit-scrollbar { display: none; }
.sg-filter__cats--style {
  padding-top: 4px;
  margin-top: 4px;
  border-top: 1px dashed #f0f0f3;
}
.sg-filter__cats--style .sg-chip {
  background: #fff7f3;
  color: #c98565;
}
.sg-filter__cats--style .sg-chip:hover {
  background: #ffe6d6;
}
.sg-filter__cats--style .sg-chip.active {
  background: linear-gradient(135deg, #ffb58a, #ff8c5a);
  box-shadow: 0 2px 8px rgba(255, 140, 90, 0.4);
}

.sg-chip {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border-radius: 16px;
  background: #f5f7fa;
  color: #606266;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.18s;
  user-select: none;
}
.sg-chip__emoji { font-size: 14px; }
.sg-chip:hover { background: #ffe8ee; color: #ff5a7e; }
.sg-chip.active {
  background: linear-gradient(135deg, #ff7eb3, #ff5a7e);
  color: #fff;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(255, 122, 159, 0.4);
}

.sg-filter__sort {
  display: flex;
  gap: 4px;
  margin-top: 8px;
  padding: 4px;
  background: #f5f7fa;
  border-radius: 10px;
}
.sg-sort {
  flex: 1;
  text-align: center;
  padding: 6px 0;
  border-radius: 8px;
  font-size: 12px;
  color: #606266;
  cursor: pointer;
  transition: all 0.18s;
}
.sg-sort.active {
  background: #fff;
  color: #ff5a7e;
  font-weight: 600;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

/* ============ Content ============ */
.sg-content {
  padding: 12px;
}

.sg-masonry {
  column-count: 2;
  column-gap: 12px;
}
@media (min-width: 1024px) {
  .sg-masonry { column-count: 3; }
}
@media (max-width: 480px) {
  .sg-masonry { column-count: 1; }
}

/* ============ Card ============ */
.sg-card {
  break-inside: avoid;
  margin-bottom: 12px;
  background: #fff;
  border-radius: 14px;
  overflow: hidden;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.04);
  transition: transform 0.18s, box-shadow 0.18s;
  cursor: pointer;
}
.sg-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}
.sg-card__cover {
  position: relative;
  width: 100%;
  height: 180px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.sg-card__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.sg-card__placeholder {
  font-size: 64px;
  opacity: 0.6;
}
.sg-card__difficulty {
  position: absolute;
  top: 8px;
  left: 8px;
  background: rgba(0, 0, 0, 0.55);
  color: #ffd54f;
  padding: 2px 8px;
  border-radius: 12px;
  font-size: 11px;
  letter-spacing: 1px;
}

/* ⭐ 增强 1：卡片收藏按钮 */
.sg-card__fav {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.92);
  border: none;
  cursor: pointer;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.18s, box-shadow 0.18s;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
}
.sg-card__fav:hover { transform: scale(1.12); }
.sg-card__fav.active {
  background: linear-gradient(135deg, #ff7eb3, #ff5a7e);
  box-shadow: 0 2px 10px rgba(255, 90, 126, 0.5);
}

.sg-card__body { padding: 12px; }
.sg-card__title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.sg-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 8px;
  min-height: 22px;
}
.sg-card__tags :deep(.el-tag) { margin: 0; }
.sg-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
}
.sg-card__price {
  font-size: 16px;
  font-weight: 700;
  color: #ff5a7e;
}
.sg-card__stats {
  display: flex;
  gap: 12px;
  font-size: 11px;
  color: #909399;
  padding-top: 6px;
  border-top: 1px dashed #f0f0f3;
}
.sg-card__actions {
  display: flex;
  gap: 6px;
  margin-top: 10px;
}
.sg-card__actions .el-button { flex: 1; }

/* ============ Detail Drawer ============ */
.sg-detail { padding-bottom: 24px; }
.sg-detail__cover {
  position: relative;
  width: 100%;
  height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.sg-detail__img { width: 100%; height: 100%; object-fit: cover; }
.sg-detail__placeholder { font-size: 120px; opacity: 0.7; }
.sg-detail__close {
  position: absolute;
  top: 12px;
  right: 12px;
  background: rgba(255, 255, 255, 0.9);
}
.sg-detail__body { padding: 16px; }

/* ⭐ 增强 1：详情标题 + 收藏按钮 */
.sg-detail__title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.sg-detail__title {
  font-size: 20px;
  font-weight: 700;
  margin: 0;
  color: #303133;
}
.sg-detail__fav {
  border: 1px solid #ffb6c1;
  background: #fff5f7;
  color: #ff5a7e;
  padding: 6px 12px;
  border-radius: 20px;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.18s;
}
.sg-detail__fav.active {
  background: linear-gradient(135deg, #ff7eb3, #ff5a7e);
  color: #fff;
  border-color: transparent;
  box-shadow: 0 2px 8px rgba(255, 90, 126, 0.3);
}

.sg-detail__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
}
.sg-detail__price-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  background: linear-gradient(135deg, #fff5f7, #fff);
  padding: 12px;
  border-radius: 10px;
  margin-bottom: 14px;
}
.sg-detail__price {
  font-size: 28px;
  font-weight: 700;
  color: #ff5a7e;
}
.sg-detail__price .cur { font-size: 14px; margin-right: 2px; }
.sg-detail__time { font-size: 13px; color: #909399; }

.sg-detail__desc {
  font-size: 14px;
  line-height: 1.7;
  color: #606266;
  margin: 0 0 14px;
}
.sg-detail__desc-table { margin-bottom: 16px; }

.sg-detail__actions {
  display: flex;
  gap: 10px;
  padding: 12px 0;
  border-top: 1px solid #f0f0f3;
}

/* ============ ⭐ 增强 2：分享 Dialog ============ */
.sg-share {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px 0;
}
.sg-share__qr {
  width: 220px;
  height: 220px;
  border-radius: 12px;
  overflow: hidden;
  background: #fff;
  padding: 8px;
  border: 1px solid #f0f0f3;
  margin-bottom: 14px;
}
.sg-share__qr img {
  width: 100%;
  height: 100%;
  display: block;
}
.sg-share__url {
  width: 100%;
  margin-bottom: 10px;
}
.sg-share__hint {
  text-align: center;
  font-size: 12px;
  color: #909399;
  line-height: 1.6;
  margin: 0;
}

/* ============ Footer ============ */
.sg-footer {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  text-align: center;
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(8px);
  border-top: 1px solid #f0f0f3;
  padding: 10px 12px;
  font-size: 12px;
  color: #909399;
  z-index: 40;
}
.sg-footer b { color: #ff5a7e; margin: 0 4px; }
</style>
