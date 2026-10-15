<template>
  <div class="overview-tab">
    <div class="kpis">
      <div class="kpi">
        <div class="kpi-label">会员总数</div>
        <div class="kpi-value">{{ total }}</div>
      </div>
      <div class="kpi">
        <div class="kpi-label">活跃分群数</div>
        <div class="kpi-value">{{ activeSegmentCount }} / {{ segments.length }}</div>
        <div class="kpi-sub">有会员的分群</div>
      </div>
      <div class="kpi" v-if="topSegment">
        <div class="kpi-label">Top 分群</div>
        <div class="kpi-value small">{{ topSegment.segmentName }}</div>
        <div class="kpi-sub">{{ topSegment.memberCount }} 人</div>
      </div>
      <div class="kpi">
        <div class="kpi-label">最近刷新</div>
        <div class="kpi-value small">{{ refreshedAt || '—' }}</div>
        <el-button size="small" link type="primary" @click="refresh">刷新</el-button>
      </div>
    </div>

    <el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
      show-icon
      class="overview-alert"
    />

    <el-empty v-if="!isLoading && !segments.length" description="暂无 RFM 分群数据" />

    <template v-else>
      <div class="overview-filter">
        <span class="overview-filter__label">品牌</span>
        <el-input-number
          v-model="brandId"
          placeholder="全部品牌"
          :min="0"
          :step="1"
          style="width: 180px"
          clearable
        />
        <el-button size="small" @click="refresh" :loading="isLoading">应用</el-button>
        <span v-if="brandId != null && brandId !== ('' as any)" class="overview-filter__hint">
          已筛选 brandId={{ brandId }}
        </span>
      </div>

      <SegmentPie :segments="segments" />

      <div class="cards">
        <SegmentCard
          v-for="s in segments"
          :key="s.segment"
          :data="s"
          :percent="percent(s.memberCount ?? 0)"
          @click="onCardClick"
        />
      </div>
    </template>

    <SegmentDrawer
      v-model:visible="drawerVisible"
      :segment-code="drawerSegment"
      :segment-name="drawerSegmentName"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import SegmentCard from './SegmentCard.vue'
import SegmentDrawer from './SegmentDrawer.vue'
import SegmentPie from './SegmentPie.vue'
import { useRfmOverview } from './useRfmOverview'
import type { SegmentOverviewItem } from '../../../api/rfm'

const emit = defineEmits<{
  (e: 'open-drawer', code: string, name: string): void
}>()

const {
  segments,
  total,
  topSegment,
  activeSegmentCount,
  isLoading,
  error,
  load,
  percent
} = useRfmOverview()

const brandId = ref<number | undefined>(undefined)

const drawerVisible = ref(false)
const drawerSegment = ref('')
const drawerSegmentName = ref('')

const refreshedAt = ref('')

async function refresh() {
  await load(brandId.value)
  refreshedAt.value = new Date().toLocaleTimeString()
}

function onCardClick(item: SegmentOverviewItem) {
  drawerSegment.value = item.segment ?? ''
  drawerSegmentName.value = item.segmentName ?? ''
  drawerVisible.value = true
  emit('open-drawer', drawerSegment.value, drawerSegmentName.value)
}

onMounted(() => { refresh() })
</script>

<style scoped>
.overview-tab { display: flex; flex-direction: column; gap: 12px; }
.kpis {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}
.kpi {
  background: #ffffff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 12px 16px;
}
.kpi-label { font-size: 12px; color: #909399; }
.kpi-value {
  font-size: 22px;
  font-weight: 600;
  color: #303133;
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.kpi-value.small { font-size: 16px; }
.kpi-sub { font-size: 12px; color: #909399; margin-top: 2px; }
.overview-alert { margin: 0; }
.cards {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
}
.overview-filter {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: #fafbfc;
  border: 1px solid #ebeef5;
  border-radius: 6px;
}
.overview-filter__label { font-size: 13px; color: #606266; }
.overview-filter__hint { font-size: 12px; color: #909399; margin-left: 4px; }
</style>