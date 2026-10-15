<template>
  <div class="page-rfm">
    <div class="page-rfm__hero">
      <div class="hero-title">RFM 客户分群</div>
      <div class="hero-sub">基于 R（最近消费）/ F（消费频次）/ M（消费金额）的会员智能分层</div>
    </div>

    <el-tabs v-model="tab" class="page-rfm__tabs">
      <el-tab-pane label="分群概览" name="overview">
        <OverviewTab />
      </el-tab-pane>
      <el-tab-pane label="单会员 RFM" name="compute">
        <ComputeTab />
      </el-tab-pane>
      <el-tab-pane label="标签字典" name="dict">
        <TagDictTable />
      </el-tab-pane>
      <el-tab-pane label="批量 / 示例数据" name="batch">
        <BatchTab />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import OverviewTab from './OverviewTab.vue'
import ComputeTab from './ComputeTab.vue'
import TagDictTable from './TagDictTable.vue'
import BatchTab from './BatchTab.vue'

const tab = ref<string>('overview')
</script>

<style scoped>
.page-rfm { padding: 16px 20px 24px; background: #f7f8fa; min-height: 100%; }
.page-rfm__hero { margin-bottom: 12px; }
.hero-title { font-size: 20px; font-weight: 600; color: #303133; }
.hero-sub { font-size: 13px; color: #909399; margin-top: 4px; }
.page-rfm__tabs :deep(.el-tabs__nav-wrap)::after { background: transparent; }
</style>