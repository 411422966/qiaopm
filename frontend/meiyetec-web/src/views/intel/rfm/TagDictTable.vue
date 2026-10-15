<template>
  <div class="tag-dict">
    <div class="tag-dict__toolbar">
      <div class="tag-dict__filter">
        <el-input
          v-model="keyword"
          placeholder="搜索编码 / 名称 / 描述"
          clearable
          style="width: 240px"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-select v-model="category" placeholder="全部分类" clearable style="width: 160px">
          <el-option label="全部分类" value="" />
          <el-option
            v-for="c in categories"
            :key="c"
            :label="c"
            :value="c"
          />
        </el-select>
        <span class="tag-dict__count">
          {{ filtered.length }} / {{ dict.length }}
        </span>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增标签</el-button>
    </div>

    <el-alert
      v-if="error"
      :title="error"
      type="error"
      :closable="false"
      show-icon
      class="tag-dict__alert"
    />

    <el-skeleton v-if="isLoading" :rows="4" animated />

    <el-empty v-else-if="!filtered.length" description="暂无匹配的标签字典" />

    <!-- B7: 按 category 分组折叠 -->
    <el-collapse
      v-else
      v-model="openGroups"
      class="tag-dict__groups"
    >
      <el-collapse-item
        v-for="group in groupedByCategory"
        :key="group.category"
        :name="group.category"
      >
        <template #title>
          <div class="tag-dict__group-title">
            <span class="tag-dict__group-name">{{ group.category || '未分类' }}</span>
            <el-tag size="small" type="info" effect="plain">{{ group.items.length }}</el-tag>
          </div>
        </template>
        <el-table :data="group.items" stripe>
          <el-table-column label="颜色" width="64" align="center">
            <template #default="{ row }">
              <span
                v-if="row.color"
                class="tag-dict__swatch"
                :style="{ backgroundColor: row.color }"
                :title="row.color"
              />
              <span v-else class="tag-dict__muted">—</span>
            </template>
          </el-table-column>
          <el-table-column label="图标" width="64" align="center">
            <template #default="{ row }">
              <el-icon v-if="iconOf(row.icon)" class="tag-dict__icon" :title="row.icon">
                <component :is="iconOf(row.icon)" />
              </el-icon>
              <span v-else class="tag-dict__muted">—</span>
            </template>
          </el-table-column>
          <el-table-column prop="tagCode" label="编码" width="180">
            <template #default="{ row }">
              <span v-html="highlight(row.tagCode)" />
            </template>
          </el-table-column>
          <el-table-column prop="tagName" label="名称" width="160">
            <template #default="{ row }">
              <span v-html="highlight(row.tagName)" />
            </template>
          </el-table-column>
          <el-table-column prop="rule" label="规则" min-width="160" />
          <el-table-column prop="description" label="描述" min-width="220">
            <template #default="{ row }">
              <span v-html="highlight(row.description)" />
            </template>
          </el-table-column>
          <el-table-column label="状态" width="80" align="center">
            <template #default="{ row }">
              <el-tag
                size="small"
                :type="row.enabled === 1 || row.enabled === undefined ? 'success' : 'info'"
                effect="plain"
              >
                {{ row.enabled === 1 || row.enabled === undefined ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button size="small" link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-popconfirm
                :title="`确定删除「${row.tagName}」？`"
                confirm-button-text="删除"
                cancel-button-text="取消"
                @confirm="onDelete(row)"
              >
                <template #reference>
                  <el-button size="small" link type="danger">删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
      </el-collapse-item>
    </el-collapse>

    <el-dialog
      v-model="dialogVisible"
      :title="editing ? '编辑标签字典' : '新增标签字典'"
      width="540px"
      :close-on-click-modal="false"
      @closed="resetForm"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="84px"
        label-position="right"
      >
        <el-form-item label="编码" prop="tagCode">
          <el-input
            v-model="form.tagCode"
            placeholder="如 RFM-CHAMPION"
            :disabled="!!editing"
          />
        </el-form-item>
        <el-form-item label="名称" prop="tagName">
          <el-input v-model="form.tagName" placeholder="中文名称" />
        </el-form-item>
        <el-form-item label="类别" prop="category">
          <el-input
            v-model="form.category"
            placeholder="如 RFM / CARE / RISK"
          />
        </el-form-item>
        <el-form-item label="规则">
          <el-input v-model="form.rule" placeholder="可选，自然语言描述" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="可选，业务含义"
          />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" :step="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="isSaving" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import * as ElIcons from '@element-plus/icons-vue'
import { rfmApi } from '../../../api/rfm'
import type { TagDictionary } from '../../../api/rfm'
import { useRfmDict } from './useRfmDict'

const {
  dict,
  keyword,
  category,
  categories,
  filtered,
  isLoading,
  error,
  reload
} = useRfmDict()

function escapeHtml(s?: string) {
  if (s === undefined || s === null) return ''
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function highlight(text?: string) {
  const safe = escapeHtml(text)
  const kw = keyword.value.trim()
  if (!kw) return safe || '—'
  const safeKw = escapeHtml(kw).replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
  const re = new RegExp(safeKw, 'gi')
  return (safe || '—').replace(re, (m) => `<mark class="tag-dict__hl">${m}</mark>`)
}

/**
 * B4: icon 列
 *  - 数据库 icon 字段存放 Element Plus 图标组件名（如 Star / Trophy）
 *  - 通过全量图标对象做一次查表，命中则渲染，否则显示 —
 *  - 与 setting/category.vue 的实现保持一致
 */
function iconOf(name?: string) {
  if (!name) return null
  const key = name as keyof typeof ElIcons
  return (ElIcons as any)[key] ?? null
}

// 全部折叠展开状态，默认全部展开（运营一进来就能看见所有类别）
const openGroups = ref<string[]>([])

/**
 * B7: 按 category 分组
 *  - 复用 filtered 结果，避免重复计算
 *  - 类别排序稳定（categories 已按字典出现顺序去重）
 */
const groupedByCategory = computed(() => {
  const map = new Map<string, TagDictionary[]>()
  for (const item of filtered.value) {
    const key = item.category || '未分类'
    if (!map.has(key)) map.set(key, [])
    map.get(key)!.push(item)
  }
  return Array.from(map.entries()).map(([cat, items]) => ({
    category: cat,
    items
  }))
})

const dialogVisible = ref(false)
const isSaving = ref(false)
const editing = ref<TagDictionary | null>(null)
const formRef = ref<FormInstance | null>(null)

const form = reactive<Record<string, any>>({
  tagCode: '',
  tagName: '',
  category: 'RFM',
  rule: '',
  description: '',
  sortOrder: 0
})

const rules: FormRules = {
  tagCode: [{ required: true, message: '编码必填', trigger: 'blur' }],
  tagName: [{ required: true, message: '名称必填', trigger: 'blur' }],
  category: [{ required: true, message: '类别必填', trigger: 'blur' }]
}

function openCreate() {
  editing.value = null
  Object.assign(form, {
    tagCode: '',
    tagName: '',
    category: 'RFM',
    rule: '',
    description: '',
    sortOrder: 0
  })
  dialogVisible.value = true
}

function openEdit(row: TagDictionary) {
  editing.value = row
  Object.assign(form, {
    tagCode: row.tagCode,
    tagName: row.tagName,
    category: row.category ?? 'RFM',
    rule: row.rule ?? '',
    description: row.description ?? '',
    sortOrder: row.sortOrder ?? 0
  })
  dialogVisible.value = true
}

async function onSubmit() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  isSaving.value = true
  try {
    const payload: Record<string, unknown> = { ...form }
    if (editing.value?.id) payload.id = editing.value.id
    await rfmApi.upsertDict(payload)
    ElMessage.success(editing.value ? '已更新' : '已新增')
    dialogVisible.value = false
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.message ?? '保存失败')
  } finally {
    isSaving.value = false
  }
}

async function onDelete(row: TagDictionary) {
  if (!row.id) return
  try {
    await rfmApi.deleteDict(row.id)
    ElMessage.success('已删除')
    await reload()
  } catch (e: any) {
    ElMessage.error(e?.message ?? '删除失败')
  }
}

function resetForm() {
  editing.value = null
  formRef.value?.resetFields()
}
</script>

<style scoped>
.tag-dict { display: flex; flex-direction: column; gap: 12px; }
.tag-dict__toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.tag-dict__filter { display: flex; gap: 8px; align-items: center; }
.tag-dict__count {
  font-size: 12px;
  color: #909399;
  padding-left: 4px;
}
.tag-dict__alert { margin: 0; }

/* B7 分组标题 */
.tag-dict__groups :deep(.el-collapse-item__header) {
  font-weight: 600;
}
.tag-dict__group-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.tag-dict__group-name { color: #303133; }

/* B6 色块 */
.tag-dict__swatch {
  display: inline-block;
  width: 18px;
  height: 18px;
  border-radius: 4px;
  border: 1px solid rgba(0, 0, 0, 0.08);
  vertical-align: middle;
}

/* B4 图标 */
.tag-dict__icon {
  font-size: 18px;
  vertical-align: middle;
  color: #606266;
}

.tag-dict__muted {
  color: #c0c4cc;
  font-size: 12px;
}

:deep(.tag-dict__hl) {
  background: #FFF3A3;
  color: #C25B00;
  padding: 0 2px;
  border-radius: 2px;
}
</style>