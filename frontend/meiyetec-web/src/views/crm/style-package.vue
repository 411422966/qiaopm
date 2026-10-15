<template>
  <div class="style-page">
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="款式库" name="library" />
      <el-tab-pane label="客户偏好" name="preference" />
      <el-tab-pane label="材料包管理" name="package" />
      <el-tab-pane label="款式包关联" name="relation" />
    </el-tabs>

    <!-- ============ 款式库 ============ -->
    <div v-if="activeTab === 'library'">
      <el-row :gutter="16" style="margin-bottom: 16px">
        <el-col :span="6">
          <el-card class="dash-stat">
            <div class="dash-icon blue"><el-icon :size="28"><Picture /></el-icon></div>
            <div>
              <div class="dash-value">{{ styleList.length }}</div>
              <div class="dash-label">款式总数</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="dash-stat">
            <div class="dash-icon green"><el-icon :size="28"><Star /></el-icon></div>
            <div>
              <div class="dash-value">{{ newCount }}</div>
              <div class="dash-label">新品</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="dash-stat">
            <div class="dash-icon orange"><el-icon :size="28"><StarFilled /></el-icon></div>
            <div>
              <div class="dash-value">{{ hotCount }}</div>
              <div class="dash-label">热门款式</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="dash-stat">
            <div class="dash-icon purple"><el-icon :size="28"><View /></el-icon></div>
            <div>
              <div class="dash-value">{{ totalUseCount }}</div>
              <div class="dash-label">累计使用</div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-card>
        <template #header>
          <div class="card-header">
            <span>款式库管理</span>
            <div class="filter-form">
              <el-select v-model="styleTypeFilter" placeholder="款式类型" clearable style="width:140px" @change="loadStyles">
                <el-option v-for="o in styleTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
              <el-select v-model="styleTagFilter" placeholder="风格标签" clearable style="width:140px" @change="loadStyles">
                <el-option v-for="o in styleTagOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
              <el-select v-model="difficultyFilter" placeholder="难度" clearable style="width:120px" @change="loadStyles">
                <el-option v-for="o in difficultyOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
              <el-button type="primary" @click="handleAddStyle">
                <el-icon><Plus /></el-icon>新增款式
              </el-button>
            </div>
          </div>
        </template>

        <el-row :gutter="12">
          <el-col v-for="s in styleList" :key="s.id" :xs="12" :sm="8" :md="6" :lg="4" class="style-card-col">
            <div class="style-card" @click="handleViewStyle(s)">
              <div class="style-image">
                <el-image v-if="s.mainImage" :src="s.mainImage" fit="cover" style="width:100%;height:100%" />
                <div v-else class="placeholder">
                  <el-icon :size="32"><Picture /></el-icon>
                </div>
                <div v-if="s.isNew === 1" class="tag-new">NEW</div>
                <div v-if="s.isHot === 1" class="tag-hot">HOT</div>
              </div>
              <div class="style-info">
                <div class="style-name">{{ s.styleName }}</div>
                <div class="style-tags">
                  <el-tag v-if="s.styleTag" size="small">{{ s.styleTag }}</el-tag>
                  <el-tag size="small" type="info" v-if="s.difficultyLevel">
                    {{ getDifficultyLabel(s.difficultyLevel) }}
                  </el-tag>
                </div>
                <div class="style-meta">
                  <span class="price">¥{{ s.basePrice || 0 }}</span>
                  <span class="use"><el-icon><Star /></el-icon> {{ s.useCount || 0 }}</span>
                </div>
              </div>
            </div>
          </el-col>
        </el-row>
        <el-empty v-if="!styleLoading && !styleList.length" description="暂无款式" />
      </el-card>
    </div>

    <!-- ============ 客户偏好 ============ -->
    <div v-if="activeTab === 'preference'">
      <el-row :gutter="16">
        <el-col :span="8">
          <el-card>
            <template #header>
              <span><el-icon><Search /></el-icon> 客户搜索</span>
            </template>
            <el-input v-model="memberKeyword" placeholder="客户名/手机号" clearable @input="searchMemberList">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <div class="member-list">
              <div
                v-for="m in searchedMembers"
                :key="m.id"
                class="member-item"
                :class="{ active: currentMember?.id === m.id }"
                @click="selectMember(m)"
              >
                <el-avatar :size="32" :src="m.avatar">{{ m.name?.[0] }}</el-avatar>
                <div class="mi-info">
                  <div class="mi-name">{{ m.name }}</div>
                  <div class="mi-phone">{{ m.phone }}</div>
                </div>
              </div>
              <el-empty v-if="!searchedMembers.length" description="请输入关键词搜索" />
            </div>
          </el-card>
        </el-col>

        <el-col :span="16">
          <el-card v-if="currentMember">
            <template #header>
              <span>{{ currentMember.name }} 的款式偏好</span>
            </template>
            <el-tabs v-model="prefTab">
              <el-tab-pane label="偏好设置" name="pref" />
              <el-tab-pane label="最近浏览" name="recent" />
              <el-tab-pane label="收藏款式" name="favorite" />
              <el-tab-pane label="智能推荐" name="recommend" />
            </el-tabs>

            <div v-if="prefTab === 'pref'">
              <el-form :model="pref" label-width="120px">
                <el-row :gutter="16">
                  <el-col :span="12">
                    <el-form-item label="偏好类型">
                      <el-select v-model="pref.preferredType" style="width:100%">
                        <el-option v-for="o in styleTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="偏好形状">
                      <el-select v-model="pref.preferredShape" style="width:100%">
                        <el-option v-for="o in nailShapeOptions" :key="o.value" :label="`${o.icon} ${o.label}`" :value="o.value" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                </el-row>
                <el-row :gutter="16">
                  <el-col :span="12">
                    <el-form-item label="偏好长度">
                      <el-radio-group v-model="pref.preferredLength">
                        <el-radio v-for="o in lengthOptions" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
                      </el-radio-group>
                    </el-form-item>
                  </el-col>
                </el-row>
                <el-form-item label="偏好风格">
                  <el-select v-model="pref.preferredStyles" multiple filterable style="width:100%">
                    <el-option v-for="o in styleTagOptions" :key="o.value" :label="o.label" :value="o.value" />
                  </el-select>
                </el-form-item>
                <el-form-item label="偏好色系">
                  <el-select v-model="pref.preferredColors" multiple filterable style="width:100%">
                    <el-option v-for="o in colorSchemeOptions" :key="o.value" :label="o.value">
                      <span :style="{ display: 'inline-block', width: '14px', height: '14px', background: o.color, marginRight: '6px', borderRadius: '50%' }"></span>
                      {{ o.value }}
                    </el-option>
                  </el-select>
                </el-form-item>
                <el-form-item label="忌讳色系">
                  <el-select v-model="pref.avoidColors" multiple filterable style="width:100%">
                    <el-option v-for="o in colorSchemeOptions" :key="o.value" :label="o.value" :value="o.value" />
                  </el-select>
                </el-form-item>
                <el-form-item label="过敏成分">
                  <el-input v-model="pref.allergyIngredients" type="textarea" :rows="2" placeholder="例: 丙烯酸酯类、溶剂类" />
                </el-form-item>
                <el-form-item label="特殊备注">
                  <el-input v-model="pref.specialNote" type="textarea" :rows="2" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" @click="savePreference">保存偏好</el-button>
                </el-form-item>
              </el-form>
            </div>

            <div v-if="prefTab === 'recent' || prefTab === 'favorite'">
              <div class="mini-styles">
                <div v-for="sid in getPrefStyleIds(prefTab)" :key="sid" class="mini-card">
                  <div class="mini-img">
                    <el-icon :size="32"><Picture /></el-icon>
                  </div>
                  <div class="mini-name">款式 #{{ sid }}</div>
                </div>
              </div>
              <el-empty v-if="!getPrefStyleIds(prefTab).length" description="暂无记录" />
            </div>

            <div v-if="prefTab === 'recommend'">
              <el-button type="primary" @click="loadRecommendations" :loading="recLoading">
                <el-icon><MagicStick /></el-icon>重新生成推荐
              </el-button>
              <div class="mini-styles" style="margin-top: 16px">
                <div v-for="s in recommendList" :key="s.id" class="mini-card">
                  <div class="mini-img">
                    <el-image v-if="s.mainImage" :src="s.mainImage" fit="cover" />
                    <el-icon v-else :size="32"><Picture /></el-icon>
                  </div>
                  <div class="mini-name">{{ s.styleName }}</div>
                  <div class="mini-tag">
                    <el-tag size="small">{{ s.styleTag }}</el-tag>
                  </div>
                </div>
              </div>
              <el-empty v-if="!recLoading && !recommendList.length" description="点击按钮生成AI推荐" />
            </div>
          </el-card>
          <el-empty v-else description="请先在左侧选择客户" />
        </el-col>
      </el-row>
    </div>

    <!-- ============ 材料包管理 ============ -->
    <div v-if="activeTab === 'package'">
      <el-card>
        <template #header>
          <div class="card-header">
            <span>材料包管理</span>
            <div class="filter-form">
              <el-select v-model="packageTypeFilter" placeholder="包类型" clearable style="width:140px" @change="loadPackages">
                <el-option v-for="o in packageTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
              <el-button type="primary" @click="handleAddPackage">
                <el-icon><Plus /></el-icon>新增材料包
              </el-button>
            </div>
          </div>
        </template>

        <el-row :gutter="12">
          <el-col v-for="p in packageList" :key="p.id" :xs="24" :sm="12" :md="8" :lg="6">
            <div class="package-card">
              <div class="package-image">
                <el-image v-if="p.mainImage" :src="p.mainImage" fit="cover" style="width:100%;height:100%" />
                <div v-else class="placeholder">
                  <el-icon :size="40"><Box /></el-icon>
                </div>
                <div class="package-type-tag">
                  <el-tag>{{ getPackageTypeLabel(p.packageType) }}</el-tag>
                </div>
              </div>
              <div class="package-body">
                <h3 class="package-name">{{ p.packageName }}</h3>
                <div class="package-purp">{{ p.purpose }}</div>
                <div class="package-prices">
                  <span class="price-final">¥{{ p.finalPrice || p.salePrice || 0 }}</span>
                  <span class="price-original" v-if="p.discountRate && p.discountRate < 1">¥{{ p.salePrice }}</span>
                  <el-tag size="small" type="danger" v-if="p.discountRate && p.discountRate < 1">
                    {{ ((1 - p.discountRate) * 100).toFixed(0) }}% OFF
                  </el-tag>
                </div>
                <div class="package-meta">
                  <span>销量: {{ p.salesCount || 0 }}</span>
                  <span>包含款式: {{ p.styleCount || 0 }}</span>
                </div>
              </div>
              <div class="package-actions">
                <el-button type="primary" link @click="handleEditPackage(p)">编辑</el-button>
                <el-button type="warning" link @click="handleEditPackageItems(p)">明细</el-button>
                <el-button type="danger" link @click="handleDeletePackage(p)">删除</el-button>
              </div>
            </div>
          </el-col>
        </el-row>
        <el-empty v-if="!packageLoading && !packageList.length" description="暂无材料包" />
      </el-card>
    </div>

    <!-- ============ 款式包关联 ============ -->
    <div v-if="activeTab === 'relation'">
      <el-card>
        <template #header>
          <span>款式包关联（一个款式可关联多个材料包）</span>
        </template>
        <el-empty description="此功能为关联视图,可从款式库或材料包详情页进入" />
      </el-card>
    </div>

    <!-- 款式编辑对话框 -->
    <el-dialog v-model="styleDialogVisible" :title="styleDialogTitle" width="700px">
      <el-form :model="styleForm" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="款式名称">
              <el-input v-model="styleForm.styleName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="款式类型">
              <el-select v-model="styleForm.styleType" style="width:100%">
                <el-option v-for="o in styleTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="风格标签">
              <el-select v-model="styleForm.styleTag" style="width:100%">
                <el-option v-for="o in styleTagOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="难度等级">
              <el-rate v-model="styleForm.difficultyLevel" :max="5" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="基础价格">
              <el-input-number v-model="styleForm.basePrice" :min="0" :precision="2" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="耗时(分钟)">
              <el-input-number v-model="styleForm.durationMinutes" :min="0" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="主色系">
              <el-select v-model="styleForm.colorScheme" multiple style="width:100%">
                <el-option v-for="o in colorSchemeOptions" :key="o.value" :label="o.value" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="主图URL">
              <el-input v-model="styleForm.mainImage" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="款式描述">
          <el-input v-model="styleForm.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="操作步骤">
          <el-input v-model="styleForm.operationSteps" type="textarea" :rows="3" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="新品">
              <el-switch v-model="styleForm.isNew" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="热门">
              <el-switch v-model="styleForm.isHot" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="上架">
              <el-switch v-model="styleForm.status" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="styleDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveStyle">保存</el-button>
      </template>
    </el-dialog>

    <!-- 材料包编辑对话框 -->
    <el-dialog v-model="packageDialogVisible" :title="packageDialogTitle" width="700px">
      <el-form :model="packageForm" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="包名称">
              <el-input v-model="packageForm.packageName" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="包类型">
              <el-select v-model="packageForm.packageType" style="width:100%">
                <el-option v-for="o in packageTypeOptions" :key="o.value" :label="`${o.label} - ${o.description}`" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="用途描述">
          <el-input v-model="packageForm.purpose" type="textarea" :rows="2" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="售价">
              <el-input-number v-model="packageForm.salePrice" :min="0" :precision="2" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="折扣(0-1)">
              <el-input-number v-model="packageForm.discountRate" :min="0" :max="1" :step="0.05" :precision="2" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="预计款式数">
              <el-input-number v-model="packageForm.serveCount" :min="0" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="上架状态">
              <el-radio-group v-model="packageForm.status">
                <el-radio :value="1">上架</el-radio>
                <el-radio :value="0">下架</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="库存">
              <el-input-number v-model="packageForm.stockQuantity" :min="0" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="packageDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSavePackage">保存</el-button>
      </template>
    </el-dialog>

    <!-- 材料包明细对话框 -->
    <el-dialog v-model="itemsDialogVisible" title="材料包明细" width="800px">
      <div style="margin-bottom: 12px">
        <el-button type="primary" size="small" @click="handleAddItem">
          <el-icon><Plus /></el-icon>添加明细
        </el-button>
        <el-button type="success" size="small" @click="saveItems">
          <el-icon><Check /></el-icon>保存并重算价格
        </el-button>
      </div>
      <el-table :data="itemList" border>
        <el-table-column label="耗材" min-width="160">
          <template #default="{ row }">
            <el-select v-model="row.productId" filterable style="width:100%" @change="onProductSelectChange(row)">
              <el-option v-for="p in stockProductList" :key="p.id" :label="p.productName" :value="p.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="120">
          <template #default="{ row }">
            <el-input-number v-model="row.quantity" :min="0" :precision="2" @change="recalcItem(row)" />
          </template>
        </el-table-column>
        <el-table-column label="单价" width="120">
          <template #default="{ row }">
            <el-input-number v-model="row.unitPrice" :min="0" :precision="2" @change="recalcItem(row)" />
          </template>
        </el-table-column>
        <el-table-column label="小计" prop="subtotal" width="120" />
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ $index }">
            <el-button type="danger" link size="small" @click="itemList.splice($index, 1)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="items-total">
        总成本: <span class="total-num">¥{{ totalCost.toFixed(2) }}</span>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Picture, Star, StarFilled, View, Plus, Search, MagicStick, Box, Check
} from '@element-plus/icons-vue'
import request from '@/utils/http'
import {
  styleApi,
  styleTypeOptions,
  styleTagOptions,
  colorSchemeOptions,
  nailShapeOptions,
  lengthOptions,
  difficultyOptions,
  packageTypeOptions
} from '@/api/style-package'

