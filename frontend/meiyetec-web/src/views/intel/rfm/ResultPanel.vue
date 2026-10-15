<template>
  <div class="result-panel">
    <el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
      show-icon
      class="result-alert"
    />

    <el-empty v-if="!result && !error" description="请选择会员并点击「计算 RFM」" />

    <template v-else-if="result">
      <el-descriptions :column="3" border>
        <el-descriptions-item label="R">{{ result.rScore }}</el-descriptions-item>
        <el-descriptions-item label="F">{{ result.fScore }}</el-descriptions-item>
        <el-descriptions-item label="M">{{ result.mScore }}</el-descriptions-item>
        <el-descriptions-item label="RFM">{{ result.rfmScore }}</el-descriptions-item>
        <el-descriptions-item label="分群" :span="2">
          {{ result.segmentName }} ({{ result.segment }})
        </el-descriptions-item>
        <el-descriptions-item label="最近消费距今">
          {{ result.recencyDays }} 天
        </el-descriptions-item>
        <el-descriptions-item label="频次">{{ result.frequency }}</el-descriptions-item>
        <el-descriptions-item label="金额">¥{{ result.amountLabel }}</el-descriptions-item>
        <el-descriptions-item label="建议" :span="3">
          {{ result.explanation }}
        </el-descriptions-item>
      </el-descriptions>

      <div v-if="memberTags.length" class="tag-list">
        <div class="tag-list__title">会员已有标签（{{ memberTags.length }}）</div>
        <el-tag
          v-for="t in memberTags"
          :key="t.id"
          size="small"
          effect="plain"
          class="tag-list__item"
        >
          {{ t.tagName }} ({{ t.tagCode }})
        </el-tag>
      </div>

      <div class="meta-row">计算耗时 {{ result.durationMs }} ms</div>

      <RfmHistoryChart :member-id="result.memberId" />
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import RfmHistoryChart from './RfmHistoryChart.vue'
import type { MemberRfm, MemberTagRow } from '../../../api/rfm'

const props = defineProps<{
  result: MemberRfm | null
  error: string | null
  memberTags: MemberTagRow[]
}>()

const result = computed(() => {
  if (!props.result) return null
  return {
    ...props.result,
    amountLabel: Number(props.result.monetary).toFixed(2)
  }
})
</script>

<style scoped>
.result-panel { display: flex; flex-direction: column; gap: 12px; }
.result-alert { margin: 0; }
.tag-list { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
.tag-list__title { font-weight: 600; color: #606266; margin-right: 6px; font-size: 13px; }
.tag-list__item { margin-right: 4px; }
.meta-row { font-size: 12px; color: #909399; }
</style>