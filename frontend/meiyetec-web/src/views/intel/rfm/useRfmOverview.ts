import { computed, ref } from 'vue'
import { rfmApi } from '../../../api/rfm'
import type { SegmentOverviewItem } from '../../../api/rfm'

/**
 * 分群概览 composable
 *  - load()  加载 /intel/rfm/segment/overview
 *  - bySegment  按 segment 分组查找（用于 SegmentCard 命中）
 *  - total      所有分群会员总数
 */
export function useRfmOverview() {
  const segments = ref<SegmentOverviewItem[]>([])
  const isLoading = ref(false)
  const error = ref<string | null>(null)

  async function load(brandId?: number) {
    isLoading.value = true
    error.value = null
    try {
      const data = await rfmApi.segmentOverview(brandId)
      segments.value = Array.isArray(data) ? data : []
    } catch (e: any) {
      error.value = e?.message ?? '分群概览加载失败'
      segments.value = []
    } finally {
      isLoading.value = false
    }
  }

  const total = computed(() =>
    (segments.value as SegmentOverviewItem[]).reduce(
      (s: number, c: SegmentOverviewItem) => s + (c.memberCount ?? 0),
      0
    )
  )

  const topSegment = computed(() => {
    const list = segments.value as SegmentOverviewItem[]
    if (!list.length) return null
    return [...list].sort((a, b) => (b.memberCount ?? 0) - (a.memberCount ?? 0))[0]
  })

  const activeSegmentCount = computed(() =>
    (segments.value as SegmentOverviewItem[]).filter(
      (s: SegmentOverviewItem) => (s.memberCount ?? 0) > 0
    ).length
  )

  function getByCode(code: string): SegmentOverviewItem | undefined {
    return segments.value.find(s => (s.segment ?? '').toUpperCase() === code.toUpperCase())
  }

  function percent(count: number): string {
    if (!total.value) return '0.0'
    return ((count / total.value) * 100).toFixed(1)
  }

  return {
    segments,
    total,
    topSegment,
    activeSegmentCount,
    isLoading,
    error,
    load,
    getByCode,
    percent
  }
}