const activeTab = ref('library')

// ============ 款式库 ============
const styleList = ref<any[]>([])
const styleLoading = ref(false)
const styleTypeFilter = ref('')
const styleTagFilter = ref('')
const difficultyFilter = ref<number | null>(null)
const styleDialogVisible = ref(false)
const styleDialogTitle = ref('新增款式')
const styleForm = reactive<any>({
  id: null,
  styleName: '',
  styleType: 'nail_art',
  styleTag: '',
  basePrice: 100,
  difficultyLevel: 2,
  durationMinutes: 60,
  colorScheme: '',
  mainImage: '',
  description: '',
  operationSteps: '',
  isNew: 0,
  isHot: 0,
  status: 1
})

const newCount = computed(() => styleList.value.filter(s => s.isNew === 1).length)
const hotCount = computed(() => styleList.value.filter(s => s.isHot === 1).length)
const totalUseCount = computed(() => styleList.value.reduce((sum, s) => sum + (s.useCount || 0), 0))

const loadStyles = async () => {
  styleLoading.value = true
  try {
    const res: any = await styleApi.styles({
      styleType: styleTypeFilter.value || undefined,
      styleTag: styleTagFilter.value || undefined,
      difficultyLevel: difficultyFilter.value || undefined
    })
    styleList.value = res.data || []
  } catch (e) {
    styleList.value = []
  } finally {
    styleLoading.value = false
  }
}

