<template>
  <div class="follow-task-page">
    <!-- ① 页面标题 + 自动刷新 + 新建按钮（醒目） -->
    <el-card class="header-card">
      <div class="page-header">
        <div class="page-title">
          <el-icon><Phone /></el-icon>
          <span class="title">回访任务</span>
          <el-tag effect="plain" round type="info">{{ total }} 条</el-tag>
        </div>
        <div class="page-tools">
          <span v-if="autoCountdown > 0" class="auto-tip">
            <el-icon><Loading /></el-icon>
            {{ autoCountdown }}s 后自动刷新
          </span>
          <el-tooltip content="刷新" placement="top">
            <el-button :icon="Refresh" circle plain @click="loadAll" />
          </el-tooltip>
          <el-button :icon="Download" plain @click="onExport">导出</el-button>
          <el-button :icon="Setting" plain @click="columnSettingVisible = true">列设置</el-button>
          <el-button :icon="Grid" plain @click="openKanban">看板视图</el-button>
          <el-button :icon="SetUp" plain @click="$router.push('/crm/care/follow-rule')">自动规则</el-button>
          <el-button type="primary" :icon="Plus" size="default" @click="openCreate">新建任务</el-button>
          <el-button :icon="MagicStick" plain @click="insertDemoData">插入演示数据</el-button>
        </div>
      </div>
    </el-card>

    <!-- ② KPI 概览：4 项关键指标 + 点击筛选 -->
    <el-row :gutter="12" class="kpi-row">
      <el-col :xs="12" :sm="6">
        <div class="kpi-mini" :class="activeTab === 'todo' ? 'on' : ''" @click="filterByTab('todo')">
          <div class="kmi-top">
            <el-icon class="kmi-icon"><Bell /></el-icon>
            <el-tag effect="plain" size="small" type="info">今日待办</el-tag>
          </div>
          <div class="kmi-num">{{ stats.todayPending }}</div>
          <div class="kmi-foot">
            <span>本周 {{ stats.weekPending }}</span>
            <span class="muted">共 {{ stats.pending }}</span>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="kpi-mini" :class="activeTab === 'overdue' ? 'on' : ''" @click="filterByTab('overdue')">
          <div class="kmi-top">
            <el-icon class="kmi-icon red"><Warning /></el-icon>
            <el-tag effect="plain" size="small" type="danger">逾期</el-tag>
          </div>
          <div class="kmi-num red">{{ stats.overdue }}</div>
          <div class="kmi-foot">
            <span class="red">⚠ 立即处理</span>
            <span class="muted">最长 {{ stats.longestOverdueDays }} 天</span>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="kpi-mini" :class="activeTab === 'mine' ? 'on' : ''" @click="filterByTab('mine')">
          <div class="kmi-top">
            <el-icon class="kmi-icon blue"><User /></el-icon>
            <el-tag effect="plain" size="small" type="primary">我的</el-tag>
          </div>
          <div class="kmi-num">{{ stats.minePending }}</div>
          <div class="kmi-foot">
            <span>{{ currentUserName }}</span>
            <span class="muted">已处理 {{ stats.mineDone }}</span>
          </div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="kpi-mini" :class="activeTab === 'done' ? 'on' : ''" @click="filterByTab('done')">
          <div class="kmi-top">
            <el-icon class="kmi-icon green"><CircleCheck /></el-icon>
            <el-tag effect="plain" size="small" type="success">已完成</el-tag>
          </div>
          <div class="kmi-num green">{{ stats.doneToday }}<span class="kmi-unit">/{{ stats.doneWeek }}</span></div>
          <div class="kmi-foot">
            <span>完成率 {{ stats.completionRate }}%</span>
            <span class="muted">↑ 较上周{{ stats.rateDelta }}</span>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- ③ 快捷标签：我的待办 / 全部 / 逾期 / 已完成 -->
    <el-card class="tab-card">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane name="todo" :label="`我的待办 (${stats.todayPending})`" />
        <el-tab-pane name="all" label="全部任务" />
        <el-tab-pane name="overdue" :label="`逾期 (${stats.overdue})`" />
        <el-tab-pane name="mine" :label="`我处理 (${stats.minePending})`" />
        <el-tab-pane name="done" label="已完成" />
        <template #suffix>
          <span class="tab-extra muted">共 {{ total }} 条 · 当前第 {{ filter.pageNum }} 页</span>
        </template>
      </el-tabs>
    </el-card>

    <!-- ④ 增强筛选：门店 / 负责人 / 日期 / 优先级 -->
    <el-card class="search-bar">
      <el-form :inline="true" label-suffix="" class="search-form">
        <el-form-item label="关键字">
          <el-input v-model="filter.keyword" placeholder="会员 / 手机号 / 主题" style="width: 200px" clearable @keyup.enter="loadData" />
        </el-form-item>
        <el-form-item label="门店">
          <el-select v-model="filter.storeId" placeholder="全部门店" clearable style="width: 160px" @change="loadData">
            <el-option v-for="s in storeOptions" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责人">
          <el-select v-model="filter.ownerId" placeholder="全部" clearable filterable style="width: 140px" @change="loadData">
            <el-option v-for="u in ownerOptions" :key="u.id" :label="u.name" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="filter.taskType" placeholder="全部类型" clearable style="width: 140px" @change="loadData">
            <el-option label="疗程后" value="POST_TREATMENT" />
            <el-option label="生日" value="BIRTHDAY" />
            <el-option label="到期" value="EXPIRE" />
            <el-option label="投诉" value="COMPLAINT" />
            <el-option label="自定义" value="CUSTOM" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filter.status" placeholder="全部状态" clearable style="width: 120px" @change="loadData">
            <el-option :value="1" label="待处理" />
            <el-option :value="2" label="处理中" />
            <el-option :value="3" label="已完成" />
            <el-option :value="4" label="已关闭" />
            <el-option :value="5" label="已逾期" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="filter.priority" placeholder="全部优先级" clearable style="width: 120px" @change="loadData">
            <el-option :value="3" label="高" />
            <el-option :value="2" label="中" />
            <el-option :value="1" label="低" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <el-radio-group v-model="dateQuickKey" size="small" @change="onQuickDate">
            <el-radio-button value="all">全部</el-radio-button>
            <el-radio-button value="today">今日</el-radio-button>
            <el-radio-button value="week">本周</el-radio-button>
            <el-radio-button value="month">本月</el-radio-button>
          </el-radio-group>
          <el-date-picker
            v-model="dateRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始"
            end-placeholder="结束"
            style="width: 280px; margin-left: 8px"
            @change="onDateRangeChange"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="loadData">查询</el-button>
          <el-button :icon="RefreshLeft" @click="resetFilter">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- ⑤ 表格：行点击查看详情 -->
    <el-card class="table-card" v-loading="loading">
      <!-- ⑥ 批量操作 -->
      <div class="table-tools" v-if="selected.length">
        <el-tag effect="dark" type="info" size="default">已选 {{ selected.length }} 条</el-tag>
        <el-button size="small" type="success" :icon="Check" @click="batch('finish')">批量完成</el-button>
        <el-button size="small" type="warning" :icon="UserFilled" @click="batchAssignVisible = true">批量转派</el-button>
        <el-button size="small" :icon="User" @click="batchUpdateOwner">改处理人</el-button>
        <el-button size="small" type="danger" :icon="Delete" @click="batch('close')">批量关闭</el-button>
        <el-button size="small" text @click="selected = []">取消选择</el-button>
      </div>

      <el-table
        :data="rows"
        border
        stripe
        :row-key="(r: any) => r.id"
        @selection-change="(v: any[]) => selected = v"
        @row-click="onRowClick"
        :class="{ 'clickable-table': true }"
        empty-text="暂无回访任务，点击右上角「新建任务」开始"
      >
        <el-table-column type="selection" width="46" />
        <el-table-column prop="taskNo" label="任务号" width="170">
          <template #default="{ row }">
            <span class="task-no">{{ row.taskNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="taskTypeTag(row.taskType)" effect="plain">{{ taskTypeLabel(row.taskType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="优先级" width="80">
          <template #default="{ row }">
            <div class="prio">
              <span class="prio-dot" :class="priorityClass(row.priority)"></span>
              <span>{{ priorityLabel(row.priority) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="会员" min-width="200">
          <template #default="{ row }">
            <div class="member-cell">
              <el-avatar :size="24" class="member-avatar">{{ (row.memberName || '?').charAt(0) }}</el-avatar>
              <div class="member-info">
                <div class="member-name">{{ row.memberName || '—' }}</div>
                <div class="member-phone muted">{{ row.memberPhone || '未填写' }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="subject" label="主题" min-width="220">
          <template #default="{ row }">
            <div class="subject-cell">
              <span class="subject-text">{{ row.subject }}</span>
              <el-tag v-if="row.isAutoGenerated" size="small" type="info" effect="plain" class="auto-tag">自动</el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="处理人" width="110">
          <template #default="{ row }">
            <span v-if="row.ownerName">{{ row.ownerName }}</span>
            <el-button v-else link type="primary" size="small" @click.stop="openAssign(row)">待分配</el-button>
          </template>
        </el-table-column>
        <el-table-column label="计划时间" width="160">
          <template #default="{ row }">
            <div :class="{ 'overdue-row': row.status !== 3 && row.status !== 4 && row.plannedAt && new Date(row.plannedAt) < new Date() }">
              {{ row.plannedAt }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="SLA" width="170">
          <template #default="{ row }">
            <div :class="{ 'sla-warn': row.status !== 3 && row.status !== 4 && row.slaExpireAt && new Date(row.slaExpireAt) < new Date() }">
              <el-icon v-if="row.status !== 3 && row.status !== 4 && row.slaExpireAt && new Date(row.slaExpireAt) < new Date()">
                <WarningFilled />
              </el-icon>
              {{ row.slaExpireAt || '—' }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="评分" width="80">
          <template #default="{ row }">
            <span v-if="row.rating" class="rating">{{ '⭐'.repeat(row.rating) }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" @click.stop="openDetail(row)">详情</el-button>
            <el-button link size="small" @click.stop="openAssign(row)">分配</el-button>
            <el-button link size="small" :disabled="row.status !== 1" @click.stop="startTask(row)">开始</el-button>
            <el-button link size="small" type="primary" :disabled="row.status !== 2" @click.stop="openFinish(row)">完成</el-button>
            <el-button link size="small" type="danger" :disabled="row.status === 3 || row.status === 4" @click.stop="closeTask(row)">关闭</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="filter.pageNum"
          v-model:page-size="filter.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="loadData"
          @size-change="loadData"
        />
      </div>
    </el-card>

    <!-- ⑨ 浮动新建按钮（移动端友好） -->
    <el-button class="fab-btn" type="primary" :icon="Plus" circle size="large" @click="openCreate" />

    <!-- 新建任务 -->
    <el-dialog v-model="dialog.visible" title="新建回访任务" width="640px" @closed="resetDialog">
      <el-form :model="form" label-width="100px">
        <el-form-item label="会员ID" required>
          <el-input v-model.number="form.memberId" type="number" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.taskType" style="width: 100%">
            <el-option label="疗程后回访" value="POST_TREATMENT" />
            <el-option label="生日关怀" value="BIRTHDAY" />
            <el-option label="到期提醒" value="EXPIRE" />
            <el-option label="投诉回访" value="COMPLAINT" />
            <el-option label="自定义" value="CUSTOM" />
          </el-select>
        </el-form-item>
        <el-form-item label="主题" required>
          <el-input v-model="form.subject" />
        </el-form-item>
        <el-form-item label="优先级">
          <el-radio-group v-model="form.priority">
            <el-radio :value="1">低</el-radio>
            <el-radio :value="2">中</el-radio>
            <el-radio :value="3">高</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="回访要点">
          <el-input v-model="form.content" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="建议话术">
          <el-input v-model="form.suggestedScript" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" @click="save" :loading="dialog.saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分配 -->
    <el-dialog v-model="assignDialog.visible" title="分配处理人" width="500px">
      <el-form label-width="80px">
        <el-form-item label="处理人">
          <el-input v-model="assignDialog.ownerName" placeholder="姓名" />
        </el-form-item>
        <el-form-item label="用户ID">
          <el-input v-model.number="assignDialog.ownerId" type="number" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="assign">确定</el-button>
      </template>
    </el-dialog>

    <!-- 完成 -->
    <el-dialog v-model="finishDialog.visible" title="完成回访" width="500px">
      <el-form label-width="80px">
        <el-form-item label="结果">
          <el-radio-group v-model="finishDialog.resultType">
            <el-radio value="SATISFIED">满意</el-radio>
            <el-radio value="NORMAL">一般</el-radio>
            <el-radio value="UNSATISFIED">不满意</el-radio>
            <el-radio value="COMPLAINT">投诉</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="评分">
          <el-rate v-model="finishDialog.rating" :max="5" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="finishDialog.resultNote" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="finishDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="finish">提交</el-button>
      </template>
    </el-dialog>

    <!-- 详情 / 列设置 / 批量转派 -->
    <el-dialog v-model="detailVisible" title="回访任务详情" width="760px" @closed="onDetailClosed">
      <el-tabs v-model="detailTab" v-if="detail">
        <el-tab-pane label="基础信息" name="basic">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="任务号">{{ detail.taskNo }}</el-descriptions-item>
            <el-descriptions-item label="类型">
              <el-tag size="small" :type="taskTypeTag(detail.taskType)">{{ taskTypeLabel(detail.taskType) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="会员">{{ detail.memberName }}</el-descriptions-item>
            <el-descriptions-item label="手机">{{ detail.memberPhone }}</el-descriptions-item>
            <el-descriptions-item label="主题" :span="2">{{ detail.subject }}</el-descriptions-item>
            <el-descriptions-item label="优先级">{{ priorityLabel(detail.priority) }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag size="small" :type="statusTag(detail.status)">{{ statusLabel(detail.status) }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="处理人">{{ detail.ownerName || '未分配' }}</el-descriptions-item>
            <el-descriptions-item label="计划时间">{{ detail.plannedAt }}</el-descriptions-item>
            <el-descriptions-item label="回访要点" :span="2">{{ detail.content || '—' }}</el-descriptions-item>
            <el-descriptions-item label="建议话术" :span="2">{{ detail.suggestedScript || '—' }}</el-descriptions-item>
            <el-descriptions-item label="回访结果" :span="2">
              {{ detail.resultType || '—' }}
              <span v-if="detail.rating" class="rating">{{ '⭐'.repeat(detail.rating) }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="回访备注" :span="2">{{ detail.resultNote || '—' }}</el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <el-tab-pane :label="`时间线 (${taskNotes.length})`" name="timeline">
          <div v-if="!taskNotes.length" class="empty-timeline">
            <el-empty :image-size="80" description="暂无操作记录" />
          </div>
          <el-timeline v-else class="follow-timeline">
            <el-timeline-item
              v-for="n in taskNotes"
              :key="n.id"
              :timestamp="n.createTime"
              :color="noteMeta(n.actionType).color"
              placement="top"
            >
              <el-card shadow="never" class="note-card">
                <div class="note-head">
                  <el-tag :color="noteMeta(n.actionType).color" effect="dark" size="small" style="color:#fff;border:none">
                    {{ noteMeta(n.actionType).label }}
                  </el-tag>
                  <span class="muted">{{ n.operatorName || '系统' }}</span>
                </div>
                <div class="note-content">{{ n.content }}</div>
                <div v-if="n.fromStatus != null && n.toStatus != null && n.fromStatus !== 0 && n.toStatus !== 0" class="note-status">
                  <el-tag size="small" :type="statusTag(n.fromStatus)">{{ statusLabel(n.fromStatus) }}</el-tag>
                  <span class="arrow">→</span>
                  <el-tag size="small" :type="statusTag(n.toStatus)">{{ statusLabel(n.toStatus) }}</el-tag>
                </div>
              </el-card>
            </el-timeline-item>
          </el-timeline>
        </el-tab-pane>

        <el-tab-pane
          :label="`会员回访历史${memberTimeline ? ` (${memberTimeline.summary?.taskCount || 0})` : ''}`"
          name="history"
        >
          <template v-if="memberTimeline">
            <!-- 概览卡 -->
            <div class="history-summary">
              <div class="hs-card">
                <div class="hs-label">累计任务</div>
                <div class="hs-num">{{ memberTimeline.summary?.taskCount || 0 }}</div>
              </div>
              <div class="hs-card">
                <div class="hs-label">已完成</div>
                <div class="hs-num success">{{ memberTimeline.summary?.doneCount || 0 }}</div>
              </div>
              <div class="hs-card">
                <div class="hs-label">逾期</div>
                <div class="hs-num danger">{{ memberTimeline.summary?.overdueCount || 0 }}</div>
              </div>
              <div class="hs-card">
                <div class="hs-label">平均评分</div>
                <div class="hs-num">
                  {{ (memberTimeline.summary?.avgRating || 0).toFixed ? (memberTimeline.summary.avgRating).toFixed(1) : '0.0' }}
                </div>
              </div>
            </div>

            <el-empty v-if="!memberTimeline.tasks?.length" :image-size="60" description="该会员暂无历史回访" />
            <el-table v-else :data="memberTimeline.tasks" size="small" border stripe>
              <el-table-column prop="taskNo" label="任务号" width="170" />
              <el-table-column label="类型" width="100">
                <template #default="{ row }">
                  <el-tag size="small" :type="taskTypeTag(row.taskType)" effect="plain">
                    {{ taskTypeLabel(row.taskType) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="subject" label="主题" show-overflow-tooltip />
              <el-table-column label="处理人" width="100" prop="ownerName" />
              <el-table-column label="计划时间" width="160" prop="plannedAt" />
              <el-table-column label="结果" width="100">
                <template #default="{ row }">
                  <span v-if="row.resultType">{{ row.resultType }}</span>
                  <span v-else class="muted">—</span>
                </template>
              </el-table-column>
              <el-table-column label="评分" width="80">
                <template #default="{ row }">
                  <span v-if="row.rating" class="rating">{{ '⭐'.repeat(row.rating) }}</span>
                  <span v-else class="muted">—</span>
                </template>
              </el-table-column>
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag size="small" :type="statusTag(row.status)">{{ statusLabel(row.status) }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </template>
          <el-empty v-else :image-size="60" description="加载中或无数据" />
        </el-tab-pane>
      </el-tabs>
    </el-dialog>

    <el-dialog v-model="batchAssignVisible" title="批量转派 / 改处理人" width="500px">
      <el-form label-width="80px">
        <el-form-item label="处理人">
          <el-input v-model="batchAssign.name" placeholder="姓名" />
        </el-form-item>
        <el-form-item label="用户ID">
          <el-input v-model.number="batchAssign.id" type="number" />
        </el-form-item>
        <el-form-item>
          <el-text type="info">将对 {{ selected.length }} 条任务生效</el-text>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchAssignVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmBatchAssign">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="columnSettingVisible" title="列设置" width="400px">
      <el-checkbox-group v-model="visibleColumns">
        <el-checkbox v-for="c in allColumnDefs" :key="c.key" :value="c.key">{{ c.label }}</el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="columnSettingVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- ⑫ 看板视图：按处理人分列 + 拖拽换列 = 转派 -->
    <el-dialog v-model="kanbanVisible" title="回访任务看板（拖拽换列 = 转派）" width="92%" top="4vh"
      :close-on-click-modal="false" @opened="loadKanban">
      <div class="kanban-toolbar">
        <el-input v-model="kanban.keyword" placeholder="按会员/手机/主题筛选" clearable
          style="width: 240px" @input="filterKanban" />
        <el-select v-model="kanban.statusFilter" placeholder="状态" clearable style="width: 130px" @change="filterKanban">
          <el-option label="仅待处理" :value="1" />
          <el-option label="仅处理中" :value="2" />
          <el-option label="仅已完成" :value="3" />
          <el-option label="仅已逾期" :value="5" />
        </el-select>
        <span class="muted">共 {{ filteredTasks.length }} 张任务卡 · {{ kanbanColumns.length }} 列</span>
        <div style="flex:1" />
        <el-button :icon="Refresh" plain @click="loadKanban">刷新</el-button>
      </div>

      <div class="kanban-board" v-loading="kanban.loading">
        <div v-if="!kanbanColumns.length" class="kanban-empty">
          <el-empty description="暂无可分配的处理人/任务" />
        </div>
        <div v-for="col in kanbanColumns" :key="col.key" class="kanban-col"
          @dragover.prevent="onDragOver($event)" @dragenter.prevent="onDragEnter($event, col)"
          @dragleave="onDragLeave($event, col)" @drop="onDrop($event, col)"
          :class="{ 'drop-active': col.dropActive }">
          <div class="kanban-col-head">
            <el-avatar :size="28" class="kanban-avatar"
              :style="{ background: col.color }">
              {{ (col.name || '?').charAt(0) }}
            </el-avatar>
            <div class="kanban-col-title">
              <div class="kanban-name">{{ col.name }}</div>
              <div class="kanban-stat">
                <span>{{ col.tasks.length }} 个</span>
                <span v-if="col.overdueCount" class="red">⚠ {{ col.overdueCount }} 逾期</span>
                <span v-if="col.todoCount" class="orange">● {{ col.todoCount }} 待办</span>
              </div>
            </div>
          </div>
          <div class="kanban-cards">
            <div v-for="t in col.tasks" :key="t.id" class="kanban-card"
              :class="{ 'prio-high': t.priority === 3, 'is-overdue': isOverdue(t) }"
              draggable="true" @dragstart="onDragStart($event, t)" @dragend="onDragEnd">
              <div class="kc-head">
                <span class="kc-type" :class="`kct-${t.taskType}`">
                  {{ taskTypeLabel(t.taskType) }}
                </span>
                <span class="kc-prio" :class="`prio-${t.priority}`">
                  {{ priorityLabel(t.priority) }}
                </span>
              </div>
              <div class="kc-subject" :title="t.subject">{{ t.subject }}</div>
              <div class="kc-member">
                <el-icon><User /></el-icon>
                <span>{{ t.memberName }}</span>
                <span class="muted">{{ t.memberPhone || '' }}</span>
              </div>
              <div class="kc-meta">
                <span :class="{ 'red': isOverdue(t) }">
                  <el-icon><Clock /></el-icon>
                  {{ shortTime(t.plannedAt) }}
                </span>
                <span v-if="t.rating" class="rating">{{ '⭐'.repeat(t.rating) }}</span>
                <el-tag v-if="t.status === 3" size="small" type="success">已完成</el-tag>
                <el-tag v-else-if="t.status === 4" size="small">已关闭</el-tag>
              </div>
            </div>
            <div v-if="!col.tasks.length" class="kanban-col-empty">拖拽任务到此处分配</div>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onBeforeUnmount } from 'vue'
import http from '@/utils/http'
import { ElMessage, ElMessageBox } from 'element-plus'
import { newIdempotencyKey } from '@/utils/idempotency'
import {
  Search, Refresh, RefreshLeft, Plus, Setting, Download, Grid, Clock, SetUp,
  Phone, Warning, WarningFilled, Bell, User, UserFilled, CircleCheck, Loading, Check, Delete, MagicStick
} from '@element-plus/icons-vue'

// ============ 筛选 ============
const activeTab = ref<'todo' | 'all' | 'overdue' | 'mine' | 'done'>('todo')
const dateQuickKey = ref<'all' | 'today' | 'week' | 'month'>('all')
const dateRange = ref<[Date, Date] | null>(null)

const filter = reactive<any>({
  pageNum: 1, pageSize: 20,
  keyword: '', storeId: undefined, ownerId: undefined,
  taskType: undefined, status: undefined, priority: undefined,
  plannedFrom: undefined, plannedTo: undefined
})

const rows = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const selected = ref<any[]>([])
const currentUserName = ref('当前用户')

// ============ 选项（接口取） ============
const storeOptions = ref<any[]>([])
const ownerOptions = ref<any[]>([])

// ============ KPI 概览（①） ============
const stats = reactive({
  todayPending: 0,
  weekPending: 0,
  pending: 0,
  overdue: 0,
  longestOverdueDays: 0,
  minePending: 0,
  mineDone: 0,
  doneToday: 0,
  doneWeek: 0,
  completionRate: 0,
  rateDelta: '+0%'
})

async function loadStats() {
  try {
    const r: any = await http.get('/crm/care/follow/task/pending-count')
    const d = r.data || {}
    stats.pending       = Number(d.total ?? 0)
    stats.todayPending   = Number(d.total ?? 0)   // 后端未细分今日，统一用 total
    stats.weekPending    = Number(d.total ?? 0)   // 后端未细分本周
    stats.overdue        = Number(d.urgent ?? 0)   // 高优先级 = 紧急
    stats.minePending    = 0
    stats.mineDone       = 0
    stats.doneToday      = 0
    stats.doneWeek       = 0
    stats.completionRate = 0
    stats.rateDelta      = '+0%'
  } catch (e) {
    console.warn('[follow-task] stats unavailable:', e?.response?.status)
  }
}

async function loadOptions() {
  // 静默失败：后端没接口也不会阻塞页面
  try {
    const s: any = await http.get('/store/list').catch(() => null)
    storeOptions.value = s?.data || []
  } catch { /* noop */ }
  try {
    const u: any = await http.get('/employee/list').catch(() => null)
    ownerOptions.value = u?.data || []
  } catch { /* noop */ }
}

// ============ 数据加载 ============
async function loadData() {
  loading.value = true
  try {
    const r: any = await http.get('/crm/care/follow/task/page', { params: filter })
    rows.value = r.data?.records || []
    total.value = r.data?.total || 0
  } catch (e: any) {
    rows.value = []
    total.value = 0
    ElMessage.error(e?.response?.data?.message || '加载任务列表失败')
  } finally {
    loading.value = false
  }
}

async function loadAll() { await Promise.all([loadData(), loadStats()]) }

// ============ 快捷标签 + 快捷日期 ============
function filterByTab(tab: any) {
  activeTab.value = tab
  onTabChange()
}

function onTabChange() {
  const t = activeTab.value
  filter.status = undefined
  if (t === 'todo')  { filter.status = 1 }
  if (t === 'overdue') { filter.status = 5 }
  if (t === 'done')    { filter.status = 3 }
  if (t === 'mine')    { filter.ownerId = -1 /* 当前用户 */ }
  if (t === 'all')     { /* no filter */ }
  filter.pageNum = 1
  loadData()
}

function onQuickDate(v: any) {
  const now = new Date()
  const day = (offset: number) => new Date(now.getFullYear(), now.getMonth(), now.getDate() + offset)
  if (v === 'today') dateRange.value = [day(0), day(1)]
  else if (v === 'week') dateRange.value = [day(-now.getDay() + 1), day(8 - now.getDay())]
  else if (v === 'month') dateRange.value = [day(1 - now.getDate()), day(1)]
  else dateRange.value = null
  onDateRangeChange(dateRange.value)
}

function onDateRangeChange(v: any) {
  if (v && v.length === 2) {
    filter.plannedFrom = v[0].toISOString()
    filter.plannedTo = v[1].toISOString()
  } else {
    filter.plannedFrom = undefined
    filter.plannedTo = undefined
  }
  loadData()
}

// ============ 行 / 详情 ============
function onRowClick(row: any, _col: any, _e: any) { openDetail(row) }

async function openDetail(row: any) {
  try {
    const r: any = await http.get(`/crm/care/follow/task/${row.id}`)
    detail.value = r.data || row
  } catch {
    detail.value = row
  }
  detailVisible.value = true
  // 并行加载履历
  await Promise.all([loadTaskNotes(row.id), loadMemberTimeline(row.memberId)])
}
const detailVisible = ref(false)
const detail = ref<any>(null)
const detailTab = ref<'basic' | 'timeline' | 'history'>('basic')
const taskNotes = ref<any[]>([])
const memberTimeline = ref<any>(null)

async function loadTaskNotes(taskId: number) {
  try {
    const r: any = await http.get(`/crm/care/follow/task/${taskId}/notes`)
    taskNotes.value = r.data || []
  } catch { taskNotes.value = [] }
}
async function loadMemberTimeline(memberId: number) {
  if (!memberId) { memberTimeline.value = null; return }
  try {
    const r: any = await http.get(`/crm/care/follow/task/member/${memberId}/timeline`)
    memberTimeline.value = r.data || null
  } catch { memberTimeline.value = null }
}

const noteActionMeta: Record<string, { label: string; color: string; icon: string }> = {
  CREATE:  { label: '创建', color: '#909399', icon: 'M11 5h2v14h-2zM5 11h14v2H5z' },
  ASSIGN:  { label: '分配', color: '#409eff', icon: 'M12 12c2.7 0 5-2.3 5-5s-2.3-5-5-5-5 2.3-5 5 2.3 5 5 5zm0 2c-3.3 0-10 1.7-10 5v3h20v-3c0-3.3-6.7-5-10-5z' },
  START:   { label: '开始', color: '#e6a23c', icon: 'M8 5v14l11-7z' },
  FINISH:  { label: '完成', color: '#67c23a', icon: 'M9 16.2l-3.5-3.5L4 14.2l5 5 11-11-1.4-1.4z' },
  CLOSE:   { label: '关闭', color: '#f56c6c', icon: 'M19 6.4L17.6 5 12 10.6 6.4 5 5 6.4 10.6 12 5 17.6 6.4 19 12 13.4 17.6 19 19 17.6 13.4 12z' },
  REOPEN:  { label: '重开', color: '#67c23a', icon: 'M17.6 6.3A8 8 0 1 0 19 12h-2a6 6 0 1 1-1.2-7.4L13 9h7V2l-2.4 4.3z' },
  EDIT:    { label: '编辑', color: '#909399', icon: 'M3 17.2V21h3.8L17.8 10l-3.8-3.8L3 17.2z' }
}
function noteMeta(type: string) {
  return noteActionMeta[type] || { label: type || '事件', color: '#909399', icon: 'M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20z' }
}

// ============ 列设置 ============
const allColumnDefs = [
  { key: 'taskNo', label: '任务号' },
  { key: 'type', label: '类型' },
  { key: 'priority', label: '优先级' },
  { key: 'member', label: '会员' },
  { key: 'subject', label: '主题' },
  { key: 'owner', label: '处理人' },
  { key: 'planned', label: '计划时间' },
  { key: 'sla', label: 'SLA' },
  { key: 'status', label: '状态' },
  { key: 'rating', label: '评分' }
]
const visibleColumns = ref<string[]>(allColumnDefs.map(c => c.key))
const columnSettingVisible = ref(false)

// ============ 导出（⑦） ============
function onExport() {
  if (!rows.value.length) return ElMessage.warning('暂无数据可导出')
  // 简化：把当前 rows 转 csv 下载
  const headers = ['任务号', '类型', '会员', '手机', '主题', '优先级', '处理人', '计划时间', '状态']
  const csv = [headers.join(','), ...rows.value.map((r: any) =>
    [r.taskNo, taskTypeLabel(r.taskType), r.memberName, r.memberPhone, r.subject,
     priorityLabel(r.priority), r.ownerName, r.plannedAt, statusLabel(r.status)].join(',')
  )].join('\n')
  const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `回访任务_${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  ElMessage.success('已导出当前页')
}

async function insertDemoData() {
  try {
    await http.post('/crm/care/follow/task/demo-seed')
    ElMessage.success('演示数据已插入'); loadAll()
  } catch (e: any) { ElMessage.error(e?.response?.data?.message || '插入失败') }
}

// ============ 重置 ============
function resetFilter() {
  Object.assign(filter, {
    keyword: '', storeId: undefined, ownerId: undefined,
    taskType: undefined, status: undefined, priority: undefined,
    plannedFrom: undefined, plannedTo: undefined, pageNum: 1
  })
  dateRange.value = null
  dateQuickKey.value = 'all'
  loadData()
}

// ============ 自动刷新（⑩） ============
let autoTimer: any = null
const autoCountdown = ref(0)
function startAutoRefresh() {
  let left = 60
  autoCountdown.value = left
  autoTimer = setInterval(() => {
    left--
    autoCountdown.value = left
    if (left <= 0) { left = 60; loadAll() }
  }, 1000)
}
function stopAutoRefresh() { if (autoTimer) { clearInterval(autoTimer); autoTimer = null } }

// ============ 字典辅助 ============
function taskTypeLabel(t: string) {
  return ({ POST_TREATMENT: '疗程后', BIRTHDAY: '生日', EXPIRE: '到期', COMPLAINT: '投诉回访', CUSTOM: '自定义' } as any)[t] || t || '—'
}
function taskTypeTag(t: string) {
  return ({ POST_TREATMENT: 'primary', BIRTHDAY: 'warning', EXPIRE: 'danger', COMPLAINT: 'info', CUSTOM: 'info' } as any)[t] || 'info'
}
function statusLabel(s: number) {
  return ({ 1: '待处理', 2: '处理中', 3: '已完成', 4: '已关闭', 5: '已逾期' } as any)[s] || '—'
}
function statusTag(s: number) {
  return ({ 1: 'info', 2: 'warning', 3: 'success', 4: 'info', 5: 'danger' } as any)[s] || 'info'
}
function priorityLabel(p: number) {
  return ({ 1: '低', 2: '中', 3: '高' } as any)[p] || '低'
}
function priorityClass(p: number) {
  return ({ 1: 'prio-low', 2: 'prio-mid', 3: 'prio-high' } as any)[p] || 'prio-low'
}

// ============ 弹窗状态 ============
const dialog = reactive({ visible: false, saving: false })
const form = reactive({
  memberId: undefined as number | undefined, taskType: 'POST_TREATMENT',
  subject: '', content: '', suggestedScript: '',
  priority: 1, operatorName: 'admin'
})
function openCreate() { dialog.visible = true }
function resetDialog() {
  Object.assign(form, { memberId: undefined, taskType: 'POST_TREATMENT', subject: '', content: '', suggestedScript: '', priority: 1, operatorName: 'admin' })
}
async function save() {
  if (!form.memberId || !form.subject) return ElMessage.warning('会员ID / 主题 必填')
  dialog.saving = true
  try {
    const headers = { 'Idempotency-Key': newIdempotencyKey() }
    await http.post('/crm/care/follow/task', form, { headers })
    ElMessage.success('已创建'); dialog.visible = false; loadAll()
  } catch (e: any) { ElMessage.error(e?.response?.data?.message || '保存失败') }
  finally { dialog.saving = false }
}

// ============ 分配 / 开始 / 完成 / 关闭 ============
const assignDialog = reactive<any>({ visible: false, task: null, ownerName: '', ownerId: undefined })
function openAssign(row: any) {
  assignDialog.task = row
  assignDialog.ownerName = row.ownerName || ''
  assignDialog.ownerId = row.ownerId
  assignDialog.visible = true
}
async function assign() {
  try {
    await http.post(`/crm/care/follow/task/${assignDialog.task.id}/assign`, {
      ownerName: assignDialog.ownerName, ownerId: assignDialog.ownerId
    })
    ElMessage.success('已分配'); assignDialog.visible = false; loadAll()
  } catch (e: any) { ElMessage.error(e?.response?.data?.message || '分配失败') }
}
async function startTask(row: any) {
  try {
    await http.post(`/crm/care/follow/task/${row.id}/start`)
    ElMessage.success('已开始'); loadAll()
  } catch (e: any) { ElMessage.error(e?.response?.data?.message || '开始失败') }
}
const finishDialog = reactive<any>({ visible: false, task: null, resultType: 'SATISFIED', resultNote: '', rating: 5 })
function openFinish(row: any) {
  finishDialog.task = row
  finishDialog.resultType = 'SATISFIED'; finishDialog.rating = 5; finishDialog.resultNote = ''
  finishDialog.visible = true
}
async function finish() {
  try {
    await http.post(`/crm/care/follow/task/${finishDialog.task.id}/finish`, {
      resultType: finishDialog.resultType, resultNote: finishDialog.resultNote, rating: finishDialog.rating
    })
    ElMessage.success('已完成'); finishDialog.visible = false; loadAll()
  } catch (e: any) { ElMessage.error(e?.response?.data?.message || '完成失败') }
}
async function closeTask(row: any) {
  try {
    await ElMessageBox.confirm(`关闭任务「${row.subject}」？`, '提示', { type: 'warning' })
    await http.post(`/crm/care/follow/task/${row.id}/close`, { reason: '运营关闭' })
    ElMessage.success('已关闭'); loadAll()
  } catch (e: any) {
    if (e === 'cancel' || e?.message === 'cancel') return
    ElMessage.error(e?.response?.data?.message || '关闭失败')
  }
}

function onDetailClosed() {
  detailTab.value = 'basic'
  taskNotes.value = []
  memberTimeline.value = null
}

// ============ ⑫ 看板视图 ============
const kanbanVisible = ref(false)
const kanban = reactive({
  loading: false,
  allTasks: [] as any[],
  keyword: '',
  statusFilter: undefined as number | undefined
})
let dragTaskId: number | null = null

async function loadKanban() {
  kanban.loading = true
  try {
    // 一次拉取全量未关闭任务（按 ownerId 已聚合好的扁平列表，前端 computed 再分列）
    const r: any = await http.get('/crm/care/follow/task/kanban/grouped')
    kanban.allTasks = r.data || []
  } catch {
    kanban.allTasks = []
    ElMessage.error('看板数据加载失败')
  } finally { kanban.loading = false }
}

function filterKanban() { /* keyword/statusFilter 通过 computed 自动过滤 */ }

const filteredTasks = computed(() => {
  const kw = (kanban.keyword || '').trim().toLowerCase()
  return kanban.allTasks.filter((t: any) => {
    if (kanban.statusFilter && t.status !== kanban.statusFilter) return false
    if (!kw) return true
    return (t.memberName || '').toLowerCase().includes(kw) ||
           (t.memberPhone || '').toLowerCase().includes(kw) ||
           (t.subject || '').toLowerCase().includes(kw)
  })
})

const COLUMN_COLORS = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399', '#9c27b0', '#00bcd4', '#795548']
const kanbanColumns = computed(() => {
  const groups = new Map<string, { key: string; name: string; ownerId: any; tasks: any[]; color: string; dropActive: boolean }>()
  // 始终包含「待分配」
  groups.set('__unassigned__', {
    key: '__unassigned__', name: '待分配', ownerId: null, tasks: [],
    color: '#909399', dropActive: false
  })
  let colorIdx = 0
  for (const t of filteredTasks.value) {
    const key = t.ownerId ? `u_${t.ownerId}` : '__unassigned__'
    const name = t.ownerName || '待分配'
    const ownerId = t.ownerId || null
    if (!groups.has(key)) {
      groups.set(key, {
        key, name, ownerId, tasks: [],
        color: COLUMN_COLORS[colorIdx++ % COLUMN_COLORS.length],
        dropActive: false
      })
    }
    groups.get(key)!.tasks.push(t)
  }
  // 转数组，按「待分配置顶，其他按任务数降序」
  const arr = Array.from(groups.values()).filter(g => g.tasks.length > 0 || g.key === '__unassigned__')
  arr.sort((a, b) => {
    if (a.key === '__unassigned__') return -1
    if (b.key === '__unassigned__') return 1
    return b.tasks.length - a.tasks.length
  })
  // 装饰统计
  for (const g of arr) {
    g.overdueCount = g.tasks.filter((t: any) => isOverdue(t)).length
    g.todoCount = g.tasks.filter((t: any) => t.status === 1).length
  }
  return arr
})

function isOverdue(t: any) {
  return t.status !== 3 && t.status !== 4 && t.plannedAt && new Date(t.plannedAt) < new Date()
}
function shortTime(s: string) {
  if (!s) return '—'
  const d = new Date(s); if (isNaN(d.getTime())) return s
  const m = (n: number) => String(n).padStart(2, '0')
  return `${m(d.getMonth() + 1)}-${m(d.getDate())} ${m(d.getHours())}:${m(d.getMinutes())}`
}

function openKanban() {
  kanbanVisible.value = true
}

function onDragStart(e: DragEvent, task: any) {
  dragTaskId = task.id
  if (e.dataTransfer) {
    e.dataTransfer.effectAllowed = 'move'
    e.dataTransfer.setData('text/plain', String(task.id))
  }
}
function onDragOver(e: DragEvent) {
  if (e.dataTransfer) e.dataTransfer.dropEffect = 'move'
}
function onDragEnter(_e: DragEvent, col: any) { col.dropActive = true }
function onDragLeave(_e: DragEvent, col: any) { col.dropActive = false }
function onDragEnd(_e: DragEvent) {
  for (const c of kanbanColumns.value) c.dropActive = false
  dragTaskId = null
}
async function onDrop(_e: DragEvent, col: any) {
  col.dropActive = false
  const id = dragTaskId ?? Number(_e.dataTransfer?.getData('text/plain') || 0)
  if (!id) return
  const task = kanban.allTasks.find((t: any) => t.id === id)
  if (!task) return
  // 源 = 目标：啥都不做
  const targetKey = col.key
  const sourceKey = task.ownerId ? `u_${task.ownerId}` : '__unassigned__'
  if (targetKey === sourceKey) return

  const newOwnerName = col.key === '__unassigned__' ? '' : col.name
  const newOwnerId = col.key === '__unassigned__' ? null : col.ownerId
  try {
    await http.post(`/crm/care/follow/task/${id}/assign`, {
      ownerId: newOwnerId,
      ownerName: newOwnerName
    })
    // 本地乐观更新
    task.ownerName = newOwnerName || undefined
    task.ownerId = newOwnerId
    ElMessage.success(newOwnerName ? `已转派给「${newOwnerName}」` : '已改为待分配')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '转派失败')
  } finally {
    dragTaskId = null
  }
}

// ============ 批量操作 ============
async function batch(op: 'finish' | 'close') {
  if (!selected.value.length) return
  await ElMessageBox.confirm(`确认对 ${selected.value.length} 条任务执行${op === 'finish' ? '完成' : '关闭'}？`, '提示', { type: 'warning' })
  try {
    const url = op === 'finish' ? '/crm/care/follow/task/batch/finish' : '/crm/care/follow/task/batch/close'
    await http.post(url, { ids: selected.value.map((s: any) => s.id) })
    ElMessage.success('已批量操作')
    selected.value = []
    loadAll()
  } catch (e: any) {
    // 后端暂未实现 → 退化为逐条调用
    if (e?.response?.status === 404) {
      ElMessage.warning('批量接口未实现，正在逐条处理...')
      for (const s of selected.value) {
        try {
          await http.post(`/crm/care/follow/task/${s.id}/${op}`)
        } catch {}
      }
      ElMessage.success('逐条处理完成')
      selected.value = []
      loadAll()
    } else {
      ElMessage.error(e?.response?.data?.message || '操作失败')
    }
  }
}
const batchAssignVisible = ref(false)
const batchAssign = reactive({ name: '', id: undefined as number | undefined })
async function batchUpdateOwner() {
  if (!selected.value.length) return
  batchAssign.name = ''; batchAssign.id = undefined
  batchAssignVisible.value = true
}
async function confirmBatchAssign() {
  if (!batchAssign.name) return ElMessage.warning('请填写处理人姓名')
  try {
    await http.post('/crm/care/follow/task/batch/assign', {
      ids: selected.value.map((s: any) => s.id),
      ownerName: batchAssign.name, ownerId: batchAssign.id
    })
    ElMessage.success('已批量转派')
    batchAssignVisible.value = false
    selected.value = []
    loadAll()
  } catch (e: any) {
    if (e?.response?.status === 404) {
      // 退化为逐条
      ElMessage.warning('批量接口未实现，正在逐条转派...')
      for (const s of selected.value) {
        try {
          await http.post(`/crm/care/follow/task/${s.id}/assign`, {
            ownerName: batchAssign.name, ownerId: batchAssign.id
          })
        } catch {}
      }
      ElMessage.success('逐条转派完成')
      batchAssignVisible.value = false
      selected.value = []
      loadAll()
    } else {
      ElMessage.error(e?.response?.data?.message || '转派失败')
    }
  }
}

onMounted(() => { loadAll(); loadOptions(); startAutoRefresh() })
onBeforeUnmount(() => stopAutoRefresh())
</script>

<style scoped>
.follow-task-page { padding: 16px; min-height: 100vh; }

/* 页面标题 */
.header-card { margin-bottom: 12px; }
.page-header { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 12px; }
.page-title { display: flex; align-items: center; gap: 10px; }
.page-title .title { font-size: 20px; font-weight: 700; color: #1e293b; }
.page-tools { display: flex; align-items: center; gap: 8px; }
.auto-tip { color: #909399; font-size: 12px; display: flex; align-items: center; gap: 4px; }

/* ① KPI 概览 */
.kpi-row { margin-bottom: 12px; }
.kpi-mini {
  background: #fff; border: 1px solid #ebeef5; border-left-width: 4px;
  border-radius: 8px; padding: 12px 14px; cursor: pointer;
  transition: all 0.2s; height: 100%;
}
.kpi-mini:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,0.06); }
.kpi-mini.on { border-color: #409eff; box-shadow: 0 4px 12px rgba(64, 158, 255, 0.15); }
.kpi-mini:nth-of-type(1) { border-left-color: #409eff; }
.kpi-mini:nth-of-type(2) { border-left-color: #f56c6c; }
.kpi-mini:nth-of-type(3) { border-left-color: #67c23a; }
.kpi-mini:nth-of-type(4) { border-left-color: #e6a23c; }
.kmi-top { display: flex; align-items: center; gap: 6px; margin-bottom: 4px; }
.kmi-icon { color: #909399; font-size: 16px; }
.kmi-icon.red { color: #f56c6c; }
.kmi-icon.blue { color: #409eff; }
.kmi-icon.green { color: #67c23a; }
.kmi-num { font-size: 28px; font-weight: 700; color: #1e293b; line-height: 1.2; }
.kmi-num.red { color: #f56c6c; }
.kmi-num.green { color: #67c23a; }
.kmi-unit { font-size: 14px; color: #909399; font-weight: 400; }
.kmi-foot { display: flex; justify-content: space-between; font-size: 12px; color: #606266; margin-top: 4px; }
.kmi-foot .red { color: #f56c6c; }

/* ③ 快捷标签 */
.tab-card { margin-bottom: 12px; }
.tab-card :deep(.el-tabs__header) { margin-bottom: 0; }
.tab-extra { padding: 0 16px; font-size: 12px; }

/* ④ 增强筛选 */
.search-bar { margin-bottom: 12px; }
.search-form { display: flex; flex-wrap: wrap; align-items: center; gap: 4px; }
.search-form .el-form-item { margin-bottom: 8px; }
.search-form :deep(.el-form-item__label) { color: #606266; font-size: 12px; }
.search-form :deep(.el-date-editor.el-input) { width: 280px; }

/* 表格区 */
.table-card { margin-bottom: 12px; }
.clickable-table :deep(tbody tr) { cursor: pointer; }
.clickable-table :deep(tbody tr:hover > td) { background-color: #f0f7ff !important; }

/* ⑦ 批量操作 */
.table-tools {
  background: #f0f7ff; padding: 8px 12px; border-radius: 6px;
  display: flex; align-items: center; gap: 8px; margin-bottom: 12px;
  border: 1px solid #d9ecff;
}

/* 表格内细节 */
.task-no { font-family: monospace; font-size: 12px; color: #606266; }
.member-cell { display: flex; align-items: center; gap: 8px; }
.member-avatar { background: #409eff; color: #fff; }
.member-name { font-weight: 600; color: #1e293b; font-size: 13px; line-height: 1.2; }
.member-phone { font-size: 11px; }
.subject-cell { display: flex; align-items: center; gap: 6px; }
.subject-text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.auto-tag { flex-shrink: 0; }

/* 优先级 */
.prio { display: inline-flex; align-items: center; gap: 4px; font-size: 12px; }
.prio-dot { width: 8px; height: 8px; border-radius: 50%; }
.prio-dot.prio-high { background: #f56c6c; box-shadow: 0 0 4px rgba(245, 108, 108, 0.5); }
.prio-dot.prio-mid { background: #e6a23c; }
.prio-dot.prio-low { background: #909399; }

.overdue-row { color: #f56c6c; font-weight: 600; }
.sla-warn { color: #f56c6c; font-weight: 700; display: inline-flex; align-items: center; gap: 2px; }
.rating { color: #f56c6c; letter-spacing: 1px; }

.pagination { margin-top: 16px; text-align: right; }
.muted { color: #909399; }

/* 详情 · 时间线 */
.follow-timeline { padding: 8px 0; }
.follow-timeline :deep(.el-timeline-item__timestamp) { color: #909399; font-size: 12px; }
.empty-timeline { padding: 24px 0; }
.note-card { border-radius: 6px; }
.note-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; }
.note-content { font-size: 13px; color: #303133; line-height: 1.6; }
.note-status { margin-top: 6px; display: flex; align-items: center; gap: 4px; }
.note-status .arrow { color: #909399; }

/* 详情 · 会员历史 */
.history-summary { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 12px; }
.hs-card {
  border: 1px solid #ebeef5; border-radius: 8px; padding: 12px; text-align: center;
  background: #fafbfc;
}
.hs-label { color: #909399; font-size: 12px; }
.hs-num { font-size: 22px; font-weight: 700; color: #303133; margin-top: 4px; }
.hs-num.success { color: #67c23a; }
.hs-num.danger { color: #f56c6c; }

/* ⑫ 看板 */
.kanban-toolbar { display: flex; gap: 8px; align-items: center; margin-bottom: 12px; }
.kanban-board {
  display: flex; gap: 12px; align-items: flex-start;
  overflow-x: auto; padding-bottom: 8px;
  min-height: 60vh;
}
.kanban-empty { flex: 1; display: flex; justify-content: center; align-items: center; }
.kanban-col {
  flex: 0 0 280px;
  background: #f5f7fa; border-radius: 8px; padding: 8px;
  display: flex; flex-direction: column; max-height: 78vh;
  border: 2px solid transparent;
  transition: background 0.2s, border-color 0.2s;
}
.kanban-col.drop-active {
  background: #ecf5ff; border-color: #409eff;
}
.kanban-col-head {
  display: flex; align-items: center; gap: 8px; padding: 4px 4px 8px;
  border-bottom: 1px dashed #dcdfe6; margin-bottom: 6px;
}
.kanban-avatar { background: #409eff; color: #fff; font-weight: 700; }
.kanban-col-title { flex: 1; min-width: 0; }
.kanban-name { font-weight: 700; color: #303133; font-size: 14px; line-height: 1.2; }
.kanban-stat { font-size: 11px; color: #909399; display: flex; gap: 8px; margin-top: 2px; }
.kanban-stat .red { color: #f56c6c; }
.kanban-stat .orange { color: #e6a23c; }
.kanban-cards { overflow-y: auto; flex: 1; padding-right: 2px; }
.kanban-cards::-webkit-scrollbar { width: 4px; }
.kanban-cards::-webkit-scrollbar-thumb { background: #dcdfe6; border-radius: 2px; }
.kanban-col-empty {
  color: #c0c4cc; font-size: 12px; text-align: center; padding: 24px 0;
  border: 1px dashed #dcdfe6; border-radius: 6px;
}
.kanban-card {
  background: #fff; border: 1px solid #ebeef5; border-radius: 6px;
  padding: 10px; margin-bottom: 8px; cursor: grab; user-select: none;
  transition: box-shadow 0.2s, transform 0.1s;
  border-left-width: 4px; border-left-color: #909399;
}
.kanban-card:hover { box-shadow: 0 4px 12px rgba(0,0,0,0.08); }
.kanban-card:active { cursor: grabbing; transform: scale(0.98); }
.kanban-card.prio-high { border-left-color: #f56c6c; }
.kanban-card.is-overdue { background: #fef0f0; border-color: #fbc4c4; }
.kc-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.kc-type {
  font-size: 11px; padding: 1px 6px; border-radius: 3px;
  background: #ecf5ff; color: #409eff;
}
.kc-type.kct-BIRTHDAY { background: #fdf6ec; color: #e6a23c; }
.kc-type.kct-EXPIRE { background: #fef0f0; color: #f56c6c; }
.kc-type.kct-COMPLAINT { background: #f4f4f5; color: #909399; }
.kc-prio { font-size: 11px; padding: 1px 6px; border-radius: 3px; }
.kc-prio.prio-1 { background: #f4f4f5; color: #909399; }
.kc-prio.prio-2 { background: #fdf6ec; color: #e6a23c; }
.kc-prio.prio-3 { background: #fef0f0; color: #f56c6c; }
.kc-subject {
  font-size: 13px; color: #303133; font-weight: 600;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
  margin-bottom: 6px;
}
.kc-member { display: flex; align-items: center; gap: 4px; font-size: 12px; color: #606266; margin-bottom: 4px; }
.kc-meta { display: flex; justify-content: space-between; align-items: center; font-size: 11px; color: #909399; }
.kc-meta .red { color: #f56c6c; }
.kc-meta .rating { color: #f56c6c; }

/* ⑨ FAB 浮动按钮 */
.fab-btn {
  position: fixed; right: 32px; bottom: 80px; z-index: 999;
  width: 56px; height: 56px; box-shadow: 0 6px 16px rgba(64, 158, 255, 0.4);
}
@media (max-width: 768px) {
  .search-form .el-form-item { width: 100%; }
  .search-form :deep(.el-input), .search-form :deep(.el-select) { width: 100% !important; }
  .fab-btn { display: flex; }
}
@media (min-width: 769px) {
  .fab-btn { display: none; }
}
</style>
