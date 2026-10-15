<template>
  <div class="detection-page">
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="肤质检测" name="skin" />
      <el-tab-pane label="发质检测" name="hair" />
      <el-tab-pane label="甲面检测" name="nail" />
      <el-tab-pane label="检测报告" name="report" />
      <el-tab-pane label="方案配置" name="solution" />
    </el-tabs>

    <!-- ============ 肤质检测 ============ -->
    <div v-if="activeTab === 'skin'">
      <el-card>
        <template #header>
          <div class="card-header">
            <span>肤质检测记录</span>
            <div class="filter-form">
              <el-select v-model="memberFilter" placeholder="客户" filterable clearable remote :remote-method="searchMembers" style="width:200px" @change="loadSkinList">
                <el-option v-for="m in memberList" :key="m.id" :label="m.name" :value="m.id" />
              </el-select>
              <el-select v-model="skinTypeFilter" placeholder="肤质类型" clearable style="width:140px" @change="loadSkinList">
                <el-option v-for="o in skinTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
              <el-button type="primary" @click="handleAddSkin">
                <el-icon><Plus /></el-icon>新增检测
              </el-button>
            </div>
          </div>
        </template>

        <el-table :data="skinList" border stripe v-loading="skinLoading">
          <el-table-column label="检测编号" prop="detectionNo" width="160" fixed />
          <el-table-column label="客户" prop="memberName" width="120" />
          <el-table-column label="肤质类型" width="120">
            <template #default="{ row }">
              <el-tag :style="{ background: getSkinColor(row.skinType), color: '#fff' }">
                {{ getSkinLabel(row.skinType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="综合得分" width="120" align="center">
            <template #default="{ row }">
              <span :style="{ color: getScoreColor(row.overallScore), fontWeight: 600 }">
                {{ row.overallScore || 0 }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="水分" prop="moistureScore" width="100" align="center" />
          <el-table-column label="油脂" prop="oilScore" width="100" align="center" />
          <el-table-column label="敏感度" prop="sensitivityScore" width="100" align="center" />
          <el-table-column label="皱纹" prop="wrinkleScore" width="100" align="center" />
          <el-table-column label="检测日期" prop="detectionDate" width="120" />
          <el-table-column label="检测人" prop="checkEmployeeName" width="120" />
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="handleViewSkin(row)">查看</el-button>
              <el-button type="warning" link size="small" @click="handleEditSkin(row)">编辑</el-button>
              <el-button type="danger" link size="small" @click="handleDeleteSkin(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!skinLoading && !skinList.length" description="暂无检测记录" />
      </el-card>
    </div>

    <!-- ============ 发质检测 ============ -->
    <div v-if="activeTab === 'hair'">
      <el-card>
        <template #header>
          <div class="card-header">
            <span>发质检测记录</span>
            <div class="filter-form">
              <el-select v-model="memberFilter" placeholder="客户" filterable clearable remote :remote-method="searchMembers" style="width:200px" @change="loadHairList">
                <el-option v-for="m in memberList" :key="m.id" :label="m.name" :value="m.id" />
              </el-select>
              <el-select v-model="hairTypeFilter" placeholder="发质类型" clearable style="width:140px" @change="loadHairList">
                <el-option v-for="o in hairTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
              <el-button type="primary" @click="handleAddHair">
                <el-icon><Plus /></el-icon>新增检测
              </el-button>
            </div>
          </div>
        </template>

        <el-table :data="hairList" border stripe v-loading="hairLoading">
          <el-table-column label="检测编号" prop="detectionNo" width="160" fixed />
          <el-table-column label="客户" prop="memberName" width="120" />
          <el-table-column label="发质类型" width="120">
            <template #default="{ row }">
              <el-tag :style="{ background: getHairColor(row.hairType), color: '#fff' }">
                {{ getHairLabel(row.hairType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="综合得分" width="120" align="center">
            <template #default="{ row }">
              <span :style="{ color: getScoreColor(row.overallScore), fontWeight: 600 }">
                {{ row.overallScore || 0 }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="水分" prop="moistureScore" width="100" align="center" />
          <el-table-column label="油脂" prop="oilScore" width="100" align="center" />
          <el-table-column label="损伤" prop="damageScore" width="100" align="center" />
          <el-table-column label="断裂风险" prop="breakageRisk" width="100" align="center" />
          <el-table-column label="脱发风险" prop="hairLossRisk" width="100" align="center" />
          <el-table-column label="头屑" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.dandruffLevel === 0" type="success">无</el-tag>
              <el-tag v-else-if="row.dandruffLevel === 1" type="info">轻微</el-tag>
              <el-tag v-else-if="row.dandruffLevel === 2" type="warning">中度</el-tag>
              <el-tag v-else type="danger">严重</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="检测日期" prop="detectionDate" width="120" />
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="handleViewHair(row)">查看</el-button>
              <el-button type="warning" link size="small" @click="handleEditHair(row)">编辑</el-button>
              <el-button type="danger" link size="small" @click="handleDeleteHair(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!hairLoading && !hairList.length" description="暂无检测记录" />
      </el-card>
    </div>

    <!-- ============ 甲面检测 ============ -->
    <div v-if="activeTab === 'nail'">
      <el-card>
        <template #header>
          <div class="card-header">
            <span>甲面检测记录</span>
            <div class="filter-form">
              <el-select v-model="memberFilter" placeholder="客户" filterable clearable remote :remote-method="searchMembers" style="width:200px" @change="loadNailList">
                <el-option v-for="m in memberList" :key="m.id" :label="m.name" :value="m.id" />
              </el-select>
              <el-select v-model="nailTypeFilter" placeholder="指甲类型" clearable style="width:140px" @change="loadNailList">
                <el-option v-for="o in nailTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
              <el-button type="primary" @click="handleAddNail">
                <el-icon><Plus /></el-icon>新增检测
              </el-button>
            </div>
          </div>
        </template>

        <el-table :data="nailList" border stripe v-loading="nailLoading">
          <el-table-column label="检测编号" prop="detectionNo" width="160" fixed />
          <el-table-column label="客户" prop="memberName" width="120" />
          <el-table-column label="指甲类型" width="120">
            <template #default="{ row }">
              <el-tag :style="{ background: getNailColor(row.nailType), color: '#fff' }">
                {{ getNailLabel(row.nailType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="综合得分" width="120" align="center">
            <template #default="{ row }">
              <span :style="{ color: getScoreColor(row.overallScore), fontWeight: 600 }">
                {{ row.overallScore || 0 }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="硬度" prop="hardnessScore" width="100" align="center" />
          <el-table-column label="光泽" prop="glossScore" width="100" align="center" />
          <el-table-column label="韧性" prop="toughnessScore" width="100" align="center" />
          <el-table-column label="平整度" prop="surfaceSmoothness" width="100" align="center" />
          <el-table-column label="断裂历史" prop="breakHistory" width="100" align="center" />
          <el-table-column label="检测日期" prop="detectionDate" width="120" />
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="handleViewNail(row)">查看</el-button>
              <el-button type="warning" link size="small" @click="handleEditNail(row)">编辑</el-button>
              <el-button type="danger" link size="small" @click="handleDeleteNail(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!nailLoading && !nailList.length" description="暂无检测记录" />
      </el-card>
    </div>

    <!-- ============ 检测报告 ============ -->
    <div v-if="activeTab === 'report'">
      <el-card>
        <template #header>
          <div class="card-header">
            <span>检测报告</span>
            <div class="filter-form">
              <el-select v-model="memberFilter" placeholder="客户" filterable clearable remote :remote-method="searchMembers" style="width:200px" @change="loadReports">
                <el-option v-for="m in memberList" :key="m.id" :label="m.name" :value="m.id" />
              </el-select>
              <el-select v-model="reportTypeFilter" placeholder="报告类型" clearable style="width:140px" @change="loadReports">
                <el-option v-for="o in reportTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
              <el-button type="primary" @click="handleGenerateReport">
                <el-icon><MagicStick /></el-icon>生成综合报告
              </el-button>
            </div>
          </div>
        </template>

        <el-table :data="reportList" border stripe v-loading="reportLoading">
          <el-table-column label="报告编号" prop="reportNo" width="180" fixed />
          <el-table-column label="客户" prop="memberName" width="120" />
          <el-table-column label="报告类型" width="120">
            <template #default="{ row }">
              <el-tag>{{ getReportTypeLabel(row.reportType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="综合得分" width="120" align="center">
            <template #default="{ row }">
              <span :style="{ color: getScoreColor(row.overallScore), fontWeight: 600, fontSize: '16px' }">
                {{ row.overallScore || 0 }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="优先级问题" width="280" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="concerns-text">{{ row.priorityConcerns || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="治疗周期" width="120" align="center">
            <template #default="{ row }">
              <span v-if="row.treatmentCycle">{{ row.treatmentCycle }}天</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="getReportStatusType(row.status)">{{ getReportStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="生成时间" prop="generateTime" width="160" />
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="handleViewReport(row)">查看</el-button>
              <el-button v-if="row.status === 'draft'" type="success" link size="small" @click="handleFinalizeReport(row)">确认</el-button>
              <el-button type="danger" link size="small" @click="handleDeleteReport(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!reportLoading && !reportList.length" description="暂无报告" />
      </el-card>
    </div>

    <!-- ============ 方案配置 ============ -->
    <div v-if="activeTab === 'solution'">
      <el-card>
        <template #header>
          <div class="card-header">
            <span>检测方案配置</span>
            <el-button type="primary" @click="handleAddSolution">
              <el-icon><Plus /></el-icon>新增方案
            </el-button>
          </div>
        </template>

        <el-table :data="solutionList" border stripe v-loading="solutionLoading">
          <el-table-column label="方案名称" prop="solutionName" min-width="200" fixed />
          <el-table-column label="适用类型" width="120">
            <template #default="{ row }">
              <el-tag>{{ getApplyTypeLabel(row.applyType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="触发条件" prop="triggerConditions" width="200" show-overflow-tooltip />
          <el-table-column label="分数阈值" prop="scoreThreshold" width="120" align="center" />
          <el-table-column label="优先级" prop="priority" width="100" align="center" />
          <el-table-column label="治疗周期" width="120" align="center">
            <template #default="{ row }">
              <span v-if="row.treatmentCycle">{{ row.treatmentCycle }}天</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-switch v-model="row.status" :active-value="1" :inactive-value="0" @change="toggleSolution(row)" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="handleEditSolution(row)">编辑</el-button>
              <el-button type="danger" link size="small" @click="handleDeleteSolution(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!solutionLoading && !solutionList.length" description="暂无方案" />
      </el-card>
    </div>

    <!-- ============ 肤质检测表单 ============ -->
    <el-dialog v-model="skinDialogVisible" :title="skinDialogTitle" width="800px" top="5vh">
      <el-form :model="skinForm" label-width="120px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="客户">
              <el-select v-model="skinForm.memberId" placeholder="选择客户" filterable :disabled="!!currentUserMemberId" style="width:100%">
                <el-option v-for="m in memberList" :key="m.id" :label="m.name" :value="m.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="检测日期">
              <el-date-picker v-model="skinForm.detectionDate" value-format="YYYY-MM-DD" type="date" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="肤质类型">
              <el-select v-model="skinForm.skinType" style="width:100%">
                <el-option v-for="o in skinTypeOptions" :key="o.value" :label="`${o.label} - ${o.description}`" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="检测设备">
              <el-select v-model="skinForm.detectionDevice" style="width:100%">
                <el-option v-for="o in deviceOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">检测指标</el-divider>
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="综合得分"><el-input-number v-model="skinForm.overallScore" :min="0" :max="100" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="水分"><el-input-number v-model="skinForm.moistureScore" :min="0" :max="100" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="油脂"><el-input-number v-model="skinForm.oilScore" :min="0" :max="100" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="弹性"><el-input-number v-model="skinForm.elasticityScore" :min="0" :max="100" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="毛孔"><el-input-number v-model="skinForm.poreScore" :min="0" :max="100" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="色素"><el-input-number v-model="skinForm.pigmentationScore" :min="0" :max="100" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="皱纹"><el-input-number v-model="skinForm.wrinkleScore" :min="0" :max="100" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="敏感度"><el-input-number v-model="skinForm.sensitivityScore" :min="0" :max="100" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="痘痘等级">
            <el-select v-model="skinForm.acneLevel" style="width:100%">
              <el-option v-for="o in levelOptions" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="黑眼圈等级">
            <el-select v-model="skinForm.darkCircleLevel" style="width:100%">
              <el-option v-for="o in levelOptions" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="8"><el-form-item label="色斑数量"><el-input-number v-model="skinForm.spotCount" :min="0" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="细纹数量"><el-input-number v-model="skinForm.fineLineCount" :min="0" /></el-form-item></el-col>
        </el-row>

        <el-form-item label="诊断建议">
          <el-input v-model="skinForm.diagnosisAdvice" type="textarea" :rows="3" placeholder="请填写诊断建议" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="skinForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="skinDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveSkin">保存</el-button>
      </template>
    </el-dialog>

    <!-- 发质检测对话框 -->
    <el-dialog v-model="hairDialogVisible" :title="hairDialogTitle" width="800px" top="5vh">
      <el-form :model="hairForm" label-width="120px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="客户">
              <el-select v-model="hairForm.memberId" placeholder="选择客户" filterable style="width:100%">
                <el-option v-for="m in memberList" :key="m.id" :label="m.name" :value="m.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="检测日期">
              <el-date-picker v-model="hairForm.detectionDate" value-format="YYYY-MM-DD" type="date" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="发质类型">
              <el-select v-model="hairForm.hairType" style="width:100%">
                <el-option v-for="o in hairTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="检测设备">
              <el-select v-model="hairForm.detectionDevice" style="width:100%">
                <el-option v-for="o in deviceOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">检测指标</el-divider>
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="综合得分"><el-input-number v-model="hairForm.overallScore" :min="0" :max="100" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="水分"><el-input-number v-model="hairForm.moistureScore" :min="0" :max="100" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="油脂"><el-input-number v-model="hairForm.oilScore" :min="0" :max="100" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="弹性"><el-input-number v-model="hairForm.elasticityScore" :min="0" :max="100" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="损伤"><el-input-number v-model="hairForm.damageScore" :min="0" :max="100" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="断裂风险"><el-input-number v-model="hairForm.breakageRisk" :min="0" :max="100" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="脱发风险"><el-input-number v-model="hairForm.hairLossRisk" :min="0" :max="100" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="分叉"><el-input-number v-model="hairForm.splitEndScore" :min="0" :max="100" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="头屑等级">
            <el-select v-model="hairForm.dandruffLevel" style="width:100%">
              <el-option v-for="o in levelOptions" :key="o.value" :label="o.label" :value="o.value" />
            </el-select>
          </el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="头皮敏感度"><el-input-number v-model="hairForm.scalpSensitivity" :min="0" :max="100" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="染色次数"><el-input-number v-model="hairForm.dyeCount" :min="0" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="烫发次数"><el-input-number v-model="hairForm.permCount" :min="0" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="诊断建议">
          <el-input v-model="hairForm.diagnosisAdvice" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="hairDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveHair">保存</el-button>
      </template>
    </el-dialog>

    <!-- 甲面检测对话框 -->
    <el-dialog v-model="nailDialogVisible" :title="nailDialogTitle" width="800px" top="5vh">
      <el-form :model="nailForm" label-width="120px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="客户">
              <el-select v-model="nailForm.memberId" placeholder="选择客户" filterable style="width:100%">
                <el-option v-for="m in memberList" :key="m.id" :label="m.name" :value="m.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="检测日期">
              <el-date-picker v-model="nailForm.detectionDate" value-format="YYYY-MM-DD" type="date" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="指甲类型">
              <el-select v-model="nailForm.nailType" style="width:100%">
                <el-option v-for="o in nailTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">检测指标</el-divider>
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="综合得分"><el-input-number v-model="nailForm.overallScore" :min="0" :max="100" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="硬度"><el-input-number v-model="nailForm.hardnessScore" :min="0" :max="100" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="光泽"><el-input-number v-model="nailForm.glossScore" :min="0" :max="100" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="水分"><el-input-number v-model="nailForm.moistureScore" :min="0" :max="100" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="韧性"><el-input-number v-model="nailForm.toughnessScore" :min="0" :max="100" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="平整度"><el-input-number v-model="nailForm.surfaceSmoothness" :min="0" :max="100" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="甲周皮肤"><el-input-number v-model="nailForm.periungualScore" :min="0" :max="100" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="甲沟炎风险"><el-input-number v-model="nailForm.paronychiaRisk" :min="0" :max="100" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="真菌风险"><el-input-number v-model="nailForm.fungalRisk" :min="0" :max="100" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="8"><el-form-item label="纵裂数量"><el-input-number v-model="nailForm.verticalCracks" :min="0" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="横纹数量"><el-input-number v-model="nailForm.horizontalRidges" :min="0" /></el-form-item></el-col>
          <el-col :span="8"><el-form-item label="倒刺数量"><el-input-number v-model="nailForm.hangnailCount" :min="0" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="诊断建议">
          <el-input v-model="nailForm.diagnosisAdvice" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="nailDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveNail">保存</el-button>
      </template>
    </el-dialog>

    <!-- 报告生成对话框 -->
    <el-dialog v-model="reportGenVisible" title="生成综合报告" width="400px">
      <el-form :model="reportGenForm" label-width="100px">
        <el-form-item label="客户">
          <el-select v-model="reportGenForm.memberId" placeholder="选择客户" filterable style="width:100%">
            <el-option v-for="m in memberList" :key="m.id" :label="m.name" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="报告类型">
          <el-select v-model="reportGenForm.reportType" style="width:100%">
            <el-option v-for="o in reportTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reportGenVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmGenerateReport">生成</el-button>
      </template>
    </el-dialog>

    <!-- 报告详情对话框 -->
    <el-dialog v-model="reportDetailVisible" title="检测报告" width="700px">
      <div v-if="currentReport">
        <h2 class="report-title">{{ currentReport.reportNo }}</h2>
        <el-tag>{{ getReportTypeLabel(currentReport.reportType) }}</el-tag>
        <el-tag :type="getReportStatusType(currentReport.status)" style="margin-left:8px">{{ getReportStatusLabel(currentReport.status) }}</el-tag>

        <div class="report-score">
          <div class="score-circle" :style="{ background: getScoreGradient(currentReport.overallScore) }">
            <span class="score-num">{{ currentReport.overallScore || 0 }}</span>
            <span class="score-label">综合得分</span>
          </div>
        </div>

        <el-descriptions :column="1" border style="margin-top:16px">
          <el-descriptions-item label="综合评估">{{ currentReport.overallAssessment || '-' }}</el-descriptions-item>
          <el-descriptions-item label="优先级问题">{{ currentReport.priorityConcerns || '-' }}</el-descriptions-item>
          <el-descriptions-item label="推荐项目">{{ currentReport.recommendedServices || '-' }}</el-descriptions-item>
          <el-descriptions-item label="推荐产品">{{ currentReport.recommendedProducts || '-' }}</el-descriptions-item>
          <el-descriptions-item label="居家护理">{{ currentReport.homeCareAdvice || '-' }}</el-descriptions-item>
          <el-descriptions-item label="治疗周期">{{ currentReport.treatmentCycle ? `${currentReport.treatmentCycle}天` : '-' }}</el-descriptions-item>
          <el-descriptions-item label="预期效果">{{ currentReport.expectedOutcome || '-' }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>

    <!-- 方案对话框 -->
    <el-dialog v-model="solutionDialogVisible" :title="solutionDialogTitle" width="600px">
      <el-form :model="solutionForm" label-width="100px">
        <el-form-item label="方案名称">
          <el-input v-model="solutionForm.solutionName" />
        </el-form-item>
        <el-form-item label="编码">
          <el-input v-model="solutionForm.solutionCode" />
        </el-form-item>
        <el-form-item label="适用类型">
          <el-select v-model="solutionForm.applyType" style="width:100%">
            <el-option label="肤质" value="skin" />
            <el-option label="发质" value="hair" />
            <el-option label="甲面" value="nail" />
          </el-select>
        </el-form-item>
        <el-form-item label="触发条件">
          <el-input v-model="solutionForm.triggerConditions" placeholder="JSON数组,例: ['sensitive','acne']" />
        </el-form-item>
        <el-form-item label="分数阈值">
          <el-input-number v-model="solutionForm.scoreThreshold" :min="0" :max="100" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-input-number v-model="solutionForm.priority" :min="0" :max="10" />
        </el-form-item>
        <el-form-item label="推荐项目">
          <el-input v-model="solutionForm.recommendedServices" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="推荐产品">
          <el-input v-model="solutionForm.recommendedProducts" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="居家护理">
          <el-input v-model="solutionForm.homeCareAdvice" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="治疗周期(天)">
          <el-input-number v-model="solutionForm.treatmentCycle" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="solutionForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="solutionDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveSolution">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, MagicStick } from '@element-plus/icons-vue'
import request from '@/utils/http'
import {
  detectionApi,
  skinTypeOptions,
  hairTypeOptions,
  nailTypeOptions,
  reportTypeOptions,
  reportStatusOptions,
  levelOptions,
  deviceOptions
} from '@/api/detection'

const activeTab = ref('skin')
const memberList = ref<any[]>([])

// ============ 肤质 ============
const skinList = ref<any[]>([])
const skinLoading = ref(false)
const memberFilter = ref<number | null>(null)
const skinTypeFilter = ref('')
const skinDialogVisible = ref(false)
const skinDialogTitle = ref('新增肤质检测')
const skinForm = reactive<any>({
  id: null,
  memberId: null,
  skinType: 'normal',
  overallScore: 80,
  moistureScore: 60,
  oilScore: 40,
  elasticityScore: 70,
  poreScore: 30,
  pigmentationScore: 30,
  wrinkleScore: 20,
  sensitivityScore: 30,
  acneLevel: 0,
  darkCircleLevel: 0,
  spotCount: 0,
  fineLineCount: 0,
  detectionDate: new Date().toISOString().slice(0, 10),
  detectionDevice: 'visia',
  diagnosisAdvice: '',
  remark: ''
})

const loadSkinList = async () => {
  skinLoading.value = true
  try {
    const res: any = await detectionApi.skinList({
      memberId: memberFilter.value || undefined,
      skinType: skinTypeFilter.value || undefined
    })
    skinList.value = res.data || []
  } catch (e) {
    skinList.value = []
  } finally {
    skinLoading.value = false
  }
}

const handleAddSkin = () => {
  Object.assign(skinForm, {
    id: null,
    memberId: memberFilter.value,
    skinType: 'normal',
    overallScore: 80,
    moistureScore: 60,
    oilScore: 40,
    elasticityScore: 70,
    poreScore: 30,
    pigmentationScore: 30,
    wrinkleScore: 20,
    sensitivityScore: 30,
    acneLevel: 0,
    darkCircleLevel: 0,
    spotCount: 0,
    fineLineCount: 0,
    detectionDate: new Date().toISOString().slice(0, 10),
    detectionDevice: 'visia',
    diagnosisAdvice: '',
    remark: ''
  })
  skinDialogTitle.value = '新增肤质检测'
  skinDialogVisible.value = true
}

const handleEditSkin = (row: any) => {
  Object.assign(skinForm, row)
  skinDialogTitle.value = '编辑肤质检测'
  skinDialogVisible.value = true
}

const handleViewSkin = (row: any) => {
  Object.assign(skinForm, row)
  skinDialogTitle.value = '查看肤质检测'
  skinDialogVisible.value = true
}

const handleSaveSkin = async () => {
  try {
    if (skinForm.id) {
      await detectionApi.skinUpdate(skinForm)
    } else {
      await detectionApi.skinCreate(skinForm)
    }
    ElMessage.success('保存成功')
    skinDialogVisible.value = false
    loadSkinList()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

const handleDeleteSkin = async (row: any) => {
  try {
    await ElMessageBox.confirm('确定删除该检测记录吗？', '提示', { type: 'warning' })
    await detectionApi.skinDelete(row.id)
    ElMessage.success('删除成功')
    loadSkinList()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

// ============ 发质 ============
const hairList = ref<any[]>([])
const hairLoading = ref(false)
const hairTypeFilter = ref('')
const hairDialogVisible = ref(false)
const hairDialogTitle = ref('新增发质检测')
const hairForm = reactive<any>({
  id: null,
  memberId: null,
  hairType: 'normal',
  overallScore: 80,
  moistureScore: 60,
  oilScore: 40,
  elasticityScore: 70,
  damageScore: 20,
  breakageRisk: 20,
  hairLossRisk: 20,
  splitEndScore: 20,
  dandruffLevel: 0,
  scalpSensitivity: 20,
  dyeCount: 0,
  permCount: 0,
  detectionDate: new Date().toISOString().slice(0, 10),
  detectionDevice: 'microscope',
  diagnosisAdvice: ''
})

const loadHairList = async () => {
  hairLoading.value = true
  try {
    const res: any = await detectionApi.hairList({
      memberId: memberFilter.value || undefined,
      hairType: hairTypeFilter.value || undefined
    })
    hairList.value = res.data || []
  } catch (e) {
    hairList.value = []
  } finally {
    hairLoading.value = false
  }
}

const handleAddHair = () => {
  Object.assign(hairForm, {
    id: null,
    memberId: memberFilter.value,
    hairType: 'normal',
    overallScore: 80,
    moistureScore: 60,
    oilScore: 40,
    elasticityScore: 70,
    damageScore: 20,
    breakageRisk: 20,
    hairLossRisk: 20,
    splitEndScore: 20,
    dandruffLevel: 0,
    scalpSensitivity: 20,
    detectionDate: new Date().toISOString().slice(0, 10),
    detectionDevice: 'microscope',
    diagnosisAdvice: ''
  })
  hairDialogTitle.value = '新增发质检测'
  hairDialogVisible.value = true
}

const handleEditHair = (row: any) => {
  Object.assign(hairForm, row)
  hairDialogTitle.value = '编辑发质检测'
  hairDialogVisible.value = true
}

const handleViewHair = (row: any) => {
  Object.assign(hairForm, row)
  hairDialogTitle.value = '查看发质检测'
  hairDialogVisible.value = true
}

const handleSaveHair = async () => {
  try {
    if (hairForm.id) {
      await detectionApi.hairUpdate(hairForm)
    } else {
      await detectionApi.hairCreate(hairForm)
    }
    ElMessage.success('保存成功')
    hairDialogVisible.value = false
    loadHairList()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

const handleDeleteHair = async (row: any) => {
  try {
    await ElMessageBox.confirm('确定删除该检测记录吗？', '提示', { type: 'warning' })
    await detectionApi.hairDelete(row.id)
    ElMessage.success('删除成功')
    loadHairList()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

// ============ 甲面 ============
const nailList = ref<any[]>([])
const nailLoading = ref(false)
const nailTypeFilter = ref('')
const nailDialogVisible = ref(false)
const nailDialogTitle = ref('新增甲面检测')
const nailForm = reactive<any>({
  id: null,
  memberId: null,
  nailType: 'strong',
  overallScore: 80,
  hardnessScore: 70,
  glossScore: 70,
  moistureScore: 70,
  periungualScore: 70,
  surfaceSmoothness: 70,
  toughnessScore: 70,
  paronychiaRisk: 20,
  fungalRisk: 10,
  verticalCracks: 0,
  horizontalRidges: 0,
  hangnailCount: 0,
  breakHistory: 0,
  detectionDate: new Date().toISOString().slice(0, 10),
  detectionDevice: 'manual',
  diagnosisAdvice: ''
})

const loadNailList = async () => {
  nailLoading.value = true
  try {
    const res: any = await detectionApi.nailList({
      memberId: memberFilter.value || undefined,
      nailType: nailTypeFilter.value || undefined
    })
    nailList.value = res.data || []
  } catch (e) {
    nailList.value = []
  } finally {
    nailLoading.value = false
  }
}

const handleAddNail = () => {
  Object.assign(nailForm, {
    id: null,
    memberId: memberFilter.value,
    nailType: 'strong',
    overallScore: 80,
    hardnessScore: 70,
    glossScore: 70,
    moistureScore: 70,
    periungualScore: 70,
    surfaceSmoothness: 70,
    toughnessScore: 70,
    paronychiaRisk: 20,
    fungalRisk: 10,
    verticalCracks: 0,
    horizontalRidges: 0,
    hangnailCount: 0,
    breakHistory: 0,
    detectionDate: new Date().toISOString().slice(0, 10),
    detectionDevice: 'manual',
    diagnosisAdvice: ''
  })
  nailDialogTitle.value = '新增甲面检测'
  nailDialogVisible.value = true
}

const handleEditNail = (row: any) => {
  Object.assign(nailForm, row)
  nailDialogTitle.value = '编辑甲面检测'
  nailDialogVisible.value = true
}

const handleViewNail = (row: any) => {
  Object.assign(nailForm, row)
  nailDialogTitle.value = '查看甲面检测'
  nailDialogVisible.value = true
}

const handleSaveNail = async () => {
  try {
    if (nailForm.id) {
      await detectionApi.nailUpdate(nailForm)
    } else {
      await detectionApi.nailCreate(nailForm)
    }
    ElMessage.success('保存成功')
    nailDialogVisible.value = false
    loadNailList()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

const handleDeleteNail = async (row: any) => {
  try {
    await ElMessageBox.confirm('确定删除该检测记录吗？', '提示', { type: 'warning' })
    await detectionApi.nailDelete(row.id)
    ElMessage.success('删除成功')
    loadNailList()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

// ============ 报告 ============
const reportList = ref<any[]>([])
const reportLoading = ref(false)
const reportTypeFilter = ref('')
const reportGenVisible = ref(false)
const reportDetailVisible = ref(false)
const currentReport = ref<any>(null)
const reportGenForm = reactive({
  memberId: null as number | null,
  reportType: 'comprehensive'
})

const loadReports = async () => {
  reportLoading.value = true
  try {
    const res: any = await detectionApi.reportList({
      memberId: memberFilter.value || undefined,
      reportType: reportTypeFilter.value || undefined
    })
    reportList.value = res.data || []
  } catch (e) {
    reportList.value = []
  } finally {
    reportLoading.value = false
  }
}

const handleGenerateReport = () => {
  reportGenForm.memberId = memberFilter.value
  reportGenForm.reportType = 'comprehensive'
  reportGenVisible.value = true
}

const confirmGenerateReport = async () => {
  try {
    const res: any = await detectionApi.reportGenerate(reportGenForm)
    ElMessage.success('报告生成成功')
    reportGenVisible.value = false
    loadReports()
    handleViewReport(res.data)
  } catch (e) {
    ElMessage.error('生成失败')
  }
}

const handleViewReport = (row: any) => {
  currentReport.value = row
  reportDetailVisible.value = true
}

const handleFinalizeReport = async (row: any) => {
  try {
    await detectionApi.reportUpdate({ id: row.id, status: 'final' })
    ElMessage.success('已确认')
    loadReports()
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

const handleDeleteReport = async (row: any) => {
  try {
    await ElMessageBox.confirm('确定删除该报告吗？', '提示', { type: 'warning' })
    await detectionApi.reportDelete(row.id)
    ElMessage.success('删除成功')
    loadReports()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

// ============ 方案 ============
const solutionList = ref<any[]>([])
const solutionLoading = ref(false)
const solutionDialogVisible = ref(false)
const solutionDialogTitle = ref('新增方案')
const solutionForm = reactive<any>({
  id: null,
  solutionName: '',
  solutionCode: '',
  applyType: 'skin',
  triggerConditions: '',
  scoreThreshold: 80,
  priority: 5,
  recommendedServices: '',
  recommendedProducts: '',
  homeCareAdvice: '',
  treatmentCycle: 30,
  status: 1
})

const loadSolutions = async () => {
  solutionLoading.value = true
  try {
    const res: any = await detectionApi.solutionList({})
    solutionList.value = res.data || []
  } catch (e) {
    solutionList.value = []
  } finally {
    solutionLoading.value = false
  }
}

const handleAddSolution = () => {
  Object.assign(solutionForm, {
    id: null,
    solutionName: '',
    solutionCode: '',
    applyType: 'skin',
    triggerConditions: '',
    scoreThreshold: 80,
    priority: 5,
    recommendedServices: '',
    recommendedProducts: '',
    homeCareAdvice: '',
    treatmentCycle: 30,
    status: 1
  })
  solutionDialogTitle.value = '新增方案'
  solutionDialogVisible.value = true
}

const handleEditSolution = (row: any) => {
  Object.assign(solutionForm, row)
  solutionDialogTitle.value = '编辑方案'
  solutionDialogVisible.value = true
}

const handleSaveSolution = async () => {
  try {
    if (solutionForm.id) {
      await detectionApi.solutionUpdate(solutionForm)
    } else {
      await detectionApi.solutionCreate(solutionForm)
    }
    ElMessage.success('保存成功')
    solutionDialogVisible.value = false
    loadSolutions()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

const handleDeleteSolution = async (row: any) => {
  try {
    await ElMessageBox.confirm('确定删除该方案吗？', '提示', { type: 'warning' })
    await detectionApi.solutionDelete(row.id)
    ElMessage.success('删除成功')
    loadSolutions()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

const toggleSolution = async (row: any) => {
  try {
    await detectionApi.solutionUpdate(row)
  } catch (e) {
    ElMessage.error('更新失败')
  }
}

// ============ 辅助 ============
const getSkinLabel = (t: string) => skinTypeOptions.find(o => o.value === t)?.label || t
const getSkinColor = (t: string) => skinTypeOptions.find(o => o.value === t)?.color || '#909399'
const getHairLabel = (t: string) => hairTypeOptions.find(o => o.value === t)?.label || t
const getHairColor = (t: string) => hairTypeOptions.find(o => o.value === t)?.color || '#909399'
const getNailLabel = (t: string) => nailTypeOptions.find(o => o.value === t)?.label || t
const getNailColor = (t: string) => nailTypeOptions.find(o => o.value === t)?.color || '#909399'

const getReportTypeLabel = (t: string) => reportTypeOptions.find(o => o.value === t)?.label || t
const getReportStatusLabel = (s: string) => reportStatusOptions.find(o => o.value === s)?.label || s
const getReportStatusType = (s: string) => reportStatusOptions.find(o => o.value === s)?.type || 'info'

const getApplyTypeLabel = (t: string) => {
  const map: any = { skin: '肤质', hair: '发质', nail: '甲面' }
  return map[t] || t
}

const getScoreColor = (score: number) => {
  if (!score) return '#909399'
  if (score >= 80) return '#67C23A'
  if (score >= 60) return '#E6A23C'
  return '#F56C6C'
}

const getScoreGradient = (score: number) => {
  const c = getScoreColor(score)
  return `linear-gradient(135deg, ${c}, ${c}99)`
}

const searchMembers = async (keyword: string) => {
  try {
    const res: any = await request.get('/member/search', { params: { keyword } })
    memberList.value = res.data || []
  } catch (e) {
    memberList.value = []
  }
}

const handleTabChange = (tab: string) => {
  if (tab === 'skin') loadSkinList()
  else if (tab === 'hair') loadHairList()
  else if (tab === 'nail') loadNailList()
  else if (tab === 'report') loadReports()
  else if (tab === 'solution') loadSolutions()
}

onMounted(() => {
  searchMembers('')
  loadSkinList()
})
</script>

<style scoped>
.detection-page {
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

.concerns-text {
  color: #F56C6C;
  font-size: 12px;
}

.report-title {
  margin: 0 0 8px 0;
  font-size: 18px;
  font-weight: 600;
}

.report-score {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}

.score-circle {
  width: 100px;
  height: 100px;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #fff;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.15);
}

.score-num {
  font-size: 28px;
  font-weight: 700;
}

.score-label {
  font-size: 12px;
  opacity: 0.9;
}
</style>