const handleAddStyle = () => {
  Object.assign(styleForm, {
    id: null,
    styleName: '',
    styleType: 'nail_art',
    styleTag: '',
    basePrice: 100,
    difficultyLevel: 2,
    durationMinutes: 60,
    mainImage: '',
    description: '',
    operationSteps: '',
    isNew: 0,
    isHot: 0,
    status: 1
  })
  styleDialogTitle.value = '新增款式'
  styleDialogVisible.value = true
}

const handleEditStyle = (row: any) => {
  Object.assign(styleForm, row)
  styleDialogTitle.value = '编辑款式'
  styleDialogVisible.value = true
}

const handleViewStyle = (row: any) => {
  Object.assign(styleForm, row)
  styleDialogTitle.value = '查看款式'
  styleDialogVisible.value = true
  // 记录浏览
  styleApi.recordView(row.id)
}

const handleSaveStyle = async () => {
  try {
    if (styleForm.id) {
      await styleApi.updateStyle(styleForm)
    } else {
      await styleApi.createStyle(styleForm)
    }
    ElMessage.success('保存成功')
    styleDialogVisible.value = false
    loadStyles()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

const getDifficultyLabel = (d: number) => difficultyOptions.find(o => o.value === d)?.label || d

// ============ 客户偏好 ============
const memberKeyword = ref('')
const searchedMembers = ref<any[]>([])
const currentMember = ref<any>(null)
const pref = reactive<any>({
  id: null,
  memberId: null,
  preferredType: '',
  preferredStyles: '',
  preferredColors: '',
  avoidColors: '',
  preferredLength: 'medium',
  preferredShape: 'round',
  allergyIngredients: '',
  specialNote: '',
  recentStyles: '',
  favoriteStyles: '',
  historyStyles: ''
})
const prefTab = ref('pref')
const recLoading = ref(false)
const recommendList = ref<any[]>([])

const searchMemberList = async () => {
  if (!memberKeyword.value) {
    searchedMembers.value = []
    return
  }
  try {
    const res: any = await request.get('/member/search', { params: { keyword: memberKeyword.value } })
    searchedMembers.value = res.data || []
  } catch (e) {
    searchedMembers.value = []
  }
}

const selectMember = async (m: any) => {
  currentMember.value = m
  const res: any = await styleApi.preference(m.id)
  Object.assign(pref, res.data || { memberId: m.id })
  // 初始化偏好字符串为数组(用于多选组件)
  pref.preferredStyles = pref.preferredStyles ? pref.preferredStyles.split(',') : []
  pref.preferredColors = pref.preferredColors ? pref.preferredColors.split(',') : []
  pref.avoidColors = pref.avoidColors ? pref.avoidColors.split(',') : []
}

const savePreference = async () => {
  try {
    // 数组转字符串
    const data = {
      ...pref,
      preferredStyles: Array.isArray(pref.preferredStyles) ? pref.preferredStyles.join(',') : pref.preferredStyles,
      preferredColors: Array.isArray(pref.preferredColors) ? pref.preferredColors.join(',') : pref.preferredColors,
      avoidColors: Array.isArray(pref.avoidColors) ? pref.avoidColors.join(',') : pref.avoidColors
    }
    await styleApi.updatePreference(currentMember.value.id, data)
    ElMessage.success('保存成功')
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

const getPrefStyleIds = (tab: string) => {
  const data = tab === 'recent' ? pref.recentStyles : pref.favoriteStyles
  if (!data) return []
  return data.split(',').filter(Boolean)
}

const loadRecommendations = async () => {
  recLoading.value = true
  try {
    const res: any = await styleApi.recommend({
      memberId: currentMember.value.id,
      styleType: pref.preferredType || undefined,
      limit: 12
    })
    recommendList.value = res.data || []
  } catch (e) {
    recommendList.value = []
  } finally {
    recLoading.value = false
  }
}

// ============ 材料包 ============
const packageList = ref<any[]>([])
const packageLoading = ref(false)
const packageTypeFilter = ref('')
const packageDialogVisible = ref(false)
const packageDialogTitle = ref('新增材料包')
const packageForm = reactive<any>({
  id: null,
  packageName: '',
  packageType: 'kit',
  purpose: '',
  salePrice: 0,
  discountRate: 0.9,
  serveCount: 0,
  status: 1,
  stockQuantity: 0,
  mainImage: ''
})

const loadPackages = async () => {
  packageLoading.value = true
  try {
    const res: any = await styleApi.packages({
      packageType: packageTypeFilter.value || undefined
    })
    packageList.value = res.data || []
  } catch (e) {
    packageList.value = []
  } finally {
    packageLoading.value = false
  }
}

const handleAddPackage = () => {
  Object.assign(packageForm, {
    id: null,
    packageName: '',
    packageType: 'kit',
    purpose: '',
    salePrice: 0,
    discountRate: 0.9,
    serveCount: 0,
    status: 1,
    stockQuantity: 0
  })
  packageDialogTitle.value = '新增材料包'
  packageDialogVisible.value = true
}

const handleEditPackage = (row: any) => {
  Object.assign(packageForm, row)
  packageDialogTitle.value = '编辑材料包'
  packageDialogVisible.value = true
}

const handleSavePackage = async () => {
  try {
    if (packageForm.id) {
      await styleApi.updatePackage(packageForm)
    } else {
      await styleApi.createPackage(packageForm)
    }
    ElMessage.success('保存成功')
    packageDialogVisible.value = false
    loadPackages()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

const handleDeletePackage = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确定删除包"${row.packageName}"吗？`, '提示', { type: 'warning' })
    await styleApi.deletePackage(row.id)
    ElMessage.success('删除成功')
    loadPackages()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

const getPackageTypeLabel = (t: string) => packageTypeOptions.find(o => o.value === t)?.label || t

// ============ 材料包明细 ============
const itemsDialogVisible = ref(false)
const itemList = ref<any[]>([])
const stockProductList = ref<any[]>([])
const totalCost = computed(() => itemList.value.reduce((sum, i) => sum + (Number(i.subtotal) || 0), 0))

const handleEditPackageItems = async (row: any) => {
  const res: any = await styleApi.packageDetail(row.id)
  itemList.value = res.data?.items || []
  packageForm.id = row.id
  itemsDialogVisible.value = true
}

const handleAddItem = () => {
  itemList.value.push({
    productId: null,
    productName: '',
    quantity: 1,
    unitPrice: 0,
    subtotal: 0
  })
}

const onProductSelectChange = (row: any) => {
  const p = stockProductList.value.find(item => item.id === row.productId)
  if (p) {
    row.productName = p.productName
    row.unitPrice = p.costPrice || p.cost_price || 0
    row.spec = p.spec
    recalcItem(row)
  }
}

const recalcItem = (row: any) => {
  row.subtotal = (Number(row.quantity) || 0) * (Number(row.unitPrice) || 0)
}

const saveItems = async () => {
  try {
    await styleApi.replaceItems(packageForm.id, itemList.value)
    ElMessage.success('保存成功,价格已重算')
    itemsDialogVisible.value = false
    loadPackages()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

const loadStockProducts = async () => {
  try {
    const res: any = await request.get('/stock/product/list')
    stockProductList.value = res.data || []
  } catch (e) {}
}

// ============ Tab 切换 ============
const handleTabChange = (tab: string) => {
  if (tab === 'library') loadStyles()
  else if (tab === 'package') loadPackages()
}

onMounted(() => {
  loadStyles()
  loadStockProducts()
})
</script>

<style scoped>
.style-page {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-form {
  display: flex;
  gap: 12px;
  align-items: center;
}

.dash-stat {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
}

.dash-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.dash-icon.blue { background: linear-gradient(135deg, #409eff, #79bbff); }
.dash-icon.green { background: linear-gradient(135deg, #67c23a, #95d475); }
.dash-icon.orange { background: linear-gradient(135deg, #e6a23c, #f3d19e); }
.dash-icon.purple { background: linear-gradient(135deg, #9c27b0, #ce93d8); }

.dash-value { font-size: 24px; font-weight: 700; color: #303133; }
.dash-label { font-size: 13px; color: #909399; margin-top: 2px; }

/* 款式卡片 */
.style-card-col { margin-bottom: 16px; }
.style-card {
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
}
.style-card:hover {
  box-shadow: 0 4px 16px rgba(0,0,0,0.1);
  transform: translateY(-2px);
}

.style-image {
  width: 100%;
  height: 160px;
  position: relative;
  background: #f5f7fa;
}

.style-image .placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
}

.tag-new, .tag-hot {
  position: absolute;
  top: 6px;
  padding: 2px 6px;
  font-size: 11px;
  font-weight: 600;
  color: #fff;
  border-radius: 3px;
}

.tag-new { right: 6px; background: #67c23a; }
.tag-hot { left: 6px; background: #f56c6c; }

.style-info { padding: 10px; }
.style-name {
  font-weight: 600;
  color: #303133;
  font-size: 14px;
  margin-bottom: 6px;
}

.style-tags {
  display: flex;
  gap: 4px;
  margin-bottom: 6px;
}

.style-meta {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
}

.price { color: #f56c6c; font-weight: 600; }
.use { color: #909399; display: flex; align-items: center; gap: 2px; }

/* 客户列表 */
.member-list { margin-top: 12px; max-height: 500px; overflow-y: auto; }
.member-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 4px;
}
.member-item:hover { background: #f5f7fa; }
.member-item.active { background: #ecf5ff; }
.mi-info { flex: 1; min-width: 0; }
.mi-name { font-weight: 600; font-size: 14px; }
.mi-phone { font-size: 12px; color: #909399; }

/* 推荐款式 */
.mini-styles {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
}

.mini-card {
  background: #fafbfc;
  border-radius: 6px;
  overflow: hidden;
  text-align: center;
}

.mini-img {
  width: 100%;
  height: 100px;
  background: #f0f0f0;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
}

.mini-name {
  padding: 8px;
  font-size: 13px;
  color: #303133;
}

.mini-tag {
  padding: 0 8px 8px;
}

/* 材料包卡片 */
.package-card {
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 16px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
  position: relative;
}

.package-image {
  width: 100%;
  height: 180px;
  background: linear-gradient(135deg, #fce4ec, #f8bbd0);
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c2185b;
}

.package-image .placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
}

.package-type-tag {
  position: absolute;
  top: 8px;
  right: 8px;
}

.package-body { padding: 12px; }
.package-name { font-size: 15px; font-weight: 600; margin: 0 0 6px; }
.package-purp { font-size: 12px; color: #909399; margin-bottom: 8px; min-height: 32px; }

.package-prices {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.price-final { font-size: 18px; font-weight: 700; color: #f56c6c; }
.price-original { text-decoration: line-through; color: #909399; font-size: 12px; }

.package-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #909399;
}

.package-actions {
  padding: 8px 12px;
  border-top: 1px solid #f5f7fa;
  text-align: right;
}

.items-total {
  margin-top: 12px;
  text-align: right;
  font-size: 14px;
}

.total-num {
  font-size: 18px;
  font-weight: 700;
  color: #f56c6c;
  margin-left: 4px;
}
</style>