import { computed, ref } from 'vue'
import { rfmApi } from '../../../api/rfm'
import type { TagDictionary } from '../../../api/rfm'

/**
 * 标签字典 composable
 *  - 提供分类筛选与关键词搜索（前端过滤，避免来回请求）
 *  - 暴露 reload() 让维护弹窗保存后刷新
 */
export function useRfmDict() {
  const dict = ref<TagDictionary[]>([])
  const isLoading = ref(false)
  const error = ref<string | null>(null)
  /** 触发 reload 的计数器 */
  const reloadTick = ref(0)

  const keyword = ref('')
  const category = ref<string>('')

  async function load() {
    isLoading.value = true
    error.value = null
    try {
      const data = await rfmApi.dictList()
      dict.value = Array.isArray(data) ? data : []
    } catch (e: any) {
      error.value = e?.message ?? '字典加载失败'
      dict.value = []
    } finally {
      isLoading.value = false
    }
  }

  async function reload() {
    reloadTick.value++
    await load()
  }

  const categories = computed(() => {
    const set = new Set<string>()
    for (const d of dict.value as TagDictionary[]) {
      if (d.category) set.add(d.category)
    }
    return Array.from(set)
  })

  const filtered = computed<TagDictionary[]>(() => {
    let list: TagDictionary[] = dict.value as TagDictionary[]
    if (category.value) {
      list = list.filter((d: TagDictionary) => d.category === category.value)
    }
    const kw = keyword.value.trim().toLowerCase()
    if (kw) {
      list = list.filter((d: TagDictionary) =>
        (d.tagCode ?? '').toLowerCase().includes(kw) ||
        (d.tagName ?? '').toLowerCase().includes(kw) ||
        (d.description ?? '').toLowerCase().includes(kw)
      )
    }
    return list
  })

  return {
    dict,
    keyword,
    category,
    categories,
    filtered,
    isLoading,
    error,
    load,
    reload,
    reloadTick
  }
}