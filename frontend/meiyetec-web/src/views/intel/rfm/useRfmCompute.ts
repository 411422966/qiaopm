import { computed, ref } from 'vue'
import { rfmApi } from '../../../api/rfm'
import request from '../../../api/request'
import type { MemberRfm, MemberTagRow } from '../../../api/rfm'

export interface MemberOption {
  id: number
  name?: string
  phone?: string
  memberNo?: string
}

/**
 * 单会员 RFM 计算 + 会员选择
 *  - 修复原 rfm.vue 调 /member/search 不存在的问题：改用 /member/list 一次性加载
 *    客户端按手机号 / 姓名 / 编号过滤
 *  - 同时按需加载会员的标签
 */
export function useRfmCompute() {
  const memberKeyword = ref('')
  const members = ref<MemberOption[]>([])
  const memberId = ref<number | null>(null)
  const result = ref<MemberRfm | null>(null)
  const memberTags = ref<MemberTagRow[]>([])
  const isLoadingList = ref(false)
  const isComputing = ref(false)
  const isLoadingTags = ref(false)
  const error = ref<string | null>(null)

  async function loadMembers() {
    isLoadingList.value = true
    try {
      const env = await request.get('/member/list', { silent: true } as any)
      const data = Array.isArray(env?.data) ? env.data : []
      members.value = data.map((m: any) => ({
        id: m.id,
        name: m.name,
        phone: m.phone,
        memberNo: m.memberNo
      }))
    } catch {
      members.value = []
    } finally {
      isLoadingList.value = false
    }
  }

  const filteredMembers = computed(() => {
    const kw = memberKeyword.value.trim().toLowerCase()
    if (!kw) return members.value
    return members.value.filter(m =>
      (m.name ?? '').toLowerCase().includes(kw) ||
      (m.phone ?? '').includes(kw) ||
      (m.memberNo ?? '').toLowerCase().includes(kw)
    )
  })

  async function loadTags(mid: number) {
    isLoadingTags.value = true
    try {
      const data = await rfmApi.memberTags(mid)
      memberTags.value = Array.isArray(data) ? data : []
    } catch {
      memberTags.value = []
    } finally {
      isLoadingTags.value = false
    }
  }

  async function compute() {
    if (!memberId.value) {
      error.value = '请先选择会员'
      return
    }
    isComputing.value = true
    error.value = null
    result.value = null
    try {
      const data = await rfmApi.computeMember(memberId.value)
      result.value = data
      await loadTags(memberId.value)
    } catch (e: any) {
      error.value = e?.message ?? '计算失败'
    } finally {
      isComputing.value = false
    }
  }

  function reset() {
    result.value = null
    memberTags.value = []
    error.value = null
  }

  return {
    memberKeyword,
    members,
    filteredMembers,
    memberId,
    result,
    memberTags,
    isLoadingList,
    isComputing,
    isLoadingTags,
    error,
    loadMembers,
    compute,
    reset
  }
}