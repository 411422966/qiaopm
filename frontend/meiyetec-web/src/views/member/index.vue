<template>
  <div class="member-page">
    <!-- 顶部统计卡片 -->
    <div class="stats-row">
      <div class="stat-card stat-1">
        <div class="stat-icon"><el-icon :size="28"><User /></el-icon></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.total || 0 }}</div>
          <div class="stat-label">会员总数</div>
        </div>
        <div class="stat-trend">本月新增 <span :class="trendClass(stats.totalTrend)">{{ trendSign(stats.totalTrend) }}{{ stats.totalTrend || 0 }}%</span></div>
      </div>
      <div class="stat-card stat-2">
        <div class="stat-icon"><el-icon :size="28"><UserFilled /></el-icon></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.active || 0 }}</div>
          <div class="stat-label">活跃会员</div>
        </div>
        <div class="stat-trend">活跃率 <span class="up">{{ stats.activeRate || 0 }}%</span></div>
      </div>
      <div class="stat-card stat-3">
        <div class="stat-icon"><el-icon :size="28"><Money /></el-icon></div>
        <div class="stat-info">
          <div class="stat-value">¥{{ formatMoney(stats.totalBalance) }}</div>
          <div class="stat-label">储值总额</div>
        </div>
        <div class="stat-trend">本月新增 <span :class="trendClass(stats.balanceTrend)">{{ trendSign(stats.balanceTrend) }}{{ stats.balanceTrend || 0 }}%</span></div>
      </div>
      <div class="stat-card stat-4">
        <div class="stat-icon"><el-icon :size="28"><Coin /></el-icon></div>
        <div class="stat-info">
          <div class="stat-value">{{ stats.totalPoints || 0 }}</div>
          <div class="stat-label">积分总量</div>
        </div>
        <div class="stat-trend">本月新增 <span :class="trendClass(stats.pointsTrend)">{{ trendSign(stats.pointsTrend) }}{{ stats.pointsTrend || 0 }}%</span></div>
      </div>
    </div>

    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-input
          ref="keywordInputRef"
          v-model="query.keyword"
          placeholder="搜索会员姓名/手机号/编号（Ctrl+F 聚焦）" clearable
          style="width: 240px" @keyup.enter="handleSearch">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="query.status" placeholder="会员状态" clearable style="width: 110px" @change="handleSearch">
          <el-option label="正常" :value="1" />
          <el-option label="冻结" :value="2" />
        </el-select>
        <el-select v-model="query.levelId" placeholder="会员等级" clearable style="width: 130px" @change="handleSearch">
          <el-option v-for="l in levelList" :key="l.id" :label="l.levelName" :value="l.id" />
        </el-select>
        <el-select v-model="query.tagId" placeholder="会员标签" clearable style="width: 130px" @change="handleSearch">
          <el-option v-for="t in tagList" :key="t.id" :label="t.tagName" :value="t.id" />
        </el-select>
        <el-select v-model="query.storeId" placeholder="所属门店" clearable style="width: 130px" @change="handleSearch">
          <el-option v-for="s in storeList" :key="s.id" :label="s.storeName" :value="s.id" />
        </el-select>
        <el-button type="primary" @click="handleSearch"><el-icon><Search /></el-icon>查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </div>
      <div class="toolbar-right">
        <el-dropdown @command="handleSort" trigger="click">
          <el-button>
            <el-icon><Sort /></el-icon>排序：{{ sortLabel }}<el-icon class="ml-1"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="default">默认（最新）</el-dropdown-item>
              <el-dropdown-item command="totalConsume-desc">消费额 从高到低</el-dropdown-item>
              <el-dropdown-item command="totalConsume-asc">消费额 从低到高</el-dropdown-item>
              <el-dropdown-item command="points-desc">积分 从高到低</el-dropdown-item>
              <el-dropdown-item command="points-asc">积分 从低到高</el-dropdown-item>
              <el-dropdown-item command="balance-desc">余额 从高到低</el-dropdown-item>
              <el-dropdown-item command="lastVisit-desc">最近到店</el-dropdown-item>
              <el-dropdown-item command="joinDate-desc">入会时间</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button @click="handleExport">
          <el-icon><Download /></el-icon>导出
        </el-button>
        <el-button @click="handleImport">
          <el-icon><Upload /></el-icon>导入
        </el-button>
        <el-button type="primary" @click="handleAdd">
          <el-icon><Plus /></el-icon>新增会员
        </el-button>

        <!-- 搜索键盘快捷键已通过 Ctrl+F 监听器实现 -->
        <!-- 视图（卡片模式下保存排序/筛选组合） -->
        <el-dropdown trigger="click" @command="handleViewCmd">
          <el-button :icon="Collection">视图</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="save">💾 保存当前筛选/排序</el-dropdown-item>
              <el-dropdown-item v-if="savedViews.length === 0" disabled>（暂无保存的视图）</el-dropdown-item>
              <el-dropdown-item
                v-for="v in savedViews"
                :key="v.id"
                :command="`apply:${v.id}`">
                📋 {{ v.name }}
              </el-dropdown-item>
              <el-dropdown-item divided v-if="savedViews.length > 0" command="manage">管理视图…</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <!-- 浮动批量操作栏 -->
    <transition name="el-fade-in">
      <div v-if="selectedIds.length > 0" class="batch-bar">
        <div class="batch-info">
          <el-icon><Check /></el-icon>已选 <b>{{ selectedIds.length }}</b> 位会员
        </div>
        <div class="batch-actions">
          <el-button size="small" @click="handleBatchTag(2)"><el-icon><Plus /></el-icon>追加标签</el-button>
          <el-button size="small" @click="handleBatchTag(1)"><el-icon><Edit /></el-icon>覆盖标签</el-button>
          <el-button size="small" type="warning" @click="handleBatchOp('freeze')"><el-icon><Lock /></el-icon>冻结</el-button>
          <el-button size="small" type="success" @click="handleBatchOp('unfreeze')"><el-icon><Unlock /></el-icon>解冻</el-button>
          <el-button size="small" type="danger" @click="handleBatchOp('delete')"><el-icon><Delete /></el-icon>删除</el-button>
          <el-button size="small" text @click="clearSelection">取消选择</el-button>
        </div>
      </div>
    </transition>

    <!-- 会员卡片网格 -->
    <div class="member-grid" v-loading="loading">
      <div class="member-card" v-for="m in tableData" :key="m.id" :class="{ selected: isSelected(m.id), frozen: m.status === 2 }">
        <div class="card-checkbox" @click.stop>
          <el-checkbox :model-value="isSelected(m.id)" @change="toggleSelect(m.id)" />
        </div>
        <div class="card-header" :class="getLevelClass(m.levelName)" @click="handleView(m)">
          <div class="card-level">
            <el-icon><Medal /></el-icon>
            <span>{{ m.levelName || '普通会员' }}</span>
          </div>
          <span class="level-rank" v-if="m.totalConsume">¥{{ formatMoney(m.totalConsume) }}</span>
        </div>
        <div class="card-body" @click="handleView(m)">
          <div class="avatar-wrap">
            <div class="avatar-circle" :style="{ background: getAvatarColor(m.name) }">
              {{ m.name?.charAt(0) }}
            </div>
            <div class="gender-icon" v-if="m.gender === 2">女</div>
            <div class="gender-icon male" v-else-if="m.gender === 1">男</div>
          </div>
          <div class="member-name">{{ m.name }}</div>
          <div class="member-no">{{ m.memberNo }}</div>
          <div class="member-phone">
            <el-icon><Phone /></el-icon>
            <span>{{ m.phone }}</span>
          </div>
          <div class="tag-list" v-if="m.tags && m.tags.length">
            <span v-for="t in m.tags" :key="t.id" class="mini-tag"
              :style="{ background: t.tagColor + '20', color: t.tagColor, borderColor: t.tagColor }">
              {{ t.tagName }}
            </span>
          </div>
        </div>
        <div class="card-stats">
          <div class="stat-item">
            <div class="stat-num">¥{{ formatMoney(m.balance) }}</div>
            <div class="stat-lbl">余额</div>
          </div>
          <div class="stat-divider"></div>
          <div class="stat-item">
            <div class="stat-num">{{ m.points || 0 }}</div>
            <div class="stat-lbl">积分</div>
          </div>
          <div class="stat-divider"></div>
          <div class="stat-item">
            <div class="stat-num">¥{{ formatMoney(m.totalConsume) }}</div>
            <div class="stat-lbl">累计</div>
          </div>
        </div>
        <div class="card-badges" v-if="m.status === 2">
          <el-tag type="danger" size="small" effect="dark">已冻结</el-tag>
        </div>
        <div class="card-footer" @click.stop>
          <el-button text type="primary" size="small" @click="handleCall(m)">
            <el-icon><Phone /></el-icon>拨打
          </el-button>
          <el-button text type="primary" size="small" @click="handleEdit(m)">
            <el-icon><Edit /></el-icon>编辑
          </el-button>
          <el-button v-if="m.status === 1" text type="warning" size="small" @click="handleFreeze(m)">
            <el-icon><Lock /></el-icon>冻结
          </el-button>
          <el-button v-else text type="success" size="small" @click="handleUnfreeze(m)">
            <el-icon><Unlock /></el-icon>解冻
          </el-button>
          <el-button text type="danger" size="small" @click="handleDelete(m)">
            <el-icon><Delete /></el-icon>删除
          </el-button>
        </div>
      </div>

      <!-- 运营引导卡片：会员数 < 6 时显示 -->
      <div class="guide-card" v-if="!loading && total > 0 && total < 6">
        <div class="guide-icon"><el-icon :size="48"><Promotion /></el-icon></div>
        <h3>会员数量较少？</h3>
        <p>批量导入数据或开启营销活动，让会员快速增长</p>
        <div class="guide-actions">
          <el-button type="primary" @click="handleImport">导入会员</el-button>
          <el-button @click="handleAdd">新增会员</el-button>
        </div>
      </div>

      <div class="empty-tip" v-if="!loading && total === 0">
        <el-icon :size="64"><User /></el-icon>
        <p>暂无会员数据</p>
        <div class="empty-actions">
          <el-button type="primary" @click="handleAdd">新增第一位会员</el-button>
          <el-button @click="handleImport">导入会员</el-button>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div class="pagination" v-if="total > 0">
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :page-sizes="[12, 24, 48]"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadData"
        @size-change="loadData" />
    </div>

    <!-- 新增/编辑 弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="680px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" label-position="right">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="姓名" prop="name">
              <el-input v-model="form.name" placeholder="请输入姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="form.phone" placeholder="请输入11位手机号" maxlength="11" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="性别">
              <el-radio-group v-model="form.gender">
                <el-radio :value="1">男</el-radio>
                <el-radio :value="2">女</el-radio>
                <el-radio :value="0">未知</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="生日">
              <el-date-picker v-model="form.birthday" type="date" placeholder="选择生日"
                value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="会员等级">
              <el-select v-model="form.levelId" placeholder="选择等级" style="width: 100%">
                <el-option v-for="l in levelList" :key="l.id" :label="l.levelName" :value="l.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属门店">
              <el-select v-model="form.storeId" placeholder="选择门店" style="width: 100%">
                <el-option v-for="s in storeList" :key="s.id" :label="s.storeName" :value="s.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="储值余额">
              <el-input-number v-model="form.balance" :precision="2" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="积分">
              <el-input-number v-model="form.points" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="累计消费">
              <el-input-number v-model="form.totalConsume" :precision="2" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="标签">
          <el-select v-model="form.tagIds" multiple placeholder="选择标签" style="width: 100%">
            <el-option v-for="t in tagList" :key="t.id" :label="t.tagName" :value="t.id">
              <span class="tag-dot" :style="{ background: t.tagColor }"></span>{{ t.tagName }}
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="form.address" placeholder="详细地址" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="备注信息" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">正常</el-radio>
            <el-radio :value="2">冻结</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="drawerVisible" title="会员详情" size="640px" destroy-on-close>
      <div v-if="current" class="detail-wrap">
        <div class="detail-hero" :class="getLevelClass(current.levelName)">
          <div class="hero-avatar" :style="{ background: getAvatarColor(current.name) }">
            {{ current.name?.charAt(0) }}
          </div>
          <div class="hero-info">
            <div class="hero-name">{{ current.name }}</div>
            <div class="hero-meta">
              <el-tag effect="dark" round>{{ current.levelName || '普通会员' }}</el-tag>
              <span class="hero-no">{{ current.memberNo }}</span>
              <el-tag v-if="current.status === 2" type="danger" effect="dark" size="small">已冻结</el-tag>
            </div>
            <div class="hero-actions">
              <el-button size="small" type="primary" plain @click="handleCall(current)">
                <el-icon><Phone /></el-icon>拨打
              </el-button>
              <el-button size="small" plain @click="copyText(current.phone, '手机号')">
                <el-icon><CopyDocument /></el-icon>复制手机号
              </el-button>
              <el-button size="small" plain v-if="profile?.wechat" @click="copyText(profile.wechat, '微信号')">
                <el-icon><ChatDotRound /></el-icon>复制微信
              </el-button>
            </div>
          </div>
        </div>

        <el-tabs v-model="activeTab" class="detail-tabs">
          <el-tab-pane label="基本信息" name="basic">
            <div class="detail-grid">
              <div class="detail-item"><label>手机号</label><span>{{ current.phone }}</span></div>
              <div class="detail-item"><label>性别</label><span>{{ genderText(current.gender) }}</span></div>
              <div class="detail-item"><label>生日</label><span>{{ current.birthday || '-' }}</span></div>
              <div class="detail-item"><label>所属门店</label><span>{{ current.storeName || '-' }}</span></div>
              <div class="detail-item"><label>入会日期</label><span>{{ current.joinDate || '-' }}</span></div>
              <div class="detail-item"><label>最后到店</label><span>{{ formatDate(current.lastVisit) || '暂无' }}</span></div>
            </div>

            <div class="detail-section">
              <h4>财务信息</h4>
              <div class="finance-grid">
                <div class="finance-box">
                  <div class="finance-label">储值余额</div>
                  <div class="finance-val">¥{{ formatMoney(current.balance) }}</div>
                </div>
                <div class="finance-box">
                  <div class="finance-label">积分</div>
                  <div class="finance-val">{{ current.points || 0 }}</div>
                </div>
                <div class="finance-box">
                  <div class="finance-label">累计消费</div>
                  <div class="finance-val">¥{{ formatMoney(current.totalConsume) }}</div>
                </div>
              </div>
            </div>

            <div class="detail-section" v-if="current.tags && current.tags.length">
              <h4>会员标签</h4>
              <div class="tag-cloud">
                <span v-for="t in current.tags" :key="t.id" class="big-tag"
                  :style="{ background: t.tagColor + '20', color: t.tagColor, borderColor: t.tagColor }">
                  {{ t.tagName }}
                </span>
              </div>
            </div>

            <div class="detail-section" v-if="current.address">
              <h4>联系地址</h4>
              <p class="text-content">{{ current.address }}</p>
            </div>

            <div class="detail-section" v-if="current.remark">
              <h4>备注</h4>
              <p class="text-content">{{ current.remark }}</p>
            </div>

            <div class="detail-section" v-if="profile">
              <div class="section-header-row">
                <h4>档案信息</h4>
                <el-button v-if="!editProfileMode" size="small" text type="primary" @click="startEditProfile">
                  <el-icon><Edit /></el-icon>编辑档案
                </el-button>
              </div>

              <!-- 编辑模式 -->
              <el-form v-if="editProfileMode" :model="profileForm" label-width="70px" label-position="left" style="max-width:400px">
                <el-form-item label="肤质">
                  <el-select v-model="profileForm.skinType" placeholder="选择肤质" clearable style="width:100%">
                    <el-option v-for="s in skinTypeOptions" :key="s" :value="s" :label="s" />
                  </el-select>
                </el-form-item>
                <el-form-item label="发质">
                  <el-select v-model="profileForm.hairType" placeholder="选择发质" clearable style="width:100%">
                    <el-option v-for="h in hairTypeOptions" :key="h" :value="h" :label="h" />
                  </el-select>
                </el-form-item>
                <el-form-item label="甲面">
                  <el-select v-model="profileForm.nailType" placeholder="选择甲面" clearable style="width:100%">
                    <el-option v-for="n in nailTypeOptions" :key="n" :value="n" :label="n" />
                  </el-select>
                </el-form-item>
                <el-form-item label="过敏史">
                  <el-input v-model="profileForm.allergy" placeholder="填写过敏史" />
                </el-form-item>
                <el-form-item label="职业">
                  <el-input v-model="profileForm.job" placeholder="填写职业" />
                </el-form-item>
                <el-form-item label="爱好">
                  <el-input v-model="profileForm.hobby" placeholder="填写爱好" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" size="small" :loading="savingProfile" @click="submitProfile">保存</el-button>
                  <el-button size="small" @click="cancelEditProfile">取消</el-button>
                </el-form-item>
              </el-form>

              <!-- 只读模式 -->
              <div class="detail-grid" v-else>
                <div class="detail-item"><label>肤质</label><span>{{ profile.skinType || '-' }}</span></div>
                <div class="detail-item"><label>发质</label><span>{{ profile.hairType || '-' }}</span></div>
                <div class="detail-item"><label>甲面</label><span>{{ profile.nailType || '-' }}</span></div>
                <div class="detail-item"><label>过敏史</label><span>{{ profile.allergy || '-' }}</span></div>
                <div class="detail-item"><label>职业</label><span>{{ profile.job || '-' }}</span></div>
                <div class="detail-item"><label>爱好</label><span>{{ profile.hobby || '-' }}</span></div>
                <div class="detail-item"><label>微信号</label><span>{{ profile.wechat || '-' }}</span></div>
                <div class="detail-item"><label>紧急联系人</label><span>{{ profile.emergencyName || '-' }} {{ profile.emergencyPhone || '' }}</span></div>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="消费记录" name="consume">
            <div v-loading="loadingConsume">
              <div v-if="memberOrders.length === 0" class="tab-placeholder">
                <el-icon :size="48"><Document /></el-icon>
                <p>该会员暂无消费记录</p>
              </div>
              <el-table v-else :data="memberOrders" border size="small">
                <el-table-column prop="orderNo" label="订单号" width="180" />
                <el-table-column label="消费时间" width="170">
                  <template #default="{ row }">{{ formatDateTime(row.paidAt || row.createdAt) }}</template>
                </el-table-column>
                <el-table-column label="实付金额" width="120" align="right">
                  <template #default="{ row }">¥{{ formatMoney(row.finalAmount || row.paidAmount) }}</template>
                </el-table-column>
                <el-table-column label="折扣" width="100" align="right">
                  <template #default="{ row }">
                    <span v-if="row.discountAmount > 0" style="color:#f56c6c">-¥{{ formatMoney(row.discountAmount) }}</span>
                    <span v-else style="color:#909399">-</span>
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="100" align="center">
                  <template #default="{ row }">
                    <el-tag size="small" :type="orderStatusType(row.status)">
                      {{ orderStatusLabel(row.status) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="收银员" width="100">
                  <template #default="{ row }">{{ row.cashierName || '-' }}</template>
                </el-table-column>
                <el-table-column label="项目明细" min-width="200">
                  <template #default="{ row }">
                    <span v-if="row.items && row.items.length">
                      {{ row.items.map(it => `${it.serviceName || it.name} × ${it.quantity || 1}`).join('，') }}
                    </span>
                    <span v-else style="color:#c0c4cc">无明细</span>
                  </template>
                </el-table-column>
              </el-table>
              <div class="pager-bar" v-if="memberOrdersTotal > memberOrdersPageSize">
                <el-pagination
                  background layout="prev, pager, next"
                  :total="memberOrdersTotal"
                  :page-size="memberOrdersPageSize"
                  :current-page="memberOrdersPageNum"
                  @current-change="onConsumePageChange" />
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="预约" name="booking">
            <div class="tab-placeholder">
              <el-icon :size="48"><Calendar /></el-icon>
              <p>暂无预约记录</p>
              <small>M06 预约管理模块完成后将自动展示</small>
            </div>
          </el-tab-pane>

          <el-tab-pane label="优惠券" name="coupon">
            <div v-loading="loadingCoupon">
              <div v-if="memberCoupons.length === 0" class="tab-placeholder">
                <el-icon :size="48"><Tickets /></el-icon>
                <p>该会员暂无优惠券</p>
              </div>
              <el-table v-else :data="memberCoupons" border>
                <el-table-column prop="couponName" label="优惠券名称" />
                <el-table-column prop="code" label="编号" width="160" />
                <el-table-column label="状态" width="100">
                  <template #default="{ row }">
                    <el-tag :type="row.status === 1 ? 'success' : row.status === 2 ? 'info' : 'danger'" size="small">
                      {{ row.status === 1 ? '未使用' : row.status === 2 ? '已使用' : row.status === 3 ? '已过期' : '未知' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="expireTime" label="过期时间" width="180" />
                <el-table-column prop="source" label="来源" width="100">
                  <template #default="{ row }">
                    <span>{{ ({1:'主动领取',2:'系统发放',3:'活动奖励',5:'等级批量'})[row.source] || row.source }}</span>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </el-tab-pane>

          <el-tab-pane label="积分" name="points">
            <div v-loading="loadingPoints">
              <el-descriptions :column="3" border v-if="memberPoints">
                <el-descriptions-item label="当前积分">{{ memberPoints.balance }}</el-descriptions-item>
                <el-descriptions-item label="累计获得">{{ memberPoints.totalEarned }}</el-descriptions-item>
                <el-descriptions-item label="累计消耗">{{ memberPoints.totalConsumed }}</el-descriptions-item>
              </el-descriptions>
              <el-divider />
              <div v-if="pointRecords.length === 0" class="tab-placeholder">
                <el-icon :size="48"><Tickets /></el-icon>
                <p>暂无积分记录</p>
              </div>
              <el-table v-else :data="pointRecords" border>
                <el-table-column label="类型" width="80">
                  <template #default="{ row }">
                    <el-tag :type="row.type === 1 ? 'success' : 'danger'" size="small">
                      {{ row.type === 1 ? '获得' : '消耗' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="积分" width="100">
                  <template #default="{ row }">
                    <span :class="row.type === 1 ? 'text-green' : 'text-red'">
                      {{ row.type === 1 ? '+' : '-' }}{{ row.pointValue }}
                    </span>
                  </template>
                </el-table-column>
                <el-table-column prop="balanceAfter" label="余额" width="80" />
                <el-table-column prop="sourceText" label="来源" />
                <el-table-column prop="remark" label="备注" />
                <el-table-column prop="createdAt" label="时间" width="170" />
              </el-table>
            </div>
          </el-tab-pane>

          <el-tab-pane label="套餐次卡" name="package">
            <div v-loading="loadingPkg">
              <div v-if="pkgOrders.length === 0" class="tab-placeholder">
                <el-icon :size="48"><Tickets /></el-icon>
                <p>暂未购买套餐</p>
                <small>该会员尚未持有任何套餐</small>
              </div>
              <div v-else>
                <el-row :gutter="12" style="margin-bottom:12px">
                  <el-col :span="6"><el-card shadow="never" class="pkg-stat"><div class="lbl">持有订单</div><div class="val">{{ pkgStats.total }}</div></el-card></el-col>
                  <el-col :span="6"><el-card shadow="never" class="pkg-stat"><div class="lbl">总剩余次数</div><div class="val text-success">{{ pkgStats.remain }}</div></el-card></el-col>
                  <el-col :span="6"><el-card shadow="never" class="pkg-stat"><div class="lbl">累计购买金额</div><div class="val text-primary">¥{{ formatMoney(pkgStats.spent) }}</div></el-card></el-col>
                  <el-col :span="6"><el-card shadow="never" class="pkg-stat"><div class="lbl">已核销次数</div><div class="val text-warning">{{ pkgStats.used }}</div></el-card></el-col>
                </el-row>

                <el-table :data="pkgOrders" stripe size="small">
                  <el-table-column label="订单号" prop="orderNo" min-width="170" />
                  <el-table-column label="套餐" prop="packageName" min-width="150" show-overflow-tooltip />
                  <el-table-column label="总/剩余" width="100" align="center">
                    <template #default="{ row }">
                      <el-tag size="small" type="info">{{ row.totalTimes }}</el-tag>
                      /
                      <el-tag size="small" :type="row.remainingTimes > 0 ? 'success' : 'danger'">{{ row.remainingTimes }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="实付" prop="price" width="100" align="right">
                    <template #default="{ row }">¥{{ formatMoney(row.price) }}</template>
                  </el-table-column>
                  <el-table-column label="支付方式" prop="payMethod" width="90" align="center" />
                  <el-table-column label="购买时间" width="150">
                    <template #default="{ row }">{{ formatDate(row.buyTime) }}</template>
                  </el-table-column>
                  <el-table-column label="到期" width="170">
                    <template #default="{ row }">
                      <span :class="{ 'expire-soon': isExpireSoon(row.expireTime) }">
                        {{ formatDate(row.expireTime) }}
                        <el-tag v-if="isExpireSoon(row.expireTime)" size="small" type="danger" effect="dark" style="margin-left:4px">即将到期</el-tag>
                      </span>
                    </template>
                  </el-table-column>
                  <el-table-column label="状态" width="90" align="center">
                    <template #default="{ row }">
                      <el-tag :type="statusTagType(row.status)" size="small" effect="dark">{{ statusLabel(row.status) }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="240" fixed="right">
                    <template #default="{ row }">
                      <el-button v-if="canSplit(row)" text type="primary" size="small" :icon="MagicStick" @click="openSplitRedeem(row)">拆分核销</el-button>
                      <el-button v-if="canRefund(row)" text type="warning" size="small" :icon="RefreshLeft" @click="openRefund(row)">退款</el-button>
                      <el-button v-if="canTransfer(row)" text type="success" size="small" :icon="Share" @click="openTransfer(row)">转赠</el-button>
                      <el-button text type="info" size="small" :icon="Document" @click="openRedemptions(row)">核销记录</el-button>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="疗程进度" name="treatment">
            <div v-loading="loadingTreatment">
              <div v-if="treatmentProgresses.length">
                <div v-for="tp in treatmentProgresses" :key="tp.id" class="treatment-card">
                  <div class="tp-head">
                    <div>
                      <h4>{{ tp.planName }}（{{ tp.serviceName }}）</h4>
                      <div class="tp-meta">
                        <el-tag size="small" :type="treatmentStatusType(tp.status)">{{ treatmentStatusLabel(tp.status) }}</el-tag>
                        <span>当前阶段：{{ tp.currentPhaseName || '—' }}</span>
                        <span>开始：{{ formatDate(tp.startedAt) }}</span>
                        <span>最近服务：{{ formatDate(tp.lastSessionAt) || '—' }}</span>
                      </div>
                    </div>
                    <el-button size="small" type="primary" @click="openRecordSession(tp)" :disabled="tp.status !== 1">打卡服务</el-button>
                  </div>
                  <el-progress
                    :percentage="Number(tp.progressPercent || 0)"
                    :status="tp.status === 2 ? 'success' : tp.status === 3 ? 'exception' : ''"
                  />
                  <div class="tp-counts">
                    <span>总：{{ tp.totalSessions }} 次</span>
                    <span>已用：{{ tp.usedSessions }} 次</span>
                    <span>剩余：<b :style="{ color: tp.remainingSessions > 0 ? '#67c23a' : '#f56c6c' }">{{ tp.remainingSessions }}</b> 次</span>
                    <span v-if="tp.expectedFinishAt">预计完成：{{ formatDate(tp.expectedFinishAt) }}</span>
                  </div>
                  <el-button size="small" link @click="viewTreatmentDetail(tp)" style="padding-left: 0">查看阶段与明细 →</el-button>
                </div>
              </div>
              <div v-else class="tab-placeholder">
                <el-icon :size="48"><Medal /></el-icon>
                <p>暂无疗程进度</p>
                <el-button type="primary" @click="openStartTreatment">+ 开通疗程</el-button>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="储值流水" name="wallet">
            <div v-loading="loadingWallet">
              <div v-if="walletInfo" class="wallet-summary">
                <div class="ws-item">
                  <div class="ws-label">当前余额</div>
                  <div class="ws-val text-success">¥{{ formatMoney(walletInfo.balance) }}</div>
                </div>
                <div class="ws-item">
                  <div class="ws-label">累计充值</div>
                  <div class="ws-val">¥{{ formatMoney(walletInfo.totalRecharge) }}</div>
                </div>
                <div class="ws-item">
                  <div class="ws-label">累计消费</div>
                  <div class="ws-val">¥{{ formatMoney(walletInfo.totalConsume) }}</div>
                </div>
              </div>
              <el-table v-if="walletTxns.length" :data="walletTxns" stripe size="small" max-height="500">
                <el-table-column label="时间" width="160">
                  <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
                </el-table-column>
                <el-table-column label="类型" width="80">
                  <template #default="{ row }">
                    <el-tag :type="walletTagType(row.type)" effect="dark" size="small">{{ row.typeLabel }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="金额" align="right">
                  <template #default="{ row }">
                    <span :class="row.type === 1 || row.type === 3 || row.type === 4 ? 'text-success' : 'text-danger'">
                      {{ row.type === 1 || row.type === 3 || row.type === 4 ? '+' : '-' }}¥{{ formatMoney(row.amount) }}
                    </span>
                    <el-tag v-if="row.giftAmount && Number(row.giftAmount) > 0" size="small" type="warning" effect="plain" style="margin-left:4px">送¥{{ formatMoney(row.giftAmount) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="余额后" prop="balanceAfter" align="right" width="100">
                  <template #default="{ row }">¥{{ formatMoney(row.balanceAfter) }}</template>
                </el-table-column>
                <el-table-column label="备注" prop="remark" show-overflow-tooltip />
              </el-table>
              <div v-else class="tab-placeholder">
                <el-icon :size="48"><Wallet /></el-icon>
                <p>暂无储值流水</p>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>

    <!-- 批量打标签弹窗 -->
    <el-dialog v-model="batchTagDialog" :title="batchTagMode === 1 ? '覆盖标签' : '追加标签'" width="480px">
      <p class="batch-tip">将为 <b>{{ selectedIds.length }}</b> 位会员{{ batchTagMode === 1 ? '覆盖' : '追加' }}以下标签：</p>
      <el-select v-model="batchTagIds" multiple placeholder="选择标签" style="width: 100%">
        <el-option v-for="t in tagList" :key="t.id" :label="t.tagName" :value="t.id">
          <span class="tag-dot" :style="{ background: t.tagColor }"></span>{{ t.tagName }}
        </el-option>
      </el-select>
      <template #footer>
        <el-button @click="batchTagDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmBatchTag">确定</el-button>
      </template>
    </el-dialog>

    <!-- 导入弹窗 -->
    <el-dialog v-model="importDialog" title="导入会员" width="540px">
      <div class="import-content">
        <p class="import-tip">请先下载模板，填写后上传 CSV 文件（UTF-8 编码）。</p>
        <div class="format-hint">
          <b>格式说明：</b>会员号（可选）,姓名,手机号,性别,生日（YYYY-MM-DD）,备注
        </div>
        <el-upload
          class="upload-area"
          drag
          :auto-upload="false"
          :limit="1"
          accept=".csv"
          :on-change="handleFileChange"
          :on-exceed="handleExceed">
          <el-icon class="upload-icon" :size="48"><UploadFilled /></el-icon>
          <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
          <template #tip>
            <div class="el-upload__tip">仅支持 .csv 文件，UTF-8 编码</div>
          </template>
        </el-upload>
        <div class="import-result" v-if="importResult">
          <el-alert :title="`导入完成：成功 ${importResult.success}，失败 ${importResult.failed}`"
            :type="importResult.failed > 0 ? 'warning' : 'success'" :closable="false">
            <div v-if="importResult.errors && importResult.errors.length">
              <p v-for="(e, i) in importResult.errors" :key="i" class="err-line">{{ e }}</p>
            </div>
          </el-alert>
        </div>
      </div>
      <template #footer>
        <el-button @click="downloadTemplate">下载模板</el-button>
        <el-button @click="importDialog = false">关闭</el-button>
        <el-button type="primary" @click="submitImport" :loading="importing">开始导入</el-button>
      </template>
    </el-dialog>

    <!-- 套餐：拆分核销 -->
    <el-dialog v-model="splitVisible" title="拆分核销" width="460px">
      <el-form :model="splitForm" label-width="90px">
        <el-form-item label="订单"><el-input :value="splitForm._pkgLabel" disabled /></el-form-item>
        <el-form-item label="剩余次数"><el-input :value="splitForm._remaining + ' 次'" disabled /></el-form-item>
        <el-form-item label="本次核销">
          <el-input-number v-model="splitForm.times" :min="1" :max="splitForm._remaining" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="splitForm.note" placeholder="可选" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="splitVisible = false">取消</el-button>
        <el-button type="primary" :loading="splitSubmitting" @click="submitSplit">确认核销</el-button>
      </template>
    </el-dialog>

    <!-- 套餐：转赠 -->
    <el-dialog v-model="transferVisible" title="套餐转赠" width="480px">
      <el-alert type="warning" :closable="false" style="margin-bottom:14px">
        转赠后原订单将标记为「已转赠」并清零剩余次数，目标会员获得一张全新的子订单
      </el-alert>
      <el-form :model="transferForm" label-width="100px">
        <el-form-item label="订单"><el-input :value="transferForm._pkgLabel" disabled /></el-form-item>
        <el-form-item label="剩余次数"><el-input :value="transferForm._remaining + ' 次'" disabled /></el-form-item>
        <el-form-item label="目标会员">
          <el-select v-model="transferForm.toMemberId" placeholder="搜索目标会员" filterable remote
            :remote-method="searchMembersForTransfer" :loading="searchMemberLoading" style="width:100%">
            <el-option v-for="m in transferCandidates" :key="m.id" :value="m.id"
              :label="`${m.name} | ${m.phone}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="transferForm.remark" placeholder="可选" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" :loading="transferSubmitting" @click="submitTransfer">确认转赠</el-button>
      </template>
    </el-dialog>

    <!-- 套餐：退款 -->
    <el-dialog v-model="refundVisible" title="套餐退款（退至钱包）" width="460px">
      <el-form :model="refundForm" label-width="100px">
        <el-form-item label="套餐"><el-input :value="refundForm._pkgLabel" disabled /></el-form-item>
        <el-form-item label="原实付"><el-input :value="'¥' + formatMoney(refundForm._price)" disabled /></el-form-item>
        <el-form-item label="剩余次数"><el-input :value="refundForm._remaining + ' / ' + refundForm._total + ' 次'" disabled /></el-form-item>
        <el-form-item label="退款方式">
          <el-radio-group v-model="refundForm.mode">
            <el-radio value="auto">按剩余次数比例</el-radio>
            <el-radio value="custom">自定义金额</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="应退金额">
          <el-input-number v-if="refundForm.mode === 'custom'" v-model="refundForm.amount" :min="0" :max="Number(refundForm._price)" :precision="2" />
          <el-input v-else :value="'¥' + formatMoney(refundForm._expected)" disabled />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="refundForm.remark" placeholder="可选" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="refundVisible = false">取消</el-button>
        <el-button type="danger" :loading="refundSubmitting" @click="submitRefund">确认退款</el-button>
      </template>
    </el-dialog>

    <!-- 套餐：核销记录 -->
    <el-dialog v-model="redemptionVisible" title="核销记录" width="700px">
      <el-table :data="redemptions" v-loading="loadingRedemptions" size="small" stripe>
        <el-table-column label="次数" prop="times" width="80" align="center" />
        <el-table-column label="备注" prop="note" min-width="160" show-overflow-tooltip />
        <el-table-column label="核销人" prop="operatorName" width="100" />
        <el-table-column label="时间" width="170">
          <template #default="{ row }">{{ formatDate(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- R2.1-A 疗程详情 -->
    <el-dialog v-model="treatmentDetailDialog" title="疗程进度详情" width="720px" destroy-on-close>
      <div v-if="treatmentDetail.progress">
        <h4>{{ treatmentDetail.progress.planName || '—' }} ({{ treatmentDetail.progress.serviceName || '—' }})</h4>
        <div style="color:#666; font-size: 13px; margin-bottom: 12px">
          总 {{ treatmentDetail.progress.totalSessions }} 次 · 已用 {{ treatmentDetail.progress.usedSessions }} · 剩余 {{ treatmentDetail.progress.remainingSessions }} · 当前阶段 {{ treatmentDetail.progress.currentPhaseNo }}
        </div>

        <el-divider content-position="left">阶段进度</el-divider>
        <el-timeline>
          <el-timeline-item
            v-for="(ph, i) in treatmentDetail.phases"
            :key="ph.id"
            :timestamp="`阶段 ${ph.phaseNo} · ${ph.sessionCount} 次`"
            :type="i === 0 ? 'primary' : i === 1 ? 'warning' : 'success'"
            :hollow="treatmentDetail.progress.currentPhaseNo !== ph.phaseNo"
          >
            <b>{{ ph.phaseName }}</b>
            <div style="font-size: 12px; color:#999">{{ ph.sop || '—' }}</div>
          </el-timeline-item>
        </el-timeline>

        <el-divider content-position="left">服务记录</el-divider>
        <el-empty v-if="!treatmentDetail.sessions?.length" description="暂无服务记录" :image-size="60" />
        <el-table v-else :data="treatmentDetail.sessions" stripe size="small">
          <el-table-column label="阶段" width="80">
            <template #default="{ row }">阶段 {{ row.phaseNo }}</template>
          </el-table-column>
          <el-table-column label="次序" width="80">
            <template #default="{ row }">第 {{ row.sessionNo }} 次</template>
          </el-table-column>
          <el-table-column label="技师" prop="employeeName" width="100" />
          <el-table-column label="服务时间" width="180">
            <template #default="{ row }">{{ formatDate(row.sessionAt) }}</template>
          </el-table-column>
          <el-table-column label="备注" prop="remark" show-overflow-tooltip />
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/utils/http'
import {
  Search, Plus, Edit, Delete, Lock, Unlock, User, UserFilled, Medal, Phone, Money, Coin,
  Sort, ArrowDown, Download, Upload, Check, Promotion, CopyDocument, ChatDotRound,
  Document, Calendar, Tickets, Wallet, UploadFilled, MagicStick, Share, RefreshLeft,
  Minus, Menu, Setting, Collection
} from '@element-plus/icons-vue'
import { memberApi, memberTagApi, memberLevelApi, storeApi, walletApi } from '@/api/basic'
import { packageApi } from '@/api/package'
import { treatmentApi } from '@/api/treatment'
import { couponApi } from '@/api/coupon'
import { pointApi } from '@/api/point'
import { orderApi } from '@/api/pos'

const loading = ref(false)
const submitting = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const stats = ref<any>({})
const levelList = ref<any[]>([])
const storeList = ref<any[]>([])
const tagList = ref<any[]>([])

const query = reactive({
  pageNum: 1, pageSize: 12, keyword: '', status: undefined as number | undefined,
  levelId: undefined as number | undefined,
  tagId: undefined as number | undefined,
  storeId: undefined as number | undefined,
  sortField: '', sortOrder: ''
})

// ===== 筛选条件持久化 =====
const FILTER_STORAGE_KEY = 'member-filter'
function loadPersistedFilter() {
  try {
    const raw = localStorage.getItem(FILTER_STORAGE_KEY)
    if (!raw) return
    const saved = JSON.parse(raw) as any
    if (saved.keyword !== undefined) query.keyword = saved.keyword
    if (saved.status !== undefined) query.status = saved.status
    if (saved.levelId !== undefined) query.levelId = saved.levelId
    if (saved.tagId !== undefined) query.tagId = saved.tagId
    if (saved.storeId !== undefined) query.storeId = saved.storeId
    if (saved.sortField) query.sortField = saved.sortField
    if (saved.sortOrder) query.sortOrder = saved.sortOrder
    if (saved.pageSize) query.pageSize = saved.pageSize
  } catch {}
}
watch(
  () => ({
    k: query.keyword, s: query.status, l: query.levelId, t: query.tagId,
    st: query.storeId, sf: query.sortField, so: query.sortOrder, ps: query.pageSize
  }),
  (v) => localStorage.setItem(FILTER_STORAGE_KEY, JSON.stringify(v)),
  { deep: false }
)

// ===== 表格密度 =====
const tableSize = ref<'small' | 'default' | 'large'>(
  (localStorage.getItem('member-size') as any) || 'default'
)
watch(tableSize, (v) => localStorage.setItem('member-size', v))

// ===== 列设置 =====
interface ColDef { prop: string; label: string; visible: boolean }
const DEFAULT_COLS: ColDef[] = [
  { prop: 'memberNo', label: '会员编号', visible: true },
  { prop: 'name', label: '姓名', visible: true },
  { prop: 'phone', label: '手机', visible: true },
  { prop: 'gender', label: '性别', visible: true },
  { prop: 'levelName', label: '等级', visible: true },
  { prop: 'tags', label: '标签', visible: true },
  { prop: 'balance', label: '余额', visible: true },
  { prop: 'points', label: '积分', visible: true },
  { prop: 'totalConsume', label: '累计消费', visible: true },
  { prop: 'visitCount', label: '到店次数', visible: false },
  { prop: 'lastVisitAt', label: '最近到店', visible: true },
  { prop: 'joinDate', label: '入会时间', visible: true },
  { prop: 'status', label: '状态', visible: true }
]
const COL_STORAGE_KEY = 'member-cols'
const columnDefs = ref<ColDef[]>([])
function loadColumnSettings() {
  try {
    const raw = localStorage.getItem(COL_STORAGE_KEY)
    if (raw) {
      const saved = JSON.parse(raw) as Record<string, boolean>
      columnDefs.value = DEFAULT_COLS.map((c) => ({ ...c, visible: saved[c.prop] ?? c.visible }))
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
function isColVisible(prop: string) {
  return columnDefs.value.find((c) => c.prop === prop)?.visible ?? true
}

// ===== 保存视图 =====
interface SavedView { id: string; name: string; size: 'small' | 'default' | 'large'; cols: Record<string, boolean> }
const VIEWS_KEY = 'member-views'
const savedViews = ref<SavedView[]>([])
function loadSavedViews() {
  try {
    const raw = localStorage.getItem(VIEWS_KEY)
    savedViews.value = raw ? JSON.parse(raw) : []
  } catch { savedViews.value = [] }
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
  savedViews.value.push({ id: 'v' + Date.now(), name: name.trim(), size: tableSize.value, cols })
  persistSavedViews()
  ElMessage.success('已保存视图')
}
function applyView(v: SavedView) {
  tableSize.value = v.size
  columnDefs.value = DEFAULT_COLS.map((c) => ({ ...c, visible: v.cols[c.prop] ?? c.visible }))
  persistColumnSettings()
  ElMessage.success(`已应用视图：${v.name}`)
}
async function deleteView(v: SavedView) {
  await ElMessageBox.confirm(`确认删除视图「${v.name}」？`, '提示', { type: 'warning' })
  savedViews.value = savedViews.value.filter((x) => x.id !== v.id)
  persistSavedViews()
}
async function handleViewCmd(cmd: string) {
  if (cmd === 'save') return saveCurrentAsView()
  if (cmd === 'manage') {
    const { value: picked } = await ElMessageBox.prompt(
      `请输入要删除的视图名（${savedViews.value.map((v) => v.name).join(' / ')}）`,
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
    const v = savedViews.value.find((x) => x.id === cmd.slice(6))
    if (v) applyView(v)
  }
}

// ===== 自适应高度 =====
const tableHeight = computed(() => {
  const h = window.innerHeight - 120 - 60 - 60 - 32
  return Math.max(420, h)
})

// ===== 快捷键 =====
const keywordInputRef = ref<any>(null)
function onGlobalKeydown(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'f') {
    e.preventDefault()
    keywordInputRef.value?.focus?.()
  }
}

const sortLabel = ref('默认')

const dialogVisible = ref(false)
const dialogTitle = ref('新增会员')
const isEdit = ref(false)
const formRef = ref()
const form = reactive<any>({
  id: null, name: '', phone: '', gender: 0, birthday: '', levelId: null, storeId: null,
  balance: 0, points: 0, totalConsume: 0, address: '', remark: '', status: 1, tagIds: [] as number[]
})

const rules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式错误', trigger: 'blur' }
  ]
}

const drawerVisible = ref(false)
const activeTab = ref('basic')
const current = ref<any>(null)
const profile = ref<any>(null)
const walletInfo = ref<any>(null)

// M03 档案编辑
const editProfileMode = ref(false)
const savingProfile = ref(false)
const profileForm = ref<any>({})
const skinTypeOptions = ['干性', '油性', '混合性', '敏感性', '正常']
const hairTypeOptions = ['油性', '干性', '中性', '混合性', '受损', '染烫', '细软', '粗硬']
const nailTypeOptions = ['薄软甲', '厚硬甲', '易脆甲', '干燥甲', '湿润甲', '凹凸不平', '正常甲']

const startEditProfile = () => {
  profileForm.value = { ...profile.value }
  editProfileMode.value = true
}

const cancelEditProfile = () => {
  editProfileMode.value = false
  profileForm.value = {}
}

const submitProfile = async () => {
  savingProfile.value = true
  try {
    await memberApi.saveProfile(current.value.id, profileForm.value)
    ElMessage.success('档案已保存')
    profile.value = { ...profileForm.value }
    editProfileMode.value = false
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    savingProfile.value = false
  }
}

// M03 历史消费追溯
const loadingConsume = ref(false)
const memberOrders = ref<any[]>([])
const memberOrdersTotal = ref(0)
const memberOrdersPageNum = ref(1)
const memberOrdersPageSize = ref(10)
const walletTxns = ref<any[]>([])
const loadingWallet = ref(false)

const treatmentProgresses = ref<any[]>([])
const loadingTreatment = ref(false)

const walletTagType = (t: number) => {
  const m: Record<number, string> = { 1: 'success', 2: 'warning', 3: 'info', 4: 'primary', 5: 'danger', 6: 'success' }
  return m[t] || ''
}

const treatmentStatusType = (s: number) => ({ 1: 'primary', 2: 'success', 3: 'info' }[s] || 'info')
const treatmentStatusLabel = (s: number) => ({ 1: '进行中', 2: '已完成', 3: '已停用' }[s] || '未知')

// 翻页锁定滚动
let scrollTop = 0

const selectedIds = ref<number[]>([])
const isSelected = (id: number) => selectedIds.value.includes(id)
const toggleSelect = (id: number) => {
  const i = selectedIds.value.indexOf(id)
  if (i >= 0) selectedIds.value.splice(i, 1)
  else selectedIds.value.push(id)
}
const clearSelection = () => { selectedIds.value = [] }

const batchTagDialog = ref(false)
const batchTagMode = ref(1)
const batchTagIds = ref<number[]>([])

const importDialog = ref(false)
const importing = ref(false)
const importFile = ref<File | null>(null)
const importResult = ref<any>(null)

const loadData = async (scrollRestore = false) => {
  loading.value = true
  try {
    const res: any = await memberApi.page(query)
    tableData.value = res.data?.records || []
    total.value = Number(res.data?.total || 0)
  } finally { loading.value = false }

  if (scrollRestore) {
    await nextTick()
    window.scrollTo({ top: scrollTop, behavior: 'instant' as ScrollBehavior })
  }
}

const loadStats = async () => {
  const res: any = await memberApi.stats()
  stats.value = res.data || {}
}

const loadOptions = async () => {
  const [lv, st, tg] = await Promise.all([
    memberLevelApi.list(),
    storeApi.list(),
    memberTagApi.list()
  ])
  levelList.value = (lv as any).data || []
  storeList.value = (st as any).data || []
  tagList.value = (tg as any).data || []
}

const handleSearch = () => {
  query.pageNum = 1
  loadData()
}

const resetQuery = () => {
  query.keyword = ''
  query.status = undefined
  query.levelId = undefined
  query.tagId = undefined
  query.storeId = undefined
  query.sortField = ''
  query.sortOrder = ''
  sortLabel.value = '默认'
  query.pageNum = 1
  loadData()
}

const handleSort = (cmd: string) => {
  const map: Record<string, { label: string, field: string, order: string }> = {
    'default': { label: '默认', field: '', order: '' },
    'totalConsume-desc': { label: '消费额↓', field: 'totalConsume', order: 'desc' },
    'totalConsume-asc': { label: '消费额↑', field: 'totalConsume', order: 'asc' },
    'points-desc': { label: '积分↓', field: 'points', order: 'desc' },
    'points-asc': { label: '积分↑', field: 'points', order: 'asc' },
    'balance-desc': { label: '余额↓', field: 'balance', order: 'desc' },
    'lastVisit-desc': { label: '最近到店', field: 'lastVisit', order: 'desc' },
    'joinDate-desc': { label: '入会时间', field: 'joinDate', order: 'desc' },
  }
  const cfg = map[cmd]
  if (cfg) {
    sortLabel.value = cfg.label
    query.sortField = cfg.field
    query.sortOrder = cfg.order
    loadData()
  }
}

const handleAdd = () => {
  isEdit.value = false; dialogTitle.value = '新增会员'
  Object.assign(form, {
    id: null, name: '', phone: '', gender: 0, birthday: '', levelId: null, storeId: null,
    balance: 0, points: 0, totalConsume: 0, address: '', remark: '', status: 1, tagIds: []
  })
  dialogVisible.value = true
}

const handleEdit = (row: any) => {
  isEdit.value = true; dialogTitle.value = '编辑会员'
  Object.assign(form, {
    ...row,
    tagIds: (row.tags || []).map((t: any) => t.id)
  })
  dialogVisible.value = true
}

const handleView = async (row: any) => {
  current.value = row
  profile.value = null
  walletInfo.value = null
  walletTxns.value = []
  activeTab.value = 'basic'
  drawerVisible.value = true
  const res: any = await memberApi.getById(row.id)
  if (res.data) {
    current.value = res.data.member
    profile.value = res.data.profile
  }
  // 加载详情 Tab 数据（消费/预约/套餐/储值），M04-M07 完成后会返回真实数据
  try {
    await memberApi.tabs(row.id)
  } catch {}
  // M04 已完成，加载储值流水
  loadWalletData(row.id)
  loadTreatmentData(row.id)
}

const loadWalletData = async (memberId: number) => {
  loadingWallet.value = true
  try {
    const [bal, txn]: any[] = await Promise.all([
      walletApi.getBalance(memberId),
      walletApi.transactions(memberId, { pageNum: 1, pageSize: 50 })
    ])
    walletInfo.value = bal.data
    walletTxns.value = txn.data?.records || []
  } catch {} finally { loadingWallet.value = false }
}

const loadTreatmentData = async (memberId: number) => {
  loadingTreatment.value = true
  try {
    const r: any = await treatmentApi.memberProgress(memberId)
    treatmentProgresses.value = r.data || []
  } catch {} finally { loadingTreatment.value = false }
}

/* ===== 疗程进度（R2.1-A T-A15） ===== */
const openStartTreatment = () => {
  ElMessageBox.prompt('输入疗程方案 ID', '开通疗程', {
    inputPattern: /^\\d+$/,
    inputErrorMessage: '请输入数字 ID'
  }).then(async ({ value }) => {
    try {
      await treatmentApi.createProgress({
        memberId: current.value?.id,
        planId: Number(value),
        operatorName: 'admin'
      })
      ElMessage.success('已开通')
      await loadTreatmentData(current.value.id)
    } catch (e: any) {
      ElMessage.error(e?.response?.data?.message || '开通失败')
    }
  }).catch(() => {})
}

const openRecordSession = (tp: any) => {
  ElMessageBox.prompt(
    `为「${tp.planName}」第 ${tp.currentPhaseNo} 阶段打卡一次？\n备注：`,
    '疗程打卡',
    { inputPlaceholder: '可填写备注（可选）' }
  ).then(async ({ value }) => {
    try {
      await treatmentApi.recordSession(tp.id, {
        phaseNo: tp.currentPhaseNo,
        remark: value || ''
      })
      ElMessage.success('已记录')
      await loadTreatmentData(current.value.id)
    } catch (e: any) {
      ElMessage.error(e?.response?.data?.message || '打卡失败')
    }
  }).catch(() => {})
}

const treatmentDetailDialog = ref(false)
const treatmentDetail = ref<any>({ progress: null, phases: [], sessions: [] })
const viewTreatmentDetail = async (tp: any) => {
  try {
    const r: any = await treatmentApi.progressDetail(tp.id)
    treatmentDetail.value = r.data
    treatmentDetailDialog.value = true
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || '加载失败')
  }
}

/* ===== 套餐次卡（M05） ===== */
const loadingPkg = ref(false)
const pkgOrders = ref<any[]>([])
const pkgStats = computed(() => {
  const total = pkgOrders.value.length
  const remain = pkgOrders.value.reduce((s, o) => s + (o.remainingTimes || 0), 0)
  const used = pkgOrders.value.reduce((s, o) => s + ((o.totalTimes || 0) - (o.remainingTimes || 0)), 0)
  const spent = pkgOrders.value.reduce((s, o) => s + Number(o.price || 0), 0)
  return { total, remain, used, spent }
})

const splitVisible = ref(false)
const transferVisible = ref(false)
const refundVisible = ref(false)
const redemptionVisible = ref(false)
const loadingRedemptions = ref(false)
const redemptions = ref<any[]>([])
const transferCandidates = ref<any[]>([])
const searchMemberLoading = ref(false)
const splitSubmitting = ref(false)
const transferSubmitting = ref(false)
const refundSubmitting = ref(false)

const splitForm = reactive<any>({ orderId: null, times: 1, note: '', _pkgLabel: '', _remaining: 0 })
const transferForm = reactive<any>({ orderId: null, toMemberId: null, remark: '', _pkgLabel: '', _remaining: 0 })
const refundForm = reactive<any>({ orderId: null, mode: 'auto', amount: 0, remark: '', _pkgLabel: '', _price: 0, _remaining: 0, _total: 0, _expected: 0 })

const statusLabel = (s: number) => ({ 1: '正常', 2: '冻结', 3: '已用完', 4: '已转赠', 5: '已退款', 6: '已过期' }[s] || '-')
const statusTagType = (s: number) => ({ 1: 'success', 2: 'warning', 3: 'info', 4: 'primary', 5: 'danger', 6: 'info' }[s] || '')
const canSplit = (r: any) => r.status === 1 && r.remainingTimes > 0
const canRefund = (r: any) => r.status === 1 && r.remainingTimes > 0
const canTransfer = (r: any) => r.status === 1 && r.remainingTimes > 0

const loadPackageOrders = async (memberId: number) => {
  if (!memberId) return
  loadingPkg.value = true
  try {
    const res: any = await packageApi.memberPackages(memberId, { pageNum: 1, pageSize: 50 })
    pkgOrders.value = res.data?.records || res.data || []
  } catch (e) {
    pkgOrders.value = []
  } finally { loadingPkg.value = false }
}

// POINTS-001~004：会员积分 tab
const memberPoints = ref<any | null>(null)
const pointRecords = ref<any[]>([])
const loadingPoints = ref(false)
async function loadMemberPoints(memberId: number) {
  if (!memberId) return
  loadingPoints.value = true
  try {
    const [ptRes, recRes] = await Promise.all([
      pointApi.get(memberId),
      pointApi.records(memberId, { pageNum: 1, pageSize: 20 })
    ])
    memberPoints.value = ptRes.data || ptRes
    pointRecords.value = (recRes.data?.records || recRes.data || [])
  } catch (e) {
    memberPoints.value = null
    pointRecords.value = []
  } finally {
    loadingPoints.value = false
  }
}

// M03 历史消费追溯：拉取会员 POS 订单
async function loadMemberOrders(memberId: number, page = 1) {
  if (!memberId) return
  loadingConsume.value = true
  try {
    const res: any = await memberApi.orders(memberId, { pageNum: page, pageSize: memberOrdersPageSize.value })
    const data = res.data || {}
    memberOrders.value = data.records || data.list || []
    memberOrdersTotal.value = data.total || 0
    memberOrdersPageNum.value = page
  } catch (e) {
    memberOrders.value = []
    memberOrdersTotal.value = 0
  } finally {
    loadingConsume.value = false
  }
}

function onConsumePageChange(p: number) {
  if (current.value?.id) loadMemberOrders(current.value.id, p)
}

// 订单状态辅助
function orderStatusLabel(s: number | undefined) {
  switch (s) {
    case 1: return '待支付'
    case 2: return '已支付'
    case 3: return '已退款'
    case 4: return '已作废'
    case 5: return '部分退款'
    default: return '未知'
  }
}
function orderStatusType(s: number | undefined) {
  switch (s) {
    case 2: return 'success'
    case 3: return 'danger'
    case 4: return 'info'
    case 5: return 'warning'
    default: return ''
  }
}

watch(activeTab, (tab) => {
  if (tab === 'package' && current.value?.id) loadPackageOrders(current.value.id)
  if (tab === 'coupon' && current.value?.id) loadMemberCoupons(current.value.id)
  if (tab === 'points' && current.value?.id) loadMemberPoints(current.value.id)
  if (tab === 'consume' && current.value?.id) loadMemberOrders(current.value.id, 1)
})
watch(() => current.value?.id, (id) => {
  if (id && activeTab.value === 'package') loadPackageOrders(id)
  if (id && activeTab.value === 'coupon') loadMemberCoupons(id)
  if (id && activeTab.value === 'points') loadMemberPoints(id)
  if (id && activeTab.value === 'consume') loadMemberOrders(id, 1)
})

// COUPON-008：会员优惠券
const memberCoupons = ref<any[]>([])
const loadingCoupon = ref(false)
async function loadMemberCoupons(memberId: number) {
  loadingCoupon.value = true
  try {
    const r: any = await couponApi.memberCoupons(memberId, { pageNum: 1, pageSize: 100 })
    memberCoupons.value = r.data?.records || []
  } catch (e) {
    memberCoupons.value = []
  } finally {
    loadingCoupon.value = false
  }
}

const openSplitRedeem = (row: any) => {
  Object.assign(splitForm, { orderId: row.id, times: 1, note: '', _pkgLabel: `${row.packageName} (¥${formatMoney(row.price)})`, _remaining: row.remainingTimes })
  splitVisible.value = true
}
const submitSplit = async () => {
  if (!splitForm.times || splitForm.times < 1) return ElMessage.warning('请填写核销次数')
  splitSubmitting.value = true
  try {
    const res: any = await packageApi.redeemSplit({
      orderId: splitForm.orderId,
      operatorId: 1,
      note: splitForm.note,
      items: [{ times: splitForm.times, serviceItem: splitForm._pkgLabel?.split(' ')[0] || '套餐核销' }]
    })
    if (res.code === 200) {
      ElMessage.success(res.message || '核销成功')
      splitVisible.value = false
      loadPackageOrders(current.value.id)
    } else ElMessage.error(res.message || '核销失败')
  } finally { splitSubmitting.value = false }
}

const searchMembersForTransfer = async (kw: string) => {
  searchMemberLoading.value = true
  try {
    const res: any = await memberApi.page({ pageNum: 1, pageSize: 30, keyword: kw || '', status: 1 })
    transferCandidates.value = (res.data?.records || []).filter((m: any) => m.id !== current.value?.id)
  } finally { searchMemberLoading.value = false }
}
const openTransfer = async (row: any) => {
  Object.assign(transferForm, { orderId: row.id, toMemberId: null, remark: '', _pkgLabel: `${row.packageName} (¥${formatMoney(row.price)})`, _remaining: row.remainingTimes })
  transferVisible.value = true
  await searchMembersForTransfer('')
}
const submitTransfer = async () => {
  if (!transferForm.toMemberId) return ElMessage.warning('请选择目标会员')
  transferSubmitting.value = true
  try {
    const res: any = await packageApi.transfer(transferForm.orderId, {
      toMemberId: transferForm.toMemberId, operatorId: 1, remark: transferForm.remark
    })
    if (res.code === 200) {
      ElMessage.success(res.message || '转赠成功')
      transferVisible.value = false
      loadPackageOrders(current.value.id)
    } else ElMessage.error(res.message || '转赠失败')
  } finally { transferSubmitting.value = false }
}

const calcExpectedRefund = () => {
  if (refundForm.mode === 'custom') return Number(refundForm.amount || 0)
  const used = Number(refundForm._total || 0) - Number(refundForm._remaining || 0)
  const total = Number(refundForm._total || 0)
  if (total <= 0) return 0
  const kept = Math.max(0, Math.min(used, total)) / total
  return Number((Number(refundForm._price || 0) * (1 - kept)).toFixed(2))
}
watch(() => [refundForm.mode, refundForm.amount, refundForm._price, refundForm._remaining, refundForm._total],
  () => { refundForm._expected = calcExpectedRefund() })

const openRefund = (row: any) => {
  Object.assign(refundForm, {
    orderId: row.id, mode: 'auto', amount: 0, remark: '',
    _pkgLabel: `${row.packageName} (¥${formatMoney(row.price)})`,
    _price: Number(row.price), _remaining: row.remainingTimes, _total: row.totalTimes, _expected: 0
  })
  refundForm._expected = calcExpectedRefund()
  refundVisible.value = true
}
const submitRefund = async () => {
  refundSubmitting.value = true
  try {
    const params: any = { operatorId: 1, remark: refundForm.remark }
    if (refundForm.mode === 'custom') params.amount = Number(refundForm.amount || 0)
    const res: any = await packageApi.refund(refundForm.orderId, params)
    if (res.code === 200) {
      ElMessage.success(res.message || '退款成功')
      refundVisible.value = false
      loadPackageOrders(current.value.id)
    } else ElMessage.error(res.message || '退款失败')
  } finally { refundSubmitting.value = false }
}

const openRedemptions = async (row: any) => {
  redemptionVisible.value = true
  loadingRedemptions.value = true
  try {
    const res: any = await packageApi.redemptions(row.id, { pageNum: 1, pageSize: 50 })
    redemptions.value = res.data?.records || res.data || []
  } catch { redemptions.value = [] }
  finally { loadingRedemptions.value = false }
}


const handleSubmit = async () => {
  await formRef.value.validate()
  submitting.value = true
  try {
    if (isEdit.value) await memberApi.update(form)
    else await memberApi.create(form)
    ElMessage.success(isEdit.value ? '更新成功' : '创建成功')
    dialogVisible.value = false
    loadData(); loadStats()
  } finally { submitting.value = false }
}

const handleDelete = async (row: any) => {
  await ElMessageBox.confirm(`确定删除会员「${row.name}」?`, '提示', { type: 'warning' })
  await memberApi.delete(row.id)
  ElMessage.success('删除成功')
  selectedIds.value = selectedIds.value.filter(id => id !== row.id)
  loadData(); loadStats()
}

const handleFreeze = async (row: any) => {
  await memberApi.freeze(row.id)
  ElMessage.success('已冻结')
  loadData(); loadStats()
}

const handleUnfreeze = async (row: any) => {
  await memberApi.unfreeze(row.id)
  ElMessage.success('已解冻')
  loadData(); loadStats()
}

const handleCall = (row: any) => {
  if (row.phone) {
    window.location.href = `tel:${row.phone}`
  }
}

const copyText = async (text: string, label: string) => {
  if (!text) {
    ElMessage.warning(`${label}为空`)
    return
  }
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(`${label}已复制`)
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}

const handleBatchTag = (mode: number) => {
  batchTagMode.value = mode
  batchTagIds.value = []
  batchTagDialog.value = true
}

const confirmBatchTag = async () => {
  if (batchTagIds.value.length === 0) {
    ElMessage.warning('请选择标签')
    return
  }
  const res: any = await memberApi.batchTag({
    memberIds: selectedIds.value,
    tagIds: batchTagIds.value,
    op: batchTagMode.value
  })
  ElMessage.success(res.message || '操作成功')
  batchTagDialog.value = false
  clearSelection()
  loadData()
}

const handleBatchOp = async (op: string) => {
  const opName = op === 'freeze' ? '冻结' : op === 'unfreeze' ? '解冻' : '删除'
  await ElMessageBox.confirm(`确定${opName}选中的 ${selectedIds.value.length} 位会员?`, '提示', { type: 'warning' })
  const res: any = await memberApi.batchOperation({ memberIds: selectedIds.value, op })
  ElMessage.success(res.message || '操作成功')
  clearSelection()
  loadData(); loadStats()
}

const handleExport = async () => {
  const params = {
    keyword: query.keyword,
    status: query.status,
    levelId: query.levelId,
    tagId: query.tagId,
    storeId: query.storeId
  }
  try {
    const result: any = await memberApi.exportCsv(params)
    const url = URL.createObjectURL(result.blob)
    const a = document.createElement('a')
    a.href = url
    a.download = result.filename
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
    ElMessage.success('导出已开始')
  } catch (e: any) {
    ElMessage.error(e?.message || '导出失败')
  }
}

const handleImport = () => {
  importFile.value = null
  importResult.value = null
  importDialog.value = true
}

const handleFileChange = (file: any) => {
  importFile.value = file.raw
  importResult.value = null
}

const handleExceed = () => {
  ElMessage.warning('只能上传一个文件')
}

const submitImport = async () => {
  if (!importFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }
  importing.value = true
  try {
    const fd = new FormData()
    fd.append('file', importFile.value)
    const res: any = await memberApi.import(fd)
    importResult.value = res.data
    ElMessage.success(res.message || '导入完成')
    loadData(); loadStats()
  } finally {
    importing.value = false
  }
}

const downloadTemplate = () => {
  const csv = '\ufeff姓名,手机号,性别,生日,备注\n张三,13800138000,女,1990-01-01,示例备注\n'
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'member_template.csv'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

const formatMoney = (v: any) => Number(v || 0).toFixed(2)
const formatDate = (v: any) => v ? new Date(v).toLocaleString('zh-CN') : ''
const formatDateTime = (v: any) => v ? new Date(v).toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-') : ''
const isExpireSoon = (v: any) => v ? (new Date(v).getTime() - Date.now()) < 30 * 24 * 3600 * 1000 : false
const genderText = (g: number) => g === 1 ? '男' : g === 2 ? '女' : '未知'

const trendClass = (v: any) => {
  if (v > 0) return 'up'
  if (v < 0) return 'down'
  return 'flat'
}

const trendSign = (v: any) => {
  if (v > 0) return '+'
  if (v < 0) return ''
  return ''
}

const getAvatarColor = (name: string) => {
  const colors = ['#FF6B6B', '#4ECDC4', '#45B7D1', '#FFA07A', '#98D8C8', '#FFD93D', '#6BCB77', '#C77DFF', '#F8B500', '#43E97B']
  if (!name) return colors[0]
  return colors[name.charCodeAt(0) % colors.length]
}

const getLevelClass = (levelName: string) => {
  if (!levelName) return 'level-default'
  if (levelName.includes('钻石')) return 'level-diamond'
  if (levelName.includes('金')) return 'level-gold'
  if (levelName.includes('银')) return 'level-silver'
  return 'level-default'
}

onMounted(() => {
  loadColumnSettings()
  loadPersistedFilter()
  loadSavedViews()
  window.addEventListener('keydown', onGlobalKeydown)
  loadData(); loadStats(); loadOptions()
  // 翻页锁定滚动
  const orig = window.scrollTo
  window.scrollTo = function(...args: any[]) {
    if (document.querySelector('.member-grid')) {
      scrollTop = window.pageYOffset
    }
    return orig.apply(window, args as any)
  }
})

onUnmounted(() => {
  window.removeEventListener('keydown', onGlobalKeydown)
})
</script>

<style scoped>
.member-page { padding: 0; }

/* 统计卡片 */
.stats-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 20px; margin-bottom: 20px; }
.stat-card {
  background: #fff; border-radius: 16px; padding: 24px; position: relative; overflow: hidden;
  display: flex; align-items: center; gap: 16px; transition: all 0.3s;
  box-shadow: 0 4px 12px rgba(0,0,0,0.05);
}
.stat-card:hover { transform: translateY(-3px); box-shadow: 0 8px 24px rgba(0,0,0,0.1); }
.stat-card::before {
  content: ''; position: absolute; right: -30px; top: -30px; width: 120px; height: 120px;
  border-radius: 50%; opacity: 0.08;
}
.stat-1::before { background: linear-gradient(135deg, #667eea, #764ba2); }
.stat-2::before { background: linear-gradient(135deg, #f093fb, #f5576c); }
.stat-3::before { background: linear-gradient(135deg, #4facfe, #00f2fe); }
.stat-4::before { background: linear-gradient(135deg, #43e97b, #38f9d7); }

.stat-icon {
  width: 56px; height: 56px; border-radius: 14px; display: flex; align-items: center;
  justify-content: center; color: #fff;
}
.stat-1 .stat-icon { background: linear-gradient(135deg, #667eea, #764ba2); }
.stat-2 .stat-icon { background: linear-gradient(135deg, #f093fb, #f5576c); }
.stat-3 .stat-icon { background: linear-gradient(135deg, #4facfe, #00f2fe); }
.stat-4 .stat-icon { background: linear-gradient(135deg, #43e97b, #38f9d7); }

.stat-info { flex: 1; }
.stat-value { font-size: 28px; font-weight: 700; color: #1f2937; line-height: 1.2; }
.stat-label { font-size: 13px; color: #6b7280; margin-top: 4px; }
.stat-trend { position: absolute; top: 16px; right: 16px; font-size: 12px; color: #9ca3af; }
.stat-trend .up { color: #10b981; font-weight: 600; margin-left: 4px; }
.stat-trend .down { color: #ef4444; font-weight: 600; margin-left: 4px; }
.stat-trend .flat { color: #9ca3af; font-weight: 600; margin-left: 4px; }

/* 工具栏 */
.toolbar {
  background: #fff; border-radius: 12px; padding: 16px 20px; margin-bottom: 20px;
  display: flex; justify-content: space-between; align-items: center;
  box-shadow: 0 2px 8px rgba(0,0,0,0.04);
}
.toolbar-left { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.toolbar-right { display: flex; gap: 10px; align-items: center; }
.ml-1 { margin-left: 4px; }

/* 批量操作栏 */
.batch-bar {
  background: linear-gradient(135deg, #fef3c7, #fde68a);
  border-radius: 12px; padding: 12px 20px; margin-bottom: 16px;
  display: flex; justify-content: space-between; align-items: center;
  box-shadow: 0 2px 8px rgba(245, 158, 11, 0.2);
}
.batch-info { font-size: 14px; color: #92400e; display: flex; align-items: center; gap: 6px; }
.batch-info b { color: #78350f; font-size: 16px; margin: 0 4px; }
.batch-actions { display: flex; gap: 8px; }

/* 卡片网格 */
.member-grid {
  display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px; margin-bottom: 20px;
}
.member-card {
  background: #fff; border-radius: 16px; overflow: hidden; cursor: pointer;
  transition: all 0.3s; box-shadow: 0 4px 12px rgba(0,0,0,0.05);
  position: relative;
}
.member-card:hover { transform: translateY(-5px); box-shadow: 0 12px 28px rgba(0,0,0,0.12); }
.member-card.selected { box-shadow: 0 0 0 2px #f59e0b, 0 12px 28px rgba(245, 158, 11, 0.2); }
.member-card.frozen .card-body { opacity: 0.6; }

.card-checkbox {
  position: absolute; top: 12px; right: 12px; z-index: 2;
  background: rgba(255,255,255,0.9); border-radius: 4px; padding: 4px;
}

.card-header {
  padding: 16px 20px; color: #fff; display: flex; justify-content: space-between; align-items: center;
  background: linear-gradient(135deg, #6b7280, #4b5563);
}
.card-header.level-gold { background: linear-gradient(135deg, #f59e0b, #d97706); }
.card-header.level-silver { background: linear-gradient(135deg, #9ca3af, #6b7280); }
.card-header.level-diamond { background: linear-gradient(135deg, #06b6d4, #0891b2); }
.card-level { display: flex; align-items: center; gap: 6px; font-weight: 600; }
.level-rank { font-size: 12px; opacity: 0.85; background: rgba(255,255,255,0.2); padding: 2px 8px; border-radius: 10px; }

.card-body { padding: 24px 20px 16px; text-align: center; position: relative; }
.avatar-wrap { position: relative; display: inline-block; margin-bottom: 12px; }
.avatar-circle {
  width: 72px; height: 72px; border-radius: 50%; color: #fff; font-size: 30px;
  font-weight: 600; display: flex; align-items: center; justify-content: center;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
}
.gender-icon {
  position: absolute; right: -4px; bottom: -4px; min-width: 24px; height: 24px; padding: 0 6px; border-radius: 12px;
  background: #ec4899; color: #fff; font-size: 11px; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
  border: 2px solid #fff;
}
.gender-icon.male { background: #3b82f6; }
.member-name { font-size: 18px; font-weight: 600; color: #1f2937; }
.member-no { font-size: 12px; color: #9ca3af; margin-top: 2px; }
.member-phone { font-size: 13px; color: #6b7280; margin-top: 8px; display: flex; align-items: center; justify-content: center; gap: 4px; }

.tag-list { margin-top: 12px; display: flex; flex-wrap: wrap; gap: 4px; justify-content: center; min-height: 24px; }
.mini-tag {
  font-size: 11px; padding: 2px 8px; border-radius: 10px; border: 1px solid;
}

.card-stats {
  display: flex; justify-content: space-around; padding: 16px 20px;
  background: #f9fafb; border-top: 1px solid #f3f4f6; border-bottom: 1px solid #f3f4f6;
}
.stat-item { text-align: center; }
.stat-num { font-size: 16px; font-weight: 700; color: #1f2937; }
.stat-lbl { font-size: 11px; color: #9ca3af; margin-top: 2px; }
.stat-divider { width: 1px; background: #e5e7eb; }

.card-badges { padding: 8px 16px; text-align: center; background: #fef2f2; }
.card-footer { padding: 8px 12px; display: flex; justify-content: space-around; flex-wrap: wrap; gap: 4px; }

/* 运营引导 */
.guide-card {
  background: linear-gradient(135deg, #fef3c7, #fed7aa);
  border-radius: 16px; padding: 40px 24px; text-align: center;
  border: 2px dashed #f59e0b;
}
.guide-icon { color: #f59e0b; margin-bottom: 12px; }
.guide-card h3 { margin: 0 0 8px; color: #92400e; font-size: 18px; }
.guide-card p { color: #b45309; margin: 0 0 16px; font-size: 14px; }
.guide-actions { display: flex; gap: 12px; justify-content: center; }

.empty-tip {
  grid-column: 1 / -1; background: #fff; border-radius: 16px; padding: 60px 20px;
  text-align: center; color: #9ca3af;
}
.empty-tip p { margin: 16px 0; }
.empty-actions { display: flex; gap: 12px; justify-content: center; }

/* 分页 */
.pagination { display: flex; justify-content: flex-end; padding: 20px;
  background: #fff; border-radius: 12px; box-shadow: 0 2px 8px rgba(0,0,0,0.04); }

/* 详情 */
.detail-wrap { padding: 0 8px; }
.detail-hero {
  margin: -20px -20px 20px; padding: 32px 24px; color: #fff;
  background: linear-gradient(135deg, #6b7280, #4b5563);
  display: flex; gap: 16px; align-items: center;
  border-radius: 12px 12px 0 0;
}
.detail-hero.level-gold { background: linear-gradient(135deg, #f59e0b, #d97706); }
.detail-hero.level-silver { background: linear-gradient(135deg, #9ca3af, #6b7280); }
.detail-hero.level-diamond { background: linear-gradient(135deg, #06b6d4, #0891b2); }
.hero-avatar {
  width: 80px; height: 80px; border-radius: 50%; background: rgba(255,255,255,0.3);
  color: #fff; font-size: 32px; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
  border: 3px solid rgba(255,255,255,0.5);
  flex-shrink: 0;
}
.hero-info { flex: 1; }
.hero-name { font-size: 22px; font-weight: 700; }
.hero-meta { margin-top: 8px; display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.hero-no { font-size: 13px; opacity: 0.85; }
.hero-actions { margin-top: 12px; display: flex; gap: 8px; flex-wrap: wrap; }

.detail-tabs { margin-top: -10px; }
.detail-tabs :deep(.el-tabs__nav-wrap::after) { background: #f3f4f6; }

.detail-grid {
  display: grid; grid-template-columns: 1fr 1fr; gap: 12px 24px;
  padding: 16px 0; border-bottom: 1px solid #f3f4f6;
}
.detail-item { display: flex; flex-direction: column; gap: 4px; }
.detail-item label { font-size: 12px; color: #9ca3af; }
.detail-item span { font-size: 14px; color: #1f2937; font-weight: 500; }

.detail-section { padding: 16px 0; border-bottom: 1px solid #f3f4f6; }
.detail-section h4 { margin: 0 0 12px; font-size: 14px; color: #6b7280; font-weight: 600; }
.section-header-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.section-header-row h4 { margin: 0; }

.finance-grid { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 12px; }
.finance-box {
  background: linear-gradient(135deg, #fef3c7, #fde68a); border-radius: 10px;
  padding: 16px; text-align: center;
}
.finance-label { font-size: 12px; color: #92400e; }
.finance-val { font-size: 20px; font-weight: 700; color: #78350f; margin-top: 4px; }

.tag-cloud { display: flex; flex-wrap: wrap; gap: 8px; }
.big-tag { padding: 6px 14px; border-radius: 20px; border: 1px solid; font-size: 13px; font-weight: 500; }

.text-content { margin: 0; color: #4b5563; font-size: 14px; line-height: 1.6; }

.tag-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 6px; vertical-align: middle; }

/* Tab 占位 */
.tab-placeholder {
  text-align: center; padding: 60px 20px; color: #9ca3af;
}
.tab-placeholder p { margin: 12px 0 4px; font-size: 14px; }
.tab-placeholder small { font-size: 12px; color: #c0c4cc; }
.pager-bar { display:flex; justify-content:center; margin-top: 16px; }

/* R2.1-A 疗程卡片 */
.treatment-card {
  border: 1px solid #e5e7eb; border-radius: 10px; padding: 16px; margin-bottom: 12px;
  background: #fff;
}
.treatment-card h4 { margin: 0 0 6px; font-size: 16px; }
.tp-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 8px; }
.tp-meta { display: flex; gap: 12px; flex-wrap: wrap; font-size: 13px; color: #6b7280; }
.tp-counts { display: flex; gap: 16px; font-size: 13px; color: #4b5563; margin: 8px 0 4px; }

/* 钱包摘要 */
.wallet-summary { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; margin-bottom: 16px; }
.ws-item {
  background: linear-gradient(135deg, #fef3c7, #fde68a); border-radius: 10px;
  padding: 16px; text-align: center;
}
.ws-label { font-size: 12px; color: #92400e; }
.ws-val { font-size: 20px; font-weight: 700; color: #78350f; margin-top: 4px; }
.text-success { color: #67c23a; }
.text-danger { color: #f56c6c; }

/* 批量弹窗 */
.batch-tip { color: #6b7280; margin-bottom: 16px; }

/* 导入 */
.import-content { padding: 0 12px; }
.import-tip { color: #6b7280; margin-bottom: 12px; }
.format-hint { background: #f9fafb; padding: 10px 14px; border-radius: 8px; font-size: 13px; color: #4b5563; margin-bottom: 16px; }
.upload-area { width: 100%; }
.upload-icon { color: #409eff; margin-bottom: 8px; }
.import-result { margin-top: 16px; }
.err-line { margin: 4px 0; font-size: 12px; color: #ef4444; }

/* 冻结样式 */
.member-card.frozen::after {
  content: '冻结'; position: absolute; top: 12px; left: 12px;
  background: #ef4444; color: #fff; padding: 2px 8px; font-size: 11px;
  border-radius: 10px; transform: rotate(-5deg); font-weight: 600;
  z-index: 2;
}
</style>
