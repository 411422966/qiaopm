<template>
  <div
    class="segment-card"
    :style="{ borderLeftColor: meta.color }"
    @click="emit('click', data)"
  >
    <div class="segment-card__head">
      <div class="segment-card__title">{{ data.segmentName }}</div>
      <span class="segment-card__badge" :style="{ background: meta.bg, color: meta.color }">
        {{ meta.label }}
      </span>
    </div>

    <div class="segment-card__sub">
      <el-tag size="small" effect="plain" round :style="{ color: meta.color, borderColor: meta.color }">
        {{ data.segment }}
      </el-tag>
    </div>

    <div class="segment-card__meta">
      <div class="meta-row">
        <span class="meta-key">会员数</span>
        <span class="meta-val">{{ data.memberCount ?? 0 }}</span>
      </div>
      <div class="meta-row">
        <span class="meta-key">均频次</span>
        <span class="meta-val">{{ data.avgFreq ?? 0 }}</span>
      </div>
      <div class="meta-row">
        <span class="meta-key">均金额</span>
        <span class="meta-val">¥{{ data.avgAmount ?? 0 }}</span>
      </div>
      <div class="meta-row">
        <span class="meta-key">均距今</span>
        <span class="meta-val">{{ data.avgRecency ?? 0 }} 天</span>
      </div>
    </div>

    <div class="segment-card__bar">
      <el-progress
        :percentage="Number(pct)"
        :stroke-width="6"
        :color="meta.color"
        :show-text="false"
      />
      <div class="bar-text">{{ pct }}%</div>
    </div>

    <div class="segment-card__desc">{{ meta.description }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { SEGMENT_FALLBACK, SEGMENT_META, type SegmentCode } from '../../../api/rfm'
import type { SegmentOverviewItem } from '../../../api/rfm'

const props = defineProps<{
  data: SegmentOverviewItem
  percent: string
}>()

const emit = defineEmits<{
  (e: 'click', item: SegmentOverviewItem): void
}>()

const meta = computed(() =>
  SEGMENT_META[(props.data.segment as SegmentCode)] ?? {
    ...SEGMENT_FALLBACK,
    label: props.data.segmentName ?? props.data.segment
  }
)

const pct = computed(() => props.percent)
</script>

<style scoped>
.segment-card {
  background: #ffffff;
  border: 1px solid #ebeef5;
  border-left: 4px solid #dcdfe6;
  border-radius: 8px;
  padding: 14px 16px;
  cursor: pointer;
  transition: box-shadow .15s, transform .15s;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 240px;
}
.segment-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, .06);
  transform: translateY(-1px);
}
.segment-card__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 8px;
}
.segment-card__title {
  font-weight: 600;
  font-size: 15px;
  color: #303133;
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.segment-card__badge {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 10px;
  flex-shrink: 0;
}
.segment-card__sub {
  display: flex;
  align-items: center;
  margin-top: -4px;
}
.segment-card__meta {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 4px 12px;
}
.meta-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #606266;
}
.meta-val { font-weight: 600; color: #303133; }
.segment-card__bar {
  position: relative;
  padding-top: 2px;
}
.bar-text {
  position: absolute;
  right: 0;
  top: -16px;
  font-size: 12px;
  color: #909399;
}
.segment-card__desc {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}
</style>