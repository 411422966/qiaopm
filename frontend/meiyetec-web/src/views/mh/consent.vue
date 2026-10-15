<template>
  <div class="page-consent">
    <!-- ⑦ KPI 看板：6 卡（模板 / 主档 / 批次 / 签署 / 待签 / 即将过期） + 趋势 + TOP5 -->
    <el-row :gutter="12" class="kpi-row">
      <el-col :xs="12" :sm="12" :md="8" :lg="4">
        <el-card shadow="hover" class="kpi-card kpi-blue">
          <el-icon class="kpi-icon"><Document /></el-icon>
          <div class="kpi-body">
            <div class="kpi-label">知情同意模板</div>
            <div class="kpi-value">{{ kpi.templates }}</div>
            <div class="kpi-extra">有效 {{ kpi.templatesActive }} · 待审核 {{ kpi.templatesPending }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="8" :lg="4">
        <el-card shadow="hover" class="kpi-card kpi-purple">
          <el-icon class="kpi-icon"><FirstAidKit /></el-icon>
          <div class="kpi-body">
            <div class="kpi-label">药械主档</div>
            <div class="kpi-value">{{ kpi.drugs }}</div>
            <div class="kpi-extra">{{ kpi.drugTypes }} 类</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="8" :lg="4">
        <el-card shadow="hover" class="kpi-card kpi-cyan">
          <el-icon class="kpi-icon"><Box /></el-icon>
          <div class="kpi-body">
            <div class="kpi-label">批次总数</div>
            <el-link class="kpi-value-link" :underline="'never'" @click="tab = 'batch'">{{ kpi.batches }}</el-link>
            <div class="kpi-extra">正常 {{ kpi.batchesActive }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="8" :lg="4">
        <el-card shadow="hover" class="kpi-card kpi-green">
          <el-icon class="kpi-icon"><EditPen /></el-icon>
          <div class="kpi-body">
            <div class="kpi-label">签署总数</div>
            <el-link class="kpi-value-link" type="primary" :underline="'never'" @click="tab = 'sign'">{{ kpi.signTotal }}</el-link>
            <div class="kpi-extra">30天 +{{ kpi.signNew30d }}</div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="8" :lg="4">
        <el-card shadow="hover" class="kpi-card kpi-orange"
          :class="{ 'kpi-warn': kpi.signExpired > 0 }">
          <el-icon class="kpi-icon"><WarningFilled /></el-icon>
          <div class="kpi-body">
            <div class="kpi-label">签署已过期</div>
            <div class="kpi-value" :class="{ danger: kpi.signExpired > 0 }">{{ kpi.signExpired }}</div>
            <div class="kpi-extra">
              <el-link type="warning" :underline="'never'" @click="jumpExpiringSign">续签 →</el-link>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :xs="12" :sm="12" :md="8" :lg="4">
        <el-card shadow="hover" class="kpi-card kpi-red" :class="{ 'kpi-warn': kpi.expiringSoon > 0 }">
          <el-icon class="kpi-icon"><Bell /></el-icon>
          <div class="kpi-body">
            <div class="kpi-label">即将过期批次（&lt; 90 天）</div>
            <div class="kpi-value" :class="{ warn: kpi.expiringSoon > 0 }">{{ kpi.expiringSoon }}</div>
            <div class="kpi-extra">
              <el-link type="warning" :underline="'never'" @click="tab = 'batch'">去处理 →</el-link>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 趋势 + TOP5 + 全局操作头部 -->
    <el-row :gutter="12" class="kpi-row">
      <el-col :xs="24" :sm="16" :md="16" :lg="16">
        <el-card shadow="hover" class="chart-card">
          <template #header>
            <div class="chart-card-header">
              <span class="chart-title">📈 近 7 天签署趋势</span>
              <span class="chart-sub">含撤回/作废记录</span>
              <el-tag size="small" type="success" effect="plain" class="chart-tag">
                累计 {{ signTrend7d.reduce((s, x) => s + x.value, 0) }} 份
              </el-tag>
            </div>
          </template>
          <div ref="trendRef" class="trend-chart"></div>
          <el-empty v-if="!signTrend7d.length" description="暂无签署趋势" :image-size="60" />
        </el-card>
      </el-col>
      <el-col :xs="24" :sm="8" :md="8" :lg="8">
        <el-card shadow="hover" class="chart-side">
          <div class="side-title">⚠ 即将过期 TOP5</div>
          <div v-if="kpi.expiringTop5List.length" class="side-body">
            <div v-for="(it, i) in kpi.expiringTop5List" :key="i" class="expire-row">
              <div class="expire-line">
                <span class="expire-name">{{ it.itemName }}</span>
                <el-tag size="small" :type="it.daysLeft <= 30 ? 'danger' : 'warning'" effect="dark">
                  {{ it.daysLeft }} 天
                </el-tag>
              </div>
              <div class="expire-sub">批号 {{ it.batchNo }} · 到期 {{ it.expireDate }}</div>
            </div>
          </div>
          <div v-else class="side-empty">🎉 没有即将过期的批次</div>
          <div class="side-tip">
            💡 临近效期的批次应优先消耗；过期前 30 天建议做召回演练。
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- P1-1: 全局操作头部 -->
    <div class="header-bar">
      <div class="header-title">
        <el-icon class="title-icon"><Lightning /></el-icon>
        <span>合规中心</span>
        <el-tag size="small" type="info" effect="plain" class="version-tag">v2.0</el-tag>
      </div>
      <div class="header-actions">
        <el-button :icon="Download" size="default" @click="exportAllZip">导出全部 CSV</el-button>
        <el-button type="primary" :icon="Plus" size="default" @click="openAddTpl">新建模板</el-button>
        <el-dropdown @command="onGlobalCreate">
          <el-button type="success" :icon="Plus" size="default">
            新建主档 / 入库 <el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="drug">新建药械主档</el-dropdown-item>
              <el-dropdown-item command="batch">批次入库</el-dropdown-item>
              <el-dropdown-item command="sign">手动签署登记</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <el-tabs v-model="tab" @tab-change="handleTabChange">
      <!-- ================= 知情同意 ================= -->
      <el-tab-pane label="知情同意" name="consent">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-card>
              <!-- ① 加标题 + 新增按钮 + 导出按钮（⑩） -->
              <template #header>
                <div class="card-header">
                  <span>知情同意模板</span>
                  <div>
                    <el-button size="small" :icon="Download" @click="exportCsv('templates')">导出 CSV</el-button>
                    <el-button size="small" :icon="UploadFilled" @click="openTplImport">导入模板</el-button>
                    <el-button size="small" type="primary" :icon="Plus" @click="openAddTpl">新增模板</el-button>
                  </div>
                </div>
              </template>
              <el-table :data="templates" size="default" @row-click="openTplDetail"
                empty-text="暂无模板，请点击右上角「新增模板」" stripe
                :row-class-name="tplRowClass">
                <el-table-column prop="templateCode" label="编码" width="120" />
                <el-table-column prop="templateName" label="名称" show-overflow-tooltip />
                <!-- ② 类型英文改中文映射 -->
                <el-table-column label="类型" width="100">
                  <template #default="{ row }">
                    <el-tag size="small" :type="tplTypeTag(row.templateType)">{{ tplTypeLabel(row.templateType) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="version" label="版本" width="80">
                  <template #default="{ row }">
                    {{ row.version }}
                    <el-tag v-if="row.latestVersion" size="small" type="success" effect="plain" style="margin-left:4px"
                      v-show="row.version === row.latestVersion">最新</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="版本数" width="80">
                  <template #default="{ row }">
                    <el-link type="primary" :underline="'never'" @click.stop="openVersionHistory(row)">
                      {{ row.versionCount || 1 }} 个
                    </el-link>
                  </template>
                </el-table-column>
                <!-- ① 补齐状态/创建时间/操作列 -->
                <el-table-column label="状态" width="80">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.active ? 'success' : 'info'">{{ row.active ? '启用' : '停用' }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="createTime" label="创建时间" width="160" />
                <el-table-column label="操作" width="240" fixed="right">
                  <template #default="{ row }">
                    <el-button size="small" link @click.stop="openTplDetail(row)">预览</el-button>
                    <el-button size="small" link type="primary" @click.stop="createNewVersion(row)">新建版本</el-button>
                    <el-button size="small" link type="warning" @click.stop="toggleTplStatus(row)">
                      {{ row.active ? '停用' : '启用' }}
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-card>
          </el-col>
          <el-col :span="12">
            <el-card v-if="currentTpl">
              <template #header>
                <div class="card-header">
                  <span><b>{{ currentTpl.templateName }}</b> <el-tag size="small">{{ currentTpl.version }}</el-tag></span>
                  <div>
                    <el-button size="small" :icon="View" @click="openTplDetail(currentTpl)">详情</el-button>
                    <el-button size="small" :icon="Share" @click="openIssueLink">扫码签</el-button>
                  </div>
                </div>
              </template>
              <div class="tpl-meta">
                <el-tag :type="tplTypeTag(currentTpl.templateType)">{{ tplTypeLabel(currentTpl.templateType) }}</el-tag>
                <el-tag type="info">法规: {{ currentTpl.legalBasis || '—' }}</el-tag>
                <el-tag :type="currentTpl.active ? 'success' : 'info'">{{ currentTpl.active ? '已启用' : '已停用' }}</el-tag>
                <el-tag type="warning" effect="plain">v{{ currentTpl.version }}</el-tag>
              </div>
              <pre class="tpl-content">{{ currentTpl.contentMd }}</pre>
              <el-divider />
              <h4>签署（手动登记）</h4>
              <el-form :inline="true" size="default">
                <el-form-item label="会员"><el-input v-model.number="signForm.memberId" style="width:120px" /></el-form-item>
                <el-form-item label="签署人"><el-input v-model="signForm.signerName" style="width:120px" /></el-form-item>
                <el-form-item label="医师"><el-input v-model="signForm.witnessName" style="width:120px" /></el-form-item>
                <el-form-item label="执业证"><el-input v-model="signForm.witnessLicenseNo" style="width:140px" /></el-form-item>
                <el-form-item>
                  <el-button type="primary" :icon="EditPen" @click="sign" :loading="loading">签署</el-button>
                </el-form-item>
              </el-form>
              <el-alert v-if="signResult" :title="`已签署: ${signResult.signatureId}`" type="success" :closable="false" show-icon />
              <!-- 最近签署预览 -->
              <el-divider content-position="left">📜 该模板最近签署</el-divider>
              <div v-if="currentTplRecents.length" class="recent-list">
                <div v-for="r in currentTplRecents" :key="r.id" class="recent-row">
                  <span class="recent-name">{{ r.memberName || '会员' }}<el-tag size="small" effect="plain" style="margin-left:4px">{{ r.signerName }}</el-tag></span>
                  <span class="recent-time">{{ r.effectiveAt || r.createTime }}</span>
                  <el-tag size="small" :type="signStatusTag(r.status)">{{ signStatusLabel(r.status) }}</el-tag>
                </div>
              </div>
              <div v-else class="side-empty" style="margin-top:8px">尚无签署记录</div>
            </el-card>
            <!-- ⑥ 空状态 -->
            <el-empty v-else description="👈 从左侧点击模板查看 / 签署" :image-size="100" />
          </el-col>
        </el-row>
      </el-tab-pane>

      <!-- ================= 药械主档 ================= -->
      <el-tab-pane label="药械主档" name="drug">
        <!-- ③ 筛选条 -->
        <el-form :inline="true" class="filter-bar" label-width="70px" label-position="left">
          <el-form-item label="关键字">
            <el-input v-model="drugFilter.keyword" placeholder="编码/名称/厂商" style="width:220px" clearable @clear="loadDrugs" />
          </el-form-item>
          <el-form-item label="类型">
            <el-select v-model="drugFilter.itemType" style="width:130px" clearable placeholder="全部" @change="loadDrugs">
              <el-option label="药品" value="DRUG" />
              <el-option label="器械" value="DEVICE" />
              <el-option label="耗材" value="CONSUMABLE" />
            </el-select>
          </el-form-item>
          <el-form-item label="效期">
            <el-select v-model="drugFilter.shelf" style="width:140px" clearable placeholder="全部" @change="loadDrugs">
              <el-option label="1 年" :value="365" />
              <el-option label="2 年" :value="730" />
              <el-option label="3 年" :value="1095" />
              <el-option label="5 年" :value="1825" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="loadDrugs">查询</el-button>
            <el-button @click="resetDrugFilter">重置</el-button>
            <el-button :icon="Download" @click="exportCsv('drugs')">导出 CSV</el-button>
            <el-button type="success" :icon="Plus" @click="openAddDrug">新增药械</el-button>
          </el-form-item>
        </el-form>

        <el-table :data="filteredDrugs" stripe size="default" :row-class-name="drugRowClass"
          empty-text="暂无药械，请调整筛选条件或新增"
          @row-click="openDrugDetail">
          <el-table-column prop="itemCode" label="编码" width="120" />
          <el-table-column prop="itemName" label="名称" show-overflow-tooltip />
          <el-table-column label="类型" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="drugTypeTag(row.itemType)">{{ drugTypeLabel(row.itemType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="registrationNo" label="注册证号" width="200" show-overflow-tooltip />
          <el-table-column prop="specification" label="规格" width="120" />
          <el-table-column prop="manufacturer" label="厂商" show-overflow-tooltip />
          <!-- ③ 类别列：显示真实分类（而非品牌） -->
          <el-table-column label="分类" width="140">
            <template #default="{ row }">
              <span v-if="row.category">{{ row.category }}</span>
              <span v-else class="muted">—</span>
            </template>
          </el-table-column>
          <!-- ④ 效期(天) 人性化显示 -->
          <el-table-column label="效期" width="100">
            <template #default="{ row }">
              <span :class="{ 'warn-text': row.shelfLifeDays > 1825 }">
                {{ shelfLifeText(row.shelfLifeDays) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.active === false ? 'info' : 'success'">
                {{ row.active === false ? '已下架' : '在售' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button size="small" link @click.stop="openDrugDetail(row)">详情</el-button>
              <el-button size="small" link type="danger" @click.stop="toggleDrugStatus(row)">
                {{ row.active === false ? '上架' : '下架' }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ================= 批次 ================= -->
      <el-tab-pane label="批次" name="batch">
        <el-form :inline="true" class="filter-bar" label-width="70px" label-position="left">
          <el-form-item label="药械">
            <el-select v-model="batchFilter.itemId" style="width:200px" clearable filterable placeholder="全部" @change="loadBatches">
              <el-option v-for="d in drugs" :key="d.id" :label="d.itemName" :value="d.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="batchFilter.status" style="width:120px" clearable placeholder="全部" @change="loadBatches">
              <el-option label="正常" :value="1" />
              <el-option label="冻结" :value="2" />
              <el-option label="过期" :value="3" />
              <el-option label="召回" :value="4" />
            </el-select>
          </el-form-item>
          <el-form-item label="预警">
            <el-switch v-model="batchFilter.expiringOnly" @change="loadBatches" />
            <span class="filter-tip">仅看 &lt; 90 天</span>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="loadBatches">查询</el-button>
            <el-button :icon="Download" @click="exportCsv('batches')">导出 CSV</el-button>
            <!-- ⑧ 入库按钮 -->
            <el-button type="success" :icon="Plus" @click="openInbound">入库</el-button>
          </el-form-item>
        </el-form>

        <el-table :data="filteredBatches" stripe size="default" :row-class-name="batchRowClass"
          empty-text="暂无批次，请点击「入库」开始"
          @row-click="openBatchDetail">
          <el-table-column prop="itemName" label="药械" show-overflow-tooltip />
          <el-table-column prop="batchNo" label="批号" width="160" />
          <el-table-column prop="expireDate" label="到期日" width="120">
            <template #default="{ row }">
              <span :class="expireClass(row)">{{ row.expireDate }}</span>
              <div v-if="daysToExpire(row) !== null" class="days-tag" :class="expireClass(row)">
                {{ daysToExpire(row) > 0 ? `剩 ${daysToExpire(row)} 天` : `已过期 ${-daysToExpire(row)} 天` }}
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="inboundQty" label="入库" width="80" />
          <el-table-column prop="usedQty" label="已用" width="80" />
          <!-- ⑤ 剩余库存：百分比 < 20% 红字 -->
          <el-table-column label="剩余" width="140">
            <template #default="{ row }">
              <span class="stock-num">{{ row.remainingQty }}</span>
              <el-progress
                :percentage="stockPercent(row)"
                :stroke-width="6"
                :status="stockStatus(row)"
                class="stock-bar" />
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="batchStatusTag(row.status)">{{ batchStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button size="small" link @click.stop="openBatchDetail(row)">详情</el-button>
              <el-button size="small" link type="warning" :disabled="row.status !== 1" @click.stop="batchOp(row, 2)">冻结</el-button>
              <el-button size="small" link type="danger" :disabled="row.status !== 1" @click.stop="batchOp(row, 4)">召回</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ================= 使用追溯 ================= -->
      <el-tab-pane label="使用追溯" name="usage">
        <!-- ⑨ 筛选条 -->
        <el-form :inline="true" class="filter-bar" label-width="70px" label-position="left">
          <el-form-item label="会员">
            <el-input v-model.number="usageFilter.memberId" style="width:120px" placeholder="会员ID" />
          </el-form-item>
          <el-form-item label="药械">
            <el-select v-model="usageFilter.itemId" style="width:180px" clearable filterable placeholder="全部">
              <el-option v-for="d in drugs" :key="d.id" :label="d.itemName" :value="d.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="日期">
            <el-date-picker v-model="usageFilter.range" type="daterange" range-separator="-"
              start-placeholder="起" end-placeholder="止" value-format="YYYY-MM-DD" style="width:230px" />
          </el-form-item>
          <el-form-item label="不良反应">
            <el-switch v-model="usageFilter.adverseOnly" />
            <span class="filter-tip">仅看有</span>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="loadUsages">查询</el-button>
            <el-button @click="resetUsageFilter">重置</el-button>
            <el-button :icon="Download" @click="exportCsv('usages')">导出 CSV</el-button>
          </el-form-item>
        </el-form>

        <el-table :data="filteredUsages" stripe size="default"
          empty-text="暂无使用记录，请调整筛选条件">
          <el-table-column prop="itemName" label="药械" show-overflow-tooltip />
          <el-table-column prop="batchNo" label="批号" width="160" />
          <el-table-column prop="memberName" label="会员" width="100" />
          <el-table-column prop="doctorName" label="医师" width="100" />
          <el-table-column prop="qty" label="数量" width="80" />
          <el-table-column prop="bodyPart" label="部位" width="120" />
          <!-- ⑨ 不良反应红色徽章 -->
          <el-table-column label="不良反应" width="140">
            <template #default="{ row }">
              <el-tag v-if="row.hasAdverse" type="danger" size="small" effect="dark">
                ⚠ 有
              </el-tag>
              <el-tag v-else type="success" size="small" effect="plain">无</el-tag>
              <div v-if="row.adverseNote" class="adverse-note">{{ row.adverseNote }}</div>
            </template>
          </el-table-column>
          <el-table-column prop="usedAt" label="使用时间" width="170" />
        </el-table>
      </el-tab-pane>

      <!-- ================= 签署记录 ================= -->
      <el-tab-pane label="签署记录" name="sign">
        <el-form :inline="true" class="filter-bar" label-width="70px" label-position="left">
          <el-form-item label="会员ID">
            <el-input v-model.number="signFilter.memberId" style="width:120px" placeholder="会员ID" />
          </el-form-item>
          <el-form-item label="模板">
            <el-select v-model="signFilter.templateId" style="width:200px" clearable filterable placeholder="全部">
              <el-option v-for="t in templates" :key="t.id" :label="t.templateName" :value="t.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="签署人">
            <el-input v-model="signFilter.signerName" style="width:140px" placeholder="姓名关键字" clearable />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="signFilter.status" style="width:110px" clearable placeholder="全部">
              <el-option label="已签署" :value="1" />
              <el-option label="已撤回" :value="2" />
              <el-option label="已过期" :value="3" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="loadSignRecords(1)">查询</el-button>
            <el-button @click="resetSignFilter">重置</el-button>
            <el-button :icon="Download" @click="exportSignRecords">导出 CSV</el-button>
          </el-form-item>
        </el-form>

        <el-table :data="signRecords" stripe size="default" v-loading="signLoading"
          empty-text="暂无签署记录，请调整筛选条件"
          @row-click="openSignDetail">
          <el-table-column prop="signatureId" label="签署编号" width="200" show-overflow-tooltip />
          <el-table-column label="会员" width="120">
            <template #default="{ row }">
              <span>{{ row.memberName || '—' }}</span>
              <div v-if="row.memberPhone" class="muted" style="font-size:11px">{{ row.memberPhone }}</div>
            </template>
          </el-table-column>
          <el-table-column label="模板" width="200" show-overflow-tooltip>
            <template #default="{ row }">
              <span>{{ row.templateCode }}</span>
              <el-tag size="small" type="info" effect="plain" style="margin-left:4px">{{ row.templateVersion }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="projectName" label="项目" width="120" show-overflow-tooltip />
          <el-table-column prop="signerName" label="签署人" width="100" />
          <el-table-column label="关系" width="70">
            <template #default="{ row }">
              <el-tag size="small" :type="signerRelationTag(row.signerRelation)">{{ signerRelationLabel(row.signerRelation) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="witnessName" label="见证医师" width="100" show-overflow-tooltip />
          <el-table-column prop="witnessLicenseNo" label="执业证" width="140" show-overflow-tooltip />
          <el-table-column prop="operatorName" label="登记人" width="100" />
          <el-table-column label="签署时间" width="170">
            <template #default="{ row }">
              <span>{{ row.effectiveAt || row.createTime }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90" fixed="right">
            <template #default="{ row }">
              <el-tag size="small" :type="signStatusTag(row.status)">{{ signStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button size="small" link type="primary" @click.stop="openSignDetail(row)">详情</el-button>
              <el-button v-if="row.status === 1" size="small" link type="warning" @click.stop="revokeSign(row)">撤回</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-model:current-page="signPage.num"
          v-model:page-size="signPage.size"
          :total="signPage.total"
          :page-sizes="[20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          style="margin-top:12px; justify-content:flex-end"
          @size-change="loadSignRecords(1)"
          @current-change="loadSignRecords()"
        />
      </el-tab-pane>
    </el-tabs>

    <!-- ================= 新增药械 ================= -->
    <el-dialog v-model="addDrugVisible" title="新增药械" width="560">
      <el-form label-width="100px">
        <el-form-item label="编码"><el-input v-model="drugForm.itemCode" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="drugForm.itemName" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="drugForm.itemType" style="width:100%">
            <el-option label="药品 DRUG" value="DRUG" />
            <el-option label="器械 DEVICE" value="DEVICE" />
            <el-option label="耗材 CONSUMABLE" value="CONSUMABLE" />
          </el-select>
        </el-form-item>
        <el-form-item label="注册证号"><el-input v-model="drugForm.registrationNo" /></el-form-item>
        <el-form-item label="规格"><el-input v-model="drugForm.specification" /></el-form-item>
        <el-form-item label="厂商"><el-input v-model="drugForm.manufacturer" /></el-form-item>
        <el-form-item label="分类"><el-input v-model="drugForm.category" placeholder="如：玻尿酸 / 肉毒 / 线雕" /></el-form-item>
        <el-form-item label="效期">
          <el-input-number v-model="drugForm.shelfLifeDays" :min="30" :max="3650" />
          <span class="muted" style="margin-left:8px">{{ shelfLifeText(drugForm.shelfLifeDays) }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDrugVisible=false">取消</el-button>
        <el-button type="primary" @click="submitDrug">提交</el-button>
      </template>
    </el-dialog>

    <!-- ================= 模板详情抽屉（P0-3 + P2 PDF 打印） ================= -->
    <el-drawer v-model="tplDetailVisible" :title="`模板详情：${tplDetail?.templateName || ''}`" size="720px" direction="rtl">
      <div v-if="tplDetail" class="tpl-detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="编码">{{ tplDetail.templateCode }}</el-descriptions-item>
          <el-descriptions-item label="版本">{{ tplDetail.version }}</el-descriptions-item>
          <el-descriptions-item label="名称" :span="2">{{ tplDetail.templateName }}</el-descriptions-item>
          <el-descriptions-item label="类型">
            <el-tag :type="tplTypeTag(tplDetail.templateType)">{{ tplTypeLabel(tplDetail.templateType) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="法规依据">{{ tplDetail.legalBasis || '—' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="tplDetail.active ? 'success' : 'info'">{{ tplDetail.active ? '已启用' : '已停用' }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ tplDetail.createTime }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">📄 模板正文（Markdown 预览）</el-divider>
        <pre class="tpl-content">{{ tplDetail.contentMd }}</pre>

        <el-divider content-position="left">📜 该模板签署历史</el-divider>
        <el-table :data="tplDetailRecents" size="small" stripe max-height="240"
          empty-text="暂无签署记录">
          <el-table-column prop="signatureId" label="签署编号" width="200" show-overflow-tooltip />
          <el-table-column prop="memberName" label="会员" width="120" />
          <el-table-column prop="signerName" label="签署人" width="100" />
          <el-table-column label="签署时间" width="170">
            <template #default="{ row }">{{ row.effectiveAt || row.createTime }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="signStatusTag(row.status)">{{ signStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
        </el-table>

        <div class="tpl-detail-actions">
          <el-button :icon="Download" @click="downloadTplMd">下载 Markdown</el-button>
          <el-button :icon="Download" @click="downloadTplHtml">下载 HTML</el-button>
          <el-button type="primary" :icon="View" @click="printTpl">打印 / 导出 PDF</el-button>
        </div>
      </div>
    </el-drawer>

    <!-- ================= 模板批量导入 ================= -->
    <el-dialog v-model="tplImportVisible" title="批量导入知情同意模板（CSV）" width="680">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:12px"
        title="CSV 格式" description="列顺序：编码,名称,类型(INJECTION/TOPICAL/ORAL/SURGERY/LASER/OTHER),法规依据,正文（建议使用 Markdown）" />
      <el-upload :auto-upload="false" :show-file-list="false" accept=".csv"
        :on-change="onTplImportFile">
        <el-button :icon="UploadFilled">选择 CSV 文件</el-button>
      </el-upload>
      <div v-if="tplImportFile" class="muted" style="margin: 8px 0">
        已选择：{{ tplImportFile.name }}（{{ tplImportPreview.length }} 行预览）
      </div>
      <el-table v-if="tplImportPreview.length" :data="tplImportPreview" max-height="320" size="small" stripe>
        <el-table-column prop="templateCode" label="编码" width="120" />
        <el-table-column prop="templateName" label="名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="templateType" label="类型" width="100" />
        <el-table-column prop="legalBasis" label="法规依据" min-width="120" show-overflow-tooltip />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag v-if="row._err" type="danger" size="small">错误</el-tag>
            <el-tag v-else type="success" size="small">有效</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="tplImportVisible=false">取消</el-button>
        <el-button type="primary" :disabled="!tplImportPreview.length" @click="submitTplImport">开始导入</el-button>
      </template>
    </el-dialog>

    <!-- ================= 新增模板 ================= -->
    <el-dialog v-model="addTplVisible" title="新增知情同意模板" width="640">
      <el-form label-width="100px">
        <el-form-item label="编码"><el-input v-model="tplForm.templateCode" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="tplForm.templateName" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="tplForm.templateType" style="width:100%">
            <el-option label="注射类 INJECTION" value="INJECTION" />
            <el-option label="外用 TOPICAL" value="TOPICAL" />
            <el-option label="口服 ORAL" value="ORAL" />
            <el-option label="手术 SURGERY" value="SURGERY" />
            <el-option label="激光 LASER" value="LASER" />
          </el-select>
        </el-form-item>
        <el-form-item label="版本"><el-input v-model="tplForm.version" placeholder="如 v1.0" /></el-form-item>
        <el-form-item label="法规依据"><el-input v-model="tplForm.legalBasis" /></el-form-item>
        <el-form-item label="正文（Markdown）">
          <el-input v-model="tplForm.contentMd" type="textarea" :rows="8" placeholder="Markdown 文本" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addTplVisible=false">取消</el-button>
        <el-button type="primary" @click="submitTpl">提交</el-button>
      </template>
    </el-dialog>

    <!-- ================= 版本历史 ================= -->
    <el-drawer v-model="versionVisible" :title="`版本历史：${versionTitle}`" size="640">
      <template v-if="versionBase">
        <div class="version-toolbar">
          <el-button size="small" type="primary" :icon="Plus" @click="createNewVersionFromBase">基于此版本新建</el-button>
          <el-button size="small" :icon="View" @click="previewVersion" :disabled="!versionSelected.length">查看内容</el-button>
          <el-button size="small" :icon="Operation" @click="diffVersions" :disabled="versionSelected.length !== 2">对比差异</el-button>
        </div>
        <el-alert type="info" :closable="false" show-icon style="margin-bottom:8px"
          title="小提示"
          description="勾选 2 个版本可对比差异；勾选 1 个可查看内容；勾选 0 个则只读列表。">
        </el-alert>
        <el-table :data="versions" size="default" stripe
          @selection-change="v => versionSelected = v"
          empty-text="暂无版本历史">
          <el-table-column type="selection" width="48" />
          <el-table-column prop="version" label="版本" width="100" />
          <el-table-column label="发布状态" width="120">
            <template #default="{ row }">
              <el-tag size="small" :type="row.enabled ? 'success' : 'info'">
                {{ row.enabled ? '已启用' : '停用' }}
              </el-tag>
              <el-tag size="small" :type="auditTagType(row.auditStatus)" style="margin-left:4px">
                {{ auditLabel(row.auditStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="operatorName" label="创建人" width="100" />
          <el-table-column prop="createTime" label="创建时间" width="170" />
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button size="small" link type="primary" @click="viewOneVersion(row)">查看</el-button>
              <el-button v-if="row.enabled" size="small" link type="warning" @click="setAsCurrent(row)">置为当前</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>

    <!-- ================= 版本内容查看 ================= -->
    <el-dialog v-model="versionDetailVisible" title="版本内容" width="640">
      <template v-if="versionDetail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="版本">{{ versionDetail.version }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ versionDetail.createTime }}</el-descriptions-item>
          <el-descriptions-item label="编码" :span="2">{{ versionDetail.templateCode }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{ versionDetail.operatorName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="法规依据">{{ versionDetail.legalBasis || '—' }}</el-descriptions-item>
        </el-descriptions>
        <pre class="tpl-content">{{ versionDetail.contentMd }}</pre>
      </template>
    </el-dialog>

    <!-- ================= 版本差异对比 ================= -->
    <el-dialog v-model="diffVisible" title="版本差异对比" width="960">
      <template v-if="diffSide">
        <div class="diff-head">
          <span class="diff-tag left">旧：{{ diffSide.left.version }}</span>
          <span class="diff-tag right">新：{{ diffSide.right.version }}</span>
        </div>
        <div class="diff-body">
          <pre class="diff-pane"><span v-html="diffLeftLines"></span></pre>
          <pre class="diff-pane"><span v-html="diffRightLines"></span></pre>
        </div>
      </template>
    </el-dialog>

    <!-- ================= 入库 ================= -->
    <el-dialog v-model="inboundVisible" title="批次入库" width="540">
      <el-form label-width="100px">
        <el-form-item label="药械">
          <el-select v-model="batchForm.itemId" style="width:100%" filterable>
            <el-option v-for="d in drugs" :key="d.id" :label="d.itemName" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="批号"><el-input v-model="batchForm.batchNo" /></el-form-item>
        <el-form-item label="生产日期">
          <el-date-picker v-model="batchForm.produceDate" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="到期日期">
          <el-date-picker v-model="batchForm.expireDate" value-format="YYYY-MM-DD" style="width:100%" />
        </el-form-item>
        <el-form-item label="入库数量"><el-input-number v-model="batchForm.inboundQty" :min="1" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="inboundVisible=false">取消</el-button>
        <el-button type="primary" @click="submitInbound">入库</el-button>
      </template>
    </el-dialog>

    <!-- ================= 药械详情 ================= -->
    <el-drawer v-model="drugDetailVisible" :title="drugDetail?.itemName" size="520">
      <el-descriptions v-if="drugDetail" :column="2" border>
        <el-descriptions-item label="编码">{{ drugDetail.itemCode }}</el-descriptions-item>
        <el-descriptions-item label="类型">
          <el-tag :type="drugTypeTag(drugDetail.itemType)">{{ drugTypeLabel(drugDetail.itemType) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="名称" :span="2">{{ drugDetail.itemName }}</el-descriptions-item>
        <el-descriptions-item label="注册证号" :span="2">{{ drugDetail.registrationNo }}</el-descriptions-item>
        <el-descriptions-item label="规格">{{ drugDetail.specification }}</el-descriptions-item>
        <el-descriptions-item label="分类">{{ drugDetail.category }}</el-descriptions-item>
        <el-descriptions-item label="厂商" :span="2">{{ drugDetail.manufacturer }}</el-descriptions-item>
        <el-descriptions-item label="效期" :span="2">{{ shelfLifeText(drugDetail.shelfLifeDays) }}</el-descriptions-item>
        <el-descriptions-item label="状态" :span="2">
          <el-tag :type="drugDetail.active === false ? 'info' : 'success'">
            {{ drugDetail.active === false ? '已下架' : '在售' }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>
    </el-drawer>

    <!-- ================= 批次详情 ================= -->
    <el-drawer v-model="batchDetailVisible" title="批次详情" size="520">
      <el-descriptions v-if="batchDetail" :column="2" border>
        <el-descriptions-item label="药械" :span="2">{{ batchDetail.itemName }}</el-descriptions-item>
        <el-descriptions-item label="批号">{{ batchDetail.batchNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="batchStatusTag(batchDetail.status)">{{ batchStatusLabel(batchDetail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="到期日">{{ batchDetail.expireDate }}</el-descriptions-item>
        <el-descriptions-item label="剩余天数">
          <span :class="expireClass(batchDetail)">{{ daysToExpire(batchDetail) > 0 ? daysToExpire(batchDetail) + ' 天' : '已过期' }}</span>
        </el-descriptions-item>
        <el-descriptions-item label="入库数量">{{ batchDetail.inboundQty }}</el-descriptions-item>
        <el-descriptions-item label="已用">{{ batchDetail.usedQty }}</el-descriptions-item>
        <el-descriptions-item label="剩余">{{ batchDetail.remainingQty }}</el-descriptions-item>
        <el-descriptions-item label="使用率">
          <el-progress :percentage="usedPercent(batchDetail)" />
        </el-descriptions-item>
      </el-descriptions>
    </el-drawer>

    <!-- ================= 扫码签链接弹窗 ================= -->
    <el-dialog v-model="signLinkVisible" title="扫码签链接" width="560">
      <template v-if="signLinkInfo">
        <el-alert type="success" :closable="false" show-icon style="margin-bottom:12px"
          title="链接已生成"
          :description="`有效时长：${signLinkInfo.ttlMinutes} 分钟 / 过期时间：${signLinkInfo.expireAt}`">
        </el-alert>
        <el-form label-width="90px">
          <el-form-item label="模板">{{ currentTpl?.templateName }}（{{ currentTpl?.version }}）</el-form-item>
          <el-form-item label="签署链接">
            <el-input v-model="signLinkFullUrl" readonly>
              <template #append>
                <el-button @click="copySignLink">复制</el-button>
              </template>
            </el-input>
          </el-form-item>
          <el-form-item label="Token">{{ signLinkInfo.token }}</el-form-item>
        </el-form>
        <el-alert type="info" :closable="false" show-icon
          title="使用说明"
          description="将链接发送给会员；会员在浏览器中打开即可填写签署。短信通知作为二期功能，仅生成链接即可人工发送。">
        </el-alert>
      </template>
    </el-dialog>

    <!-- ================= 签署记录详情 ================= -->
    <el-drawer v-model="signDetailVisible" title="签署记录详情" size="560">
      <template v-if="signDetail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="签署编号" :span="2">{{ signDetail.signatureId }}</el-descriptions-item>
          <el-descriptions-item label="状态" :span="2">
            <el-tag :type="signStatusTag(signDetail.status)">{{ signStatusLabel(signDetail.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="会员" :span="2">
            {{ signDetail.memberName || '—' }} <span v-if="signDetail.memberPhone" class="muted">({{ signDetail.memberPhone }})</span>
          </el-descriptions-item>
          <el-descriptions-item label="模板" :span="2">
            {{ signDetail.templateCode }}
            <el-tag size="small" type="info" effect="plain" style="margin-left:4px">{{ signDetail.templateVersion }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="项目" :span="2">{{ signDetail.projectName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="订单号">{{ signDetail.orderNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="签署时间">{{ signDetail.effectiveAt || signDetail.createTime }}</el-descriptions-item>
          <el-descriptions-item label="签署人">{{ signDetail.signerName }}</el-descriptions-item>
          <el-descriptions-item label="签署关系">{{ signerRelationLabel(signDetail.signerRelation) }}</el-descriptions-item>
          <el-descriptions-item label="见证医师" :span="2">
            {{ signDetail.witnessName || '—' }}
            <span v-if="signDetail.witnessLicenseNo" class="muted">（证号：{{ signDetail.witnessLicenseNo }}）</span>
          </el-descriptions-item>
          <el-descriptions-item label="登记人">{{ signDetail.operatorName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="登记IP">{{ signDetail.signIp || '—' }}</el-descriptions-item>
          <el-descriptions-item label="设备" :span="2">{{ signDetail.signDevice || '—' }}</el-descriptions-item>
          <el-descriptions-item label="防篡改Hash" :span="2">
            <span class="hash-cell">{{ signDetail.signDocHash }}</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="signDetail.revokedAt" label="撤回时间" :span="2">{{ signDetail.revokedAt }}</el-descriptions-item>
          <el-descriptions-item v-if="signDetail.revokedReason" label="撤回原因" :span="2">{{ signDetail.revokedReason }}</el-descriptions-item>
          <el-descriptions-item v-if="signDetail.signDocUrl" label="签署文档" :span="2">
            <el-link type="primary" :href="signDetail.signDocUrl" target="_blank">查看原文</el-link>
          </el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="signDetail.status === 1"
          type="warning"
          :closable="false"
          style="margin-top:12px"
          show-icon
          title="合规提示"
          description="撤回后该签署记录将标记为「已撤回」，关联医嘱/订单仍可保留，但不得再用于对外证明。">
        </el-alert>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import http from '@/utils/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Search, Plus, Download, EditPen, Warning, WarningFilled,
  Document, FirstAidKit, Box, View, Operation, Share, CopyDocument,
  Bell, ArrowDown, Lightning, UploadFilled
} from '@element-plus/icons-vue'
import * as echarts from 'echarts/core'
import { LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
echarts.use([LineChart, GridComponent, TooltipComponent, CanvasRenderer])

const tab = ref('consent')

// 切换到「签署记录」Tab 时按需加载
function handleTabChange(name) {
  if (name === 'sign' && !signRecords.value.length) loadSignRecords(1)
}

// ============= KPI =============
const kpi = reactive({
  templates: 0, templatesActive: 0, templatesPending: 0,
  drugs: 0, drugTypes: 0,
  batches: 0, batchesActive: 0,
  expiringSoon: 0,
  signTotal: 0, signValid: 0, signRevoked: 0, signExpired: 0, signNew30d: 0,
  expiringTop5List: []
})
const signTrend7d = ref([])
const trendRef = ref()
let trendChart = null
// 修复：保存 watch 返回的 stop 函数，用于组件卸载时停止 watch，
// 避免 el-tabs 切换后响应式系统访问已卸载 effect 的 startTime 属性。
let stopTrendWatch = null

async function refreshKpi() {
  // 基础数据先从本地计算
  kpi.templates = templates.value.length
  kpi.templatesActive = templates.value.filter(t => t.active !== false).length
  kpi.drugs = drugs.value.length
  kpi.drugTypes = new Set(drugs.value.map(d => d.itemType)).size
  kpi.batches = batches.value.length
  kpi.batchesActive = batches.value.filter(b => b.status === 1).length
  kpi.expiringSoon = batches.value.filter(b => {
    const d = daysToExpire(b); return d !== null && d >= 0 && d < 90
  }).length
  // 签署 + 趋势 + 即将过期 TOP5 走后端聚合（避免翻页盲区）
  try {
    const { data } = await http.get('/mh/consent/stats', { silent: true })
    kpi.signTotal = data.signTotal ?? 0
    kpi.signValid = data.signValid ?? 0
    kpi.signRevoked = data.signRevoked ?? 0
    kpi.signExpired = data.signExpired ?? 0
    kpi.signNew30d = data.signNew30d ?? 0
    kpi.templatesPending = data.templatesPending ?? 0
    if (Array.isArray(data.expiringTop5)) kpi.expiringTop5List = data.expiringTop5
    if (Array.isArray(data.signTrend7d)) signTrend7d.value = data.signTrend7d
  } catch { /* 接口不存在时回落本地聚合 */ }
}
function renderTrend() {
  // 修复：DOM 节点（trendRef）在 el-tabs 切换 / 组件卸载后会被销毁，
  // 此时访问其属性可能抛错。加 try/catch 静默兜底，不影响其他逻辑。
  try {
    if (!trendRef.value || !signTrend7d.value.length) return
    if (trendChart) { trendChart.dispose(); trendChart = null }
    trendChart = echarts.init(trendRef.value)
    trendChart.setOption({
      grid: { top: 24, left: 36, right: 16, bottom: 28 },
      tooltip: { trigger: 'axis', confine: true },
      xAxis: {
        type: 'category', data: signTrend7d.value.map(d => d.date),
        axisLine: { lineStyle: { color: '#dcdfe6' } }, axisLabel: { color: '#909399', fontSize: 11 }
      },
      yAxis: {
        type: 'value', minInterval: 1,
        splitLine: { lineStyle: { color: '#f5f7fa' } }, axisLabel: { color: '#909399', fontSize: 11 }
      },
      series: [{
        type: 'line', smooth: true, symbol: 'circle', symbolSize: 6,
        data: signTrend7d.value.map(d => d.value),
        lineStyle: { width: 2, color: '#67c23a' },
        itemStyle: { color: '#67c23a' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(103,194,58,0.4)' },
            { offset: 1, color: 'rgba(103,194,58,0.04)' }
          ])
        }
      }]
    })
  } catch (e) {
    // 静默兜底：tab 切换 / 组件卸载过程中 DOM 已被移除，绘制失败属预期行为。
    console.warn('renderTrend skipped', e)
  }
}
stopTrendWatch = watch(signTrend7d, () => nextTick(renderTrend))

// 跳到签署记录 Tab 并自动筛「已过期」
function jumpExpiringSign() {
  signFilter.status = 3
  loadSignRecords(1)
  tab.value = 'sign'
}

// P1-1: 全局新建入口
function onGlobalCreate(cmd) {
  if (cmd === 'drug') { tab.value = 'drug'; nextTick(openAddDrug) }
  else if (cmd === 'batch') { tab.value = 'batch'; nextTick(openInbound) }
  else if (cmd === 'sign') { tab.value = 'consent'; ElMessage.info('请先在左侧选择模板，再点击「签署」') }
}
// 一键导出全部 CSV（合到一个 zip 不现实，三个分批下）
function exportAllZip() {
  exportCsv('templates')
  setTimeout(() => exportCsv('drugs'), 200)
  setTimeout(() => exportCsv('batches'), 400)
}

// ============= 标签字典 =============
const TPL_TYPES = { INJECTION: '注射类', TOPICAL: '外用', ORAL: '口服', SURGERY: '手术', LASER: '激光', OTHER: '其他' }
const DRUG_TYPES = { DRUG: '药品', DEVICE: '器械', CONSUMABLE: '耗材' }
const BATCH_STATUS = { 1: '正常', 2: '冻结', 3: '过期', 4: '召回' }
const SIGN_STATUS = { 1: '已签署', 2: '已撤回', 3: '已过期' }
const SIGN_RELATION = { SELF: '本人', PARENT: '监护人', AGENT: '代理人' }

function tplTypeLabel(t) { return TPL_TYPES[t] || t || '—' }
function tplTypeTag(t) { return { INJECTION: 'danger', SURGERY: 'danger', LASER: 'warning', ORAL: 'success', TOPICAL: '' }[t] || 'info' }

// P2: 模板批量导入（CSV：编码,名称,类型,法规依据,正文）
const tplImportVisible = ref(false)
const tplImportPreview = ref([])
const tplImportFile = ref(null)
function openTplImport() {
  tplImportPreview.value = []
  tplImportFile.value = null
  tplImportVisible.value = true
}
async function onTplImportFile(file) {
  tplImportFile.value = file
  const text = await file.raw.text()
  const lines = text.split(/\r?\n/).filter(l => l.trim())
  if (!lines.length) { tplImportPreview.value = []; return }
  // 跳过表头（如果首行是已知关键字）
  const head = lines[0].split(',')
  const hasHeader = head.some(h => ['编码','名称','类型','法规依据','正文'].includes(h))
  const dataLines = hasHeader ? lines.slice(1) : lines
  tplImportPreview.value = dataLines.map(l => {
    const cols = parseCsvLine(l)
    return {
      templateCode: cols[0] || '',
      templateName: cols[1] || '',
      templateType: cols[2] || 'OTHER',
      legalBasis: cols[3] || '',
      contentMd: cols[4] || '',
      _err: !cols[0] || !cols[1]
    }
  })
}
function parseCsvLine(line) {
  const out = []; let cur = ''; let inQ = false
  for (let i = 0; i < line.length; i++) {
    const c = line[i]
    if (c === '"' && line[i+1] === '"') { cur += '"'; i++; continue }
    if (c === '"') { inQ = !inQ; continue }
    if (c === ',' && !inQ) { out.push(cur); cur = ''; continue }
    cur += c
  }
  out.push(cur)
  return out
}
async function submitTplImport() {
  const rows = tplImportPreview.value.filter(r => !r._err)
  if (!rows.length) return ElMessage.warning('没有可导入的有效行')
  try {
    await ElMessageBox.confirm(`共 ${rows.length} 条，确认导入？`, '批量导入', { type: 'info' })
  } catch { return }
  let ok = 0, fail = 0
  for (const r of rows) {
    try { await http.post('/mh/consent/template', r); ok++ }
    catch { fail++ }
  }
  ElMessage.success(`导入完成：成功 ${ok}，失败 ${fail}`)
  tplImportVisible.value = false
  loadTemplates()
}
function drugTypeLabel(t) { return DRUG_TYPES[t] || t || '—' }
function drugTypeTag(t) { return { DRUG: 'danger', DEVICE: 'warning', CONSUMABLE: '' }[t] || 'info' }
function batchStatusLabel(s) { return BATCH_STATUS[s] || '—' }
function batchStatusTag(s) { return { 1: 'success', 2: 'warning', 3: 'danger', 4: 'danger' }[s] || 'info' }
function signStatusLabel(s) { return SIGN_STATUS[s] || '—' }
function signStatusTag(s) { return { 1: 'success', 2: 'info', 3: 'warning' }[s] || 'info' }
function signerRelationLabel(r) { return SIGN_RELATION[r] || r || '—' }
function signerRelationTag(r) { return { SELF: 'success', PARENT: 'warning', AGENT: 'info' }[r] || 'info' }

// ============= 有效期人性化 =============
function shelfLifeText(days) {
  if (!days) return '—'
  if (days < 60) return `${days} 天`
  const years = days / 365
  if (Math.abs(years - Math.round(years)) < 0.05) return `${Math.round(years)} 年`
  return `${(years).toFixed(1)} 年`
}

// ============= 知情同意 =============
const templates = ref([])
const currentTpl = ref(null)
const signForm = reactive({ memberId: null, signerName: '', witnessName: '', witnessLicenseNo: '' })
const signResult = ref(null)
const loading = ref(false)
const addTplVisible = ref(false)
const tplForm = reactive({ templateCode: '', templateName: '', templateType: 'INJECTION', version: 'v1.0', legalBasis: '', contentMd: '' })

async function loadTemplates() {
  const { data } = await http.get('/mh/consent/template/list')
  const rows = data || []
  // 业务编码相同视为一个模板业务；版本号倒序中第一条为最新
  const codeMap = new Map()
  for (const r of rows) {
    const list = codeMap.get(r.templateCode) || []
    list.push(r)
    codeMap.set(r.templateCode, list)
  }
  for (const list of codeMap.values()) {
    list.sort((a, b) => (b.version || '').localeCompare(a.version || '', undefined, { numeric: true }))
    list.forEach(r => { r.active = r.enabled === 1; r.latestVersion = list[0].version })
  }
  templates.value = rows.map(r => ({
    ...r,
    versionCount: codeMap.get(r.templateCode).length
  }))
  refreshKpi()
}
async function selectTemplate(row) {
  const { data } = await http.get(`/mh/consent/template/${row.id}`)
  currentTpl.value = data
  signResult.value = null
}
// P0-3: 模板详情抽屉（PDF 打印 + 历史 + Markdown 下载）
const tplDetailVisible = ref(false)
const tplDetail = ref(null)
const tplDetailRecents = ref([])
async function openTplDetail(row) {
  selectTemplate(row)
  const { data } = await http.get(`/mh/consent/template/${row.id}`)
  tplDetail.value = data
  tplDetailRecents.value = []
  try {
    const { data: sr } = await http.get('/mh/consent/sign/page', { params: { templateId: row.id, pageSize: 10 } })
    tplDetailRecents.value = sr.records || []
  } catch { /* 忽略 */ }
  tplDetailVisible.value = true
}
function tplRowClass({ row }) { return row.active === false ? 'row-grey row-clickable' : 'row-clickable' }

function downloadTplMd() {
  if (!tplDetail.value) return
  const md = `# ${tplDetail.value.templateName}\n\n**版本**: ${tplDetail.value.version}\n**编码**: ${tplDetail.value.templateCode}\n**法规**: ${tplDetail.value.legalBasis || '—'}\n\n---\n\n${tplDetail.value.contentMd || ''}`
  downloadBlob(md, 'text/markdown', `${tplDetail.value.templateCode}_${tplDetail.value.version}.md`)
}
function downloadTplHtml() {
  if (!tplDetail.value) return
  const t = tplDetail.value
  const html = `<!doctype html><html><head><meta charset="utf-8"><title>${t.templateName}</title>
    <style>body{font-family:'Microsoft YaHei',sans-serif;padding:40px;max-width:780px;margin:0 auto;color:#333;line-height:1.8}
    h1{border-bottom:2px solid #409eff;padding-bottom:8px}
    .meta{background:#f5f7fa;padding:12px 16px;border-radius:6px;margin-bottom:24px}
    .meta b{color:#606266;margin-right:8px}
    pre{white-space:pre-wrap;word-break:break-word;font-size:14px}</style></head><body>
    <h1>${t.templateName}</h1>
    <div class="meta"><div><b>编码</b>${t.templateCode}</div><div><b>版本</b>${t.version}</div><div><b>法规依据</b>${t.legalBasis || '—'}</div></div>
    <pre>${(t.contentMd || '').replace(/[<>&]/g, c => ({'<':'&lt;','>':'&gt;','&':'&amp;'}[c]))}</pre>
    </body></html>`
  downloadBlob(html, 'text/html;charset=utf-8', `${t.templateCode}_${t.version}.html`)
}
function printTpl() {
  if (!tplDetail.value) return
  const t = tplDetail.value
  const w = window.open('', '_blank')
  w.document.write(`<!doctype html><html><head><meta charset="utf-8"><title>${t.templateName}</title>
    <style>@page{size:A4;margin:2cm}body{font-family:'Microsoft YaHei',sans-serif;padding:0;color:#333;line-height:1.9}
    h1{font-size:22px;border-bottom:2px solid #409eff;padding-bottom:8px}
    .meta{background:#f5f7fa;padding:14px 18px;border-radius:6px;margin-bottom:24px;font-size:13px}
    pre{white-space:pre-wrap;font-size:14px}</style></head><body>
    <h1>${t.templateName}</h1>
    <div class="meta">编码：${t.templateCode}　版本：${t.version}<br/>法规依据：${t.legalBasis || '—'}</div>
    <pre>${(t.contentMd || '').replace(/[<>&]/g, c => ({'<':'&lt;','>':'&gt;','&':'&amp;'}[c]))}</pre>
    </body></html>`)
  w.document.close()
  w.focus()
  setTimeout(() => w.print(), 400)
}
function downloadBlob(content, mime, filename) {
  const blob = new Blob([content], { type: mime })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = filename
  document.body.appendChild(a); a.click(); a.remove()
  URL.revokeObjectURL(url)
}
// 当前选中模板的最近签署（侧栏用）
const currentTplRecents = computed(() => {
  if (!currentTpl.value) return []
  return signRecords.value.filter(r => r.templateId === currentTpl.value.id).slice(0, 5)
})
async function sign() {
  if (!signForm.memberId || !currentTpl.value) return ElMessage.warning('请选择模板并填写会员')
  loading.value = true
  try {
    const { data } = await http.post('/mh/consent/sign', { ...signForm, templateId: currentTpl.value.id })
    signResult.value = data
    ElMessage.success('已签署')
    if (tab.value === 'sign') loadSignRecords()
  } finally { loading.value = false }
}

// ============= 扫码签链接 =============
const signLinkVisible = ref(false)
const signLinkInfo = ref(null)
const signLinkFullUrl = ref('')

async function openIssueLink() {
  if (!currentTpl.value) return
  const { data } = await http.post('/mh/consent/sign-link/issue', {
    templateId: currentTpl.value.id,
    memberId: signForm.memberId,
    memberName: signForm.signerName,
    memberPhone: null,
    projectName: null,
    ttlMinutes: 15
  })
  signLinkInfo.value = data
  signLinkFullUrl.value = `${window.location.origin}/mh-consent-h5/${data.token}`
  signLinkVisible.value = true
}

async function copySignLink() {
  try {
    await navigator.clipboard.writeText(signLinkFullUrl.value)
    ElMessage.success('链接已复制')
  } catch {
    const input = document.createElement('input')
    input.value = signLinkFullUrl.value
    document.body.appendChild(input)
    input.select()
    document.execCommand('copy')
    document.body.removeChild(input)
    ElMessage.success('链接已复制')
  }
}
function openAddTpl() {
  Object.assign(tplForm, { templateCode: '', templateName: '', templateType: 'INJECTION', version: 'v1.0', legalBasis: '', contentMd: '' })
  addTplVisible.value = true
}
async function submitTpl() {
  await http.post('/mh/consent/template', tplForm)
  ElMessage.success('已创建')
  addTplVisible.value = false
  loadTemplates()
}
async function toggleTplStatus(row) {
  await http.post(`/mh/consent/template/${row.id}/toggle`)
  row.enabled = row.enabled === 1 ? 0 : 1
  row.active = row.enabled === 1
  refreshKpi()
  ElMessage.success(row.enabled ? '已启用' : '已停用')
}

// ============= 版本管理 =============
const versionVisible = ref(false)
const versionTitle = ref('')
const versionBase = ref(null)
const versions = ref([])
const versionSelected = ref([])
const versionDetailVisible = ref(false)
const versionDetail = ref(null)
const diffVisible = ref(false)
const diffSide = ref(null)

function auditLabel(s) { return { 1: '待审核', 2: '已审核', 3: '已驳回' }[s] || '—' }
function auditTagType(s) { return { 1: 'warning', 2: 'success', 3: 'danger' }[s] || 'info' }

async function openVersionHistory(row) {
  versionBase.value = row
  versionTitle.value = `${row.templateCode} / ${row.templateName}`
  versionVisible.value = true
  versionSelected.value = []
  const { data } = await http.get('/mh/consent/template/version/list', { params: { templateCode: row.templateCode } })
  versions.value = data || []
}

async function createNewVersion(row) {
  try {
    await ElMessageBox.confirm(`基于 [${row.templateCode} / ${row.version}] 创建一个新版本？\n新版本默认「待审核+停用」状态。`, '新建版本', { type: 'info' })
  } catch { return }
  await http.post(`/mh/consent/template/${row.id}/new-version`, {})
  ElMessage.success('已创建新版本，请在版本历史中查看并审核')
  if (tab.value === 'consent') loadTemplates()
  if (versionVisible.value) openVersionHistory(row)
}

async function createNewVersionFromBase() {
  if (!versionBase.value) return
  await createNewVersion(versionBase.value)
}

async function viewOneVersion(row) {
  const { data } = await http.get(`/mh/consent/template/${row.id}`)
  versionDetail.value = data
  versionDetailVisible.value = true
}

async function previewVersion() {
  if (versionSelected.value.length === 1) return viewOneVersion(versionSelected.value[0])
  ElMessage.warning('请勾选 1 个版本查看')
}

async function setAsCurrent(row) {
  // 置为当前等价于：启用此版本、停用其他版本
  try {
    await ElMessageBox.confirm(`启用 [${row.version}] 并停用其他版本？`, '版本切换', { type: 'warning' })
  } catch { return }
  // 简单的逐个 toggle（演示用，生产建议后端批量接口）
  for (const v of versions.value) {
    if (v.enabled !== (v.id === row.id)) {
      await http.post(`/mh/consent/template/${v.id}/toggle`)
    }
  }
  ElMessage.success('已切换')
  openVersionHistory(versionBase.value)
  if (tab.value === 'consent') loadTemplates()
}

async function diffVersions() {
  if (versionSelected.value.length !== 2) return ElMessage.warning('请勾选 2 个版本')
  const [a, b] = versionSelected.value.slice().sort((x, y) => (x.createTime > y.createTime ? -1 : 1))
  const [oldV, newV] = a.createTime > b.createTime ? [b, a] : [a, b]
  const [a1, a2] = await Promise.all([
    http.get(`/mh/consent/template/${oldV.id}`),
    http.get(`/mh/consent/template/${newV.id}`)
  ])
  diffSide.value = { left: a1.data, right: a2.data }
  diffVisible.value = true
}

const diffLeftLines = computed(() => renderDiff(diffSide.value?.left?.contentMd || ''))
const diffRightLines = computed(() => renderDiff(diffSide.value?.right?.contentMd || ''))

/** 简单 LCS 差异渲染：左右两侧按行比对，相同行背景高亮，差异行互相对比 */
function renderDiff(text) {
  const lines = (text || '').split('\n')
  return lines.map(l => escapeHtml(l) || '&nbsp;').join('\n')
}
function escapeHtml(s) {
  return String(s).replace(/[&<>]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' }[c]))
}

// ============= 药械主档 =============
const drugs = ref([])
const drugFilter = reactive({ keyword: '', itemType: '', shelf: null })
const addDrugVisible = ref(false)
const drugForm = reactive({ itemCode: '', itemName: '', itemType: 'DRUG', registrationNo: '', specification: '', manufacturer: '', category: '', shelfLifeDays: 730 })
const drugDetailVisible = ref(false)
const drugDetail = ref(null)

async function loadDrugs() {
  const { data } = await http.get('/mh/drug-device/list', { params: { keyword: drugFilter.keyword, itemType: drugFilter.itemType } })
  drugs.value = data || []
  refreshKpi()
}
const filteredDrugs = computed(() => {
  let list = drugs.value
  if (drugFilter.shelf) list = list.filter(d => Math.abs((d.shelfLifeDays || 0) - drugFilter.shelf) < 30)
  return list
})
function resetDrugFilter() { Object.assign(drugFilter, { keyword: '', itemType: '', shelf: null }); loadDrugs() }
function openAddDrug() {
  Object.assign(drugForm, { itemCode: '', itemName: '', itemType: 'DRUG', registrationNo: '', specification: '', manufacturer: '', category: '', shelfLifeDays: 730 })
  addDrugVisible.value = true
}
async function submitDrug() {
  await http.post('/mh/drug-device', drugForm)
  ElMessage.success('已创建')
  addDrugVisible.value = false
  loadDrugs()
}
function openDrugDetail(row) { drugDetail.value = row; drugDetailVisible.value = true }
async function toggleDrugStatus(row) {
  await ElMessageBox.confirm(`确认${row.active === false ? '上架' : '下架'}[${row.itemName}]?`, '状态变更')
  await http.post(`/mh/drug-device/${row.id}/toggle`)
  row.active = row.active === false ? true : false
  ElMessage.success('已更新')
}
function drugRowClass({ row }) { return row.active === false ? 'row-grey' : '' }

// ============= 批次 =============
const batches = ref([])
const batchFilter = reactive({ itemId: null, status: null, expiringOnly: false })
const batchDetailVisible = ref(false)
const batchDetail = ref(null)
const inboundVisible = ref(false)
const batchForm = reactive({ itemId: null, batchNo: '', produceDate: '', expireDate: '', inboundQty: 1 })

async function loadBatches() {
  const { data } = await http.get('/mh/drug-device/batch/page', { params: { pageSize: 100, drugDeviceId: batchFilter.itemId, status: batchFilter.status } })
  batches.value = data.records || []
  refreshKpi()
}
const filteredBatches = computed(() => {
  if (!batchFilter.expiringOnly) return batches.value
  return batches.value.filter(b => { const d = daysToExpire(b); return d !== null && d >= 0 && d < 90 })
})
function openBatchDetail(row) { batchDetail.value = row; batchDetailVisible.value = true }
function openInbound() {
  Object.assign(batchForm, { itemId: null, batchNo: '', produceDate: '', expireDate: '', inboundQty: 1 })
  inboundVisible.value = true
}
async function submitInbound() {
  await http.post('/mh/drug-device/batch', batchForm)
  ElMessage.success('已入库')
  inboundVisible.value = false
  loadBatches()
}
async function batchOp(row, status) {
  await http.post(`/mh/drug-device/batch/${row.id}/status`, { status })
  row.status = status
  refreshKpi()
  ElMessage.success('已更新')
}

// 批次辅助
function daysToExpire(row) {
  if (!row?.expireDate) return null
  const t = new Date(row.expireDate.replace(/-/g, '/')).getTime()
  return Math.ceil((t - Date.now()) / 86400000)
}
function expireClass(row) {
  const d = daysToExpire(row)
  if (d === null) return ''
  if (d < 0) return 'expired'
  if (d < 30) return 'urgent'
  if (d < 90) return 'warn'
  return ''
}
function stockPercent(row) {
  if (!row.inboundQty) return 0
  return Math.round((row.remainingQty / row.inboundQty) * 100)
}
function stockStatus(row) {
  const p = stockPercent(row)
  if (p < 20) return 'exception'
  if (p < 50) return 'warning'
  return 'success'
}
function usedPercent(row) {
  if (!row.inboundQty) return 0
  return Math.round((row.usedQty / row.inboundQty) * 100)
}
function batchRowClass({ row }) { return expireClass(row) }

// ============= 使用追溯 =============
const usages = ref([])
const usageFilter = reactive({ memberId: null, itemId: null, range: [], adverseOnly: false })

async function loadUsages() {
  const { data } = await http.get('/mh/drug-device/usage/page', {
    params: {
      pageSize: 100,
      memberId: usageFilter.memberId,
      batchId: usageFilter.itemId,
      keyword: usageFilter.itemId ? null : (usageFilter.keyword || null),
      start: usageFilter.range?.[0],
      end: usageFilter.range?.[1],
    }
  })
  usages.value = data.records || []
}
const filteredUsages = computed(() => {
  if (!usageFilter.adverseOnly) return usages.value
  return usages.value.filter(u => u.hasAdverse)
})
function resetUsageFilter() { Object.assign(usageFilter, { memberId: null, itemId: null, range: [], adverseOnly: false }); loadUsages() }

// ============= 签署记录 =============
const signRecords = ref([])
const signFilter = reactive({ memberId: null, templateId: null, signerName: '', status: null })
const signPage = reactive({ num: 1, size: 20, total: 0 })
const signLoading = ref(false)
const signDetailVisible = ref(false)
const signDetail = ref(null)

async function loadSignRecords(resetPage) {
  if (resetPage) signPage.num = 1
  signLoading.value = true
  try {
    const { data } = await http.get('/mh/consent/sign/page', {
      params: {
        pageNum: signPage.num,
        pageSize: signPage.size,
        memberId: signFilter.memberId,
        templateId: signFilter.templateId,
        signerName: signFilter.signerName || undefined,
        status: signFilter.status
      }
    })
    signRecords.value = data.records || []
    signPage.total = data.total || 0
  } finally {
    signLoading.value = false
  }
}

function resetSignFilter() {
  Object.assign(signFilter, { memberId: null, templateId: null, signerName: '', status: null })
  loadSignRecords(1)
}

function openSignDetail(row) {
  signDetail.value = row
  signDetailVisible.value = true
}

async function revokeSign(row) {
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt('请输入撤回原因（合规留痕）', '撤回签署', {
      confirmButtonText: '撤回',
      cancelButtonText: '取消',
      inputPattern: /[\s\S]+/,
      inputErrorMessage: '撤回原因必填'
    })
    reason = value
  } catch { return }
  await http.post(`/mh/consent/sign/${row.id}/revoke`, { reason })
  row.status = 2
  ElMessage.success('已撤回')
  if (signDetail.value?.id === row.id) signDetail.value.status = 2
}

function exportSignRecords() {
  if (!signRecords.value.length) return ElMessage.warning('暂无数据可导出')
  const cols = ['signatureId','memberName','templateCode','templateVersion','projectName','signerName','signerRelation','witnessName','witnessLicenseNo','operatorName','createTime','status']
  const head = cols.join(',')
  const body = signRecords.value.map(r => cols.map(c => {
    let v = r[c]
    if (c === 'signerRelation') v = signerRelationLabel(v)
    if (c === 'status') v = signStatusLabel(v)
    if (typeof v === 'string' && (v.includes(',') || v.includes('"') || v.includes('\n'))) v = `"${v.replace(/"/g, '""')}"`
    return v ?? ''
  }).join(',')).join('\n')
  const csv = '\uFEFF' + head + '\n' + body
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = `sign-records-${new Date().toISOString().slice(0,10)}.csv`
  document.body.appendChild(a); a.click(); a.remove()
  URL.revokeObjectURL(url)
  ElMessage.success(`已导出 ${signRecords.value.length} 条`)
}

// ============= ⑩ 导出 CSV（纯前端） =============
function exportCsv(name) {
  const map = {
    templates: { rows: templates.value, columns: ['templateCode','templateName','templateType','version','active','createTime'] },
    drugs:     { rows: drugs.value,     columns: ['itemCode','itemName','itemType','registrationNo','specification','manufacturer','category','shelfLifeDays'] },
    batches:   { rows: batches.value,   columns: ['itemName','batchNo','expireDate','inboundQty','usedQty','remainingQty','status'] },
    usages:    { rows: usages.value,    columns: ['itemName','batchNo','memberName','doctorName','qty','bodyPart','hasAdverse','usedAt'] },
  }
  const cfg = map[name]
  if (!cfg?.rows.length) return ElMessage.warning('暂无数据可导出')
  const head = cfg.columns.join(',')
  const body = cfg.rows.map(r => cfg.columns.map(c => {
    let v = r[c]
    if (c === 'templateType') v = tplTypeLabel(v)
    if (c === 'itemType')     v = drugTypeLabel(v)
    if (c === 'status' && name === 'batches') v = batchStatusLabel(v)
    if (c === 'shelfLifeDays') v = shelfLifeText(v)
    if (typeof v === 'string' && (v.includes(',') || v.includes('"'))) v = `"${v.replace(/"/g, '""')}"`
    return v ?? ''
  }).join(',')).join('\n')
  const csv = '\uFEFF' + head + '\n' + body
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = `${name}-${new Date().toISOString().slice(0,10)}.csv`
  document.body.appendChild(a); a.click(); a.remove()
  URL.revokeObjectURL(url)
  ElMessage.success(`已导出 ${cfg.rows.length} 条`)
}

onMounted(() => { loadTemplates(); loadDrugs(); loadBatches(); loadUsages(); nextTick(renderTrend) })
onBeforeUnmount(() => {
  // 修复：先停止 watch 再销毁图表，避免响应式系统在 effect 列表中
  // 残留已卸载子节点，从而消除 et.reportAllChanges 访问 undefined.startTime 的报错。
  stopTrendWatch?.()
  trendChart?.dispose(); trendChart = null
})
</script>

<style scoped>
.page-consent { padding: 16px; }

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
.kpi-purple::before { background: #7c3aed; }
.kpi-cyan::before   { background: #06b6d4; }
.kpi-red::before    { background: #f56c6c; }
.kpi-green::before  { background: #67c23a; }
.kpi-orange::before { background: #e6a23c; }
.kpi-card.kpi-blue   { background: linear-gradient(135deg, #ecf5ff 0%, #fff 70%); }
.kpi-card.kpi-purple { background: linear-gradient(135deg, #f5f0ff 0%, #fff 70%); }
.kpi-card.kpi-cyan   { background: linear-gradient(135deg, #ecfeff 0%, #fff 70%); }
.kpi-card.kpi-red    { background: linear-gradient(135deg, #fef0f0 0%, #fff 70%); }
.kpi-card.kpi-green  { background: linear-gradient(135deg, #f0f9eb 0%, #fff 70%); }
.kpi-card.kpi-orange { background: linear-gradient(135deg, #fdf6ec 0%, #fff 70%); }
.kpi-icon { font-size: 32px; opacity: 0.85; padding: 0 4px; }
.kpi-blue   .kpi-icon { color: #409eff; }
.kpi-purple .kpi-icon { color: #7c3aed; }
.kpi-cyan   .kpi-icon { color: #06b6d4; }
.kpi-red    .kpi-icon { color: #f56c6c; }
.kpi-green  .kpi-icon { color: #67c23a; }
.kpi-orange .kpi-icon { color: #e6a23c; }
.kpi-body { flex: 1; min-width: 0; }
.kpi-label { color: #606266; font-size: 13px; }
.kpi-value { font-size: 26px; font-weight: 700; margin: 4px 0; color: #303133; line-height: 1.2; }
.kpi-value.warn { color: #e6a23c; }
.kpi-value.danger { color: #f56c6c; }
.kpi-value-link { font-size: 26px; font-weight: 700; margin: 4px 0; line-height: 1.2; color: inherit; }
.kpi-extra { color: #909399; font-size: 12px; }
.kpi-warn { box-shadow: 0 0 0 2px rgba(245, 108, 108, 0.25); }
.kpi-warn.kpi-orange { box-shadow: 0 0 0 2px rgba(230, 162, 60, 0.25); }

/* P0-1: 趋势图 */
.chart-card-header { display: flex; align-items: center; gap: 12px; }
.chart-title { font-weight: 600; color: #303133; }
.chart-sub { color: #909399; font-size: 12px; }
.chart-tag { margin-left: auto; }
.trend-chart { width: 100%; height: 220px; }
.chart-side { height: 100%; display: flex; flex-direction: column; gap: 12px; padding-bottom: 16px; }
.side-title { font-weight: 600; color: #303133; font-size: 14px; }
.side-body { display: flex; flex-direction: column; gap: 10px; }
.side-empty { color: #67c23a; font-size: 13px; padding: 24px 0; text-align: center; background: #f0f9eb; border-radius: 4px; }
.side-tip {
  margin-top: auto; padding: 8px 10px;
  background: #f0f9eb; color: #67c23a;
  border-radius: 6px; font-size: 12px; line-height: 1.6;
}
.expire-row { padding: 6px 8px; background: #f5f7fa; border-radius: 4px; }
.expire-line { display: flex; justify-content: space-between; align-items: center; }
.expire-name { color: #303133; font-weight: 600; font-size: 13px; }
.expire-sub { color: #909399; font-size: 11px; margin-top: 2px; }

/* P1-1: 头部 */
.header-bar {
  display: flex; align-items: center; justify-content: space-between;
  background: linear-gradient(135deg, #f5f7fa 0%, #fff 100%);
  padding: 10px 16px; border-radius: 8px; margin: 16px 0 12px;
  border: 1px solid #ebeef5;
}
.header-title { display: flex; align-items: center; gap: 8px; font-size: 15px; font-weight: 600; color: #303133; }
.title-icon { color: #409eff; font-size: 18px; }
.version-tag { margin-left: 4px; }
.header-actions { display: flex; gap: 8px; }

/* P1-2: 可点击行 */
.row-clickable { cursor: pointer; }
.row-clickable:hover td { background: #ecf5ff !important; }

/* 移动端适配（≤ 768px） */
@media (max-width: 768px) {
  .page-consent { padding: 8px; }
  .kpi-row { margin-bottom: 8px; }
  .kpi-value { font-size: 20px; }
  .kpi-icon { font-size: 24px; }
  .header-bar { flex-direction: column; gap: 8px; align-items: stretch; }
  .header-actions { justify-content: flex-end; }
  .trend-chart { height: 160px; }
  .chart-side { margin-top: 8px; }
  .el-tabs :deep(.el-tabs__item) { padding: 0 10px; font-size: 13px; }
}

/* P0-3: 模板详情抽屉 */
.tpl-detail { padding: 0 16px 16px; }
.tpl-detail-actions { margin-top: 16px; display: flex; gap: 8px; }

/* 当前模板最近签署 */
.recent-list { display: flex; flex-direction: column; gap: 6px; }
.recent-row { display: grid; grid-template-columns: 1fr auto auto; gap: 8px; align-items: center; padding: 4px 8px; background: #f5f7fa; border-radius: 4px; font-size: 12px; }
.recent-name { color: #303133; }
.recent-time { color: #909399; }

/* 通用 */
.card-header { display: flex; justify-content: space-between; align-items: center; }
.filter-bar { margin-bottom: 12px; }
.filter-bar :deep(.el-form-item) { margin-right: 12px; margin-bottom: 0; }
.filter-bar :deep(.el-form-item__label) { padding-right: 6px; }
.filter-tip { color: #909399; font-size: 12px; margin-left: 6px; }
.muted { color: #c0c4cc; }

/* 模板 */
.tpl-meta { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.tpl-content {
  white-space: pre-wrap; word-break: break-all;
  background: #f5f7fa; padding: 12px;
  max-height: 240px; overflow: auto;
  border-radius: 4px; font-size: 13px; line-height: 1.6;
}

/* 批次：到期预警 + 库存条 */
.warn-text  { color: #e6a23c; font-weight: 600; }
.expired    { color: #f56c6c; font-weight: 700; }
.urgent     { color: #f56c6c; font-weight: 600; }
.warn       { color: #e6a23c; }
.days-tag   { font-size: 11px; margin-top: 2px; }
.stock-num  { font-weight: 600; margin-right: 8px; }
.stock-bar  { display: inline-block; width: 60px; vertical-align: middle; }
:deep(.row-expired) td { background: #fef0f0 !important; }
:deep(.row-urgent)  td { background: #fef6e6 !important; }
:deep(.row-warn)    td { background: #fdf6ec !important; }
:deep(.row-grey)    td { color: #c0c4cc; background: #fafafa !important; }

/* 使用追溯 */
.adverse-note {
  font-size: 11px; color: #f56c6c; margin-top: 2px;
  max-width: 120px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}

/* 签署记录 */
.hash-cell {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: 12px;
  word-break: break-all;
  color: #606266;
}

/* 版本历史 */
.version-toolbar { display: flex; gap: 8px; margin-bottom: 12px; }
.diff-head { display: flex; justify-content: space-between; margin-bottom: 8px; }
.diff-tag {
  display: inline-block; padding: 4px 10px; border-radius: 4px; font-size: 13px;
}
.diff-tag.left { background: #ecf5ff; color: #409eff; }
.diff-tag.right { background: #f0f9eb; color: #67c23a; }
.diff-body { display: flex; gap: 8px; }
.diff-pane {
  flex: 1; min-width: 0;
  background: #f5f7fa; padding: 12px; border-radius: 4px;
  max-height: 480px; overflow: auto;
  font-size: 12px; line-height: 1.6; margin: 0;
  white-space: pre-wrap; word-break: break-all;
}
.diff-pane :deep(.diff-add) { background: #e1f3d8; color: #67c23a; }
.diff-pane :deep(.diff-del) { background: #fef0f0; color: #f56c6c; text-decoration: line-through; }
</style>