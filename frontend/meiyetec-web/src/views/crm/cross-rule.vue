<template>
  <div class="cross-rule-page">
    <!-- 统计看板 -->
    <el-card class="stats-card" v-loading="statsLoading" v-if="statsVisible">
      <template #header>
        <div class="stats-header">
          <span class="stats-title">📊 跨店规则数据统计</span>
          <el-button size="small" @click="loadStats" link>🔄 刷新</el-button>
        </div>
      </template>
      <div class="stats-grid">
        <div class="stat-item">
          <div class="stat-icon" style="background:#eff6ff;color:#3b82f6">💰</div>
          <div class="stat-label">本月跨店核销</div>
          <div class="stat-value accent">¥{{ stats.totalAmount?.toLocaleString() ?? 0 }}</div>
          <div class="stat-trend" v-if="stats.lastWeekAmount != null">
            <span :class="stats.totalAmount >= stats.lastWeekAmount ? 'up' : 'down'">
              {{ stats.totalAmount >= stats.lastWeekAmount ? '▲' : '▼' }}
              {{ Math.abs(((stats.totalAmount - stats.lastWeekAmount) / (stats.lastWeekAmount || 1) * 100)).toFixed(1) }}%
            </span>
            <span class="trend-label">环比上周</span>
          </div>
        </div>
        <div class="stat-item">
          <div class="stat-icon" style="background:#fffbeb;color:#f59e0b">⚡</div>
          <div class="stat-label">手续费总额</div>
          <div class="stat-value warning">¥{{ stats.totalFee?.toLocaleString() ?? 0 }}</div>
          <div class="stat-trend" v-if="stats.lastWeekFee != null">
            <span :class="stats.totalFee >= stats.lastWeekFee ? 'up' : 'down'">
              {{ stats.totalFee >= stats.lastWeekFee ? '▲' : '▼' }}
              {{ Math.abs(((stats.totalFee - stats.lastWeekFee) / (stats.lastWeekFee || 1) * 100)).toFixed(1) }}%
            </span>
            <span class="trend-label">环比上周</span>
          </div>
        </div>
        <div class="stat-item">
          <div class="stat-icon" style="background:#f0fdf4;color:#10b981">🎯</div>
          <div class="stat-label">TOP1 规则</div>
          <div class="stat-value primary" :title="stats.topRuleName">{{ stats.topRuleName ? stats.topRuleName.slice(0, 12) : '-' }}</div>
          <div class="stat-detail">{{ stats.topHitCount ?? 0 }} 次命中</div>
        </div>
        <div class="stat-item">
          <div class="stat-icon" style="background:#fdf4ff;color:#a855f7">📈</div>
          <div class="stat-label">规则总数</div>
          <div class="stat-value" style="color:#a855f7">{{ stats.totalRules ?? 0 }}</div>
          <div class="stat-detail">启用中 {{ stats.enabledRules ?? 0 }} 条</div>
        </div>
      </div>
    </el-card>

    <el-card class="search-bar">
      <div class="search-row">
        <div class="search-fields">
          <el-input v-model="filter.keyword" placeholder="规则名称" style="width: 200px" clearable @keyup.enter="loadData" />
          <el-select v-model="filter.brandId" placeholder="品牌" clearable style="width: 140px" @change="loadData">
            <el-option label="租户通用" :value="-1" />
            <el-option v-for="b in brands" :key="b.id" :label="b.brandName" :value="b.id" />
          </el-select>
          <el-select v-model="filter.enabled" placeholder="状态" clearable style="width: 120px" @change="loadData">
            <el-option label="启用" :value="1" />
            <el-option label="停用" :value="0" />
          </el-select>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="resetFilter">重置</el-button>
        </div>
        <div class="search-actions">
          <el-button @click="openTester" plain>🧪 测试器</el-button>
          <el-button @click="loadStats" :type="statsVisible ? 'warning' : 'default'" plain>📊 看板</el-button>
          <el-button @click="exportExcel" plain>📥 导出</el-button>
          <el-button @click="showRecycleBin" type="info" plain>🗑 回收站</el-button>
          <el-button type="primary" @click="openCreate">+ 新增规则</el-button>
        </div>
      </div>
    </el-card>

    <el-card class="table-card">
      <!-- 快速数据摘要（仅在有数据时显示） -->
      <div class="quick-summary" v-if="rows.length > 0">
        <span class="summary-item">📋 共 <strong>{{ total }}</strong> 条规则</span>
        <span class="summary-item">✅ 启用中 <strong>{{ rows.filter((r: any) => r.enabled === 1).length }}</strong> 条</span>
        <span class="summary-item">🎯 累计命中 <strong>{{ rows.reduce((s: number, r: any) => s + (r.hitCount || 0), 0) }}</strong> 次</span>
        <span class="summary-item">💰 手续费：<strong>{{ feeSummary }}</strong></span>
      </div>

      <!-- 批量操作栏 -->
      <div class="batch-bar" v-if="selectedRows.length > 0">
        <span>已选 {{ selectedRows.length }} 条</span>
        <el-button size="small" type="success" @click="batchEnable(true)" :loading="batchLoading">批量启用</el-button>
        <el-button size="small" type="warning" @click="batchEnable(false)" :loading="batchLoading">批量停用</el-button>
        <el-button size="small" @click="batchCopyToBrand">📋 复制到品牌</el-button>
        <el-button size="small" @click="batchMoveToTrash" type="danger">🗑 批量删除</el-button>
        <el-button size="small" @click="selectedRows = []">取消选择</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" border stripe
        @selection-change="(v: any[]) => selectedRows = v"
        row-class-name="cross-rule-row">
        <template #empty>
          <div class="empty-state">
            <div class="empty-icon">📋</div>
            <div class="empty-title">暂无跨店规则</div>
            <div class="empty-desc">创建第一条跨店规则，实现多门店会员资源共享</div>
            <el-button type="primary" @click="openCreate">+ 创建第一条规则</el-button>
          </div>
        </template>
        <el-table-column type="selection" width="40" />
        <el-table-column prop="ruleName" label="规则名称" min-width="200">
          <template #default="{ row }">
            <el-link type="primary" @click="openPreview(row)">{{ row.ruleName }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="品牌范围" width="140">
          <template #default="{ row }">
            <el-tag v-if="row.brandId" size="small" type="primary">
              {{ brands.find((b: any) => b.id === row.brandId)?.brandName || `品牌#${row.brandId}` }}
            </el-tag>
            <el-tag v-else size="small">租户通用</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="套餐/项目" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.packageCrossEnabled === 1" size="small" :type="modeTag(row.packageCrossMode)">
              {{ modeLabel(row.packageCrossMode) }}
            </el-tag>
            <el-tag v-else size="small" type="info">禁止</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="积分" width="76">
          <template #default="{ row }">
            <el-tag :type="row.pointsCrossEnabled === 1 ? 'success' : 'info'" size="small">
              {{ row.pointsCrossEnabled === 1 ? '共享' : '禁止' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="储值" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.balanceCrossEnabled === 1" size="small" :type="modeTag(row.balanceCrossMode)">
              {{ modeLabel(row.balanceCrossMode) }}
            </el-tag>
            <el-tag v-else size="small" type="info">禁止</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="优惠券" width="76">
          <template #default="{ row }">
            <el-tag :type="row.couponCrossEnabled === 1 ? 'success' : 'info'" size="small">
              {{ row.couponCrossEnabled === 1 ? '共享' : '禁止' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="手续费" width="110">
          <template #default="{ row }">
            <span v-if="row.crossFeeType === 'NONE' || !row.crossFeeType">无</span>
            <span v-else-if="row.crossFeeType === 'PERCENT'">{{ row.crossFeeRate }}%</span>
            <span v-else>固定 ¥{{ row.crossFeeRate }}</span>
          </template>
        </el-table-column>
        <el-table-column label="优先级" width="80" align="center">
          <template #default="{ row }">{{ row.priority }}</template>
        </el-table-column>
        <el-table-column label="生效时间" width="140">
          <template #default="{ row }">
            <span v-if="row.startTime || row.endTime">
              {{ row.startTime ? row.startTime.slice(0,10) : '随时' }}
              ~
              <span :class="isPermanent(row.endTime) ? 'text-permanent' : ''">
                {{ isPermanent(row.endTime) ? '永久' : row.endTime.slice(0,10) }}
              </span>
            </span>
            <span v-else class="text-muted">永久生效</span>
          </template>
        </el-table-column>
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-switch
              :model-value="row.enabled === 1"
              @change="toggleEnabled(row)"
              :loading="togglingId === row.id"
              active-text=""
              inactive-text=""
            />
          </template>
        </el-table-column>
        <el-table-column label="命中" width="70" align="center" sortable :sort-by="(row: any) => row.hitCount">
          <template #default="{ row }">
            <el-tag v-if="row.hitCount > 0" type="warning" size="small">{{ row.hitCount }}</el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="130">
          <template #default="{ row }">
            <span class="text-muted">{{ row.createTime ? row.createTime.replace('T',' ').slice(0,16) : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-dropdown trigger="click" @command="(cmd: string) => handleRowCommand(cmd, row)">
              <el-button link size="small">⋯</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="preview">详情</el-dropdown-item>
                  <el-dropdown-item command="clone">复制</el-dropdown-item>
                  <el-dropdown-item command="edit">编辑</el-dropdown-item>
                  <el-dropdown-item command="trash" divided style="color: #f56c6c">删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="filter.pageNum"
          v-model:page-size="filter.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </el-card>

    <!-- 详情预览弹窗 -->
    <el-dialog v-model="preview.visible" title="规则详情" width="640px">
      <el-descriptions :column="2" border v-if="preview.data">
        <el-descriptions-item label="规则名称" :span="2">{{ preview.data.ruleName }}</el-descriptions-item>
        <el-descriptions-item label="规则说明" :span="2">{{ preview.data.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="品牌范围">
          <el-tag v-if="preview.data.brandId" size="small" type="primary">品牌内</el-tag>
          <el-tag v-else size="small">租户通用</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="preview.data.enabled === 1 ? 'success' : 'info'" size="small">
            {{ preview.data.enabled === 1 ? '启用' : '停用' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="生效时间" :span="2">
          {{ preview.data.startTime ? preview.data.startTime.slice(0,16).replace('T',' ') : '立即' }}
          至
          <span :class="isPermanent(preview.data.endTime) ? 'text-permanent' : ''">
            {{ isPermanent(preview.data.endTime) ? '永久' : (preview.data.endTime ? preview.data.endTime.slice(0,16).replace('T',' ') : '永久') }}
          </span>
        </el-descriptions-item>
        <el-descriptions-item label="套餐/项目">
          <el-tag v-if="preview.data.packageCrossEnabled === 1" :type="modeTag(preview.data.packageCrossMode)">
            {{ modeLabel(preview.data.packageCrossMode) }}
          </el-tag>
          <span v-else>禁止</span>
        </el-descriptions-item>
        <el-descriptions-item label="积分">
          {{ preview.data.pointsCrossEnabled === 1 ? '共享' : '禁止' }}
        </el-descriptions-item>
        <el-descriptions-item label="储值">
          <el-tag v-if="preview.data.balanceCrossEnabled === 1" :type="modeTag(preview.data.balanceCrossMode)">
            {{ modeLabel(preview.data.balanceCrossMode) }}
          </el-tag>
          <span v-else>禁止</span>
        </el-descriptions-item>
        <el-descriptions-item label="优惠券">
          {{ preview.data.couponCrossEnabled === 1 ? '共享' : '禁止' }}
        </el-descriptions-item>
        <el-descriptions-item label="折扣">
          {{ preview.data.discountCrossEnabled === 1 ? '共享' : '禁止' }}
        </el-descriptions-item>
        <el-descriptions-item label="手续费">
          <span v-if="preview.data.crossFeeType === 'NONE' || !preview.data.crossFeeType">不收</span>
          <span v-else-if="preview.data.crossFeeType === 'PERCENT'">{{ preview.data.crossFeeRate }}%</span>
          <span v-else>固定 ¥{{ preview.data.crossFeeRate }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="单笔限额">
          {{ preview.data.crossMaxAmount > 0 ? '¥' + preview.data.crossMaxAmount : '不限' }}
        </el-descriptions-item>
        <el-descriptions-item label="优先级" align="center">{{ preview.data.priority }}</el-descriptions-item>
        <el-descriptions-item label="命中次数" align="center">
          <el-tag v-if="preview.data.hitCount > 0" type="warning">{{ preview.data.hitCount }}</el-tag>
          <span v-else>0</span>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间" :span="2">
          {{ preview.data.createTime ? preview.data.createTime.replace('T',' ') : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="更新时间" :span="2">
          {{ preview.data.updateTime ? preview.data.updateTime.replace('T',' ') : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="修改人">
          {{ preview.data.updateByName || preview.data.updateBy || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="匹配门店预览" :span="2">
          <div class="match-stores-preview" v-if="preview.matchedStores && preview.matchedStores.length">
            <span style="margin-right: 8px; color: #64748b; font-size: 13px;">共 {{ preview.matchedStores.length }} 家门店：</span>
            <el-tag v-for="s in preview.matchedStores.slice(0, 6)" :key="s.id" size="small" style="margin: 2px">
              {{ s.storeName }}
            </el-tag>
            <el-tag v-if="preview.matchedStores.length > 6" size="small" type="info">+{{ preview.matchedStores.length - 6 }} 更多</el-tag>
          </div>
          <span v-else class="text-muted">—</span>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="preview.visible = false">关闭</el-button>
        <el-button type="primary" @click="openEditFromPreview">编辑此规则</el-button>
      </template>
    </el-dialog>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialog.visible" :title="dialog.id ? '编辑规则' : '新增规则'" width="800px" @closed="resetDialog">
      <template #header>
        <span>{{ dialog.id ? '编辑规则' : '新增规则' }}</span>
        <el-tooltip content="查看配置说明" placement="bottom" effect="light">
          <el-button link style="margin-left: 8px; font-size: 16px;" @click="showHelp = true">❓</el-button>
        </el-tooltip>
      </template>
      <el-form :model="form" label-width="120px" :rules="formRules" ref="formRef">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="规则名称" prop="ruleName">
              <el-input v-model="form.ruleName" placeholder="如：品牌A跨店允许" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="品牌范围">
              <el-select v-model="form.brandId" placeholder="租户通用" clearable style="width: 100%" @change="checkConflict">
                <el-option v-for="b in brands" :key="b.id" :label="b.brandName" :value="b.id" />
              </el-select>
              <div class="form-tip">留空 = 租户通用规则</div>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="规则说明">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="可选，用于说明规则的用途..." />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="生效时间">
              <el-date-picker
                v-model="form.startTime"
                type="datetime"
                placeholder="开始时间（留空立即生效）"
                style="width: 100%"
                value-format="YYYY-MM-DD HH:mm:ss"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束时间">
              <el-date-picker
                v-model="form.endTime"
                type="datetime"
                placeholder="结束时间（留空永久有效）"
                style="width: 100%"
                value-format="YYYY-MM-DD HH:mm:ss"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="套餐/项目">
              <el-radio-group v-model="form.packageCrossEnabled" @change="onPackageCrossEnabledChange">
                <el-radio :value="1">允许</el-radio>
                <el-radio :value="0">禁止</el-radio>
              </el-radio-group>
              <el-select v-if="form.packageCrossEnabled === 1" v-model="form.packageCrossMode" style="width: 100%; margin-top: 6px" size="small" @change="checkConflict">
                <el-option label="品牌内" value="BRAND" />
                <el-option label="连锁内" value="CHAIN" />
                <el-option label="任意门店" value="ANY" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="储值">
              <el-radio-group v-model="form.balanceCrossEnabled" @change="onBalanceCrossEnabledChange">
                <el-radio :value="1">允许</el-radio>
                <el-radio :value="0">禁止</el-radio>
              </el-radio-group>
              <el-select v-if="form.balanceCrossEnabled === 1" v-model="form.balanceCrossMode" style="width: 100%; margin-top: 6px" size="small">
                <el-option label="品牌内" value="BRAND" />
                <el-option label="仅本门店" value="STORE" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="积分共享">
              <el-radio-group v-model="form.pointsCrossEnabled">
                <el-radio :value="1">是</el-radio>
                <el-radio :value="0">否</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="折扣共享">
              <el-radio-group v-model="form.discountCrossEnabled">
                <el-radio :value="1">是</el-radio>
                <el-radio :value="0">否</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="优惠券共享">
              <el-radio-group v-model="form.couponCrossEnabled">
                <el-radio :value="1">是</el-radio>
                <el-radio :value="0">否</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 高级设置折叠区 -->
        <el-collapse-transition>
          <div v-if="advancedExpanded">
            <el-divider content-position="left">
              <el-button link size="small" @click="advancedExpanded = false">🔧 高级设置 ▲</el-button>
            </el-divider>
            <el-row :gutter="12">
              <el-col :span="8">
                <el-form-item label="手续费类型" prop="crossFeeType">
                  <el-select v-model="form.crossFeeType" style="width: 100%" @change="checkFeeRateRequired">
                    <el-option label="不收" value="NONE" />
                    <el-option label="按比例" value="PERCENT" />
                    <el-option label="固定" value="FIXED" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="手续费值" prop="crossFeeRate">
                  <el-input-number v-model="form.crossFeeRate" :min="0" :max="form.crossFeeType === 'PERCENT' ? 100 : 999999" :step="0.5" :precision="2" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="8">
                <el-form-item label="单笔限额">
                  <el-input-number v-model="form.crossMaxAmount" :min="0" :precision="2" style="width: 100%" placeholder="0=不限" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="优先级" prop="priority">
                  <el-input-number v-model="form.priority" :min="0" :max="9999" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="启用">
                  <el-radio-group v-model="form.effectiveType">
                    <el-tooltip content="保存后立即生效" placement="top">
                      <el-radio value="IMMEDIATE">立即生效</el-radio>
                    </el-tooltip>
                    <el-tooltip content="保存后从次日0点生效" placement="top">
                      <el-radio value="NEXT_DAY">次日生效</el-radio>
                    </el-tooltip>
                  </el-radio-group>
                </el-form-item>
              </el-col>
            </el-row>
          </div>
        </el-collapse-transition>

        <!-- 一键展开高级设置提示 -->
        <div v-if="!advancedExpanded" class="advanced-hint" @click="advancedExpanded = true">
          🔧 高级设置（手续费、单笔限额、优先级、生效时间）...
        </div>

        <!-- 冲突警告 -->
        <el-alert v-if="conflictWarning" :title="conflictWarning" type="warning" show-icon :closable="false" style="margin-top: 8px" />
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="save" :loading="dialog.saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 帮助说明弹窗 -->
    <el-dialog v-model="showHelp" title="跨店规则配置说明" width="560px">
      <el-alert type="info" :closable="false" style="margin-bottom: 16px">
        <template #title>配置优先级说明</template>
        规则按 <b>优先级（priority）</b> 从高到低匹配，数字越大优先级越高。
      </el-alert>
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="套餐/项目 - 品牌内">
          允许同一品牌下的门店之间跨店消费套餐/项目
        </el-descriptions-item>
        <el-descriptions-item label="套餐/项目 - 连锁内">
          允许同一连锁（租户）下的所有门店之间跨店
        </el-descriptions-item>
        <el-descriptions-item label="套餐/项目 - 任意门店">
          允许会员在任意门店核销（权限最大，建议设为最低优先级）
        </el-descriptions-item>
        <el-descriptions-item label="手续费 - 按比例">
          每笔跨店交易收取百分比作为服务费
        </el-descriptions-item>
        <el-descriptions-item label="单笔限额">
          0 或留空 = 不限制单笔金额上限
        </el-descriptions-item>
        <el-descriptions-item label="立即/次日生效">
          立即：保存即生效；次日：次日0点自动生效，适合批量配置
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" @click="showHelp = false">我知道了</el-button>
      </template>
    </el-dialog>

    <!-- 规则测试器弹窗 -->
    <el-dialog v-model="tester.visible" title="跨店规则测试器" width="860px">
      <div class="tester-layout">
        <!-- 左侧：测试表单 -->
        <div class="tester-main">
          <el-form :model="tester" label-width="100px" inline>
            <el-form-item label="会员手机">
              <el-input v-model="tester.memberPhone" placeholder="输入手机号" style="width: 160px" clearable />
            </el-form-item>
            <el-form-item label="来源门店">
              <el-select v-model="tester.fromStoreId" placeholder="选择门店" style="width: 160px" clearable>
                <el-option v-for="s in stores" :key="s.id" :label="s.storeName" :value="s.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="目标门店">
              <el-select v-model="tester.toStoreId" placeholder="选择门店" style="width: 160px" clearable>
                <el-option v-for="s in stores" :key="s.id" :label="s.storeName" :value="s.id" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="runTest" :loading="tester.loading">开始测试</el-button>
              <el-button @click="toggleBatchMode">{{ tester.batchMode ? '🔙 单条测试' : '📋 批量测试' }}</el-button>
            </el-form-item>
          </el-form>

          <!-- 批量测试输入区 -->
          <el-collapse-transition>
            <div v-if="tester.batchMode" class="batch-test-area">
              <el-divider content-position="left">批量测试（每行一对门店，格式：来源ID,目标ID）</el-divider>
              <el-input v-model="tester.batchInput" type="textarea" :rows="6" placeholder="例如：&#10;1,2&#10;3,4&#10;5,6" style="font-family: monospace" />
              <el-button type="primary" size="small" style="margin-top: 8px" @click="runBatchTest" :loading="tester.batchLoading">执行批量测试</el-button>
            </div>
          </el-collapse-transition>

          <!-- 单条测试结果 -->
          <div v-if="tester.result && !tester.batchMode" class="test-result">
            <el-divider content-position="left">测试结果</el-divider>
            <el-descriptions :column="2" border size="small">
              <el-descriptions-item label="匹配规则">{{ tester.result.ruleName ?? '-' }}</el-descriptions-item>
              <el-descriptions-item label="结果">
                <el-tag :type="tester.result.match ? 'success' : 'danger'" size="small">
                  {{ tester.result.match ? '可跨店' : '禁止跨店' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="套餐/项目">
                <el-tag v-if="tester.result.packageCrossEnabled === 1" type="success" size="small">
                  ✅ 允许 ({{ modeLabel(tester.result.packageCrossMode) }})
                </el-tag>
                <el-tag v-else type="danger" size="small">❌ 禁止</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="积分">
                <el-tag v-if="tester.result.pointsCrossEnabled === 1" type="success" size="small">✅ 共享</el-tag>
                <el-tag v-else type="danger" size="small">❌ 禁止</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="储值">
                <span v-if="tester.result.balanceCrossEnabled === 1">
                  <el-tag type="success" size="small">✅ 允许 ({{ modeLabel(tester.result.balanceCrossMode) }})</el-tag>
                </span>
                <el-tag v-else type="danger" size="small">❌ 禁止</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="优惠券">
                <el-tag v-if="tester.result.couponCrossEnabled === 1" type="success" size="small">✅ 共享</el-tag>
                <el-tag v-else type="danger" size="small">❌ 禁止</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="手续费">
                <span v-if="tester.result.crossFeeType === 'NONE' || !tester.result.crossFeeType">不收取</span>
                <span v-else-if="tester.result.crossFeeType === 'PERCENT'">{{ tester.result.crossFeeRate }}%</span>
                <span v-else>固定 ¥{{ tester.result.crossFeeRate }}</span>
              </el-descriptions-item>
              <el-descriptions-item label="匹配原因">{{ tester.result.reason ?? '-' }}</el-descriptions-item>
            </el-descriptions>
          </div>

          <!-- 批量测试结果 -->
          <div v-if="tester.batchResults && tester.batchMode" class="test-result">
            <el-divider content-position="left">
              批量测试结果 ({{ tester.batchResults.length }} 对)
            </el-divider>
            <el-table :data="tester.batchResults" border size="small" max-height="300">
              <el-table-column label="来源门店" prop="fromStoreName" width="140" />
              <el-table-column label="目标门店" prop="toStoreName" width="140" />
              <el-table-column label="匹配规则" prop="ruleName" width="160" />
              <el-table-column label="结果" width="90">
                <template #default="{ row }">
                  <el-tag :type="row.match ? 'success' : 'danger'" size="small">
                    {{ row.match ? '可跨店' : '禁止' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="套餐">
                <template #default="{ row }">
                  <span v-if="row.packageCrossEnabled === 1" style="color: #22c55e">✅</span>
                  <span v-else style="color: #ef4444">❌</span>
                </template>
              </el-table-column>
              <el-table-column label="积分">
                <template #default="{ row }">
                  <span v-if="row.pointsCrossEnabled === 1" style="color: #22c55e">✅</span>
                  <span v-else style="color: #ef4444">❌</span>
                </template>
              </el-table-column>
              <el-table-column label="储值">
                <template #default="{ row }">
                  <span v-if="row.balanceCrossEnabled === 1" style="color: #22c55e">✅</span>
                  <span v-else style="color: #ef4444">❌</span>
                </template>
              </el-table-column>
              <el-table-column label="原因" prop="reason" show-overflow-tooltip />
            </el-table>
          </div>

          <div v-if="tester.noMatch && !tester.batchMode" class="test-result">
            <el-divider content-position="left">测试结果</el-divider>
            <el-result
              icon="warning"
              title="无匹配规则"
              sub-title="该门店组合在当前规则配置下无法跨店消费。"
            />
          </div>
        </div>

        <!-- 右侧：最近测试历史 -->
        <div class="tester-history" v-if="testHistory.length > 0">
          <div class="history-title">最近测试 ({{ testHistory.length }})</div>
          <div class="history-list">
            <div v-for="(h, i) in testHistory" :key="i" class="history-item" @click="replayTest(h)">
              <span class="history-from">{{ getStoreName(h.fromStoreId) }}</span>
              <span class="history-arrow">→</span>
              <span class="history-to">{{ getStoreName(h.toStoreId) }}</span>
              <el-tag :type="h.match ? 'success' : 'danger'" size="small" style="margin-left: 4px">
                {{ h.match ? '可' : '禁' }}
              </el-tag>
              <div class="history-time">{{ h.time }}</div>
            </div>
          </div>
          <el-button size="small" link @click="clearHistory" style="margin-top: 8px; color: #999">清除历史</el-button>
        </div>
      </div>
    </el-dialog>

    <!-- 回收站弹窗 -->
    <el-dialog v-model="recycleBin.visible" title="跨店规则回收站" width="900px">
      <div style="margin-bottom: 12px; color: #f56c6c; font-size: 13px;">
        已删除的规则可在 30 天内恢复或彻底删除。
      </div>
      <el-table :data="recycleBin.rows" v-loading="recycleBin.loading" border stripe>
        <template #empty><el-empty description="回收站为空" /></template>
        <el-table-column prop="ruleName" label="规则名称" min-width="180" />
        <el-table-column label="品牌" width="140">
          <template #default="{ row }">
            <span v-if="row.brandId">{{ brands.find((b: any) => b.id === row.brandId)?.brandName || `品牌#${row.brandId}` }}</span>
            <span v-else class="text-muted">租户通用</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }"><el-tag type="info" size="small">已删除</el-tag></template>
        </el-table-column>
        <el-table-column label="删除时间" width="150">
          <template #default="{ row }">
            <span class="text-muted">{{ row.updateTime ? row.updateTime.replace('T', ' ').slice(0, 16) : '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="success" @click="restoreRule(row)">恢复</el-button>
            <el-button size="small" type="danger" @click="permanentDelete(row)">彻底删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 批量复制到品牌弹窗 -->
    <el-dialog v-model="batchCopyDialog.visible" title="复制到品牌" width="400px">
      <el-form :label-width="80">
        <el-form-item label="目标品牌" required>
          <el-select v-model="batchCopyDialog.targetBrandId" placeholder="请选择目标品牌" style="width: 100%">
            <el-option v-for="b in brands" :key="b.id" :label="b.brandName" :value="b.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <el-alert type="warning" :closable="false">
        将把选中的 {{ selectedRows.length }} 条规则复制到目标品牌，新规则默认停用。
      </el-alert>
      <template #footer>
        <el-button @click="batchCopyDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="confirmBatchCopy" :loading="batchCopyDialog.loading">确认复制</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import http from '@/utils/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import { newIdempotencyKey } from '@/utils/idempotency'

const filter = reactive({
  pageNum: 1, pageSize: 20,
  keyword: '', brandId: undefined as number | undefined, enabled: undefined as number | undefined
})
const rows = ref<any[]>([])
const brands = ref<any[]>([])
const stores = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const togglingId = ref<number | null>(null)
const selectedRows = ref<any[]>([])
const batchLoading = ref(false)
const advancedExpanded = ref(false)
const showHelp = ref(false)
const conflictWarning = ref('')
const formRef = ref<any>(null)

// 统计看板
const statsVisible = ref(false)
const statsLoading = ref(false)
const stats = ref<any>({})

// 手续费汇总
const feeSummary = computed(() => {
  if (!rows.value.length) return '—'
  const totalFee = rows.value.reduce((s: number, r: any) => {
    if (r.crossFeeType === 'PERCENT') return s + (r.crossFeeRate || 0)
    if (r.crossFeeType === 'FIXED') return s + (r.crossFeeRate || 0)
    return s
  }, 0)
  return totalFee > 0 ? `¥${totalFee.toLocaleString()}` : '—'
})

async function loadStats() {
  statsVisible.value = !statsVisible.value
  if (!statsVisible.value) return
  statsLoading.value = true
  try {
    const r = await http.get('/crm/cross/rule/stats')
    stats.value = r.data || {}
  } catch {
    stats.value = {}
  } finally {
    statsLoading.value = false
  }
}

// 永久时间判断（2099-12-31 或 null/空 = 永久）
function isPermanent(endTime: string) {
  return !endTime || endTime.startsWith('2099') || endTime.startsWith('2999')
}

// 批量启用/停用
async function batchEnable(enable: boolean) {
  if (!selectedRows.value.length) return
  const ids = selectedRows.value.map((r: any) => r.id)
  batchLoading.value = true
  try {
    await http.put('/crm/cross/rule/batch-toggle', { ids, enabled: enable ? 1 : 0 })
    ElMessage.success(`已${enable ? '启用' : '停用'} ${ids.length} 条规则`)
    selectedRows.value = []
    loadData()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '批量操作失败')
  } finally {
    batchLoading.value = false
  }
}

// 回收站
const recycleBin = reactive({ visible: false, loading: false, rows: [] as any[] })

async function showRecycleBin() {
  recycleBin.visible = true
  recycleBin.loading = true
  try {
    const r = await http.get('/crm/cross/rule/trash', { params: { pageNum: 1, pageSize: 100 } })
    recycleBin.rows = r.data?.records || []
  } catch {
    recycleBin.rows = []
  } finally {
    recycleBin.loading = false
  }
}

async function restoreRule(row: any) {
  try {
    await http.put(`/crm/cross/rule/${row.id}/restore`)
    ElMessage.success('规则已恢复')
    showRecycleBin()
    loadData()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '恢复失败')
  }
}

async function permanentDelete(row: any) {
  await ElMessageBox.confirm(`彻底删除「${row.ruleName}」，此操作不可恢复！`, '危险操作', { type: 'error' })
  try {
    await http.delete(`/crm/cross/rule/${row.id}`)
    ElMessage.success('已彻底删除')
    showRecycleBin()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '删除失败')
  }
}

// 批量复制到品牌
const batchCopyDialog = reactive({ visible: false, targetBrandId: null as number | null, loading: false })

function batchCopyToBrand() {
  if (!selectedRows.value.length) return ElMessage.warning('请先选择规则')
  batchCopyDialog.targetBrandId = null
  batchCopyDialog.visible = true
}

async function confirmBatchCopy() {
  if (!batchCopyDialog.targetBrandId) return ElMessage.warning('请选择目标品牌')
  const ids = selectedRows.value.map((r: any) => r.id)
  batchCopyDialog.loading = true
  try {
    await http.post('/crm/cross/rule/batch-copy', { ids, targetBrandId: batchCopyDialog.targetBrandId })
    ElMessage.success(`已复制 ${ids.length} 条规则到目标品牌`)
    batchCopyDialog.visible = false
    selectedRows.value = []
    loadData()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '批量复制失败')
  } finally {
    batchCopyDialog.loading = false
  }
}

async function batchMoveToTrash() {
  if (!selectedRows.value.length) return ElMessage.warning('请先选择规则')
  await ElMessageBox.confirm(
    `确定将选中的 ${selectedRows.value.length} 条规则移入回收站？`,
    '批量删除',
    { type: 'warning' }
  )
  const ids = selectedRows.value.map((r: any) => r.id)
  try {
    await http.put('/crm/cross/rule/batch-trash', { ids })
    ElMessage.success(`已将 ${ids.length} 条规则移入回收站`)
    selectedRows.value = []
    loadData()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '批量删除失败')
  }
}

// 导出 Excel（前端生成 xlsx，无需后端接口）
async function exportExcel() {
  try {
    const r = await http.get('/crm/cross/rule/page', { params: { ...filter, pageNum: 1, pageSize: 9999 } })
    const data = r.data?.records || []
    if (!data.length) return ElMessage.warning('无数据可导出')
    const rows2: any[] = [
      ['规则名称', '品牌', '套餐跨店', '储值跨店', '积分共享', '优惠券共享',
        '手续费', '优先级', '生效时间', '状态', '创建时间']
    ]
    data.forEach((r: any) => {
      rows2.push([
        r.ruleName,
        r.brandId ? (brands.value.find((b: any) => b.id === r.brandId)?.brandName || `品牌#${r.brandId}`) : '租户通用',
        r.packageCrossEnabled === 1 ? modeLabel(r.packageCrossMode) : '禁止',
        r.balanceCrossEnabled === 1 ? modeLabel(r.balanceCrossMode) : '禁止',
        r.pointsCrossEnabled === 1 ? '是' : '否',
        r.couponCrossEnabled === 1 ? '是' : '否',
        r.crossFeeType === 'NONE' || !r.crossFeeType ? '无' :
          r.crossFeeType === 'PERCENT' ? `${r.crossFeeRate}%` : `固定¥${r.crossFeeRate}`,
        r.priority,
        r.startTime ? r.startTime.replace('T', ' ').slice(0, 16) : '立即',
        r.enabled === 1 ? '启用' : '停用',
        r.createTime ? r.createTime.replace('T', ' ').slice(0, 16) : '-'
      ])
    })
    const csvContent = rows2.map(row =>
      row.map(cell => `"${String(cell).replace(/"/g, '""')}"`).join(',')
    ).join('\r\n')
    const BOM = '\uFEFF'
    const blob = new Blob([BOM + csvContent], { type: 'text/csv;charset=utf-8;' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `跨店规则_${new Date().toISOString().slice(0,10)}.csv`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success(`已导出 ${data.length} 条规则`)
  } catch {
    ElMessage.error('导出失败')
  }
}

// 测试器历史（localStorage）
const TEST_HISTORY_KEY = 'cross_rule_test_history'
const MAX_HISTORY = 10

const testHistory = ref<any[]>([])

function loadHistory() {
  try {
    testHistory.value = JSON.parse(localStorage.getItem(TEST_HISTORY_KEY) || '[]')
  } catch {
    testHistory.value = []
  }
}

function saveHistory(entry: any) {
  const list = testHistory.value.filter(h =>
    !(h.fromStoreId === entry.fromStoreId && h.toStoreId === entry.toStoreId)
  )
  list.unshift(entry)
  if (list.length > MAX_HISTORY) list.pop()
  testHistory.value = list
  localStorage.setItem(TEST_HISTORY_KEY, JSON.stringify(list))
}

function clearHistory() {
  testHistory.value = []
  localStorage.removeItem(TEST_HISTORY_KEY)
  ElMessage.success('历史已清除')
}

function getStoreName(id: number) {
  const s = stores.value.find(x => x.id === id)
  return s ? s.storeName : `门店#${id}`
}

function replayTest(h: any) {
  tester.fromStoreId = h.fromStoreId
  tester.toStoreId = h.toStoreId
  tester.memberPhone = ''
  runTest()
}

// 规则测试器
const tester = reactive({
  visible: false,
  memberPhone: '',
  fromStoreId: undefined as number | undefined,
  toStoreId: undefined as number | undefined,
  loading: false,
  result: null as any,
  noMatch: false,
  batchMode: false,
  batchInput: '',
  batchLoading: false,
  batchResults: null as any[]
})

function openTester() {
  tester.result = null
  tester.noMatch = false
  tester.memberPhone = ''
  tester.fromStoreId = undefined
  tester.toStoreId = undefined
  tester.batchMode = false
  tester.batchInput = ''
  tester.batchResults = null
  tester.visible = true
  loadHistory()
}

function toggleBatchMode() {
  tester.batchMode = !tester.batchMode
  tester.result = null
  tester.noMatch = false
  tester.batchResults = null
}

async function runTest() {
  if (!tester.fromStoreId || !tester.toStoreId) {
    return ElMessage.warning('请选择来源门店和目标门店')
  }
  tester.loading = true
  tester.result = null
  tester.noMatch = false
  try {
    const r = await http.get('/crm/cross/rule/test', {
      params: {
        memberPhone: tester.memberPhone,
        fromStoreId: tester.fromStoreId,
        toStoreId: tester.toStoreId
      }
    })
    const data = r.data
    if (data && data.match) {
      tester.result = data
      tester.noMatch = false
      saveHistory({
        fromStoreId: tester.fromStoreId,
        toStoreId: tester.toStoreId,
        match: true,
        time: new Date().toLocaleTimeString()
      })
    } else {
      tester.noMatch = true
      tester.result = null
      saveHistory({
        fromStoreId: tester.fromStoreId,
        toStoreId: tester.toStoreId,
        match: false,
        time: new Date().toLocaleTimeString()
      })
    }
  } catch (e: any) {
    const msg = e?.response?.data?.message || ''
    if (msg.includes('无可用规则') || msg.includes('禁止')) {
      tester.noMatch = true
      saveHistory({
        fromStoreId: tester.fromStoreId,
        toStoreId: tester.toStoreId,
        match: false,
        time: new Date().toLocaleTimeString()
      })
    } else {
      ElMessage.error(msg || '测试失败')
    }
  } finally {
    tester.loading = false
  }
}

async function runBatchTest() {
  const lines = tester.batchInput.trim().split('\n').filter(l => l.trim())
  if (!lines.length) return ElMessage.warning('请输入至少一对门店')
  tester.batchLoading = true
  tester.batchResults = []
  const results: any[] = []
  for (const line of lines) {
    const [fromId, toId] = line.split(',').map(s => parseInt(s.trim()))
    if (isNaN(fromId) || isNaN(toId)) continue
    try {
      const r = await http.get('/crm/cross/rule/test', {
        params: { fromStoreId: fromId, toStoreId: toId }
      })
      const data = r.data
      results.push({
        fromStoreId: fromId,
        fromStoreName: getStoreName(fromId),
        toStoreId: toId,
        toStoreName: getStoreName(toId),
        match: data?.match ?? false,
        ruleName: data?.ruleName ?? '-',
        packageCrossEnabled: data?.packageCrossEnabled ?? 0,
        pointsCrossEnabled: data?.pointsCrossEnabled ?? 0,
        balanceCrossEnabled: data?.balanceCrossEnabled ?? 0,
        reason: data?.reason ?? '-'
      })
    } catch {
      results.push({
        fromStoreId: fromId, fromStoreName: getStoreName(fromId),
        toStoreId: toId, toStoreName: getStoreName(toId),
        match: false, ruleName: '-', reason: '请求失败', packageCrossEnabled: 0, pointsCrossEnabled: 0, balanceCrossEnabled: 0
      })
    }
  }
  tester.batchResults = results
  tester.batchLoading = false
  ElMessage.success(`完成 ${results.length} 对测试`)
}

async function loadStores() {
  try {
    const r = await http.get('/store/list')
    stores.value = r.data || []
  } catch {}
}

// 详情预览
const preview = reactive({
  visible: false,
  data: null as any,
  row: null as any,
  matchedStores: [] as any[]
})

async function openPreview(row: any) {
  preview.data = { ...row }
  preview.row = row
  preview.visible = true
  // 加载匹配门店预览
  preview.matchedStores = []
  try {
    const r = await http.get('/crm/cross/rule/preview-match', { params: { ruleId: row.id } })
    preview.matchedStores = r.data || []
  } catch {}
}

function openEditFromPreview() {
  preview.visible = false
  openEdit(preview.row)
}

async function toggleEnabled(row: any) {
  togglingId.value = row.id
  try {
    const r = await http.put(`/crm/cross/rule/${row.id}/toggle`)
    ElMessage.success(r.data.message || (row.enabled === 1 ? '已停用' : '已启用'))
    loadData()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '操作失败')
  } finally {
    togglingId.value = null
  }
}

async function clone(row: any) {
  await ElMessageBox.confirm(`确定复制规则「${row.ruleName}」？`, '复制规则')
  try {
    const r = await http.post(`/crm/cross/rule/${row.id}/clone`)
    ElMessage.success('已复制为新规则（默认停用）')
    loadData()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '复制失败')
  }
}

// 行操作下拉菜单
function handleRowCommand(cmd: string, row: any) {
  if (cmd === 'preview') openPreview(row)
  else if (cmd === 'clone') clone(row)
  else if (cmd === 'edit') openEdit(row)
  else if (cmd === 'trash') moveToTrash(row)
}

async function moveToTrash(row: any) {
  await ElMessageBox.confirm(`确定将规则「${row.ruleName}」移入回收站？`, '删除规则', { type: 'warning' })
  try {
    await http.put(`/crm/cross/rule/${row.id}/trash`)
    ElMessage.success('已移入回收站')
    loadData()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '删除失败')
  }
}

async function loadBrands() {
  try {
    const r = await http.get('/org/brand/page?pageNum=1&pageSize=100')
    brands.value = r.data?.records || []
  } catch {}
}

async function loadData() {
  loading.value = true
  try {
    // 筛选 "租户通用"：前端 -1 → 不发送 brandId 参数，让后端走 "全部"
    const params: any = { ...filter }
    if (params.brandId === -1) delete params.brandId
    const r = await http.get('/crm/cross/rule/page', { params })
    rows.value = r.data?.records || []
    total.value = r.data?.total || 0
  } finally {
    loading.value = false
  }
}

function modeLabel(m: string) {
  return ({ BRAND: '品牌内', CHAIN: '连锁内', ANY: '任意', STORE: '本门店', DISABLED: '禁止' } as any)[m] || m || '禁止'
}
function modeTag(m: string) {
  return ({ BRAND: 'primary', CHAIN: 'success', ANY: 'warning', STORE: 'info', DISABLED: 'danger' } as any)[m] || 'info'
}

function resetFilter() {
  filter.keyword = ''; filter.brandId = undefined; filter.enabled = undefined; filter.pageNum = 1
  loadData()
}

// 表单校验
const formRules = {
  ruleName: [
    { required: true, message: '规则名称必填', trigger: 'blur' },
    { min: 2, max: 50, message: '长度 2-50', trigger: 'blur' }
  ],
  priority: [
    { required: true, message: '优先级必填', trigger: 'change' },
    { type: 'number', min: 0, max: 9999, message: '优先级范围 0-9999', trigger: 'change' }
  ],
  crossFeeRate: [
    {
      validator: (_: any, val: number, callback: any) => {
        if (form.crossFeeType !== 'NONE' && (val === null || val === undefined)) {
          callback(new Error('选择收取手续费时，手续费值必填'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ]
}

function checkFeeRateRequired() {
  if (form.crossFeeType !== 'NONE' && !form.crossFeeRate) {
    form.crossFeeRate = 0
  }
}

function onPackageCrossEnabledChange(v: any) {
  if (v === 0) {
    form.packageCrossMode = 'DISABLED'
  } else if (form.packageCrossMode === 'DISABLED') {
    form.packageCrossMode = 'BRAND'
  }
  checkConflict()
}

function onBalanceCrossEnabledChange(v: any) {
  if (v === 0) {
    form.balanceCrossMode = 'DISABLED'
  } else if (form.balanceCrossMode === 'DISABLED') {
    form.balanceCrossMode = 'BRAND'
  }
}

// 规则冲突检测
function checkConflict() {
  if (form.packageCrossMode !== 'ANY') { conflictWarning.value = ''; return }
  const sameBrand = rows.value.filter(r =>
    r.packageCrossMode === 'ANY' &&
    r.id !== dialog.id &&
    (form.brandId ? r.brandId === form.brandId : !r.brandId)
  )
  if (sameBrand.length > 0) {
    conflictWarning.value = `⚠️ 检测到 ${sameBrand.length} 条「任意门店」规则与当前配置冲突，优先级可能被覆盖：${sameBrand.map(r => r.ruleName).join('、')}`
  } else {
    conflictWarning.value = ''
  }
}

const dialog = reactive({ visible: false, id: undefined as number | undefined, saving: false })
const form = reactive<any>({
  ruleName: '', brandId: null, remark: '',
  startTime: null, endTime: null,
  memberCrossEnabled: 1,
  packageCrossEnabled: 1, packageCrossMode: 'BRAND',
  pointsCrossEnabled: 1,
  balanceCrossEnabled: 1, balanceCrossMode: 'BRAND',
  discountCrossEnabled: 1,
  couponCrossEnabled: 1,
  crossFeeType: 'NONE', crossFeeRate: 0, crossMaxAmount: 0,
  enabled: 1, priority: 0,
  effectiveType: 'IMMEDIATE'
})

function openCreate() {
  dialog.id = undefined
  conflictWarning.value = ''
  advancedExpanded.value = false
  dialog.visible = true
}

function openEdit(row: any) {
  dialog.id = row.id
  Object.assign(form, {
    ruleName: row.ruleName,
    brandId: row.brandId,
    remark: row.remark || '',
    startTime: row.startTime || null,
    endTime: row.endTime || null,
    memberCrossEnabled: row.memberCrossEnabled ?? 1,
    packageCrossEnabled: row.packageCrossEnabled ?? 1,
    packageCrossMode: row.packageCrossMode || 'BRAND',
    pointsCrossEnabled: row.pointsCrossEnabled ?? 1,
    balanceCrossEnabled: row.balanceCrossEnabled ?? 1,
    balanceCrossMode: row.balanceCrossMode || 'BRAND',
    discountCrossEnabled: row.discountCrossEnabled ?? 1,
    couponCrossEnabled: row.couponCrossEnabled ?? 1,
    crossFeeType: row.crossFeeType || 'NONE',
    crossFeeRate: row.crossFeeRate ?? 0,
    crossMaxAmount: row.crossMaxAmount ?? 0,
    enabled: row.enabled ?? 1,
    priority: row.priority ?? 0,
    effectiveType: 'IMMEDIATE'
  })
  conflictWarning.value = ''
  advancedExpanded.value = false
  dialog.visible = true
  setTimeout(() => checkConflict(), 100)
}

function resetDialog() {
  Object.assign(form, {
    ruleName: '', brandId: null, remark: '',
    startTime: null, endTime: null,
    memberCrossEnabled: 1,
    packageCrossEnabled: 1, packageCrossMode: 'BRAND',
    pointsCrossEnabled: 1,
    balanceCrossEnabled: 1, balanceCrossMode: 'BRAND',
    discountCrossEnabled: 1,
    couponCrossEnabled: 1,
    crossFeeType: 'NONE', crossFeeRate: 0, crossMaxAmount: 0,
    enabled: 1, priority: 0,
    effectiveType: 'IMMEDIATE'
  })
  conflictWarning.value = ''
  formRef.value?.clearValidate()
}

async function save() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return ElMessage.warning('请检查表单填写')
  }

  if (!form.ruleName) return ElMessage.warning('规则名称必填')

  // 前端校验：选择收取手续费时，金额必须 > 0
  if (form.crossFeeType !== 'NONE' && !form.crossFeeRate) {
    return ElMessage.warning('选择收取手续费时，手续费值不能为0，请填写具体数值')
  }

  dialog.saving = true
  try {
    const headers = { 'Idempotency-Key': newIdempotencyKey() }
    const payload = { ...form }
    if (form.effectiveType === 'NEXT_DAY') {
      const next = new Date(); next.setDate(next.getDate() + 1); next.setHours(0, 0, 0, 0)
      payload.startTime = next.toISOString().slice(0, 19).replace('T', ' ')
      payload.enabled = 0 // 次日生效时先停用，次日定时任务启用
    }
    if (dialog.id) {
      await http.put(`/crm/cross/rule/${dialog.id}`, payload, { headers })
      ElMessage.success('已更新')
    } else {
      await http.post('/crm/cross/rule', payload, { headers })
      ElMessage.success('已创建')
    }
    dialog.visible = false
    loadData()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    dialog.saving = false
  }
}

async function remove(row: any) {
  await ElMessageBox.confirm(`删除规则「${row.ruleName}」？`, '提示', { type: 'warning' })
  await http.delete(`/crm/cross/rule/${row.id}`)
  ElMessage.success('已删除')
  loadData()
}

onMounted(async () => {
  await Promise.all([loadBrands(), loadStores(), loadData()])
})
</script>

<style scoped>
.cross-rule-page { padding: 16px; }
.search-bar { margin-bottom: 12px; }
.search-row { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 12px; }
.search-fields { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.search-actions { display: flex; flex-wrap: wrap; gap: 8px; align-items: center; }
.search-bar .el-input, .search-bar .el-select { margin-right: 0; margin-bottom: 0; }
.table-card { overflow-x: auto; }
.table-card :deep(.el-table) { min-width: 1200px; }
.pagination-card { margin-top: 0; }
.pagination-bar { display: flex; justify-content: flex-end; flex-wrap: wrap; }
.form-tip { font-size: 12px; color: #94a3b8; margin-top: 4px; }
.text-muted { color: #aaa; font-size: 13px; }
.text-permanent { color: #10b981; font-weight: 600; }

/* 空状态引导 */
.empty-state {
  text-align: center;
  padding: 40px 20px;
}
.empty-icon {
  font-size: 56px;
  margin-bottom: 16px;
  opacity: 0.85;
}
.empty-title {
  font-size: 16px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 8px;
}
.empty-desc {
  font-size: 14px;
  color: #9ca3af;
  margin-bottom: 20px;
}

/* 统计看板 */
.stats-card { margin-bottom: 12px; }
.stats-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.stats-title {
  font-weight: 600;
  font-size: 15px;
  color: #1f2937;
}
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}
.stat-item {
  text-align: center;
  padding: 16px 12px;
  border-right: 1px solid #f0f0f0;
  position: relative;
}
.stat-item:last-child { border-right: none; }
.stat-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  margin: 0 auto 12px;
}
.stat-label { font-size: 13px; color: #64748b; margin-bottom: 8px; font-weight: 500; }
.stat-value { font-size: 24px; font-weight: 700; color: #334155; margin-bottom: 4px; }
.stat-value.accent { color: #3b82f6; }
.stat-value.warning { color: #f59e0b; }
.stat-value.primary { color: #10b981; font-size: 18px; }
.stat-detail { font-size: 12px; color: #94a3b8; }
.stat-trend { font-size: 12px; margin-top: 4px; color: #94a3b8; }
.stat-trend .up { color: #22c55e; margin-right: 4px; }
.stat-trend .down { color: #ef4444; margin-right: 4px; }
.trend-label { color: #94a3b8; margin-left: 2px; }

/* 快速数据摘要 */
.quick-summary {
  display: flex;
  gap: 24px;
  padding: 10px 14px;
  background: linear-gradient(135deg, #f8faff, #f0f9ff);
  border-radius: 8px;
  margin-bottom: 12px;
  font-size: 13px;
  color: #64748b;
  border: 1px solid #e0e7ff;
}
.quick-summary strong { color: #3b82f6; }
.summary-item { display: flex; align-items: center; gap: 4px; }

/* 批量操作栏 */
.batch-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px;
  background: #eff6ff;
  border-radius: 6px;
  margin-bottom: 10px;
  font-size: 14px;
  color: #3b82f6;
}

/* 表格行悬停 */
:deep(.cross-rule-row:hover) { background: #f8faff !important; }

/* 测试器布局 */
.tester-layout { display: flex; gap: 16px; }
.tester-main { flex: 1; }
.tester-history {
  width: 200px;
  border-left: 1px solid #f0f0f0;
  padding-left: 16px;
  flex-shrink: 0;
}
.history-title { font-size: 13px; color: #64748b; font-weight: 600; margin-bottom: 8px; }
.history-list { max-height: 360px; overflow-y: auto; }
.history-item {
  padding: 6px 4px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 12px;
  transition: background 0.15s;
}
.history-item:hover { background: #f1f5f9; }
.history-from, .history-to { color: #334155; }
.history-arrow { color: #94a3b8; margin: 0 4px; }
.history-time { color: #94a3b8; font-size: 11px; margin-top: 2px; }

/* 测试器结果 */
.test-result { margin-top: 16px; }

/* 批量测试区 */
.batch-test-area { margin-top: 12px; }

/* 高级设置折叠提示 */
.advanced-hint {
  padding: 8px 12px;
  color: #3b82f6;
  font-size: 13px;
  cursor: pointer;
  border: 1px dashed #bfdbfe;
  border-radius: 4px;
  text-align: center;
  margin-top: 4px;
  transition: background 0.15s;
}
.advanced-hint:hover { background: #eff6ff; }

/* 匹配门店预览 */
.match-stores-preview { display: flex; flex-wrap: wrap; gap: 4px; }
</style>
