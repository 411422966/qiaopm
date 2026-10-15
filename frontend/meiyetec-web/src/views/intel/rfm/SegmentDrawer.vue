<template>
  <el-drawer
    :model-value="visible"
    :title="title"
    size="60%"
    direction="rtl"
    @update:model-value="emit('update:visible', $event)"
  >
    <div class="drawer-body">
      <div class="drawer-toolbar">
        <div class="drawer-toolbar__meta">
          共 <b>{{ total }}</b> 条会员
        </div>
        <div class="drawer-toolbar__actions">
          <el-button
            type="primary"
            size="small"
            :loading="isExporting"
            :disabled="!records.length"
            @click="onExport"
          >
            导出 CSV
          </el-button>
        </div>
      </div>

      <el-alert
        v-if="error"
        :title="error"
        type="error"
        :closable="false"
        show-icon
        class="drawer-alert"
      />

      <el-skeleton v-if="isLoading" :rows="6" animated />

      <el-empty
        v-else-if="!records.length"
        description="该分群暂无会员数据"
      />

      <template v-else>
        <el-table :data="records" stripe size="default">
          <el-table-column prop="memberId" label="会员ID" width="90" />
          <el-table-column prop="memberNo" label="编号" width="120" />
          <el-table-column label="姓名">
            <template #default="{ row }">{{ row.name ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="手机">
            <template #default="{ row }">{{ row.phone ?? '—' }}</template>
          </el-table-column>
          <el-table-column label="RFM" width="90">
            <template #default="{ row }">
              <el-tag size="small">{{ row.rfmScore }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="距今" width="80">
            <template #default="{ row }">{{ row.recencyDays }} 天</template>
          </el-table-column>
          <el-table-column label="频次" width="70">
            <template #default="{ row }">{{ row.frequency }}</template>
          </el-table-column>
          <el-table-column label="金额" width="100">
            <template #default="{ row }">¥{{ row.monetary }}</template>
          </el-table-column>
          <el-table-column label="建议" min-width="180">
            <template #default="{ row }">
              <span class="explain">{{ row.explanation ?? '—' }}</span>
            </template>
          </el-table-column>
        </el-table>

        <div class="drawer-pagination">
          <el-pagination
            v-model:current-page="pageNum"
            v-model:page-size="pageSize"
            :total="total"
            :page-sizes="[20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            @current-change="load"
            @size-change="load"
          />
        </div>
      </template>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { rfmApi } from '../../../api/rfm'
import type { SegmentMemberRow } from '../../../api/rfm'

const props = defineProps<{
  visible: boolean
  segmentCode: string
  segmentName: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
}>()

const records = ref<SegmentMemberRow[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const isLoading = ref(false)
const isExporting = ref(false)
const error = ref<string | null>(null)

const title = computed(() => `分群下钻：${props.segmentName ?? props.segmentCode}`)

async function load() {
  if (!props.segmentCode) return
  isLoading.value = true
  error.value = null
  try {
    const data: any = await rfmApi.segmentMembers(props.segmentCode, pageNum.value, pageSize.value)
    records.value = data?.records ?? []
    total.value = data?.total ?? 0
  } catch (e: any) {
    error.value = e?.message ?? '下钻加载失败'
    records.value = []
    total.value = 0
  } finally {
    isLoading.value = false
  }
}

async function onExport() {
  if (!props.segmentCode) return
  isExporting.value = true
  try {
    const blob = await rfmApi.exportSegmentMembers(props.segmentCode)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    const ts = new Date().toISOString().replace(/[-:T]/g, '').slice(0, 14)
    a.href = url
    a.download = `rfm_${props.segmentCode.toLowerCase()}_${ts}.csv`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success('已开始下载 CSV')
  } catch (e: any) {
    ElMessage.error(e?.message ?? '导出失败')
  } finally {
    isExporting.value = false
  }
}

watch(
  () => props.visible,
  (v) => { if (v) { pageNum.value = 1; load() } }
)
watch(
  () => props.segmentCode,
  () => { if (props.visible) load() }
)
</script>

<style scoped>
.drawer-body { padding: 8px 16px 16px; }
.drawer-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.drawer-toolbar__meta { font-size: 13px; color: #606266; }
.drawer-toolbar__actions { display: flex; gap: 8px; }
.drawer-alert { margin-bottom: 12px; }
.drawer-pagination {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
.explain { color: #606266; font-size: 12px; }
</style>