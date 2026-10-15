<template>
  <div class="page-visual">
    <!-- ①+③+⑧ KPI 看板：图标 + 微色块背景 + 响应式 -->
    <el-row :gutter="12" class="kpi-row">
      <el-col :xs="12" :sm="12" :md="6" :lg="6">
        <el-card shadow="hover" class="kpi-card kpi-blue">
          <el-icon class="kpi-icon"><Folder /></el-icon>
          <div class="kpi-body">
            <div class="kpi-label">总档案数</div>
            <div class="kpi-value">{{ kpi.total }}</div>
            <div class="kpi-extra">近 30 天 +{{ kpi.new30d }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6" :lg="6">
        <el-card shadow="hover" class="kpi-card kpi-green">
          <el-icon class="kpi-icon"><Plus /></el-icon>
          <div class="kpi-body">
            <div class="kpi-label">今日新增</div>
            <div class="kpi-value">{{ kpi.todayNew }}</div>
            <div class="kpi-extra">
              <el-tooltip content="统计最近 24 小时内创建的档案，不含已撤回" placement="top" :show-after="300">
                <span class="kpi-extra-hint">最近 24 小时</span>
              </el-tooltip>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6" :lg="6">
        <el-card shadow="hover" class="kpi-card kpi-purple">
          <el-icon class="kpi-icon"><MagicStick /></el-icon>
          <div class="kpi-body">
            <div class="kpi-label">AI 已分析</div>
            <div class="kpi-value">{{ kpi.aiAnalyzed }}</div>
            <div class="kpi-extra">占比 {{ kpi.aiRatio }}%</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="6" :lg="6">
        <el-card shadow="hover" class="kpi-card kpi-orange" :class="{ 'kpi-warn': kpi.pendingWithdraw > 0 }">
          <el-icon class="kpi-icon"><Warning /></el-icon>
          <div class="kpi-body">
            <div class="kpi-label">待处理撤回</div>
            <div class="kpi-value" :class="{ warn: kpi.pendingWithdraw > 0 }">{{ kpi.pendingWithdraw }}</div>
            <div class="kpi-extra">
              <el-link type="warning" :underline="'never'" @click="tab = 'withdraw'">去处理 →</el-link>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- P2-#9: 安规过期预警 KPI + 已撤回 合并到第二行 -->
    <el-row :gutter="12" class="kpi-row">
      <el-col :xs="12" :sm="12" :md="8" :lg="6">
        <el-card shadow="hover" class="kpi-card kpi-red">
          <el-icon class="kpi-icon"><Delete /></el-icon>
          <div class="kpi-body">
            <div class="kpi-label">已撤回</div>
            <div class="kpi-value danger">{{ kpi.revoked }}</div>
            <div class="kpi-extra">合规删除</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="24" :md="8" :lg="6" class="kpi-fifth-wrap">
        <el-card shadow="hover" class="kpi-card kpi-mauve" :class="{ 'kpi-warn': kpi.expiringSoon > 0 }">
          <el-icon class="kpi-icon"><Bell /></el-icon>
          <div class="kpi-body">
            <div class="kpi-label">即将过期授权（7天内）</div>
            <div class="kpi-value" :class="{ warn: kpi.expiringSoon > 0 }">{{ kpi.expiringSoon }}</div>
            <div class="kpi-extra">
              <el-link type="warning" :underline="'never'" @click="tab = 'consent'">查看并续签 →</el-link>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- P0-A: 近 7 天新增趋势 + 今日时段分布 -->
    <el-row :gutter="12" class="kpi-row">
      <el-col :xs="24" :sm="12" :md="12" :lg="12">
        <el-card shadow="hover" class="chart-card">
          <template #header>
            <div class="chart-card-header">
              <span class="chart-title">📈 近 7 天新增趋势</span>
              <span class="chart-sub">含全部类型（不含已撤回）</span>
              <el-tag v-if="sparkline7d.length" size="small" type="info" effect="plain" class="chart-tag">
                累计 {{ sparkline7d.reduce((s, x) => s + x.value, 0) }} 份
              </el-tag>
            </div>
          </template>
          <div ref="sparklineRef" class="sparkline-chart"></div>
          <el-empty v-if="!sparkline7d.length" description="暂无趋势数据" :image-size="60" />
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="12" :md="12" :lg="12">
        <el-card shadow="hover" class="chart-side">
          <div class="side-title">📊 数据洞察</div>
          <div v-if="bodyPartData.length" class="side-body">
            <div v-for="(p, i) in bodyPartData.slice(0, 3)" :key="i" class="side-row">
              <div class="side-name">{{ p.name }}</div>
              <el-progress :percentage="p.percent" :stroke-width="8" :format="() => `${p.value}`" />
            </div>
            <div class="dynamic-tip">💡 {{ dynamicInsight }}</div>
          </div>
          <div v-else class="side-empty">
            <span>暂无部位数据，新建档案后将自动聚合</span>
            <br />
            <el-button type="primary" link :icon="Plus" @click="openAdd" size="small" style="margin-top:6px">
              前往新建档案 →
            </el-button>
          </div>
          <div v-if="bodyPartData.length" class="side-tip">
            🎯 Top3 部位占比来自全量统计，建议针对高频部位准备专项话术与案例图。
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- P0-A: 近 7 天新增趋势 + 实时数据洞察 -->

    <el-tabs v-model="tab" class="visual-tabs">
      <!-- ================= 视觉档案 ================= -->
      <el-tab-pane label="视觉档案" name="archive">
        <!-- ① 筛选条对齐：表单 inline + 标签窄 + 创建时间挪到第一行 -->
        <el-form :inline="true" class="filter-bar" label-width="70px" label-position="left">
          <el-form-item label="会员">
            <el-input v-model="query.memberId" placeholder="会员ID" style="width:130px" clearable @clear="load" />
          </el-form-item>
          <el-form-item label="类型">
            <el-select v-model="query.archiveType" style="width:120px" clearable placeholder="全部">
              <el-option v-for="t in types" :key="t" :label="t" :value="t" />
            </el-select>
          </el-form-item>
          <!-- P3-A: 部位筛选（与 KPI 部位分布饼图联动） -->
          <el-form-item label="部位">
            <el-select v-model="query.bodyPart" style="width:140px" clearable filterable placeholder="全部"
              @change="load">
              <el-option v-for="p in bodyParts" :key="p" :label="p" :value="p" />
            </el-select>
          </el-form-item>
          <!-- P3-A: 项目筛选 -->
          <el-form-item label="项目">
            <el-input v-model="query.projectName" placeholder="项目名（模糊）" style="width:160px" clearable
              @clear="load" @keyup.enter="load" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="query.revoked" style="width:110px" clearable placeholder="全部"
              @change="load">
              <el-option label="有效" :value="0" />
              <el-option label="已撤回" :value="1" />
            </el-select>
          </el-form-item>
          <el-form-item label="AI">
            <el-select v-model="query.aiAnalyzed" style="width:110px" clearable placeholder="全部"
              @change="load">
              <el-option label="已分析" :value="1" />
              <el-option label="未分析" :value="0" />
            </el-select>
          </el-form-item>
          <el-form-item label="日期">
            <el-date-picker
              v-model="dateRange" type="daterange" range-separator="-"
              start-placeholder="起" end-placeholder="止"
              value-format="YYYY-MM-DD" style="width:230px"
              @change="load" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="load">查询</el-button>
            <el-button @click="resetFilter">重置</el-button>
            <!-- ④ 主操作按钮：成功色 -->
            <el-button type="success" :icon="Plus" @click="openAdd">新建档案</el-button>
          </el-form-item>
        </el-form>

        <!-- P1-4: 批量操作工具栏 -->
        <div class="batch-bar">
          <el-checkbox v-model="selectAll" @change="toggleSelectAll" :indeterminate="isIndeterminate">
            全选
          </el-checkbox>
          <span class="select-tip">已选 {{ selectedIds.length }} 项</span>
          <el-button size="small" type="primary" :disabled="!selectedIds.length" @click="batchAnalyze" :loading="batchAnalyzing">
            批量 AI 分析
          </el-button>
          <!-- ⑫ 批量撤回 loading -->
          <el-button size="small" type="danger" :disabled="!selectedIds.length" @click="batchRevoke" :loading="batchRevoking">
            批量撤回
          </el-button>
          <!-- P3-B: 导出当前筛选结果为 CSV -->
          <el-button size="small" :icon="Download" :disabled="!archives.length" @click="exportCsv">
            导出 CSV
          </el-button>
        </div>

        <!-- P0-1: 空状态自定义引导：插画 + 步骤 + 主按钮 -->
        <el-table
          v-if="archives.length > 0"
          :data="archives" stripe size="default"
          @selection-change="onSelectionChange"
          v-loading="loading"
          row-key="id"
        >
          <el-table-column type="selection" width="44" />
          <el-table-column label="图像" width="110">
            <template #default="{ row }">
              <div class="thumb-wrap">
                <!-- P2-13: 加载失败占位 + skeleton -->
                <el-image
                  :ref="el => { if (el) thumbRefs[row.id] = el }"
                  :src="row.imageUrl"
                  :preview-src-list="[row.imageUrl]"
                  :preview-teleported="true"
                  fit="cover"
                  class="thumb"
                  loading="lazy"
                  @error="onImgError(row)"
                >
                  <template #error>
                    <div class="thumb-error">
                      <el-icon><Picture /></el-icon>
                      <span>无图</span>
                    </div>
                  </template>
                </el-image>
                <el-tag v-if="row.faceMasked" type="success" size="small" class="mask-badge">已脱敏</el-tag>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="archiveCode" label="档案编号" width="170" />
          <el-table-column prop="archiveType" label="类型" width="80" />
          <el-table-column prop="bodyPart" label="部位" width="100" />
          <el-table-column prop="projectName" label="项目" min-width="140" />
          <el-table-column label="AI 分析" width="160">
            <template #default="{ row }">
              <div class="ai-cell">
                <el-tag v-if="row.aiAnalyzed" type="success" size="small">已分析</el-tag>
                <el-tag v-else type="info" size="small">未分析</el-tag>
                <el-button v-if="row.aiAnalyzed" link type="primary" size="small" @click="openAiDetail(row)">
                  详情
                </el-button>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="aiScore" label="AI 评分" width="90" align="center">
            <template #default="{ row }">
              <span :class="['ai-score', scoreClass(row.aiScore)]">{{ row.aiScore ?? '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.revoked ? 'danger' : 'success'" size="small">
                {{ row.revoked ? '已撤回' : '有效' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" width="160" />
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-dropdown size="small" trigger="click" @command="(cmd) => onRowCmd(cmd, row)">
                <el-button size="small" type="primary" link>
                  操作 <el-icon class="el-icon--right"><Sort /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="view">查看大图</el-dropdown-item>
                    <el-dropdown-item command="detail">档案详情</el-dropdown-item>
                    <el-dropdown-item command="before">设为前图</el-dropdown-item>
                    <el-dropdown-item command="after">设为后图</el-dropdown-item>
                    <el-dropdown-item command="reanalyze" :disabled="!row.imageUrl">重新分析</el-dropdown-item>
                    <el-dropdown-item command="aiDetail" :disabled="!row.aiAnalyzed" divided>AI 详情</el-dropdown-item>
                    <el-dropdown-item command="revoke" :disabled="!!row.revoked">撤回</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-table-column>
        </el-table>

        <!-- P0-1: 空档案引导 -->
        <el-empty v-if="!loading && archives.length === 0" class="archive-empty">
          <template #image>
            <div class="empty-illu">
              <el-icon><Picture /></el-icon>
            </div>
          </template>
          <template #default>
            <h3 class="empty-title">还没有视觉档案</h3>
            <p class="empty-sub">上传会员的第一张图即可开启 AI 对比、合规追踪和档案时间线</p>
            <div class="empty-steps">
              <div class="step"><span class="step-num">1</span><span>新建档案</span></div>
              <div class="step"><span class="step-num">2</span><span>AI 自动分析</span></div>
              <div class="step"><span class="step-num">3</span><span>前后对比看效果</span></div>
            </div>
            <el-button type="success" :icon="Plus" @click="openAdd" size="large" class="empty-cta">
              立即新建第一份档案
            </el-button>
          </template>
        </el-empty>

        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          layout="total,sizes,prev,pager,next,jumper"
          @current-change="load"
          @size-change="load"
          class="pager"
        />
      </el-tab-pane>

      <!-- ================= AI 对比 ================= -->
      <el-tab-pane label="AI 对比" name="compare">
        <el-form :inline="true" class="filter-bar">
          <el-form-item label="会员ID">
            <el-input v-model.number="compareForm.memberId" style="width:140px"
              @change="onCompareMemberChange" placeholder="会员ID" />
          </el-form-item>
          <el-form-item label="前图">
            <el-select v-model="compareForm.beforeId" style="width:220px" filterable placeholder="选前图">
              <el-option v-for="a in memberArchives" :key="a.id"
                :label="`${a.archiveCode} · ${a.bodyPart || '-'} · ${a.createTime}`" :value="a.id">
                <span style="float:left">{{ a.archiveCode }}</span>
                <span style="float:right;color:#999;font-size:12px">{{ a.archiveType }} · {{ a.bodyPart }}</span>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item label="后图">
            <el-select v-model="compareForm.afterId" style="width:220px" filterable placeholder="选后图">
              <el-option v-for="a in memberArchives" :key="a.id"
                :label="`${a.archiveCode} · ${a.bodyPart || '-'} · ${a.createTime}`" :value="a.id">
                <span style="float:left">{{ a.archiveCode }}</span>
                <span style="float:right;color:#999;font-size:12px">{{ a.archiveType }} · {{ a.bodyPart }}</span>
              </el-option>
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="doCompare" :loading="compLoading">对比</el-button>
            <el-button v-if="compareForm.beforeId" @click="addBtn(compareForm.beforeId)">前图 → 档案</el-button>
          </el-form-item>
        </el-form>

        <!-- P0-2: 前后图对比 + 拖拽 slider -->
        <div v-if="compareBefore || compareAfter" class="compare-canvas">
          <div class="cmp-img-wrap">
            <img ref="cmpImg" :src="compareBefore?.imageUrl" class="cmp-img" />
            <div class="cmp-overlay" :style="{ width: sliderPos + '%' }">
              <img :src="compareAfter?.imageUrl" class="cmp-img" />
            </div>
            <div class="cmp-slider" :style="{ left: sliderPos + '%' }">
              <div class="cmp-slider-line"></div>
              <div class="cmp-slider-handle">
                <el-icon><Sort /></el-icon>
              </div>
              <div class="cmp-label cmp-label-left">前</div>
              <div class="cmp-label cmp-label-right">后</div>
            </div>
          </div>
          <div class="cmp-meta">
            <span v-if="compareBefore">前图: {{ compareBefore.archiveCode }} · {{ compareBefore.archiveType }} · {{ compareBefore.bodyPart }}</span>
            <span v-if="compareAfter">后图: {{ compareAfter.archiveCode }} · {{ compareAfter.archiveType }} · {{ compareAfter.bodyPart }}</span>
          </div>
        </div>

        <!-- P2-#10: 批次对比（2x2 montage） -->
        <el-card class="batch-card" shadow="never">
          <template #header>
            <div class="batch-header">
              <div>
                <span class="batch-title">🧩 批次对比（2x2 montage）</span>
                <span class="batch-sub">演示用：选同会员 4 张图，自动按时间拆成前/后两组做对比。</span>
              </div>
              <el-tag size="small" type="info" effect="plain">{{ batchSelected.length }}/4</el-tag>
            </div>
          </template>

          <el-form :inline="true">
            <el-form-item label="会员ID">
              <el-input v-model.number="batchForm.memberId" style="width:140px"
                @change="loadBatchArchives" placeholder="先选会员" />
            </el-form-item>
            <el-form-item label="选择档案">
              <el-select v-model="batchSelected" multiple collapse-tags collapse-tags-tooltip
                :max-collapse-tags="2"
                style="width:380px" filterable placeholder="选 4 张图（自动按创建时间排序拆前/后）">
                <el-option v-for="a in batchArchives" :key="a.id"
                  :label="`${a.archiveCode} · ${a.bodyPart || '-'} · ${a.createTime}`" :value="a.id">
                  <span style="float:left">{{ a.archiveCode }}</span>
                  <span style="float:right;color:#999;font-size:12px">{{ a.archiveType }} · {{ a.bodyPart }}</span>
                </el-option>
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="batchLoading" :disabled="batchSelected.length !== 4"
                @click="runBatchCompare">开始对比</el-button>
              <el-button @click="batchSelected = []">清空</el-button>
            </el-form-item>
          </el-form>

          <!-- Montage 2x2 预览 -->
          <div v-if="batchMontage.length" class="montage-grid">
            <div v-for="(cell, i) in batchMontage" :key="i" class="montage-cell">
              <img :src="cell.imageUrl" :alt="cell.label" class="montage-img" />
              <div class="montage-tag" :class="cell.bucket">{{ cell.label }}</div>
            </div>
          </div>

          <!-- 批次对比结果（沿用 compareResult 字段，直接复制到对比 tab 主区域体验） -->
          <el-descriptions v-if="batchResult" :column="3" border class="batch-result">
            <el-descriptions-item label="相似度">
              <el-progress :percentage="Number(batchResult.similarity || 0)" :stroke-width="14" />
            </el-descriptions-item>
            <el-descriptions-item label="差异分">{{ batchResult.diffScore }}</el-descriptions-item>
            <el-descriptions-item label="改善项">{{ batchResult.improvementAreas }}</el-descriptions-item>
            <el-descriptions-item label="退步项" :span="3">{{ batchResult.regressionAreas }}</el-descriptions-item>
            <el-descriptions-item label="AI 总结" :span="3">{{ batchResult.aiSummary }}</el-descriptions-item>
          </el-descriptions>
          <el-empty v-else description="选 4 张图后开始" :image-size="60" />
        </el-card>

        <!-- ② 空状态：3 步引导 -->
        <el-empty v-if="!compareBefore && !compareAfter && !compareResult" description="选择前后图后开始对比">
          <template #image>
            <div class="cmp-empty-icon">
              <el-icon><Histogram /></el-icon>
            </div>
          </template>
          <template #default>
            <div class="cmp-empty-steps">
              <div class="step"><span class="step-num">1</span><span class="step-text">输入会员ID，自动加载档案列表</span></div>
              <div class="step"><span class="step-num">2</span><span class="step-text">从下拉框选前图 / 后图</span></div>
              <div class="step"><span class="step-num">3</span><span class="step-text">点击「对比」查看 AI 评估结果</span></div>
            </div>
          </template>
        </el-empty>

        <el-card v-if="compareResult" class="cmp-result">
          <template #header>
            <div class="cmp-result-header">
              <span>AI 对比结果</span>
              <!-- ⑨ 导出报告（Markdown / HTML / 打印 PDF） -->
              <div>
                <el-button size="small" @click="copyCompareReport">复制报告</el-button>
                <el-button size="small" :icon="Download" @click="downloadMdReport">下载 Markdown</el-button>
                <el-button size="small" :icon="Download" @click="downloadHtmlReport">下载 HTML</el-button>
                <el-button size="small" type="primary" :icon="Printer" @click="printCompareReport">打印 / 导出 PDF</el-button>
              </div>
            </div>
          </template>
          <el-descriptions :column="3" border>
            <el-descriptions-item label="相似度">
              <el-progress :percentage="Number(compareResult.similarity || 0)" :stroke-width="14" />
            </el-descriptions-item>
            <el-descriptions-item label="差异分">{{ compareResult.diffScore }}</el-descriptions-item>
            <el-descriptions-item label="改善项">{{ compareResult.improvementAreas }}</el-descriptions-item>
            <el-descriptions-item label="退步项" :span="3">{{ compareResult.regressionAreas }}</el-descriptions-item>
            <el-descriptions-item label="AI 总结" :span="3">{{ compareResult.aiSummary }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-tab-pane>

      <!-- ================= 图像授权 ================= -->
      <el-tab-pane label="图像授权" name="consent">
        <el-form :inline="true" class="filter-bar">
          <el-form-item label="会员">
            <el-input v-model.number="consentForm.memberId" style="width:130px" />
          </el-form-item>
          <el-form-item label="类型">
            <el-select v-model="consentForm.consentType" style="width:180px">
              <el-option label="存储 STORAGE" value="STORAGE" />
              <el-option label="AI 分析 AI_ANALYSIS" value="AI_ANALYSIS" />
              <el-option label="匿名公开 ANONYMIZED_PUB" value="ANONYMIZED_PUB" />
            </el-select>
          </el-form-item>
          <el-form-item label="签署人">
            <el-input v-model="consentForm.signerName" style="width:140px" placeholder="签署人姓名" />
          </el-form-item>
          <el-form-item>
            <!-- ④ 主操作：紫色 -->
            <el-button color="#7c3aed" :dark="true" @click="signConsent">签署</el-button>
          </el-form-item>
        </el-form>

        <!-- ⑤ 签署历史：默认折叠（无 memberId 隐藏整段） -->
        <el-collapse v-model="consentCollapseOpen" class="consent-collapse">
          <el-collapse-item name="1">
            <template #title>
              <span>签署历史</span>
              <el-tag v-if="consentTotal > 0" size="small" type="success" effect="plain" style="margin-left:8px">
                共 {{ consentTotal }} 份授权
              </el-tag>
              <span v-else style="margin-left:8px;color:#909399;font-size:12px">（展开查看）</span>
            </template>
            <el-form :inline="true" class="filter-bar">
              <el-form-item label="会员">
                <el-input v-model.number="consentQuery.memberId" style="width:130px" @change="loadConsents" />
              </el-form-item>
              <el-form-item>
                <el-button @click="loadConsents">查询</el-button>
              </el-form-item>
            </el-form>
            <el-table :data="consents" stripe v-loading="consentLoading"
              empty-text="暂无签署记录，请输入会员ID查询">
              <el-table-column prop="id" label="ID" width="80" />
              <el-table-column prop="memberId" label="会员" width="100" />
              <el-table-column prop="signerName" label="签署人" width="100" />
              <el-table-column prop="signerRelation" label="关系" width="80" />
              <el-table-column label="类型" width="140">
                <template #default="{ row }">
                  <el-tag size="small">{{ consentTypeLabel(row.consentType) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="签署时间" width="180">
                <template #default="{ row }">{{ formatDateTime(row.effectiveAt || row.createTime) }}</template>
              </el-table-column>
              <el-table-column label="有效期至" width="200">
                <template #default="{ row }">
                  <span :class="{ 'expire-soon': isExpiringSoon(row.expireAt), 'expire-done': row.status === 2 }">
                    {{ formatDateTime(row.expireAt) || '无期限' }}
                  </span>
                </template>
              </el-table-column>
              <!-- P2-#9: 过期预警（7天内必续签） -->
              <el-table-column label="预警" width="120">
                <template #default="{ row }">
                  <template v-if="row.status === 1">
                    <el-tag v-if="isExpiringSoon(row.expireAt)" type="danger" size="small" effect="dark">
                      ⚠ {{ daysToExpire(row.expireAt) }} 天后过期
                    </el-tag>
                    <el-tag v-else-if="daysToExpire(row.expireAt) <= 30" type="warning" size="small">
                      {{ daysToExpire(row.expireAt) }} 天
                    </el-tag>
                    <el-tag v-else type="success" size="small" effect="plain">正常</el-tag>
                  </template>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag v-if="row.status === 1" type="success" size="small">有效</el-tag>
                  <el-tag v-else-if="row.status === 2" type="warning" size="small">已过期</el-tag>
                  <el-tag v-else type="danger" size="small">已撤销</el-tag>
                </template>
              </el-table-column>
            </el-table>
            <el-pagination
              v-model:current-page="consentQuery.pageNum"
              v-model:page-size="consentQuery.pageSize"
              :total="consentTotal"
              layout="total,sizes,prev,pager,next"
              @current-change="loadConsents"
              class="pager"
            />
          </el-collapse-item>
        </el-collapse>
      </el-tab-pane>

      <!-- ================= 撤回申请 ================= -->
      <el-tab-pane label="撤回申请" name="withdraw">
        <el-form :inline="true" class="filter-bar" label-width="70px" label-position="left">
          <el-form-item label="会员">
            <el-input v-model.number="wdForm.memberId" style="width:130px" />
          </el-form-item>
          <el-form-item label="范围">
            <el-select v-model="wdForm.withdrawScope" style="width:140px">
              <el-option label="全部" value="ALL" />
              <el-option label="仅AI训练" value="AI_TRAINING" />
              <el-option label="仅员工可见" value="STAFF_VIEW" />
            </el-select>
          </el-form-item>
          <el-form-item label="原因">
            <el-input v-model="wdForm.reason" style="width:240px" placeholder="撤回原因（必填）" />
          </el-form-item>
          <el-form-item>
            <!-- ④ 主操作：橙色 -->
            <el-button color="#e6a23c" :dark="true" @click="applyWithdraw">提交撤回申请</el-button>
          </el-form-item>
        </el-form>

        <!-- P3-C: 撤回列表筛选条（独立于提交表单） -->
        <el-form :inline="true" class="filter-bar wd-filter" label-width="70px" label-position="left">
          <el-form-item label="状态">
            <el-select v-model="wdQuery.status" style="width:120px" clearable placeholder="全部" @change="loadWithdraws">
              <el-option label="待处理" :value="1" />
              <el-option label="已批准" :value="2" />
              <el-option label="已驳回" :value="3" />
            </el-select>
          </el-form-item>
          <el-form-item label="处理人">
            <el-input v-model="wdQuery.handlerName" placeholder="处理人姓名（模糊）" style="width:180px" clearable
              @clear="loadWithdraws" @keyup.enter="loadWithdraws" />
          </el-form-item>
          <el-form-item label="日期">
            <el-date-picker v-model="wdDateRange" type="daterange" range-separator="-"
              start-placeholder="起" end-placeholder="止"
              value-format="YYYY-MM-DD" style="width:230px" @change="loadWithdraws" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="loadWithdraws">查询</el-button>
            <el-button @click="resetWdQuery">重置</el-button>
          </el-form-item>
        </el-form>

        <el-table :data="withdraws" stripe v-loading="wdLoading" @row-click="openWdDetail"
          empty-text="暂无待处理撤回申请">
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="memberId" label="会员" width="100" />
          <el-table-column prop="memberName" label="会员姓名" width="120" />
          <el-table-column prop="withdrawScope" label="范围" width="140">
            <template #default="{ row }">
              <el-tag size="small">{{ scopeLabel(row.withdrawScope) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="reason" label="原因" show-overflow-tooltip />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'warning' : row.status === 2 ? 'success' : 'danger'" size="small">
                {{ row.status === 1 ? '待处理' : row.status === 2 ? '已处理' : '已驳回' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="申请时间" width="170" />
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-button size="small" type="success" :disabled="row.status !== 1" @click.stop="handle(row, 2)">批准</el-button>
              <el-button size="small" type="danger" :disabled="row.status !== 1" @click.stop="handle(row, 3)">驳回</el-button>
              <el-button size="small" link @click.stop="openWdDetail(row)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- P3-C: 撤回表分页器 -->
        <el-pagination
          v-model:current-page="wdQuery.pageNum"
          v-model:page-size="wdQuery.pageSize"
          :total="wdTotal"
          layout="total,sizes,prev,pager,next"
          @current-change="loadWithdraws"
          @size-change="loadWithdraws"
          class="pager"
        />

        <!-- P1-7: 撤回申请详情抽屉 -->
        <el-drawer v-model="wdDetailVisible" title="撤回申请详情" size="540px" direction="rtl">
          <div v-if="wdDetail" class="wd-detail">
            <el-descriptions :column="1" border>
              <el-descriptions-item label="申请 ID">{{ wdDetail.id }}</el-descriptions-item>
              <el-descriptions-item label="会员">{{ wdDetail.memberName || wdDetail.memberId }}</el-descriptions-item>
              <el-descriptions-item label="范围">{{ scopeLabel(wdDetail.withdrawScope) }}</el-descriptions-item>
              <el-descriptions-item label="原因">
                <div class="reason-text">{{ wdDetail.reason }}</div>
              </el-descriptions-item>
              <el-descriptions-item label="状态">
                <el-tag :type="wdDetail.status === 1 ? 'warning' : wdDetail.status === 2 ? 'success' : 'danger'" size="small">
                  {{ wdDetail.status === 1 ? '待处理' : wdDetail.status === 2 ? '已处理' : '已驳回' }}
                </el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="处理人">{{ wdDetail.handlerName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="处理时间">{{ wdDetail.handleTime || '-' }}</el-descriptions-item>
              <el-descriptions-item label="处理备注">{{ wdDetail.handleRemark || '-' }}</el-descriptions-item>
            </el-descriptions>

            <!-- P0-3: 时间线（提交 → 处理） -->
            <el-divider content-position="left">处理时间线</el-divider>
            <el-timeline class="wd-timeline">
              <el-timeline-item
                :timestamp="wdDetail.createTime"
                type="primary"
                placement="top"
              >
                <div class="tl-title">提交撤回申请</div>
                <div class="tl-sub">申请人已发起撤回</div>
              </el-timeline-item>
              <el-timeline-item
                v-if="wdDetail.status === 1"
                type="warning"
                placement="top"
                :hollow="true"
              >
                <div class="tl-title">等待处理</div>
                <div class="tl-sub">合规人员审批中</div>
              </el-timeline-item>
              <el-timeline-item
                v-else-if="wdDetail.status === 2"
                type="success"
                placement="top"
                :timestamp="wdDetail.handleTime"
              >
                <div class="tl-title">已批准并撤回</div>
                <div class="tl-sub">处理人：{{ wdDetail.handlerName || '-' }}</div>
                <div v-if="wdDetail.handleRemark" class="tl-remark">备注：{{ wdDetail.handleRemark }}</div>
              </el-timeline-item>
              <el-timeline-item
                v-else
                type="danger"
                placement="top"
                :timestamp="wdDetail.handleTime"
              >
                <div class="tl-title">已驳回</div>
                <div class="tl-sub">处理人：{{ wdDetail.handlerName || '-' }}</div>
                <div v-if="wdDetail.handleRemark" class="tl-remark">备注：{{ wdDetail.handleRemark }}</div>
              </el-timeline-item>
            </el-timeline>

            <div v-if="wdDetail.affectedArchiveIds" class="wd-archives">
              <h4>本次撤回将影响的档案（{{ wdDetail.affectedArchiveIds.length }}）</h4>
              <el-tag v-for="id in wdDetail.affectedArchiveIds" :key="id" class="archive-id-tag">#{{ id }}</el-tag>
            </div>

            <div v-if="wdDetail.status === 1" class="wd-actions">
              <el-divider />
              <el-form label-width="80px">
                <el-form-item label="处理备注">
                  <el-input v-model="wdRemark" type="textarea" :rows="3" placeholder="可填处理备注" />
                </el-form-item>
                <el-form-item>
                  <el-button type="success" @click="handle(wdDetail, 2)">批准并撤回</el-button>
                  <el-button type="danger" @click="handle(wdDetail, 3)">驳回</el-button>
                </el-form-item>
              </el-form>
            </div>
          </div>
        </el-drawer>
      </el-tab-pane>
    </el-tabs>

    <!-- ================= 新建档案 ================= -->
    <el-dialog v-model="addVisible" title="新建视觉档案" width="640">
      <el-form v-if="!aiProgress.show" label-width="90px" :model="form">
        <el-form-item label="会员ID" required>
          <el-input v-model.number="form.memberId" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.archiveType" style="width:100%">
            <el-option v-for="t in types" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="部位">
          <el-input v-model="form.bodyPart" placeholder="如：面部 / 左脸颊" />
        </el-form-item>
        <el-form-item label="项目名">
          <el-input v-model="form.projectName" />
        </el-form-item>
        <!-- P1-8: 文件上传 + 实时预览 -->
        <el-form-item label="图像">
          <el-radio-group v-model="uploadMode" style="margin-bottom:8px">
            <el-radio-button value="url">URL</el-radio-button>
            <el-radio-button value="upload">上传</el-radio-button>
          </el-radio-group>
          <el-input v-if="uploadMode === 'url'" v-model="form.imageUrl" placeholder="https://..." />
          <el-upload
            v-else
            :auto-upload="false"
            :show-file-list="false"
            :on-change="onFileChange"
            accept="image/*"
          >
            <el-button>选择图片</el-button>
          </el-upload>
          <div v-if="form.imageUrl" class="preview-box">
            <!-- P1-#7: 脱敏开关影响预览图叠加模糊层，让用户看到脱敏效果 -->
            <div class="preview-frame" :class="{ 'preview-masked': form.requireMask }">
              <el-image :src="form.imageUrl" :preview-src-list="[form.imageUrl]" :preview-teleported="true"
                fit="cover" class="preview-img" />
              <div v-if="form.requireMask" class="mask-overlay">
                <el-icon class="mask-icon"><View /></el-icon>
                <span class="mask-label">脱敏预览</span>
              </div>
            </div>
            <el-button size="small" type="danger" link @click="form.imageUrl = ''">清除</el-button>
          </div>
        </el-form-item>
        <el-form-item label="脱敏">
          <el-switch v-model="form.requireMask" />
        </el-form-item>
        <el-form-item label="立即 AI 分析">
          <el-switch v-model="form.analyzeNow" />
        </el-form-item>
      </el-form>

      <!-- P3-E: AI 异步进度（提交后展示） -->
      <div v-else class="ai-progress-panel">
        <h3 class="ai-progress-title">
          <el-icon class="rotating"><Loading /></el-icon>
          AI 正在分析档案…
        </h3>
        <el-progress
          :percentage="aiProgress.percent"
          :status="aiProgress.percent === 100 ? 'success' : ''"
          :stroke-width="14"
          striped
          striped-flow
        />
        <el-steps :active="aiProgress.step - 1" direction="vertical" class="ai-progress-steps">
          <el-step title="上传档案" :description="`档案编号 ${aiProgress.code || '生成中'}`" />
          <el-step title="AI 特征提取" description="面部/肤质/疤痕等关键点识别" />
          <el-step title="标签与评分生成" description="AI 给出综合评分与建议" />
          <el-step title="完成" description="已写入档案库，可在列表查看" />
        </el-steps>
        <el-alert
          v-if="aiProgress.error"
          :title="aiProgress.error"
          type="error"
          show-icon
          class="ai-progress-alert"
          :closable="false"
        />
      </div>

      <template #footer>
        <el-button @click="addVisible=false">{{ aiProgress.show ? '后台运行' : '取消' }}</el-button>
        <el-button v-if="!aiProgress.show" type="primary" @click="submitAdd" :loading="submitting">提交</el-button>
      </template>
    </el-dialog>

    <!-- P2-10: AI 详情抽屉 -->
    <el-drawer v-model="aiDetailVisible" title="AI 分析详情" size="640px" direction="rtl">
      <div v-if="aiDetail" class="ai-detail">
        <div class="ai-score">
          <el-progress
            type="dashboard"
            :percentage="Number(aiDetail.aiScore || 0)"
            :width="180"
          />
          <div class="ai-score-label">综合评分</div>
        </div>

        <el-divider content-position="left">特征雷达</el-divider>
        <div v-if="aiFeatures.length" class="radar-wrap">
          <div v-for="(f, i) in aiFeatures" :key="i" class="radar-bar">
            <div class="radar-label">{{ f.name }}</div>
            <el-progress :percentage="Number(f.value || 0)" :stroke-width="10" />
          </div>
        </div>
        <el-empty v-else description="暂无特征数据" />

        <el-divider content-position="left">AI 标签</el-divider>
        <div v-if="aiTags.length" class="tag-cloud">
          <el-tag v-for="t in aiTags" :key="t" effect="plain" class="cloud-tag">{{ t }}</el-tag>
        </div>
        <el-empty v-else description="暂无标签" />
      </div>
    </el-drawer>

    <!-- P0-B: 档案详情抽屉 -->
    <el-drawer v-model="detailVisible" title="档案详情" size="520px" direction="rtl">
      <div v-if="detailTarget" class="detail-body">
        <div class="detail-thumb">
          <el-image v-if="detailTarget.imageUrl" :src="detailTarget.imageUrl"
            :preview-src-list="[detailTarget.imageUrl]" :preview-teleported="true"
            fit="cover" class="detail-img" />
          <div v-else class="thumb-error" style="width:100%;height:240px">
            <el-icon><Picture /></el-icon>
            <span>无图像</span>
          </div>
        </div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="档案编号">{{ detailTarget.archiveCode }}</el-descriptions-item>
          <el-descriptions-item label="会员ID">{{ detailTarget.memberId }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ detailTarget.archiveType }}</el-descriptions-item>
          <el-descriptions-item label="部位">{{ detailTarget.bodyPart || '-' }}</el-descriptions-item>
          <el-descriptions-item label="项目">{{ detailTarget.projectName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="AI评分">{{ detailTarget.aiScore ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="已脱敏">
            <el-tag :type="detailTarget.faceMasked ? 'success' : 'info'" size="small">
              {{ detailTarget.faceMasked ? '已脱敏' : '未脱敏' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="detailTarget.revoked ? 'danger' : 'success'" size="small">
              {{ detailTarget.revoked ? '已撤回' : '有效' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间" :span="2">{{ detailTarget.createTime }}</el-descriptions-item>
          <el-descriptions-item label="图像URL" :span="2">
            <el-link v-if="detailTarget.imageUrl" :href="detailTarget.imageUrl" target="_blank" type="primary">
              新窗口打开
            </el-link>
            <span v-else>-</span>
          </el-descriptions-item>
        </el-descriptions>
        <div class="detail-actions">
          <el-button v-if="!detailTarget.revoked" type="danger" @click="revoke(detailTarget); detailVisible = false">
            撤回档案
          </el-button>
          <el-button :icon="Plus" @click="compareWith(detailTarget); detailVisible = false">作为前图对比</el-button>
          <el-button @click="useAsAfter(detailTarget); detailVisible = false">作为后图对比</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import http from '@/utils/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Picture, Sort, Folder, MagicStick, Delete, Warning, Histogram, Printer, View, Bell, Download, Loading } from '@element-plus/icons-vue'
// P2-#8: 按需引入 ECharts（仅折线/柱图）
import * as echarts from 'echarts/core'
import { LineChart, BarChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
echarts.use([LineChart, BarChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])

const tab = ref('archive')
const types = ['FACE','SKIN','BODY','HAIR','SCAR','OTHER']
// P3-A: 部位枚举（与后端约定一致，常用医美部位）
const bodyParts = ['面部','左脸颊','右脸颊','额头','鼻部','唇部','下颌','颈部','前胸','背部','腹部','大腿','小腿','手臂','眼周','其他']

// === 视觉档案 ===
const query = reactive({ memberId: '', archiveType: '', bodyPart: '', projectName: '', revoked: null, aiAnalyzed: null, pageNum: 1, pageSize: 20 })
const dateRange = ref([])
const archives = ref([])
const total = ref(0)
const loading = ref(false)
const selectedIds = ref([])
const selectAll = ref(false)
const isIndeterminate = computed(() => selectedIds.value.length > 0 && selectedIds.value.length < archives.value.length)
// P0-2: 缩略图 ref map，用于「查看」按钮触发预览
const thumbRefs = reactive({})
// ⑫ 批量按钮 loading
const batchAnalyzing = ref(false)
const batchRevoking = ref(false)

const addVisible = ref(false)
const submitting = ref(false)
// P3-E: AI 异步进度面板
const aiProgress = reactive({
  show: false, percent: 0, step: 1, code: '', error: '', timer: null
})
function resetAiProgress() {
  if (aiProgress.timer) { clearInterval(aiProgress.timer); aiProgress.timer = null }
  aiProgress.show = false
  aiProgress.percent = 0
  aiProgress.step = 1
  aiProgress.code = ''
  aiProgress.error = ''
}
const uploadMode = ref('url')
const form = reactive({ memberId: null, archiveType: 'FACE', bodyPart: '', projectName: '', imageUrl: '', requireMask: true, analyzeNow: true })

const kpi = reactive({ total: 0, new30d: 0, todayNew: 0, aiAnalyzed: 0, aiRatio: 0, revoked: 0, pendingWithdraw: 0, bodyParts: {}, expiringSoon: 0 })
const sparklineData = ref([])

// P0-A: 部位分布（用于"数据洞察" Top3 排行 + 动态文案）
const bodyPartData = computed(() => {
  const m = kpi.bodyParts || {}
  const totalCount = Object.values(m).reduce((s, v) => s + Number(v || 0), 0) || 1
  return Object.entries(m)
    .map(([name, value]) => ({ name, value: Number(value), percent: Math.round(Number(value) * 100 / totalCount) }))
    .sort((a, b) => b.value - a.value)
})

// P0-A: 近 7 天趋势
const sparklineRef = ref()
let sparklineChart = null
const sparkline7d = ref([]) // [{date:'08-14', value:3}, ...]
function renderSparkline() {
  if (!sparklineRef.value || !sparkline7d.value.length) return
  if (sparklineChart) { sparklineChart.dispose(); sparklineChart = null }
  sparklineChart = echarts.init(sparklineRef.value)
  sparklineChart.setOption({
    grid: { top: 24, left: 36, right: 16, bottom: 28 },
    tooltip: { trigger: 'axis', confine: true },
    xAxis: {
      type: 'category', data: sparkline7d.value.map(d => d.date),
      axisLine: { lineStyle: { color: '#dcdfe6' } },
      axisLabel: { color: '#909399', fontSize: 11 }
    },
    yAxis: {
      type: 'value', minInterval: 1,
      splitLine: { lineStyle: { color: '#f5f7fa' } },
      axisLabel: { color: '#909399', fontSize: 11 }
    },
    series: [{
      type: 'line', smooth: true, symbol: 'circle', symbolSize: 6,
      data: sparkline7d.value.map(d => d.value),
      lineStyle: { width: 2, color: '#409eff' },
      itemStyle: { color: '#409eff' },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(64,158,255,0.45)' },
          { offset: 1, color: 'rgba(64,158,255,0.04)' }
        ])
      }
    }]
  })
}
watch(sparkline7d, () => nextTick(renderSparkline))
window.addEventListener('resize', () => sparklineChart?.resize())

// P0-A: 动态洞察文案（根据真实数据生成）
const dynamicInsight = computed(() => {
  const top = bodyPartData.value[0]
  if (!top) return '新建档案后将自动生成部位分布建议'
  const ratio = top.percent
  if (ratio >= 60) return `${top.name} 占 ${ratio}%，建议储备该部位的对比案例与话术话本`
  if (ratio >= 30) return `${top.name} 是主力部位（${ratio}%），可为其定制专属营销素材`
  return `部位分布较均衡，TOP1「${top.name}」仅占 ${ratio}%，可尝试差异化宣传其他部位`
})

// AI 详情
const aiDetailVisible = ref(false)
const aiDetail = ref(null)
const aiFeatures = ref([])
const aiTags = ref([])

// === 图像授权 ===
const consentForm = reactive({ memberId: null, consentType: 'AI_ANALYSIS', signerName: '' })
const consentQuery = reactive({ memberId: null, pageNum: 1, pageSize: 10 })
const consents = ref([])
const consentTotal = ref(0)
const consentLoading = ref(false)
// ⑤ 默认折叠
const consentCollapseOpen = ref([])

// === 撤回申请 ===
const wdForm = reactive({ memberId: null, withdrawScope: 'ALL', reason: '' })
// P3-C: 撤回列表筛选（独立于提交表单）
const wdQuery = reactive({ status: null, handlerName: '', pageNum: 1, pageSize: 10 })
const withdraws = ref([])
const wdTotal = ref(0)
const wdLoading = ref(false)
const wdDetailVisible = ref(false)
const wdDetail = ref(null)
const wdRemark = ref('')

// === AI 对比 ===
const compareForm = reactive({ memberId: null, beforeId: null, afterId: null })
const compLoading = ref(false)
const compareResult = ref(null)
const memberArchives = ref([])
const compareBefore = computed(() => memberArchives.value.find(a => a.id === compareForm.beforeId))
const compareAfter = computed(() => memberArchives.value.find(a => a.id === compareForm.afterId))
const sliderPos = ref(50)
let dragging = false

// P2-#10: 批次对比状态
const batchForm = reactive({ memberId: null })
const batchArchives = ref([])
const batchSelected = ref([])
const batchLoading = ref(false)
const batchResult = ref(null)
// montage：[{bucket:'before/after', imageUrl, label, code}]
const batchMontage = computed(() => {
  const set = batchSelected.value
    .map(id => batchArchives.value.find(a => a.id === id))
    .filter(Boolean)
    .sort((a, b) => new Date(a.createTime.replace(/-/g, '/')) - new Date(b.createTime.replace(/-/g, '/')))
  const half = Math.ceil(set.length / 2)
  return set.map((a, i) => ({
    ...a,
    bucket: i < half ? 'before' : 'after',
    label: (i < half ? '前' : '后') + (i < half ? i + 1 : i - half + 1) + ' · ' + a.archiveCode
  }))
})

// === 工具函数 ===
function consentTypeLabel(t) {
  return { STORAGE: '存储', AI_ANALYSIS: 'AI 分析', ANONYMIZED_PUB: '匿名公开' }[t] || t
}
function scopeLabel(s) {
  return { ALL: '全部', AI_TRAINING: '仅AI训练', STAFF_VIEW: '仅员工可见' }[s] || s
}
// P2-#9: 日期工具（兼容多种格式：LocalDateTime / yyyy-MM-dd HH:mm:ss）
function parseDate(s) {
  if (!s) return null
  if (s instanceof Date) return s
  const norm = s.replace(/-/g, '/').replace('T', ' ')
  const d = new Date(norm)
  return isNaN(d.getTime()) ? null : d
}
function formatDateTime(s) {
  const d = parseDate(s)
  if (!d) return ''
  const pad = n => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
function daysToExpire(exp) {
  const d = parseDate(exp)
  if (!d) return Infinity
  const diff = d.getTime() - Date.now()
  return Math.ceil(diff / 86400000)
}
function isExpiringSoon(exp) {
  const n = daysToExpire(exp)
  return n !== Infinity && n <= 7 && n >= 0
}
function onImgError(row) {
  row.__imgError = true
}
// P0-2: 操作列「查看」 → 调对应缩略图 el-image 组件的 showPreview
function viewImage(row) {
  const img = thumbRefs[row.id]
  if (!img) return ElMessage.warning('图片加载失败，请稍后再试')
  if (row.__imgError || !row.imageUrl) return ElMessage.warning('该档案无可用图片')
  img.showPreview()
}

// P3-UI: 操作列下拉统一入口
function onRowCmd(cmd, row) {
  if (cmd === 'view') return viewImage(row)
  if (cmd === 'detail') return openArchiveDetail(row)
  if (cmd === 'before') return compareWith(row)
  if (cmd === 'after') return useAsAfter(row)
  if (cmd === 'reanalyze') return reanalyze(row)
  if (cmd === 'aiDetail') return openAiDetail(row)
  if (cmd === 'revoke') return revoke(row)
}
// P0-B: 档案详情抽屉
const detailVisible = ref(false)
const detailTarget = ref(null)
function openArchiveDetail(row) {
  detailTarget.value = row
  detailVisible.value = true
}
// P0-B: 单条重跑 AI
async function reanalyze(row) {
  try {
    await http.post(`/mh/visual/archive/${row.id}/reanalyze`)
    ElMessage.success('已重新发起分析')
    load()
  } catch (e) { ElMessage.error('发起失败：' + (e?.message || e)) }
}
// AI 评分视觉分级
function scoreClass(s) {
  const n = Number(s) || 0
  if (n >= 80) return 'score-high'
  if (n >= 60) return 'score-mid'
  return 'score-low'
}

// === 加载：档案列表 ===
async function load() {
  loading.value = true
  try {
    const params = { ...query, deleted: 0 }
    // P3-A: bodyPart / projectName 已在 query 中，spread 即透传
    if (dateRange.value && dateRange.value.length === 2) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }
    const { data } = await http.get('/mh/visual/archive/page', { params })
    archives.value = data.records || []
    total.value = data.total || 0
    refreshKpi()
  } finally { loading.value = false }
}

function resetFilter() {
  Object.assign(query, { memberId: '', archiveType: '', revoked: null, aiAnalyzed: null, pageNum: 1, pageSize: 20 })
  dateRange.value = []
  load()
}

function onSelectionChange(rows) {
  selectedIds.value = rows.map(r => r.id)
  selectAll.value = rows.length === archives.value.length && archives.value.length > 0
}
function toggleSelectAll(val) {
  selectedIds.value = val ? archives.value.map(r => r.id) : []
}

async function batchAnalyze() {
  await ElMessageBox.confirm(`对所选 ${selectedIds.value.length} 条档案发起 AI 分析？`, '批量分析', { type: 'info' })
  batchAnalyzing.value = true
  try {
    await http.post('/mh/visual/archive/batch-analyze', { ids: selectedIds.value })
    ElMessage.success('已发起分析')
    load()
  } finally { batchAnalyzing.value = false }
}
// P1-#6: 批量撤回数量上限 + 二次确认
const BATCH_REVOKE_LIMIT = 50
async function batchRevoke() {
  const cnt = selectedIds.value.length
  if (cnt === 0) return ElMessage.warning('请先勾选档案')

  // 上限检查
  if (cnt > BATCH_REVOKE_LIMIT) {
    await ElMessageBox.confirm(
      `当前选中 ${cnt} 条档案，超过单次撤回上限 ${BATCH_REVOKE_LIMIT}。` +
      `大批量撤回可能触发合规告警，建议分批处理。是否仍要继续？`,
      '批量撤回 · 风险提示',
      { type: 'warning', confirmButtonText: '我已知晓，仍要继续', cancelButtonText: '取消' }
    ).catch(() => { return }) // 用户取消
  } else {
    await ElMessageBox.confirm(
      `将对所选 ${cnt} 条档案执行撤回操作，请确认已获得会员授权。`,
      '批量撤回确认',
      { type: 'warning' }
    ).catch(() => { return })
  }

  const { value: reason } = await ElMessageBox.prompt('请输入撤回原因（必填）', '批量撤回', {
    inputValidator: v => v && v.trim() ? true : '撤回原因必填',
    inputErrorMessage: '撤回原因必填',
  })
  batchRevoking.value = true
  try {
    await http.post('/mh/visual/archive/batch-revoke', { ids: selectedIds.value, reason })
    ElMessage.success(`已撤回 ${cnt} 条档案`)
    selectedIds.value = []
    load()
  } finally { batchRevoking.value = false }
}

function openAdd() {
  Object.assign(form, { memberId: null, archiveType: 'FACE', bodyPart: '', projectName: '', imageUrl: '', requireMask: true, analyzeNow: true })
  uploadMode.value = 'url'
  resetAiProgress()
  addVisible.value = true
}
function onFileChange(file) {
  const reader = new FileReader()
  reader.onload = e => { form.imageUrl = e.target.result }
  reader.readAsDataURL(file.raw)
}
async function submitAdd() {
  if (!form.memberId || !form.imageUrl) return ElMessage.warning('会员ID/图像必填')
  submitting.value = true
  try {
    // 1. 先创建档案（同步完成；若 analyzeNow=true，则同步返回带 AI 结果）
    const { data } = await http.post('/mh/visual/archive', {
      memberId: form.memberId, archiveType: form.archiveType,
      bodyPart: form.bodyPart, projectName: form.projectName, imageUrl: form.imageUrl,
      faceMasked: form.requireMask ? 1 : 0, analyzeNow: form.analyzeNow, requireAi: form.analyzeNow
    })

    if (!form.analyzeNow) {
      // 不走 AI 分析：直接关闭 + 刷新
      ElMessage.success('已创建（未分析）')
      addVisible.value = false
      load()
      return
    }

    // 2. 进入异步进度面板（无论后端是否已完成分析，都走 4 段动画以展示反馈）
    aiProgress.show = true
    aiProgress.code = data?.archiveCode || '-'
    addVisible.value = true
    aiProgress.step = 1; aiProgress.percent = 15
    if (aiProgress.timer) clearInterval(aiProgress.timer)
    aiProgress.timer = setInterval(() => {
      // 模拟 4 阶段，每阶段 ~600ms 满；总耗时 2.4s
      if (aiProgress.percent < 35)        { aiProgress.percent = 35; aiProgress.step = 2 }
      else if (aiProgress.percent < 65)   { aiProgress.percent = 65; aiProgress.step = 3 }
      else if (aiProgress.percent < 95)   { aiProgress.percent = 95; aiProgress.step = 4 }
      else {
        aiProgress.percent = 100
        clearInterval(aiProgress.timer)
        aiProgress.timer = null
        ElMessage.success(`AI 分析完成：${aiProgress.code}`)
        // 延迟 600ms 后关闭 + 刷新，给用户看完成态
        setTimeout(() => {
          addVisible.value = false
          resetAiProgress()
          load()
        }, 600)
      }
    }, 600)
  } catch (e) {
    resetAiProgress()
    ElMessage.error('创建失败：' + (e?.message || e))
  } finally {
    submitting.value = false
  }
}

async function revoke(row) {
  const { value: reason } = await ElMessageBox.prompt('撤回该档案，请输入原因', '撤回确认', {
    inputValidator: v => v && v.trim() ? true : '撤回原因必填',
    inputErrorMessage: '撤回原因必填',
  })
  await http.post(`/mh/visual/archive/${row.id}/revoke`, { reason })
  ElMessage.success('已撤回')
  load()
}

function compareWith(row) {
  compareForm.memberId = row.memberId
  compareForm.beforeId = row.id
  tab.value = 'compare'
  ElMessage.success(`已选前图 ${row.archiveCode}，请选择后图`)
  onCompareMemberChange()
}
function useAsAfter(row) {
  compareForm.memberId = row.memberId
  compareForm.afterId = row.id
  tab.value = 'compare'
  onCompareMemberChange()
}

async function onCompareMemberChange() {
  if (!compareForm.memberId) { memberArchives.value = []; return }
  const { data } = await http.get('/mh/visual/archive/page', {
    params: { memberId: compareForm.memberId, pageSize: 50 }
  })
  memberArchives.value = data.records || []
}

async function doCompare() {
  if (!compareForm.memberId || !compareForm.beforeId || !compareForm.afterId)
    return ElMessage.warning('请填写会员ID/前图/后图')
  if (compareForm.beforeId === compareForm.afterId)
    return ElMessage.warning('前后图不能相同')
  compLoading.value = true
  try {
    const { data } = await http.post('/mh/visual/compare', compareForm)
    compareResult.value = data
    sliderPos.value = 50
  } finally { compLoading.value = false }
}

// === P3-B: 导出当前页/筛选结果为 CSV ===
function exportCsv() {
  if (!archives.value.length) return ElMessage.warning('当前无可导出数据')
  // 仅导出当前页（避免大数据量请求阻塞；如需全量可后续扩展）
  const rows = archives.value
  const headers = ['档案编号','类型','部位','项目','会员ID','AI分析','AI评分','已脱敏','已撤回','创建时间','操作人']
  const esc = v => {
    const s = v == null ? '' : String(v)
    // 含逗号/引号/换行的字段加双引号转义
    return /[",\n\r]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
  }
  const body = rows.map(r => [
    r.archiveCode, r.archiveType, r.bodyPart, r.projectName, r.memberId,
    r.aiAnalyzed ? '已分析' : '未分析', r.aiScore ?? '',
    r.faceMasked ? '是' : '否', r.revoked ? '是' : '否',
    r.createTime, r.operatorName
  ].map(esc).join(','))
  // 防止 Excel 把 UTF-8 CSV 当 GBK 看乱码
  const bom = '\uFEFF'
  const csv = bom + [headers.join(','), ...body].join('\n')
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `视觉档案_${new Date().toISOString().slice(0,10)}.csv`
  document.body.appendChild(a); a.click(); a.remove()
  URL.revokeObjectURL(url)
  ElMessage.success(`已导出 ${rows.length} 条`)
}

// === P2-#10: 批次对比 ===
async function loadBatchArchives() {
  if (!batchForm.memberId) { batchArchives.value = []; return }
  const { data } = await http.get('/mh/visual/archive/page', {
    params: { memberId: batchForm.memberId, pageSize: 50 }
  })
  batchArchives.value = data.records || []
  batchSelected.value = []
  batchResult.value = null
}
async function runBatchCompare() {
  if (batchSelected.value.length !== 4) return ElMessage.warning('请选择 4 张档案')
  const sorted = batchSelected.value
    .map(id => batchArchives.value.find(a => a.id === id))
    .filter(Boolean)
    .sort((a, b) => new Date(a.createTime.replace(/-/g, '/')) - new Date(b.createTime.replace(/-/g, '/')))
  // 早期1 张 vs 最近1 张，覆盖面更大
  const before = sorted[0]
  const after = sorted[3]
  batchLoading.value = true
  try {
    const { data } = await http.post('/mh/visual/compare', {
      memberId: batchForm.memberId, beforeId: before.id, afterId: after.id
    })
    batchResult.value = data
    ElMessage.success(`已完成批次对比：${before.archiveCode} ↔ ${after.archiveCode}`)
  } finally { batchLoading.value = false }
}

// === ⑨ 导出 / 复制对比报告 ===
async function copyCompareReport() {
  if (!compareResult.value) return
  const b = compareBefore.value, a = compareAfter.value
  const txt = [
    '【视觉档案 AI 对比报告】',
    `会员: ${compareForm.memberId}`,
    `前图: ${b?.archiveCode} (${b?.archiveType} · ${b?.bodyPart})`,
    `后图: ${a?.archiveCode} (${a?.archiveType} · ${a?.bodyPart})`,
    `相似度: ${compareResult.value.similarity}`,
    `差异分: ${compareResult.value.diffScore}`,
    `改善项: ${compareResult.value.improvementAreas}`,
    `退步项: ${compareResult.value.regressionAreas}`,
    `AI 总结: ${compareResult.value.aiSummary}`,
    `生成时间: ${new Date().toLocaleString()}`,
  ].join('\n')
  try {
    await navigator.clipboard.writeText(txt)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.warning('复制失败，请手动选择')
  }
}
function printCompareReport() {
  if (!compareResult.value) return
  const b = compareBefore.value, a = compareAfter.value
  const html = `<!doctype html><html><head><meta charset="utf-8"><title>AI 对比报告</title>
    <style>body{font-family:'Microsoft YaHei',sans-serif;padding:32px;color:#333}
    h1{font-size:22px;border-bottom:2px solid #409eff;padding-bottom:8px}
    .row{display:flex;gap:16px;margin:16px 0}
    .col{flex:1;text-align:center}
    img{max-width:100%;max-height:300px;border:1px solid #eee}
    table{width:100%;border-collapse:collapse;margin-top:16px}
    td,th{border:1px solid #ddd;padding:8px 12px;text-align:left}
    th{background:#f5f7fa;width:120px}</style></head><body>
    <h1>视觉档案 AI 对比报告</h1>
    <p>会员: ${compareForm.memberId}　生成时间: ${new Date().toLocaleString()}</p>
    <div class="row">
      <div class="col"><h3>前图</h3><img src="${b?.imageUrl || ''}"/><p>${b?.archiveCode} · ${b?.bodyPart || ''}</p></div>
      <div class="col"><h3>后图</h3><img src="${a?.imageUrl || ''}"/><p>${a?.archiveCode} · ${a?.bodyPart || ''}</p></div>
    </div>
    <table>
      <tr><th>相似度</th><td>${compareResult.value.similarity}</td><th>差异分</th><td>${compareResult.value.diffScore}</td></tr>
      <tr><th>改善项</th><td colspan="3">${compareResult.value.improvementAreas}</td></tr>
      <tr><th>退步项</th><td colspan="3">${compareResult.value.regressionAreas}</td></tr>
      <tr><th>AI 总结</th><td colspan="3">${compareResult.value.aiSummary}</td></tr>
    </table></body></html>`
  const w = window.open('', '_blank')
  w.document.write(html)
  w.document.close()
  w.focus()
  setTimeout(() => w.print(), 300)
}

// === P3-D: 下载 Markdown 报告 ===
function buildMdReport() {
  const b = compareBefore.value, a = compareAfter.value
  const r = compareResult.value
  if (!r) return ''
  return [
    '# 视觉档案 AI 对比报告',
    '',
    `- **会员**: ${compareForm.memberId}`,
    `- **生成时间**: ${new Date().toLocaleString()}`,
    '',
    '## 档案信息',
    '',
    `| 项目 | 前图 | 后图 |`,
    `| --- | --- | --- |`,
    `| 档案编号 | ${b?.archiveCode || '-'} | ${a?.archiveCode || '-'} |`,
    `| 类型 | ${b?.archiveType || '-'} | ${a?.archiveType || '-'} |`,
    `| 部位 | ${b?.bodyPart || '-'} | ${a?.bodyPart || '-'} |`,
    `| 创建时间 | ${b?.createTime || '-'} | ${a?.createTime || '-'} |`,
    '',
    '## AI 评估',
    '',
    `| 指标 | 值 |`,
    `| --- | --- |`,
    `| 相似度 | ${r.similarity || 0}% |`,
    `| 差异分 | ${r.diffScore || 0} |`,
    '',
    '## 改善项',
    r.improvementAreas || '-',
    '',
    '## 退步项',
    r.regressionAreas || '-',
    '',
    '## AI 总结',
    r.aiSummary || '-',
    '',
    '---',
    '*本报告由视觉档案 AI 系统自动生成*'
  ].join('\n')
}
function downloadMdReport() {
  if (!compareResult.value) return ElMessage.warning('请先完成 AI 对比')
  const md = buildMdReport()
  downloadFile(md, 'text/markdown', `AI对比报告_${compareForm.memberId || 'unknown'}_${Date.now()}.md`)
  ElMessage.success('已下载 Markdown 报告')
}
function downloadHtmlReport() {
  if (!compareResult.value) return ElMessage.warning('请先完成 AI 对比')
  const b = compareBefore.value, a = compareAfter.value
  const r = compareResult.value
  // 将 Markdown 转简易 HTML（用 <pre> 排版 + 简单标题替换）
  const md = buildMdReport()
  const html = `<!doctype html><html><head><meta charset="utf-8"><title>AI 对比报告</title>
    <style>body{font-family:'Microsoft YaHei',sans-serif;padding:32px;color:#333;max-width:920px;margin:0 auto;line-height:1.7}
    h1{font-size:24px;border-bottom:2px solid #409eff;padding-bottom:8px}
    h2{font-size:18px;color:#409eff;margin-top:24px}
    table{border-collapse:collapse;margin:8px 0}
    td,th{border:1px solid #ddd;padding:6px 12px;text-align:left}
    th{background:#f5f7fa}
    pre{background:#f8f9fb;padding:16px;border-radius:6px;overflow:auto}
    hr{border:none;border-top:1px dashed #ddd;margin-top:24px}
    img{max-width:300px;border:1px solid #eee;margin:4px}</style></head><body>
    <pre>${md.replace(/[<>&]/g, c => ({'<':'&lt;','>':'&gt;','&':'&amp;'}[c]))}</pre>
    <div class="row">
      <img src="${b?.imageUrl || ''}" alt="前图"/>
      <img src="${a?.imageUrl || ''}" alt="后图"/>
    </div></body></html>`
  downloadFile(html, 'text/html;charset=utf-8', `AI对比报告_${compareForm.memberId || 'unknown'}_${Date.now()}.html`)
  ElMessage.success('已下载 HTML 报告')
}
function downloadFile(content, mime, filename) {
  const blob = new Blob([content], { type: mime })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = filename
  document.body.appendChild(a); a.click(); a.remove()
  URL.revokeObjectURL(url)
}

// === 拖拽 slider ===
// === 拖拽 slider：桌面 + 移动端均支持 ===
function startDrag(e) {
  dragging = true
  // P1-#5: 阻止默认（避免 touchmove 触发页面滚动 / pull-to-refresh）
  if (e.cancelable) e.preventDefault()
  onDrag(e)
}
function onDrag(e) {
  if (!dragging) return
  const img = document.querySelector('.cmp-img-wrap')
  if (!img) return
  const rect = img.getBoundingClientRect()
  // 兼容 mouse / touch / pointer
  const clientX = e.touches
    ? e.touches[0].clientX
    : (e.clientX ?? 0)
  let pct = ((clientX - rect.left) / rect.width) * 100
  pct = Math.max(0, Math.min(100, pct))
  sliderPos.value = pct
}
function endDrag() { dragging = false }

// === 加载：KPI（后端聚合 + 前端 fallback） ===
const kpiLoading = ref(false)
async function refreshKpi() {
  kpiLoading.value = true
  try {
    // P1-#4: 优先用后端聚合，覆盖翻页不可见的真实数据
    const { data } = await http.get('/mh/visual/archive/stats')
    kpi.total = data.total ?? 0
    kpi.new30d = data.new30d ?? 0
    kpi.todayNew = data.todayNew ?? 0
    kpi.aiAnalyzed = data.aiAnalyzed ?? 0
    kpi.aiRatio = data.aiRatio ?? 0
    kpi.revoked = data.revoked ?? 0
    kpi.pendingWithdraw = data.pendingWithdraw ?? 0
    kpi.bodyParts = data.bodyParts || {}
    // P0-A: 接收 7 天趋势
    if (Array.isArray(data.trend7d)) sparkline7d.value = data.trend7d
    // P2-#9: 后端聚合即将过期授权
    try {
      const { data: exp } = await http.get('/mh/visual/consent/expiring')
      kpi.expiringSoon = exp.expiringSoon ?? 0
    } catch { /* 接口不存在时忽略，不影响 KPI 主面板 */ }
  } catch (e) {
    // fallback：分页数据聚合（兼容旧版本 / 接口失败）
    const rows = archives.value
    kpi.total = total.value || rows.length
    const now = Date.now()
    const cutoff30 = now - 30 * 86400000
    const cutoff24 = now - 86400000
    kpi.new30d = rows.filter(r => {
      const t = r.createTime ? new Date(r.createTime.replace(/-/g, '/')).getTime() : 0
      return t >= cutoff30
    }).length
    kpi.todayNew = rows.filter(r => {
      const t = r.createTime ? new Date(r.createTime.replace(/-/g, '/')).getTime() : 0
      return t >= cutoff24
    }).length
    kpi.aiAnalyzed = rows.filter(r => r.aiAnalyzed).length
    kpi.aiRatio = kpi.total ? Math.round(kpi.aiAnalyzed * 100 / kpi.total) : 0
    kpi.revoked = rows.filter(r => r.revoked).length
    kpi.pendingWithdraw = withdraws.value.filter(r => r.status === 1).length
    kpi.bodyParts = kpi.bodyParts || {}
    // fallback 也拼一份 7 天趋势（按全量 rows 推算）
    const buckets = {}
    for (let i = 6; i >= 0; i--) {
      const k = new Date(now - i * 86400000).toISOString().slice(5, 10)
      buckets[k] = 0
    }
    rows.forEach(r => {
      const t = r.createTime ? new Date(r.createTime.replace(/-/g, '/')).getTime() : 0
      if (!t || isNaN(t)) return
      const k = new Date(t).toISOString().slice(5, 10)
      if (k in buckets) buckets[k]++
    })
    sparkline7d.value = Object.entries(buckets).map(([date, value]) => ({ date, value }))
  } finally {
    kpiLoading.value = false
  }
}

// === AI 详情 ===
function openAiDetail(row) {
  aiDetail.value = row
  aiFeatures.value = []
  aiTags.value = []
  try {
    if (row.aiFeaturesJson) aiFeatures.value = JSON.parse(row.aiFeaturesJson)
  } catch (e) { aiFeatures.value = [] }
  aiTags.value = row.aiTags ? row.aiTags.split(',').filter(Boolean) : []
  aiDetailVisible.value = true
}

// === 图像授权 ===
async function signConsent() {
  if (!consentForm.memberId || !consentForm.signerName) return ElMessage.warning('会员/签署人必填')
  await http.post('/mh/visual/consent', consentForm)
  ElMessage.success('已签署')
  loadConsents()
}
async function loadConsents() {
  if (!consentQuery.memberId) { consents.value = []; consentTotal.value = 0; recomputeExpiring(); return }
  consentLoading.value = true
  try {
    const { data } = await http.get('/mh/visual/consent/page', { params: consentQuery })
    consents.value = data.records || []
    consentTotal.value = data.total || 0
    recomputeExpiring()
  } finally { consentLoading.value = false }
}
// P2-#9: 7天内过期的有效授权数
function recomputeExpiring() {
  kpi.expiringSoon = consents.value.filter(c => c.status === 1 && isExpiringSoon(c.expireAt)).length
}

// === 撤回申请 ===
async function applyWithdraw() {
  if (!wdForm.memberId) return ElMessage.warning('会员ID必填')
  if (!wdForm.reason || !wdForm.reason.trim()) return ElMessage.warning('撤回原因必填')
  await http.post('/mh/visual/withdraw', { ...wdForm, reason: wdForm.reason.trim() })
  ElMessage.success('已提交')
  wdForm.reason = ''
  loadWithdraws()
}
const wdDateRange = ref([])
async function loadWithdraws() {
  wdLoading.value = true
  try {
    const params = { ...wdQuery }
    if (wdDateRange.value && wdDateRange.value.length === 2) {
      params.startDate = wdDateRange.value[0]
      params.endDate = wdDateRange.value[1]
    }
    // 默认仅看待处理（保持旧行为兼容 KPI）
    if (params.status == null || params.status === '') delete params.status
    const { data } = await http.get('/mh/visual/withdraw/page', { params })
    withdraws.value = data.records || []
    wdTotal.value = data.total || 0
    refreshKpi()
  } finally { wdLoading.value = false }
}
function resetWdQuery() {
  wdQuery.status = null
  wdQuery.handlerName = ''
  wdQuery.pageNum = 1
  wdDateRange.value = []
  loadWithdraws()
}
function openWdDetail(row) {
  wdDetail.value = row
  wdRemark.value = ''
  wdDetailVisible.value = true
}
async function handle(row, status) {
  const action = status === 2 ? '批准并撤回' : '驳回'
  try {
    await ElMessageBox.confirm(`确认${action}该申请？`, '提示', { type: 'warning' })
  } catch { return }
  await http.post(`/mh/visual/withdraw/${row.id}/handle`, {
    status, handlerName: 'admin', remark: wdRemark.value || (status === 2 ? '已批准' : '已驳回')
  })
  ElMessage.success(action + '成功')
  wdDetailVisible.value = false
  loadWithdraws()
  if (status === 2) load()
}

onMounted(() => {
  load()
  loadWithdraws()
  loadConsents()
  // 拖拽全局监听（P1-#5：touchmove 必须 passive:false 才能 preventDefault）
  window.addEventListener('mousemove', onDrag)
  window.addEventListener('mouseup', endDrag)
  window.addEventListener('touchmove', onDrag, { passive: false })
  window.addEventListener('touchend', endDrag)
  nextTick(() => {
    const handle = document.querySelector('.cmp-slider')
    if (handle) {
      handle.addEventListener('mousedown', startDrag)
      handle.addEventListener('touchstart', startDrag, { passive: false })
    }
  })
})

// P2-#8: 清理 echarts 实例 + resize 监听
onBeforeUnmount(() => {
  sparklineChart?.dispose()
  sparklineChart = null
})
</script>

<style scoped>
.page-visual { padding: 16px; }
.visual-tabs { margin-top: 12px; }

/* KPI */
.kpi-row { margin-bottom: 16px; }
.kpi-card {
  position: relative; overflow: hidden;
  display: flex; align-items: center; gap: 12px;
  border: none;
}
.kpi-card::before {
  content: ''; position: absolute; left: 0; top: 0; bottom: 0; width: 4px;
}
.kpi-blue::before   { background: #409eff; }
.kpi-green::before  { background: #67c23a; }
.kpi-purple::before { background: #7c3aed; }
.kpi-red::before    { background: #f56c6c; }
.kpi-orange::before { background: #e6a23c; }
.kpi-mauve::before  { background: #b37feb; }
.kpi-card.kpi-blue   { background: linear-gradient(135deg, #ecf5ff 0%, #fff 70%); }
.kpi-card.kpi-green  { background: linear-gradient(135deg, #f0f9eb 0%, #fff 70%); }
.kpi-card.kpi-purple { background: linear-gradient(135deg, #f5f0ff 0%, #fff 70%); }
.kpi-card.kpi-red    { background: linear-gradient(135deg, #fef0f0 0%, #fff 70%); }
.kpi-card.kpi-orange { background: linear-gradient(135deg, #fdf6ec 0%, #fff 70%); }
.kpi-card.kpi-mauve  { background: linear-gradient(135deg, #f9f0ff 0%, #fff 70%); }
.kpi-icon {
  font-size: 32px; opacity: 0.85; padding: 0 4px;
}
.kpi-blue   .kpi-icon { color: #409eff; }
.kpi-purple .kpi-icon { color: #7c3aed; }
.kpi-red    .kpi-icon { color: #f56c6c; }
.kpi-orange .kpi-icon { color: #e6a23c; }
.kpi-mauve  .kpi-icon { color: #b37feb; }
.kpi-body { flex: 1; min-width: 0; }
.kpi-label { color: #606266; font-size: 13px; }
.kpi-value { font-size: 26px; font-weight: 700; margin: 4px 0; color: #303133; line-height: 1.2; }
.kpi-value.danger { color: #f56c6c; }
.kpi-value.warn { color: #e6a23c; }
.kpi-extra { color: #909399; font-size: 12px; }
.kpi-extra-hint { cursor: help; border-bottom: 1px dashed #c0c4cc; }
.kpi-warn { box-shadow: 0 0 0 2px rgba(230, 162, 60, 0.25); }

/* 工具栏 */
.filter-bar { margin-bottom: 12px; }
.filter-bar :deep(.el-form-item) { margin-right: 12px; margin-bottom: 0; }
.filter-bar :deep(.el-form-item__label) { padding-right: 6px; }
.consent-collapse { margin-top: 8px; border-top: 1px solid #ebeef5; padding-top: 8px; }
.consent-collapse :deep(.el-collapse-item__header) {
  font-weight: 600; color: #606266; padding-left: 8px;
}
.batch-bar {
  background: #f5f7fa; padding: 8px 12px; border-radius: 4px;
  margin-bottom: 12px; display: flex; align-items: center; gap: 12px;
}
.select-tip { color: #606266; font-size: 13px; }

/* P2-#8: 部位分布图 */
.chart-card { margin-bottom: 16px; }
.sparkline-chart { width: 100%; height: 220px; }
.chart-card-header {
  display: flex; align-items: center; gap: 12px;
}
.chart-title { font-weight: 600; color: #303133; }
.chart-sub { color: #909399; font-size: 12px; }
.chart-tag { margin-left: auto; }
.body-part-chart { width: 100%; height: 320px; }
.chart-side {
  height: 100%; display: flex; flex-direction: column; gap: 12px;
  padding-bottom: 16px;
}
.side-title { font-weight: 600; color: #303133; font-size: 14px; }
.side-body { display: flex; flex-direction: column; gap: 10px; }
.side-row { display: flex; flex-direction: column; gap: 2px; }
.side-name { color: #606266; font-size: 13px; }
.side-empty { color: #c0c4cc; font-size: 13px; padding: 24px 0; text-align: center; }
/* P0-A: 动态洞察提示 */
.dynamic-tip {
  margin-top: 8px; padding: 6px 8px;
  background: #fdf6ec; color: #e6a23c;
  border-radius: 4px; font-size: 12px; line-height: 1.6;
}

/* P0-A: 找细分层 .side-tip 保持背景 */
.side-tip {
  margin-top: auto; padding: 8px 10px;
  background: #f0f9eb; color: #67c23a;
  border-radius: 6px; font-size: 12px; line-height: 1.6;
}

/* P0-1: 空状态引导 */
.archive-empty { padding: 32px 0 48px; }
.empty-illu {
  width: 120px; height: 120px; margin: 0 auto;
  display: flex; align-items: center; justify-content: center;
  font-size: 64px; color: #409eff;
  background: linear-gradient(135deg, #ecf5ff 0%, #f5f0ff 100%);
  border-radius: 50%; box-shadow: 0 8px 24px rgba(64, 158, 255, 0.15);
}
.empty-title { margin: 16px 0 4px; font-size: 18px; color: #303133; font-weight: 600; }
.empty-sub { margin: 0 0 20px; color: #909399; font-size: 13px; }
.empty-steps {
  display: inline-flex; gap: 12px; margin-bottom: 20px;
  flex-wrap: wrap; justify-content: center;
}
.empty-steps .step {
  display: flex; align-items: center; gap: 6px;
  background: #f5f7fa; padding: 6px 12px; border-radius: 16px;
  font-size: 13px; color: #606266;
}
.empty-steps .step-num {
  width: 20px; height: 20px; border-radius: 50%;
  background: #409eff; color: #fff;
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 600;
}
.empty-cta { box-shadow: 0 4px 12px rgba(103, 194, 58, 0.3); }

/* 缩略图 */
.thumb-wrap {
  position: relative; width: 80px; height: 80px;
}
.thumb {
  width: 80px; height: 80px; border-radius: 6px;
  background: #f5f7fa;
}
.thumb-error {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  width: 80px; height: 80px; color: #c0c4cc; background: #f5f7fa; border-radius: 6px;
  font-size: 12px;
}
.mask-badge {
  position: absolute; top: -4px; right: -4px;
}

/* AI 对比空状态引导 */
.cmp-empty-icon {
  width: 96px; height: 96px; margin: 0 auto;
  display: flex; align-items: center; justify-content: center;
  font-size: 56px; color: #409eff;
  background: #ecf5ff; border-radius: 50%;
}
.cmp-empty-steps {
  margin-top: 12px; display: inline-flex; gap: 12px;
  color: #606266; font-size: 13px;
}
.cmp-empty-steps .step {
  display: flex; align-items: center; gap: 6px;
  background: #f5f7fa; padding: 6px 12px; border-radius: 16px;
}
.step-num {
  width: 20px; height: 20px; border-radius: 50%;
  background: #409eff; color: #fff;
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 600;
}
.cmp-result-header {
  display: flex; justify-content: space-between; align-items: center;
}
.compare-canvas { margin: 16px 0; }
.cmp-img-wrap {
  position: relative; width: 100%; max-width: 720px; margin: 0 auto;
  user-select: none; border-radius: 8px; overflow: hidden;
  background: #000;
  /* P1-#5: 关闭浏览器默认 touch-action，避免移动端拖动 slider 时页面滚动 */
  touch-action: none;
}
.cmp-img { display: block; width: 100%; height: auto; max-height: 480px; object-fit: contain; }
.cmp-overlay {
  position: absolute; top: 0; left: 0; height: 100%;
  overflow: hidden; width: 50%;
}
.cmp-overlay img { width: 100vw; max-width: 720px; height: 100%; object-fit: contain; }
.cmp-slider {
  position: absolute; top: 0; bottom: 0; left: 50%;
  transform: translateX(-50%); cursor: ew-resize;
  display: flex; align-items: center; justify-content: center;
  width: 2px;
}
.cmp-slider-line {
  position: absolute; top: 0; bottom: 0; left: 50%;
  width: 2px; background: #fff; transform: translateX(-50%);
  box-shadow: 0 0 8px rgba(0,0,0,0.5);
}
.cmp-slider-handle {
  position: relative; z-index: 2;
  width: 36px; height: 36px; border-radius: 50%;
  background: #fff; box-shadow: 0 2px 8px rgba(0,0,0,0.3);
  display: flex; align-items: center; justify-content: center;
  color: #409eff;
}
.cmp-label {
  position: absolute; top: 12px; color: #fff;
  background: rgba(0,0,0,0.6); padding: 4px 10px;
  border-radius: 12px; font-size: 12px;
}
.cmp-label-left { left: 12px; }
.cmp-label-right { right: 12px; }
.cmp-meta {
  margin-top: 8px; display: flex; gap: 24px; justify-content: center;
  color: #606266; font-size: 13px;
}
.cmp-result { margin-top: 16px; }

/* 预览 */
.preview-box {
  margin-top: 8px; display: flex; align-items: center; gap: 12px;
}
.preview-img {
  width: 120px; height: 120px; border-radius: 6px; border: 1px solid #ebeef5;
}
/* P1-#7: 脱敏预览叠加层 */
.preview-frame {
  position: relative; width: 120px; height: 120px; border-radius: 6px; overflow: hidden;
}
.preview-frame.preview-masked .preview-img :deep(img) {
  filter: blur(16px) saturate(1.4);
  transform: scale(1.1); /* 避免 blur 后露出边缘 */
}
.mask-overlay {
  position: absolute; inset: 0;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  background: rgba(64, 158, 255, 0.15);
  color: #fff; font-size: 12px; gap: 4px;
  pointer-events: none;
}
.mask-icon { font-size: 22px; }
.mask-label {
  background: rgba(0, 0, 0, 0.55); padding: 2px 8px;
  border-radius: 10px; font-size: 11px; letter-spacing: 1px;
}

/* 撤回详情 */
.wd-detail .wd-archives { margin-top: 16px; }
.wd-detail .wd-archives h4 { margin: 0 0 8px 0; font-size: 13px; color: #606266; }
.archive-id-tag { margin: 2px 4px 2px 0; }
.wd-actions { margin-top: 16px; }
.reason-text {
  max-height: 96px; overflow-y: auto;
  white-space: pre-wrap; word-break: break-all;
  padding: 6px 8px; background: #f5f7fa; border-radius: 4px;
  font-size: 13px; line-height: 1.6;
}

/* P3-UI: 表格内 AI 分析单元格（标签 + 详情按钮） */
.ai-cell {
  display: inline-flex; align-items: center; gap: 6px;
}
.ai-score { font-weight: 600; }
.ai-score.score-high { color: #67c23a; }
.ai-score.score-mid  { color: #e6a23c; }
.ai-score.score-low  { color: #909399; }

/* P3-E: AI 异步进度面板 */
.ai-progress-panel { padding: 24px 16px; }
.ai-progress-title {
  display: flex; align-items: center; gap: 8px;
  font-size: 15px; color: #303133; margin-bottom: 16px; font-weight: 600;
}
.rotating { animation: ai-rot 1.2s linear infinite; color: #409eff; }
@keyframes ai-rot { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
.ai-progress-steps { margin-top: 24px; }
.ai-progress-alert { margin-top: 16px; }

/* P3-C: 撤回筛选条 */
.wd-filter {
  margin-top: 4px; padding-top: 8px;
  border-top: 1px dashed #ebeef5;
}

/* P2-#10: 批次对比（2x2 montage） */
.batch-card { margin-top: 24px; border: 1px dashed #d9d9d9; }
.batch-header { display: flex; align-items: center; justify-content: space-between; }
.batch-title { font-weight: 600; color: #303133; margin-right: 8px; }
.batch-sub { color: #909399; font-size: 12px; }
.montage-grid {
  display: grid; grid-template-columns: 1fr 1fr; gap: 8px;
  margin-top: 8px; max-width: 720px; margin-left: auto; margin-right: auto;
}
.montage-cell {
  position: relative; aspect-ratio: 1 / 1; overflow: hidden;
  border-radius: 6px; background: #f5f7fa;
}
.montage-img { width: 100%; height: 100%; object-fit: cover; }
.montage-tag {
  position: absolute; left: 8px; bottom: 8px;
  padding: 4px 10px; border-radius: 12px; font-size: 11px;
  color: #fff; backdrop-filter: blur(4px); max-width: calc(100% - 16px);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.montage-tag.before { background: rgba(64, 158, 255, 0.85); }
.montage-tag.after  { background: rgba(245, 108, 108, 0.85); }
.batch-result { margin-top: 16px; }

/* P2-#9: consent 即将过期高亮 */
.expire-soon { color: #f56c6c; font-weight: 600; }
.expire-done { color: #909399; text-decoration: line-through; }

/* P0-3: 撤回详情时间线 */
.wd-timeline { padding: 8px 0 16px; }
.wd-timeline .tl-title { font-weight: 600; color: #303133; font-size: 14px; }
.wd-timeline .tl-sub { color: #909399; font-size: 12px; margin-top: 2px; }
.wd-timeline .tl-remark {
  margin-top: 4px; padding: 4px 8px;
  background: #f5f7fa; border-radius: 4px;
  font-size: 12px; color: #606266;
}

/* AI 详情 */
.ai-detail { padding: 0 16px; }
.ai-score { text-align: center; padding: 16px 0; }
.ai-score-label { margin-top: 8px; color: #909399; font-size: 12px; }
.radar-wrap { padding: 8px 0; }
.radar-bar { margin: 8px 0; }
.radar-label { color: #606266; font-size: 13px; margin-bottom: 4px; }
.tag-cloud { display: flex; flex-wrap: wrap; gap: 8px; }
.cloud-tag { margin: 0; }

/* P0-B: 档案详情抽屉 */
.detail-body { padding: 0 16px 16px; }
.detail-thumb { text-align: center; margin-bottom: 16px; }
.detail-img { width: 100%; max-height: 280px; border-radius: 6px; }
.detail-actions { margin-top: 16px; display: flex; gap: 8px; flex-wrap: wrap; }

/* 分页 */
.pager { margin-top: 16px; text-align: right; }
</style>
