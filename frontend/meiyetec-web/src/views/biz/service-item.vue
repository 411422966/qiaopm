<template>
  <div class="service-item-page">
    <!-- 顶部统计卡片 -->
    <div class="stat-cards">
      <div class="stat-card" @click="resetFilter">
        <div class="stat-label">项目总数</div>
        <div class="stat-value">{{ total }}</div>
      </div>
      <div class="stat-card" @click="quickFilterStatus(1)">
        <div class="stat-label">启用中</div>
        <div class="stat-value success">{{ stats.enabled }}</div>
      </div>
      <div class="stat-card" @click="quickFilterStatus(0)">
        <div class="stat-label">已停用</div>
        <div class="stat-value info">{{ stats.disabled }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-label">平均标准价</div>
        <div class="stat-value">¥{{ formatYuan(stats.avgPrice) }}</div>
      </div>
    </div>

    <!-- 快捷筛选 chips -->
    <div class="quick-chips">
      <span class="chips-label">快速筛选：</span>
      <el-check-tag
        v-for="c in categories.slice(0, 6)"
        :key="c.id"
        :checked="filter.categoryId === c.id"
        @change="(v: boolean) => toggleCategoryChip(c.id, v)">
        {{ c.categoryName }}
      </el-check-tag>
      <el-check-tag
        :checked="statusFilter === 1"
        @change="(v: boolean) => statusFilter = v ? 1 : null"
        class="chip-tag">
        ● 启用
      </el-check-tag>
      <el-check-tag
        :checked="statusFilter === 0"
        @change="(v: boolean) => statusFilter = v ? 0 : null"
        class="chip-tag">
        ● 停用
      </el-check-tag>
    </div>

    <el-card class="search-bar">
      <el-input
        ref="keywordInputRef"
        v-model="filter.keyword"
        placeholder="编码 / 名称 （Ctrl+F 聚焦）"
        style="width: 220px" clearable
        @input="onKeywordInput" @keyup.enter="loadData" />
      <el-select v-model="filter.categoryId" placeholder="分类" clearable style="width: 150px" @change="loadData">
        <el-option v-for="c in categories" :key="c.id" :label="c.categoryName" :value="c.id" />
      </el-select>
      <el-select v-model="filter.status" placeholder="状态" clearable style="width: 110px" @change="loadData">
        <el-option label="启用" :value="1" />
        <el-option label="停用" :value="0" />
      </el-select>
      <el-button type="primary" @click="loadData">查询</el-button>
      <el-button @click="resetFilter">重置</el-button>
      <el-button :icon="Refresh" @click="loadData">刷新</el-button>
      <el-button type="primary" @click="openCreate()">+ 新增项目</el-button>
      <el-button type="success" @click="exportExcel" :loading="exporting">导出当前筛选</el-button>

      <el-upload
        action=""
        :auto-upload="false"
        :show-file-list="false"
        accept=".xlsx"
        :on-change="onImportFileChange"
        style="display: inline-block">
        <el-button type="warning">📥 批量导入</el-button>
      </el-upload>
      <el-button link @click="downloadTemplate">下载导入模板</el-button>

      <span class="toolbar-spacer" />

      <!-- 密度切换 -->
      <el-tooltip content="表格密度" placement="top">
        <el-button-group>
          <el-button :type="tableSize === 'small' ? 'primary' : 'default'" size="small" @click="tableSize = 'small'">
            <el-icon><Minus /></el-icon>
          </el-button>
          <el-button :type="tableSize === 'default' ? 'primary' : 'default'" size="small" @click="tableSize = 'default'">
            <el-icon><Menu /></el-icon>
          </el-button>
          <el-button :type="tableSize === 'large' ? 'primary' : 'default'" size="small" @click="tableSize = 'large'">
            <el-icon><Plus /></el-icon>
          </el-button>
        </el-button-group>
      </el-tooltip>

      <!-- 列设置 -->
      <el-popover :width="260" trigger="click" placement="bottom-end">
        <template #reference>
          <el-button :icon="Setting">列设置</el-button>
        </template>
        <div class="column-settings">
          <div class="column-settings-title">
            <span>显示列</span>
            <div>
              <el-button link size="small" @click="setAllCols(true)">全选</el-button>
              <el-button link size="small" @click="setAllCols(false)">反选</el-button>
              <el-button link size="small" @click="resetColumnSettings">重置</el-button>
            </div>
          </div>
          <el-checkbox
            v-for="col in columnDefs"
            :key="col.prop"
            v-model="col.visible"
            @change="persistColumnSettings">
            {{ col.label }}
          </el-checkbox>
        </div>
      </el-popover>

      <!-- 保存视图 -->
      <el-dropdown trigger="click" @command="handleViewCmd">
        <el-button :icon="Collection">视图</el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="save">💾 保存当前为新视图</el-dropdown-item>
            <el-dropdown-item v-if="savedViews.length === 0" disabled>（暂无保存的视图）</el-dropdown-item>
            <el-dropdown-item
              v-for="v in savedViews"
              :key="v.id"
              :command="`apply:${v.id}`">
              📋 {{ v.name }}
              <span style="color:#909399; font-size:11px; margin-left:8px">
                [{{ v.size === 'small' ? '紧凑' : v.size === 'large' ? '舒适' : '默认' }}]
              </span>
            </el-dropdown-item>
            <el-dropdown-item divided v-if="savedViews.length > 0" command="manage">管理视图…</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </el-card>

    <el-card class="table-card">
      <!-- 批量操作工具条 -->
      <transition name="batch-bar">
        <div v-if="selectedRows.length > 0" class="batch-bar">
          <span class="batch-info">已选 <b>{{ selectedRows.length }}</b> 项</span>
          <el-button size="small" type="success" @click="batchUpdateStatus(1)">批量启用</el-button>
          <el-button size="small" @click="batchUpdateStatus(0)">批量停用</el-button>
          <el-dropdown @command="batchChangeCategory" trigger="click">
            <el-button size="small" type="primary" plain>
              批量改分类
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item
                  v-for="c in categories"
                  :key="c.id"
                  :command="c.id">
                  {{ c.categoryName }}
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <el-button size="small" type="danger" plain @click="batchRemove">批量删除</el-button>
          <el-button size="small" link @click="selectedRows = []">取消选择</el-button>
        </div>
      </transition>

      <el-table
        :data="filteredRows"
        v-loading="loading"
        element-loading-text="加载中…"
        element-loading-background="rgba(255,255,255,0.7)"
        :size="tableSize"
        :height="tableHeight"
        border
        stripe
        row-key="id"
        @selection-change="(rows: any[]) => selectedRows = rows"
        :expand-row-keys="expandedRowKeys"
        @expand-change="onExpandChange">
        <el-table-column type="selection" width="48" :fixed="true" />
        <el-table-column type="expand" width="40">
          <template #default="{ row }">
            <div class="row-expand">
              <div class="row-expand-grid">
                <div class="row-expand-section">
                  <div class="row-expand-label">描述</div>
                  <div class="row-expand-value">{{ row.description || '—' }}</div>
                </div>
                <div class="row-expand-section">
                  <div class="row-expand-label">SOP 操作流程</div>
                  <div class="row-expand-value sop">{{ row.sop || '— 未填写 SOP' }}</div>
                </div>
                <div class="row-expand-section">
                  <div class="row-expand-label">成本组成</div>
                  <div class="row-expand-value">
                    <el-tag size="small" type="info">自动根据耗材 BOM 计算</el-tag>
                    <span style="margin-left: 8px">¥{{ formatYuan(row.costPrice) }}</span>
                  </div>
                </div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column v-if="isColVisible('serviceCode')" prop="serviceCode" label="项目编码" width="130" />
        <el-table-column v-if="isColVisible('image')" label="图片" width="64" align="center">
          <template #default="{ row }">
            <el-image
              v-if="row.imageUrl"
              :src="row.imageUrl"
              fit="cover"
              class="row-thumb"
              :preview-src-list="[row.imageUrl]"
              :preview-teleported="true"
              hide-on-click-modal />
            <span v-else class="no-thumb">—</span>
          </template>
        </el-table-column>
        <el-table-column v-if="isColVisible('serviceName')" prop="serviceName" label="项目名称" width="160" />
        <el-table-column v-if="isColVisible('categoryName')" prop="categoryName" label="分类" width="100" />
        <el-table-column v-if="isColVisible('unitPrice')" label="标准价" width="120" sortable :sort-by="(r: any) => Number(r.unitPrice || 0)">
          <template #header>
            <span>标准价</span>
            <el-popover trigger="click" :width="220" placement="bottom-start" @show="onPriceFilterShow">
              <template #reference>
                <el-icon class="filter-icon" :class="{ active: priceFilterActive }"><Filter /></el-icon>
              </template>
              <div class="price-filter">
                <el-radio-group v-model="priceFilterOp" size="small">
                  <el-radio-button value="all">全部</el-radio-button>
                  <el-radio-button value=">=">≥</el-radio-button>
                  <el-radio-button value="<=">≤</el-radio-button>
                </el-radio-group>
                <el-input-number v-model="priceFilterValue" :min="0" :step="50" size="small" controls-position="right"
                  style="width: 120px; margin-top: 8px" placeholder="价格" />
                <div style="margin-top: 8px; text-align: right">
                  <el-button size="small" @click="resetPriceFilter">清空</el-button>
                  <el-button size="small" type="primary" @click="applyPriceFilter">应用</el-button>
                </div>
              </div>
            </el-popover>
          </template>
          <template #default="{ row }">
            <template v-if="editingRowId === row.id && editingField === 'unitPrice'">
              <el-input-number v-model="row.unitPrice" :precision="2" :min="0" :step="1" size="small"
                style="width: 100px" @blur="commitInlineEdit(row, 'unitPrice')"
                @keyup.enter="commitInlineEdit(row, 'unitPrice')" />
            </template>
            <span v-else class="inline-edit" :class="{ saving: row.__savingPrice }" @dblclick="startInlineEdit(row, 'unitPrice')"
              title="双击修改">
              {{ formatYuan(row.unitPrice) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column v-if="isColVisible('memberPrice')" label="会员价" width="110" sortable :sort-by="(r: any) => Number(r.memberPrice || 0)">
          <template #default="{ row }">
            <template v-if="editingRowId === row.id && editingField === 'memberPrice'">
              <el-input-number v-model="row.memberPrice" :precision="2" :min="0" :step="1" size="small"
                style="width: 95px" @blur="commitInlineEdit(row, 'memberPrice')"
                @keyup.enter="commitInlineEdit(row, 'memberPrice')" />
            </template>
            <span v-else class="inline-edit" :class="{ saving: row.__savingMember }" @dblclick="startInlineEdit(row, 'memberPrice')"
              title="双击修改">
              {{ formatYuan(row.memberPrice) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column v-if="isColVisible('costPrice')" label="成本价" width="100" sortable :sort-by="(r: any) => Number(r.costPrice || 0)">
          <template #header>
            <span>成本价</span>
            <el-tooltip content="由耗材 BOM 自动计算（仅参考）" placement="top">
              <el-icon class="info-icon"><InfoFilled /></el-icon>
            </el-tooltip>
          </template>
          <template #default="{ row }">
            <span class="readonly-price">¥{{ formatYuan(row.costPrice) }}</span>
          </template>
        </el-table-column>
        <el-table-column v-if="isColVisible('duration')" prop="duration" label="时长(分)" width="80" sortable />
        <el-table-column v-if="isColVisible('unit')" prop="unit" label="单位" width="70" />
        <el-table-column v-if="isColVisible('priceHistory')" label="历史价" width="80">
          <template #default="{ row }">
            <el-button v-if="row.id" size="small" link @click="openPriceHistory(row)">查看</el-button>
          </template>
        </el-table-column>
        <el-table-column v-if="isColVisible('sortOrder')" prop="sortOrder" label="排序" width="70" sortable />
        <el-table-column v-if="isColVisible('status')" label="状态" width="90">
          <template #header>
            <el-dropdown trigger="click" @command="(v: any) => { statusFilter = v; loadData() }">
              <span class="col-header">
                状态
                <el-icon :class="{ active: statusFilter !== null }"><Filter /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item :command="null">全部</el-dropdown-item>
                  <el-dropdown-item :command="1">启用</el-dropdown-item>
                  <el-dropdown-item :command="0">停用</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" link @click="openEdit(row)">编辑</el-button>
            <el-button size="small" link type="primary" @click="cloneItem(row)">复制</el-button>
            <el-dropdown trigger="click" size="small">
              <el-button size="small" link>更多 <el-icon><ArrowDown /></el-icon></el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="openBom(row)">耗材绑定</el-dropdown-item>
                  <el-dropdown-item @click="moveSortOrder(row, -1)" :disabled="isFirstRow(row)">上移排序</el-dropdown-item>
                  <el-dropdown-item @click="moveSortOrder(row, 1)" :disabled="isLastRow(row)">下移排序</el-dropdown-item>
                  <el-dropdown-item @click="openPriceHistory(row)">价格历史</el-dropdown-item>
                  <el-dropdown-item divided @click="remove(row)">
                    <span style="color: #f56c6c">删除</span>
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
        <template #empty>
          <div v-if="loading" class="loading-placeholder">
            <div class="loading-line"></div>
            <div class="loading-line short"></div>
            <div class="loading-line"></div>
            <div class="loading-line short"></div>
            <div style="color: #909399; font-size: 13px; margin-top: 12px">
              正在加载服务项目…
            </div>
          </div>
          <div v-else class="empty-state">
            <el-icon class="empty-icon"><DocumentRemove /></el-icon>
            <div class="empty-title">暂无服务项目</div>
            <div class="empty-desc">{{ hasFilter ? '当前筛选条件下无数据，试试调整筛选条件' : '可以点击右上角"+ 新增项目"创建' }}</div>
            <div style="display:flex; gap:8px">
              <el-button v-if="hasFilter" type="warning" plain size="small" @click="resetFilter">重置筛选</el-button>
              <el-button type="primary" plain size="small" @click="openCreate()">+ 新增项目</el-button>
            </div>
          </div>
        </template>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="filter.pageNum"
          v-model:page-size="filter.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadData"
          @size-change="loadData"
        />
      </div>
    </el-card>

    <!-- 编辑弹窗 -->
    <el-dialog v-model="dialog.visible" :title="dialog.id ? '编辑项目' : '新增项目'" width="640px" @closed="resetDialog">
      <el-form :model="form" label-width="100px">
        <el-form-item label="项目编码" required>
          <el-input v-model="form.serviceCode" :disabled="!!dialog.id" />
        </el-form-item>
        <el-form-item label="项目名称" required>
          <el-input v-model="form.serviceName" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.categoryId" placeholder="选择分类" clearable style="width: 100%">
            <el-option v-for="c in categories" :key="c.id" :label="c.categoryName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标准价" required>
          <el-input-number v-model="form.unitPrice" :precision="2" :min="0" :step="0.01" style="width: 100%" />
        </el-form-item>
        <el-form-item label="会员价">
          <el-input-number v-model="form.memberPrice" :precision="2" :min="0" :step="0.01" style="width: 100%" />
        </el-form-item>
        <el-form-item label="时长(分钟)">
          <el-input-number v-model="form.duration" :min="0" :step="5" style="width: 100%" />
        </el-form-item>
        <el-form-item label="单位">
          <el-input v-model="form.unit" placeholder="次/课时/份" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sortOrder" :min="0" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="SOP 操作流程">
          <el-input v-model="form.sop" type="textarea" :rows="3" placeholder="①... → ②... → ③..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="save" :loading="dialog.saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 价格历史弹窗 -->
    <el-dialog v-model="priceHistory.visible" :title="`价格变更历史：${priceHistory.serviceName || ''}`" width="640px">
      <el-table :data="priceHistory.records" v-loading="priceHistory.loading" border stripe>
        <el-table-column prop="field" label="字段" width="120" />
        <el-table-column label="原值" width="140">
          <template #default="{ row }">¥{{ formatMoney(row.oldValue) }}</template>
        </el-table-column>
        <el-table-column label="新值" width="140">
          <template #default="{ row }">¥{{ formatMoney(row.newValue) }}</template>
        </el-table-column>
        <el-table-column prop="changedBy" label="操作人" width="120" />
        <el-table-column prop="changeTime" label="变更时间" min-width="160" />
        <template #empty>
          <div style="padding: 24px; text-align: center; color: #909399">
            <el-icon style="font-size: 32px"><DocumentRemove /></el-icon>
            <div style="margin-top: 8px">暂无价格变更记录</div>
          </div>
        </template>
      </el-table>
      <div v-if="priceHistory.records.length === 0 && !priceHistory.loading" class="history-hint">
        <el-alert type="info" :closable="false" show-icon>
          价格历史功能依赖后端 <code>biz_service_price_log</code> 表；
          如未启用，请忽略此弹窗。
        </el-alert>
      </div>
    </el-dialog>

    <!-- Excel 导入确认 -->
    <el-dialog v-model="importDialog.visible" title="确认导入" width="480px">
      <div class="import-preview">
        <el-icon class="import-file-icon"><Files /></el-icon>
        <div class="import-file-name">{{ importDialog.file?.name }}</div>
        <div class="import-file-size">{{ ((importDialog.file?.size || 0) / 1024).toFixed(1) }} KB</div>
      </div>
      <el-alert type="warning" :closable="false" show-icon style="margin-top: 12px">
        导入会创建新项目（按项目编码去重）；若编码已存在将跳过该行并在结果中显示。
      </el-alert>
      <template #footer>
        <el-button @click="importDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="importDialog.uploading" @click="confirmImport">确认导入</el-button>
      </template>
    </el-dialog>

    <!-- Excel 导入结果 -->
    <el-dialog v-model="importResult.visible" title="导入完成" width="520px">
      <div class="import-summary">
        <div class="import-stat">
          <div class="import-stat-num">{{ importResult.total }}</div>
          <div class="import-stat-label">总行数</div>
        </div>
        <div class="import-stat success">
          <div class="import-stat-num">{{ importResult.success }}</div>
          <div class="import-stat-label">成功</div>
        </div>
        <div class="import-stat failed">
          <div class="import-stat-num">{{ importResult.failed }}</div>
          <div class="import-stat-label">失败</div>
        </div>
      </div>
      <el-collapse v-if="importResult.errors.length > 0" style="margin-top: 12px">
        <el-collapse-item :title="`错误明细（${importResult.errors.length} 条，最多展示 20 条）`" name="err">
          <ul class="import-error-list">
            <li v-for="(msg, i) in importResult.errors" :key="i">{{ msg }}</li>
          </ul>
        </el-collapse-item>
      </el-collapse>
      <template #footer>
        <el-button type="primary" @click="importResult.visible = false">完成</el-button>
      </template>
    </el-dialog>

    <!-- BOM 弹窗 -->
    <el-dialog v-model="bomDialog.visible" :title="`耗材绑定：${bomDialog.serviceName || ''}`" width="720px" @closed="resetBom">
      <el-button size="small" type="primary" @click="openAddMaterial">+ 添加耗材</el-button>
      <el-table :data="bomDialog.rows" v-loading="bomDialog.loading" border style="margin-top: 12px">
        <el-table-column prop="productCode" label="商品编码" width="140" />
        <el-table-column prop="productName" label="商品名" width="200" />
        <el-table-column prop="quantity" label="用量" width="80" />
        <el-table-column prop="unit" label="单位" width="80" />
        <el-table-column prop="isRequired" label="必用" width="80">
          <template #default="{ row }">
            <el-tag :type="row.isRequired === 1 ? 'danger' : 'info'" size="small">
              {{ row.isRequired === 1 ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button link size="small" type="danger" @click="removeMaterial(row)">解绑</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="bomDialog.visible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 添加耗材弹窗 -->
    <el-dialog v-model="addMaterialDialog.visible" title="添加耗材" width="500px" @closed="resetAddMaterial">
      <el-form :model="addMaterialForm" label-width="100px">
        <el-form-item label="商品" required>
          <el-select v-model="addMaterialForm.productId" placeholder="选择商品" filterable style="width: 100%">
            <el-option v-for="p in productList" :key="p.id" :label="`${p.productCode} - ${p.productName}`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="用量" required>
          <el-input-number v-model="addMaterialForm.quantity" :min="0.01" :step="0.1" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="单位">
          <el-input v-model="addMaterialForm.unit" placeholder="默认跟商品单位" />
        </el-form-item>
        <el-form-item label="是否必用">
          <el-radio-group v-model="addMaterialForm.isRequired">
            <el-radio :value="1">必用</el-radio>
            <el-radio :value="0">可选</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addMaterialDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="confirmAddMaterial">确认添加</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed, watch, onUnmounted } from 'vue'
import http from '@/utils/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Refresh, Filter, DocumentRemove, InfoFilled, ArrowDown,
  Setting, Minus, Menu, Plus, Collection, Files
} from '@element-plus/icons-vue'
import { newIdempotencyKey } from '@/utils/idempotency'
import { formatMoney } from '@/utils/format'

// 模板里用 formatYuan：¥ 千分位 + 两位小数；null/undefined 显示 '—'
const formatYuan = (v: any) => (v === null || v === undefined || v === '' ? '—' : formatMoney(v, true))

interface Category {
  id: number
  categoryCode: string
  categoryName: string
}

const filter = reactive({
  pageNum: 1,
  pageSize: 20,
  keyword: '',
  categoryId: undefined as number | undefined,
  status: undefined as number | undefined
})

// ===== 筛选条件持久化（keyword/categoryId/status/pageSize） =====
const FILTER_STORAGE_KEY = 'svc-item-filter'
function loadPersistedFilter() {
  try {
    const raw = localStorage.getItem(FILTER_STORAGE_KEY)
    if (!raw) return
    const saved = JSON.parse(raw) as Partial<typeof filter>
    if (saved.keyword !== undefined) filter.keyword = saved.keyword
    if (saved.categoryId !== undefined) filter.categoryId = saved.categoryId
    if (saved.status !== undefined) filter.status = saved.status
    if (saved.pageSize) filter.pageSize = saved.pageSize
  } catch {
    // ignore
  }
}
watch(
  () => ({ k: filter.keyword, c: filter.categoryId, s: filter.status, ps: filter.pageSize }),
  (v) => localStorage.setItem(FILTER_STORAGE_KEY, JSON.stringify(v)),
  { deep: false }
)

const rows = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const categories = ref<Category[]>([])
const selectedRows = ref<any[]>([])
const expandedRowKeys = ref<number[]>([])
const exporting = ref(false)
const keywordInputRef = ref<any>(null)

// ===== 表格密度（持久化到 localStorage） =====
const tableSize = ref<'small' | 'default' | 'large'>(
  (localStorage.getItem('svc-item-table-size') as any) || 'default'
)
watch(tableSize, (v) => localStorage.setItem('svc-item-table-size', v))

// ===== 列设置（持久化到 localStorage） =====
interface ColDef { prop: string; label: string; visible: boolean }
const DEFAULT_COLS: ColDef[] = [
  { prop: 'serviceCode', label: '项目编码', visible: true },
  { prop: 'image', label: '图片', visible: true },
  { prop: 'serviceName', label: '项目名称', visible: true },
  { prop: 'categoryName', label: '分类', visible: true },
  { prop: 'unitPrice', label: '标准价', visible: true },
  { prop: 'memberPrice', label: '会员价', visible: true },
  { prop: 'costPrice', label: '成本价', visible: true },
  { prop: 'duration', label: '时长', visible: true },
  { prop: 'unit', label: '单位', visible: true },
  { prop: 'priceHistory', label: '历史价', visible: false },
  { prop: 'sortOrder', label: '排序', visible: false },
  { prop: 'status', label: '状态', visible: true }
]
const COL_STORAGE_KEY = 'svc-item-col-settings'
const columnDefs = ref<ColDef[]>([])
function loadColumnSettings() {
  try {
    const raw = localStorage.getItem(COL_STORAGE_KEY)
    if (raw) {
      const saved = JSON.parse(raw) as Record<string, boolean>
      columnDefs.value = DEFAULT_COLS.map((c) => ({
        ...c,
        visible: saved[c.prop] ?? c.visible
      }))
    } else {
      columnDefs.value = DEFAULT_COLS.map((c) => ({ ...c }))
    }
  } catch {
    columnDefs.value = DEFAULT_COLS.map((c) => ({ ...c }))
  }
}
function persistColumnSettings() {
  const obj: Record<string, boolean> = {}
  columnDefs.value.forEach((c) => (obj[c.prop] = c.visible))
  localStorage.setItem(COL_STORAGE_KEY, JSON.stringify(obj))
}
function resetColumnSettings() {
  columnDefs.value = DEFAULT_COLS.map((c) => ({ ...c }))
  persistColumnSettings()
  ElMessage.success('已重置列设置')
}
function setAllCols(visible: boolean) {
  columnDefs.value.forEach((c) => (c.visible = visible))
  persistColumnSettings()
}
function isColVisible(prop: string) {
  return columnDefs.value.find((c) => c.prop === prop)?.visible ?? true
}

// ===== 保存视图（密度 + 列显隐 + 当前排序的组合） =====
interface SavedView {
  id: string
  name: string
  size: 'small' | 'default' | 'large'
  cols: Record<string, boolean>
}
const VIEWS_KEY = 'svc-item-views'
const savedViews = ref<SavedView[]>([])
function loadSavedViews() {
  try {
    const raw = localStorage.getItem(VIEWS_KEY)
    savedViews.value = raw ? JSON.parse(raw) : []
  } catch {
    savedViews.value = []
  }
}
function persistSavedViews() {
  localStorage.setItem(VIEWS_KEY, JSON.stringify(savedViews.value))
}
async function saveCurrentAsView() {
  const { value: name } = await ElMessageBox.prompt('给当前视图起个名字（最多 20 字）', '保存视图', {
    inputValue: '',
    inputValidator: (v: string) => {
      const t = v?.trim() || ''
      if (!t) return '名称不能为空'
      if (t.length > 20) return '名称过长'
      if (savedViews.value.some((x) => x.name === t)) return '已存在同名视图'
      return true
    }
  }).catch(() => ({ value: '' }))
  if (!name) return
  const cols: Record<string, boolean> = {}
  columnDefs.value.forEach((c) => (cols[c.prop] = c.visible))
  savedViews.value.push({
    id: 'v' + Date.now(),
    name: name.trim(),
    size: tableSize.value,
    cols
  })
  persistSavedViews()
  ElMessage.success('已保存视图')
}
function applyView(v: SavedView) {
  tableSize.value = v.size
  columnDefs.value = DEFAULT_COLS.map((c) => ({
    ...c,
    visible: v.cols[c.prop] ?? c.visible
  }))
  persistColumnSettings()
  ElMessage.success(`已应用视图：${v.name}`)
}
async function deleteView(v: SavedView) {
  await ElMessageBox.confirm(`确认删除视图「${v.name}」？`, '提示', { type: 'warning' })
  savedViews.value = savedViews.value.filter((x) => x.id !== v.id)
  persistSavedViews()
  ElMessage.success('已删除')
}

async function handleViewCmd(cmd: string) {
  if (cmd === 'save') {
    await saveCurrentAsView()
    return
  }
  if (cmd === 'manage') {
    const { value: picked } = await ElMessageBox.prompt(
      `请输入要删除的视图名（可选：${savedViews.value.map((v) => v.name).join(' / ')}）`,
      '管理视图',
      { inputValue: '', confirmButtonText: '删除', cancelButtonText: '关闭' }
    ).catch(() => ({ value: '' }))
    if (!picked) return
    const found = savedViews.value.find((v) => v.name === picked.trim())
    if (!found) return ElMessage.warning('未找到该视图')
    await deleteView(found)
    return
  }
  if (cmd.startsWith('apply:')) {
    const id = cmd.slice(6)
    const v = savedViews.value.find((x) => x.id === id)
    if (v) applyView(v)
  }
}

// ===== 顶部统计卡 =====
const stats = reactive({ enabled: 0, disabled: 0, avgPrice: 0 })
function recalcStats() {
  let enabled = 0, disabled = 0, sum = 0, cnt = 0
  for (const r of rows.value) {
    if (r.status === 1) enabled++; else if (r.status === 0) disabled++
    if (r.unitPrice !== null && r.unitPrice !== undefined && r.unitPrice !== '') {
      sum += Number(r.unitPrice)
      cnt++
    }
  }
  stats.enabled = enabled
  stats.disabled = disabled
  stats.avgPrice = cnt > 0 ? sum / cnt : 0
}

function quickFilterStatus(v: number | null) {
  statusFilter.value = statusFilter.value === v ? null : v
}

function toggleCategoryChip(id: number, checked: boolean) {
  filter.categoryId = checked ? id : undefined
  filter.pageNum = 1
  loadData()
}

// ===== 自适应表格高度 =====
const tableHeight = computed(() => {
  // 视口高度 - 头部统计卡(80) - 快捷chips(40) - 搜索栏(60) - 分页器(60) - 卡片内边距
  const h = window.innerHeight - 80 - 40 - 60 - 60 - 32
  return Math.max(420, h)
})

// ===== 快捷键 Ctrl/Cmd+F 聚焦搜索 =====
function onGlobalKeydown(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'f') {
    e.preventDefault()
    keywordInputRef.value?.focus?.()
  }
}
const priceHistory = reactive({
  visible: false,
  loading: false,
  serviceId: null as number | null,
  serviceName: '',
  records: [] as Array<{ field: string; oldValue: number | null; newValue: number | null; changedBy: string; changeTime: string }>
})

const hasFilter = computed(() =>
  !!filter.keyword || filter.categoryId !== undefined || filter.status !== undefined
)

function onExpandChange(row: any, expanded: any[]) {
  // el-table 的 expand-change 第二个参数是当前展开行的数组
  expandedRowKeys.value = expanded.map((r: any) => r.id)
}

// ===== 行内编辑（标准价/会员价） =====
const editingRowId = ref<number | null>(null)
const editingField = ref<string | null>(null)
const editingSnapshot = ref<any>(null)

function startInlineEdit(row: any, field: string) {
  editingRowId.value = row.id
  editingField.value = field
  editingSnapshot.value = { [field]: row[field] }
}

async function commitInlineEdit(row: any, field: string) {
  const id = editingRowId.value
  const f = editingField.value
  editingRowId.value = null
  editingField.value = null
  if (id !== row.id || f !== field) return
  const oldVal = editingSnapshot.value?.[field]
  const newVal = row[field]
  if (oldVal === newVal) return
  row.__savingPrice = true
  try {
    const payload: any = { id: row.id, [field]: newVal }
    await http.put(`/biz/service/item/${row.id}`, payload)
    ElMessage.success('已保存')
  } catch (e: any) {
    row[field] = oldVal
    ElMessage.error('保存失败')
  } finally {
    row.__savingPrice = false
  }
}

// ===== 列头筛选 =====
const statusFilter = ref<number | null>(
  (() => {
    const v = localStorage.getItem('svc-item-status-filter')
    return v === null || v === 'null' ? null : Number(v)
  })()
)
watch(statusFilter, (v) => localStorage.setItem('svc-item-status-filter', String(v)))
const priceFilterOp = ref<'all' | '>=' | '<='>('all')
const priceFilterValue = ref<number>(0)
const priceFilterActive = computed(() => priceFilterOp.value !== 'all' && priceFilterValue.value > 0)

watch(statusFilter, (v) => {
  filter.status = v === null ? undefined : v
  filter.pageNum = 1
  loadData()
})

function onPriceFilterShow() {
  // 复位到当前 filter 状态
  priceFilterOp.value = 'all'
  priceFilterValue.value = 0
}
function applyPriceFilter() {
  // 价格是前端筛：因为后端没有 priceBetween 参数，先在内存里过滤
  filter.pageNum = 1
  loadFiltered()
}
function resetPriceFilter() {
  priceFilterOp.value = 'all'
  priceFilterValue.value = 0
  loadFiltered()
}

// ===== 搜索防抖（300ms） =====
let keywordTimer: any = null
function onKeywordInput() {
  if (keywordTimer) clearTimeout(keywordTimer)
  keywordTimer = setTimeout(() => {
    filter.pageNum = 1
    loadData()
  }, 300)
}

// ===== 内存版过滤（价格 + 组合显示给用户） =====
const filteredRows = computed(() => {
  if (priceFilterOp.value === 'all' || priceFilterValue.value <= 0) return rows.value
  const target = priceFilterValue.value
  return rows.value.filter((r: any) => {
    const v = Number(r.unitPrice || 0)
    if (priceFilterOp.value === '>=') return v >= target
    if (priceFilterOp.value === '<=') return v <= target
    return true
  })
})

async function loadData() {
  loading.value = true
  try {
    const res: any = await http.get('/biz/service/item/page', { params: filter })
    rows.value = res.data?.records || []
    total.value = res.data?.total || 0
    recalcStats()
  } finally {
    loading.value = false
  }
}

function loadFiltered() {
  // 仅切回服务端全集（价格是内存筛）
  loadData()
}

async function loadCategories() {
  const res: any = await http.get('/biz/service/category/list', { params: { status: 1 } })
  categories.value = res.data || []
}

function resetFilter() {
  filter.keyword = ''
  filter.categoryId = undefined
  filter.status = undefined
  filter.pageNum = 1
  loadData()
}

const dialog = reactive({ visible: false, id: undefined as number | undefined, saving: false })
const form = reactive<any>({
  serviceCode: '',
  serviceName: '',
  categoryId: undefined,
  unitPrice: 0,
  memberPrice: undefined,
  duration: 0,
  unit: '次',
  status: 1,
  sortOrder: 0,
  description: '',
  sop: ''
})

function openCreate() {
  dialog.id = undefined
  dialog.visible = true
}

function openEdit(row: any) {
  dialog.id = row.id
  Object.assign(form, row)
  dialog.visible = true
}

function resetDialog() {
  Object.assign(form, {
    serviceCode: '',
    serviceName: '',
    categoryId: undefined,
    unitPrice: 0,
    memberPrice: undefined,
    duration: 0,
    unit: '次',
    status: 1,
    sortOrder: 0,
    description: '',
    sop: ''
  })
}

async function save() {
  if (!form.serviceCode || !form.serviceName) {
    ElMessage.warning('编码和名称必填')
    return
  }
  dialog.saving = true
  try {
    const headers = { 'Idempotency-Key': newIdempotencyKey() }
    if (dialog.id) {
      await http.put(`/biz/service/item/${dialog.id}`, form, { headers })
      ElMessage.success('已更新')
    } else {
      await http.post('/biz/service/item', form, { headers })
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
  await ElMessageBox.confirm(`确认删除项目「${row.serviceName}」？`, '提示', { type: 'warning' })
  await http.delete(`/biz/service/item/${row.id}`)
  ElMessage.success('已删除')
  loadData()
}

// BOM
const bomDialog = reactive<any>({
  visible: false, loading: false, serviceId: undefined, serviceName: '', rows: []
})
async function openBom(row: any) {
  bomDialog.serviceId = row.id
  bomDialog.serviceName = row.serviceName
  bomDialog.visible = true
  await loadBom()
}
async function loadBom() {
  bomDialog.loading = true
  try {
    const r = await http.get(`/biz/service/item/${bomDialog.serviceId}`)
    bomDialog.rows = r.data?.materials || []
  } finally {
    bomDialog.loading = false
  }
}
function resetBom() {
  bomDialog.serviceId = undefined
  bomDialog.serviceName = ''
  bomDialog.rows = []
}

const addMaterialDialog = reactive({ visible: false })
const addMaterialForm = reactive({ productId: undefined as number | undefined, quantity: 1, unit: '', isRequired: 1 })
const productList = ref<any[]>([])

async function openAddMaterial() {
  addMaterialDialog.visible = true
  if (productList.value.length === 0) {
    const r = await http.get('/stock/page', { params: { pageNum: 1, pageSize: 200, status: 1 } })
    productList.value = r.data?.records || []
  }
}

function resetAddMaterial() {
  addMaterialForm.productId = undefined
  addMaterialForm.quantity = 1
  addMaterialForm.unit = ''
  addMaterialForm.isRequired = 1
}

async function confirmAddMaterial() {
  if (!addMaterialForm.productId) return ElMessage.warning('请选商品')
  try {
    await http.post(`/biz/service/item/${bomDialog.serviceId}/material`, addMaterialForm)
    ElMessage.success('已绑定')
    addMaterialDialog.visible = false
    await loadBom()
    await loadData()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '绑定失败')
  }
}

async function removeMaterial(row: any) {
  await ElMessageBox.confirm('确认解绑？', '提示', { type: 'warning' })
  await http.delete(`/biz/service/item/${bomDialog.serviceId}/material/${row.productId}`)
  ElMessage.success('已解绑')
  await loadBom()
  await loadData()
}

async function exportExcel() {
  exporting.value = true
  try {
    const res = await http.get('/biz/service/item/export', {
      params: {
        keyword: filter.keyword,
        categoryId: filter.categoryId,
        status: filter.status
      },
      responseType: 'blob'
    })
    const blob = new Blob([res.data], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    const tag = hasFilter.value ? '_筛选结果' : '_全量'
    a.download = `服务项目清单${tag}_${new Date().toISOString().slice(0, 10)}.xlsx`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success(`已导出（${total.value} 条）`)
  } catch (e: any) {
    ElMessage.error('导出失败：' + (e?.message || '未知错误'))
  } finally {
    exporting.value = false
  }
}

// ===== Excel 导入 =====
const importDialog = reactive({ visible: false, file: null as File | null, uploading: false })
const importResult = reactive({ visible: false, total: 0, success: 0, failed: 0, errors: [] as string[] })

async function downloadTemplate() {
  try {
    const res: any = await http.get('/biz/service/item/template', { responseType: 'blob' })
    const blob = new Blob([res.data], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
    })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = '服务项目导入模板.xlsx'
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('模板已下载，请按格式填写')
  } catch {
    ElMessage.error('下载模板失败')
  }
}

function onImportFileChange(file: any) {
  const raw = file?.raw || file
  if (!raw) return
  if (!raw.name.toLowerCase().endsWith('.xlsx')) {
    ElMessage.error('仅支持 .xlsx 格式')
    return
  }
  importDialog.file = raw
  importDialog.visible = true
  // 立刻关掉 el-upload 弹层
  setTimeout(() => {
    const events = document.querySelectorAll('.el-upload-list__item.is-ready')
    events.forEach((el) => (el as any).click?.())
  }, 0)
}

async function confirmImport() {
  if (!importDialog.file) return ElMessage.warning('请选择文件')
  importDialog.uploading = true
  try {
    const fd = new FormData()
    fd.append('file', importDialog.file)
    const r: any = await http.post('/biz/service/item/import', fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    const data = r.data || {}
    importResult.total = data.total || 0
    importResult.success = data.success || 0
    importResult.failed = data.failed || 0
    importResult.errors = data.errors || []
    importResult.visible = true
    importDialog.visible = false
    if (data.success > 0) loadData()
  } catch (e: any) {
    ElMessage.error('导入失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  } finally {
    importDialog.uploading = false
  }
}

// ===== 复制项目 =====
async function cloneItem(row: any) {
  const { value: code } = await ElMessageBox.prompt('请输入新项目编码（必须唯一）', '复制服务项目', {
    inputValue: row.serviceCode + '_COPY',
    inputValidator: (v: string) => !!v?.trim() || '编码不能为空',
    confirmButtonText: '复制并打开编辑',
    cancelButtonText: '取消'
  }).catch(() => ({ value: '' }))
  if (!code) return
  try {
    const payload = {
      serviceCode: code.trim(),
      serviceName: row.serviceName + '（副本）',
      categoryId: row.categoryId,
      unitPrice: row.unitPrice,
      memberPrice: row.memberPrice,
      duration: row.duration,
      unit: row.unit,
      sortOrder: row.sortOrder,
      status: 0, // 副本默认停用，避免被无意启用
      description: row.description,
      sop: row.sop,
      imageUrl: row.imageUrl,
      tenantId: row.tenantId
    }
    const headers = { 'Idempotency-Key': newIdempotencyKey() }
    const r: any = await http.post('/biz/service/item', payload, { headers })
    ElMessage.success('已复制，请在编辑窗口完善信息')
    if (r.data?.id) {
      // 找到新行并打开编辑
      await loadData()
      const fresh = rows.value.find((x: any) => x.id === r.data.id)
      if (fresh) openEdit(fresh)
    } else {
      loadData()
    }
  } catch (e: any) {
    ElMessage.error('复制失败：' + (e?.response?.data?.message || e?.message || '未知错误'))
  }
}

// ===== 上移/下移排序 =====
function isFirstRow(row: any) {
  return filteredRows.value[0]?.id === row.id
}
function isLastRow(row: any) {
  const arr = filteredRows.value
  return arr[arr.length - 1]?.id === row.id
}
async function moveSortOrder(row: any, delta: number) {
  const arr = filteredRows.value
  const idx = arr.findIndex((x: any) => x.id === row.id)
  if (idx < 0) return
  const newIdx = idx + delta
  if (newIdx < 0 || newIdx >= arr.length) return
  const cur = arr[idx].sortOrder || 0
  const target = arr[newIdx].sortOrder || 0
  // 简单互换：直接交换 sort_order
  try {
    await Promise.all([
      http.put(`/biz/service/item/${arr[idx].id}`, { sortOrder: target }),
      http.put(`/biz/service/item/${arr[newIdx].id}`, { sortOrder: cur })
    ])
    ElMessage.success('排序已更新')
    loadData()
  } catch (e: any) {
    ElMessage.error('排序失败：' + (e?.message || '未知错误'))
  }
}

// ===== 价格历史（容错：后端无此接口时显示占位） =====
async function openPriceHistory(row: any) {
  priceHistory.visible = true
  priceHistory.serviceId = row.id
  priceHistory.serviceName = row.serviceName
  priceHistory.records = []
  priceHistory.loading = true
  try {
    const r: any = await http.get(`/biz/service/item/${row.id}/price-history`)
    priceHistory.records = r.data || []
  } catch (e: any) {
    // 404 等情况视为暂无历史
    priceHistory.records = []
  } finally {
    priceHistory.loading = false
  }
}

// ===== 批量操作 =====
async function batchUpdateStatus(status: number) {
  if (!selectedRows.value.length) return
  await ElMessageBox.confirm(
    `确认将选中的 ${selectedRows.value.length} 个项目${status === 1 ? '启用' : '停用'}？`,
    '批量操作',
    { type: 'warning' }
  )
  let ok = 0, fail = 0
  for (const r of selectedRows.value) {
    try {
      await http.put(`/biz/service/item/${r.id}`, { status })
      ok++
    } catch { fail++ }
  }
  ElMessage[fail === 0 ? 'success' : 'warning'](`成功 ${ok} 条${fail ? `，失败 ${fail} 条` : ''}`)
  selectedRows.value = []
  loadData()
}

async function batchChangeCategory(categoryId: number) {
  if (!selectedRows.value.length) return
  const cat = categories.value.find((c: any) => c.id === categoryId)
  await ElMessageBox.confirm(
    `确认将选中的 ${selectedRows.value.length} 个项目改为分类「${cat?.categoryName}」？`,
    '批量改分类',
    { type: 'warning' }
  )
  let ok = 0, fail = 0
  for (const r of selectedRows.value) {
    try {
      await http.put(`/biz/service/item/${r.id}`, { categoryId })
      ok++
    } catch { fail++ }
  }
  ElMessage[fail === 0 ? 'success' : 'warning'](`成功 ${ok} 条${fail ? `，失败 ${fail} 条` : ''}`)
  selectedRows.value = []
  loadData()
}

async function batchRemove() {
  if (!selectedRows.value.length) return
  await ElMessageBox.confirm(
    `确认删除选中的 ${selectedRows.value.length} 个项目？该操作不可撤销`,
    '批量删除',
    { type: 'error' }
  )
  let ok = 0, fail = 0
  const failMsgs: string[] = []
  for (const r of selectedRows.value) {
    try {
      await http.delete(`/biz/service/item/${r.id}`)
      ok++
    } catch (e: any) {
      fail++
      failMsgs.push(e?.response?.data?.message || e?.message || '未知错误')
    }
  }
  ElMessage[fail === 0 ? 'success' : 'warning'](
    `成功删除 ${ok} 条${fail ? `，失败 ${fail} 条` : ''}${fail ? `：${failMsgs[0]}` : ''}`
  )
  selectedRows.value = []
  loadData()
}

onMounted(async () => {
  loadColumnSettings()
  loadPersistedFilter()
  loadSavedViews()
  window.addEventListener('keydown', onGlobalKeydown)
  await loadCategories()
  await loadData()
})

onUnmounted(() => {
  window.removeEventListener('keydown', onGlobalKeydown)
})
</script>

<style scoped>
.search-bar { margin-bottom: 12px; }
.search-bar .el-input,
.search-bar .el-select { margin-right: 8px; }
.pagination { margin-top: 12px; text-align: right; }

.filter-icon {
  font-size: 14px;
  color: #c0c4cc;
  cursor: pointer;
  margin-left: 4px;
  vertical-align: middle;
}
.filter-icon.active,
.filter-icon:hover { color: #409eff; }

.col-header {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 32px 16px;
  color: #909399;
}
.empty-state .empty-icon {
  font-size: 48px;
  color: #c0c4cc;
  margin-bottom: 8px;
}
.empty-state .empty-title {
  font-size: 15px;
  color: #606266;
  margin-bottom: 4px;
}
.empty-state .empty-desc {
  font-size: 13px;
  color: #909399;
  margin-bottom: 12px;
  max-width: 360px;
  text-align: center;
}

.inline-edit {
  cursor: pointer;
  padding: 2px 4px;
  border-radius: 3px;
  transition: background 0.15s;
}
.inline-edit:hover {
  background: #ecf5ff;
  color: #409eff;
}
.inline-edit.saving { opacity: 0.5; }

.batch-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  margin-bottom: 8px;
  background: #ecf5ff;
  border: 1px solid #d9ecff;
  border-radius: 4px;
  font-size: 13px;
}
.batch-bar .batch-info { color: #606266; margin-right: 8px; }
.batch-bar .batch-info b { color: #409eff; padding: 0 4px; }

.batch-bar-enter-active, .batch-bar-leave-active { transition: all 0.25s ease; }
.batch-bar-enter-from, .batch-bar-leave-to { opacity: 0; transform: translateY(-6px); }

.row-thumb {
  width: 40px;
  height: 40px;
  border-radius: 4px;
  border: 1px solid #ebeef5;
  background: #f5f7fa;
  object-fit: cover;
}
.no-thumb { color: #c0c4cc; font-size: 13px; }

.row-expand {
  padding: 12px 24px;
  background: #fafbfc;
  border-radius: 4px;
}
.row-expand-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
}
.row-expand-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}
.row-expand-value {
  font-size: 13px;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-all;
}
.row-expand-value.sop {
  background: #fff;
  padding: 8px 10px;
  border-radius: 4px;
  border: 1px dashed #dcdfe6;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12.5px;
}

.readonly-price {
  color: #909399;
  font-variant-numeric: tabular-nums;
}
.info-icon {
  font-size: 13px;
  color: #c0c4cc;
  margin-left: 2px;
  cursor: help;
}
.info-icon:hover { color: #409eff; }

.history-hint { margin-top: 12px; }
.history-hint code {
  background: #f5f7fa;
  padding: 1px 4px;
  border-radius: 3px;
  font-family: Consolas, monospace;
}

.loading-placeholder {
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
}
.loading-line {
  width: 80%;
  height: 14px;
  border-radius: 4px;
  background: linear-gradient(90deg, #f2f6fc 25%, #e6ebf2 50%, #f2f6fc 75%);
  background-size: 200% 100%;
  animation: skeleton 1.4s ease-in-out infinite;
}
.loading-line.short { width: 50%; }
@keyframes skeleton {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* === 顶部统计卡片 === */
.stat-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  margin-bottom: 12px;
}
.stat-card {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 14px 18px;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
.stat-card:hover {
  border-color: #c6e2ff;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(50, 115, 220, 0.08);
}
.stat-card .stat-label {
  font-size: 13px;
  color: #909399;
  margin-bottom: 6px;
}
.stat-card .stat-value {
  font-size: 22px;
  font-weight: 600;
  color: #303133;
  font-variant-numeric: tabular-nums;
}
.stat-card .stat-value.success { color: #67c23a; }
.stat-card .stat-value.info { color: #909399; }

/* === 快捷筛选 chips === */
.quick-chips {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
  padding: 4px 2px;
}
.quick-chips .chips-label {
  font-size: 13px;
  color: #606266;
  margin-right: 4px;
}

/* === toolbar 右侧工具区 === */
.toolbar-spacer { flex: 1; }

/* === 列设置面板 === */
.column-settings {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 320px;
  overflow-y: auto;
}
.column-settings-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: #606266;
  margin-bottom: 4px;
  padding-bottom: 4px;
  border-bottom: 1px solid #ebeef5;
}

/* === Excel 导入 === */
.import-preview {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 4px;
}
.import-file-icon {
  font-size: 36px;
  color: #67c23a;
}
.import-file-name {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  flex: 1;
  word-break: break-all;
}
.import-file-size {
  font-size: 12px;
  color: #909399;
}
.import-summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}
.import-stat {
  background: #f5f7fa;
  border-radius: 6px;
  padding: 16px 8px;
  text-align: center;
}
.import-stat.success { background: #f0f9eb; }
.import-stat.failed { background: #fef0f0; }
.import-stat-num {
  font-size: 28px;
  font-weight: 600;
  color: #303133;
  font-variant-numeric: tabular-nums;
}
.import-stat.success .import-stat-num { color: #67c23a; }
.import-stat.failed .import-stat-num { color: #f56c6c; }
.import-stat-label {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.import-error-list {
  margin: 0;
  padding-left: 20px;
  max-height: 240px;
  overflow-y: auto;
  font-size: 12px;
  color: #f56c6c;
}
.import-error-list li { margin-bottom: 4px; }
</style>