<template>
  <div class="mp-page">
    <div class="banner">
      <div class="banner-left">
        <el-icon :size="32" color="#fff"><UserFilled /></el-icon>
        <div>
          <h2>会员套餐明细</h2>
          <p>选择会员查看其持有的套餐、剩余次数、最近核销记录，支持核销与冻结</p>
        </div>
      </div>
      <el-button @click="goList" :icon="Back">返回套餐列表</el-button>
    </div>

    <!-- 顶部系统级 KPI -->
    <div class="kpi-grid">
      <div class="kpi-card kpi-amber">
        <div class="kpi-label">在售套餐</div>
        <div class="kpi-value">{{ sysStats.listed ?? '—' }}</div>
        <div class="kpi-sub">下架 {{ sysStats.unlisted ?? '—' }} 个</div>
      </div>
      <div class="kpi-card kpi-blue">
        <div class="kpi-label">累计售出</div>
        <div class="kpi-value">{{ sysStats.totalSale ?? '—' }}</div>
        <div class="kpi-sub">累计核销 {{ sysStats.totalRedeem ?? '—' }} 次</div>
      </div>
      <div class="kpi-card kpi-purple">
        <div class="kpi-label">活跃订单</div>
        <div class="kpi-value">{{ sysStats.activeOrders ?? '—' }}</div>
        <div class="kpi-sub">状态 = 正常 / 冻结</div>
      </div>
      <div class="kpi-card kpi-green">
        <div class="kpi-label">剩余可用次数</div>
        <div class="kpi-value">{{ sysStats.totalRemaining ?? '—' }}</div>
        <div class="kpi-sub">所有活跃订单的剩余总和</div>
      </div>
    </div>

    <el-card shadow="never">
      <div class="filter-bar">
        <el-select v-model="memberId" placeholder="选择会员" filterable remote
          :remote-method="searchMembers" :loading="searchLoading" style="width: 320px" @change="loadOrders">
          <template #prefix><el-icon><User /></el-icon></template>
          <el-option v-for="m in memberOpts" :key="m.id" :value="m.id"
            :label="`${m.name} | ${m.phone}`">
            <div style="display:flex;justify-content:space-between">
              <span>{{ m.name }}</span>
              <span style="font-size:12px;color:#909399">{{ m.memberNo || m.phone }} · ¥{{ formatMoney(m.balance) }}</span>
            </div>
          </el-option>
        </el-select>
        <el-button v-if="memberId" type="success" :icon="ShoppingCart" @click="purchaseVisible = true">购买套餐</el-button>
        <el-button v-if="memberId" type="primary" :icon="Plus" @click="openRedeem()">核销套餐</el-button>
      </div>

      <!-- 未选会员时的引导 -->
      <div v-if="!memberId" class="empty-hint">
        <el-icon :size="48" color="#f59e0b"><User /></el-icon>
        <p>请在上方搜索框输入 <b>姓名 / 手机号</b> 选定一名会员</p>
        <p class="muted">系统会展示该会员持有的套餐、剩余次数、即将到期的提醒，以及核销记录</p>
      </div>

      <!-- 选中会员后顶部信息卡 -->
      <el-card v-if="memberInfo" shadow="never" class="member-card">
        <el-avatar :size="56" style="background:linear-gradient(135deg,#f59e0b,#ef4444);color:#fff;font-size:24px;font-weight:600">
          {{ memberInfo.name?.slice(0,1) || '会' }}
        </el-avatar>
        <div class="member-meta">
          <div class="row1">
            <span class="name">{{ memberInfo.name }}</span>
            <el-tag size="small" :type="memberInfo.gender === 1 ? 'primary' : 'danger'" effect="plain">
              {{ memberInfo.gender === 1 ? '男' : memberInfo.gender === 2 ? '女' : '未填' }}
            </el-tag>
            <el-tag size="small" type="warning" effect="plain" v-if="memberInfo.levelName">
              {{ memberInfo.levelName }}
            </el-tag>
          </div>
          <div class="row2">
            <span><el-icon><User /></el-icon> {{ memberInfo.phone || '未填手机' }}</span>
            <span>积分：<b>{{ memberInfo.points || 0 }}</b></span>
            <span>余额：<b class="balance">¥{{ formatMoney(memberInfo.balance) }}</b></span>
            <span>累计消费：<b>¥{{ formatMoney(memberInfo.totalAmount) }}</b></span>
            <span v-if="memberInfo.joinDate">入会：{{ formatDate(memberInfo.joinDate) }}</span>
          </div>
        </div>
      </el-card>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="持有的套餐" name="orders">
          <div v-if="!memberId" class="empty-hint-inner">
            <el-icon :size="32" color="#c0c4cc"><DocumentRemove /></el-icon>
            <p>请先在上方选择一名会员，再查看其套餐</p>
          </div>
          <template v-else>
          <el-table :data="orders" v-loading="ordersLoading" stripe :row-class-name="rowClassName">
            <el-table-column label="订单号" prop="orderNo" min-width="180" />
            <el-table-column label="套餐" prop="packageName" min-width="160" />
            <el-table-column label="总/剩余" width="100" align="center">
              <template #default="{ row }">
                <el-tag size="small" type="info">{{ row.totalTimes }}</el-tag>
                /
                <el-tag size="small" :type="row.remainingTimes > 0 ? 'success' : 'danger'">
                  {{ row.remainingTimes }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="实付" prop="price" width="100" align="right">
              <template #default="{ row }">¥{{ formatMoney(row.price) }}</template>
            </el-table-column>
            <el-table-column label="购买时间" prop="buyTime" width="160">
              <template #default="{ row }">{{ formatDate(row.buyTime) }}</template>
            </el-table-column>
            <el-table-column label="到期" prop="expireTime" width="180">
              <template #default="{ row }">
                <span :class="{ 'expire-soon': isExpireSoon(row.expireTime) }">
                  {{ formatDate(row.expireTime) }}
                  <el-tag v-if="isExpireSoon(row.expireTime)" size="small" type="danger" effect="dark" style="margin-left:4px">即将到期</el-tag>
                </span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" effect="dark" size="small">
                  {{ row.statusLabel }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="380" fixed="right">
              <template #default="{ row }">
                <el-button text type="primary" :icon="CreditCard"
                  :disabled="row.status !== 1" @click="openRedeem(row)">
                  核销
                </el-button>
                <el-button text type="warning" :icon="Clock"
                  :disabled="row.status !== 1" @click="openExtend(row)">
                  续期
                </el-button>
                <el-button text type="info" :icon="SwitchIcon"
                  :disabled="row.status !== 1 || row.remainingTimes <= 0" @click="openSplit(row)">
                  拆分核销
                </el-button>
                <el-button text type="success" :icon="PromotionIcon"
                  :disabled="row.status !== 1 || row.remainingTimes <= 0" @click="openTransfer(row)">
                  转赠
                </el-button>
                <el-button text type="danger" :icon="MoneyIcon"
                  :disabled="row.status !== 1 || row.remainingTimes <= 0" @click="openRefund(row)">
                  退款
                </el-button>
                <el-button text type="warning" :icon="ArrowUpIcon"
                  :disabled="row.status !== 1 || row.remainingTimes <= 0" @click="openUpgrade(row)">
                  升级
                </el-button>
                <el-button text :type="row.status === 4 ? 'success' : 'warning'"
                  :icon="row.status === 4 ? UnlockIcon : LockIcon"
                  @click="toggleFreeze(row)">
                  {{ row.status === 4 ? '解冻' : '冻结' }}
                </el-button>
                <el-button text type="warning" :icon="PauseIcon"
                  :disabled="row.status !== 1"
                  @click="openSuspend(row)">
                  挂起
                </el-button>
                <el-button text type="success" :icon="PlayIcon"
                  :disabled="row.status !== 8"
                  @click="openResume(row)">
                  恢复
                </el-button>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="!orders.length && memberId" class="empty">该会员暂无套餐记录</div>
          </template>
        </el-tab-pane>

        <el-tab-pane label="核销记录" name="redeems">
          <div v-if="!memberId" class="empty-hint-inner">
            <el-icon :size="32" color="#c0c4cc"><DocumentRemove /></el-icon>
            <p>请先在上方选择一名会员，再查看其核销记录</p>
          </div>
          <template v-else>
          <el-table :data="redeems" v-loading="redeemLoading" stripe>
            <el-table-column label="ID" prop="id" width="70" />
            <el-table-column label="订单ID" prop="orderId" width="90" />
            <el-table-column label="套餐" prop="packageName" min-width="160" />
            <el-table-column label="次数" prop="times" width="80" align="center" />
            <el-table-column label="服务项目" prop="serviceItem" min-width="140" />
            <el-table-column label="美容师" prop="beauticianName" width="120">
              <template #default="{ row }">
                <el-tag v-if="row.beauticianName" size="small" type="info" effect="plain">{{ row.beauticianName }}</el-tag>
                <span v-else style="color:#bbb">未指定</span>
              </template>
            </el-table-column>
            <el-table-column label="核销时间" prop="redeemTime" min-width="180">
              <template #default="{ row }">{{ formatDate(row.redeemTime) }}</template>
            </el-table-column>
            <el-table-column label="备注" prop="note" min-width="160" show-overflow-tooltip />
          </el-table>
          <div v-if="!redeems.length && memberId" class="empty">暂无核销记录</div>
          </template>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 最近会员快捷入口 + 全站流水 -->
    <el-row :gutter="12" style="margin-top: 16px">
      <el-col :xs="24" :md="8">
        <el-card shadow="never" class="side-card">
          <template #header>
            <div class="side-header">
              <span><el-icon><User /></el-icon> 最近会员（最近有套餐订单）</span>
              <el-button text type="primary" @click="loadRecentMembers" :loading="recentMembersLoading">刷新</el-button>
            </div>
          </template>
          <div v-if="recentMembers.length" class="recent-members">
            <div v-for="m in recentMembers" :key="m.id" class="member-pill" @click="quickPick(m)">
              <el-avatar :size="32" style="background:linear-gradient(135deg,#f59e0b,#ef4444);color:#fff;font-weight:600">
                {{ (m.name || '会').slice(0, 1) }}
              </el-avatar>
              <div class="info">
                <div class="name">{{ m.name }} <span class="phone">{{ m.phone || '-' }}</span></div>
                <div class="meta">最近订单 #{{ m.lastOrderId }} · {{ formatDate(m.lastBuyTime) }}</div>
              </div>
            </div>
          </div>
          <div v-else class="empty">暂无最近会员</div>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="8">
        <el-card shadow="never" class="side-card">
          <template #header>
            <div class="side-header">
              <span><el-icon><Tickets /></el-icon> 全站最近订单</span>
              <el-button text type="primary" @click="loadRecentOrders" :loading="recentOrdersLoading">刷新</el-button>
            </div>
          </template>
          <el-table :data="recentOrders" size="small" :show-header="true" max-height="320">
            <el-table-column prop="orderNo" label="订单号" min-width="120" show-overflow-tooltip />
            <el-table-column prop="memberName" label="会员" min-width="80" show-overflow-tooltip />
            <el-table-column prop="packageName" label="套餐" min-width="100" show-overflow-tooltip />
            <el-table-column label="剩余" width="60" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.remainingTimes > 0 ? 'success' : 'info'">{{ row.remainingTimes }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="buyTime" label="时间" width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ formatDate(row.buyTime) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="8">
        <el-card shadow="never" class="side-card">
          <template #header>
            <div class="side-header">
              <span><el-icon><Document /></el-icon> 全站最近核销</span>
              <el-button text type="primary" @click="loadRecentRedemptions" :loading="recentRedeemLoading">刷新</el-button>
            </div>
          </template>
          <el-table :data="recentRedeems" size="small" :show-header="true" max-height="320">
            <el-table-column prop="memberName" label="会员" min-width="80" show-overflow-tooltip />
            <el-table-column prop="packageName" label="套餐" min-width="100" show-overflow-tooltip />
            <el-table-column prop="times" label="次数" width="60" align="center" />
            <el-table-column prop="serviceItem" label="项目" min-width="100" show-overflow-tooltip />
            <el-table-column prop="redeemTime" label="时间" width="120" show-overflow-tooltip>
              <template #default="{ row }">{{ formatDate(row.redeemTime) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 核销弹窗 -->
    <el-dialog v-model="redeemVisible" title="套餐核销" width="500px">
      <el-form :model="redeemForm" label-width="100px">
        <el-form-item label="选择套餐">
          <el-select v-model="redeemForm.orderId" placeholder="选择要核销的套餐" style="width:100%">
            <el-option v-for="o in normalOrders" :key="o.orderId"
              :value="o.orderId"
              :label="`${o.packageName} | ${o.remainingTimes}/${o.totalTimes}次 | ${formatDate(o.expireTime)}到期`" />
          </el-select>
        </el-form-item>
        <el-form-item label="本次核销次数">
          <el-input-number v-model="redeemForm.times" :min="1" :max="99" style="width:200px" />
        </el-form-item>
        <el-form-item label="服务项目">
          <el-input v-model="redeemForm.serviceItem" placeholder="如：面部清洁" />
        </el-form-item>
        <el-form-item label="美容师">
          <el-select v-model="redeemForm.beauticianId" placeholder="选择美容师" filterable style="width:100%"
            :loading="employeeLoading">
            <el-option v-for="e in beauticianOpts" :key="e.id" :value="e.id"
              :label="e.employeeName + (e.position ? `（${e.position}）` : '') + (e.storeName ? ` · ${e.storeName}` : '')" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="redeemForm.note" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="redeemVisible = false">取消</el-button>
        <el-button type="primary" :loading="redeemSubmitting" @click="submitRedeem">确认核销</el-button>
      </template>
    </el-dialog>

    <!-- 续期弹窗 -->
    <el-dialog v-model="extendVisible" title="套餐续期" width="420px">
      <el-form :model="extendForm" label-width="100px">
        <el-form-item label="套餐">
          <el-input :value="extendForm._pkgLabel" disabled />
        </el-form-item>
        <el-form-item label="当前到期">
          <el-input :value="extendForm._oldExpire" disabled />
        </el-form-item>
        <el-form-item label="顺延天数">
          <el-input-number v-model="extendForm.days" :min="1" :max="3650" style="width:200px" />
          <span class="amount-tip">天</span>
        </el-form-item>
        <el-form-item label="到期预估">
          <el-input :value="extendForm._newExpire" disabled />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="extendVisible = false">取消</el-button>
        <el-button type="primary" :loading="extendSubmitting" @click="submitExtend">确认续期</el-button>
      </template>
    </el-dialog>

    <!-- 拆分核销弹窗 -->
    <el-dialog v-model="splitVisible" title="拆分核销（多员工次）" width="640px">
      <el-form :model="splitForm" label-width="100px">
        <el-form-item label="套餐">
          <el-input :value="splitForm._pkgLabel" disabled />
        </el-form-item>
        <el-form-item label="剩余次数">
          <el-tag>{{ splitForm._remaining }} 次</el-tag>
        </el-form-item>
        <el-divider>拆分子项</el-divider>
        <div v-for="(it, idx) in splitForm.items" :key="idx" class="split-row">
          <el-input v-model="it.serviceItem" placeholder="服务项目" style="width:160px" />
          <el-input-number v-model="it.times" :min="1" :max="splitForm._remaining" style="width:130px" />
          <el-select v-model="it.beauticianId" placeholder="美容师" clearable style="width:160px">
            <el-option v-for="b in beauticianOpts" :key="b.id" :label="b.employeeName" :value="b.id" />
          </el-select>
          <el-input v-model="it.note" placeholder="备注" style="width:160px" />
          <el-button text type="danger" :icon="Close" @click="splitForm.items.splice(idx, 1)" />
        </div>
        <el-button type="primary" plain :icon="Plus" @click="addSplitRow" style="margin-top:8px">添加子项</el-button>
        <el-form-item label="合计次数" style="margin-top:14px">
          <el-tag type="warning">{{ splitTotalTimes }} 次</el-tag>
          <span style="margin-left:8px;color:#909399;font-size:12px">将一次性扣减订单剩余次数</span>
        </el-form-item>
        <el-form-item label="整体备注">
          <el-input v-model="splitForm.note" type="textarea" :rows="2" placeholder="选填，会写入每条核销记录的备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="splitVisible = false">取消</el-button>
        <el-button type="primary" :loading="splitSubmitting" @click="submitSplit">确认拆分核销</el-button>
      </template>
    </el-dialog>

    <!-- 转赠弹窗 -->
    <el-dialog v-model="transferVisible" title="套餐转赠" width="480px">
      <el-alert type="warning" :closable="false" style="margin-bottom:14px">
        转赠后原订单将标记为「已转赠」并清零剩余次数，目标会员获得一张全新的子订单
      </el-alert>
      <el-form :model="transferForm" label-width="100px">
        <el-form-item label="套餐">
          <el-input :value="transferForm._pkgLabel" disabled />
        </el-form-item>
        <el-form-item label="转出会员">
          <el-input :value="transferForm._fromName" disabled />
        </el-form-item>
        <el-form-item label="剩余次数">
          <el-tag>{{ transferForm._remaining }} 次</el-tag>
        </el-form-item>
        <el-form-item label="转入会员" required>
          <el-select v-model="transferForm.toMemberId" filterable placeholder="搜索姓名/手机号" style="width:100%">
            <el-option v-for="m in transferCandidates" :key="m.id" :label="`${m.name}（${m.phone || '-'}）`" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="transferForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="primary" :loading="transferSubmitting" @click="submitTransfer">确认转赠</el-button>
      </template>
    </el-dialog>

    <!-- 退款弹窗 -->
    <el-dialog v-model="refundVisible" title="套餐退款（退至钱包）" width="460px">
      <el-form :model="refundForm" label-width="100px">
        <el-form-item label="套餐">
          <el-input :value="refundForm._pkgLabel" disabled />
        </el-form-item>
        <el-form-item label="退款方式">
          <el-radio-group v-model="refundForm.mode">
            <el-radio value="auto">按剩余折算（推荐）</el-radio>
            <el-radio value="custom">自定义金额</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="原价">
          <el-input :value="'¥' + formatMoney(refundForm._price)" disabled />
        </el-form-item>
        <el-form-item label="剩余次数">
          <el-tag>{{ refundForm._remaining }} / {{ refundForm._total }} 次</el-tag>
        </el-form-item>
        <el-form-item label="预计退款" v-if="refundForm.mode === 'auto'">
          <el-input :value="'¥' + formatMoney(refundForm._expected)" disabled />
          <span class="amount-tip">公式：原价 × (剩余 / 总额)</span>
        </el-form-item>
        <el-form-item label="退款金额" v-else>
          <el-input-number v-model="refundForm.amount" :min="0.01" :precision="2" :step="100" :max="Number(refundForm._price)" style="width:200px" />
          <span class="amount-tip">元</span>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="refundForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="refundVisible = false">取消</el-button>
        <el-button type="danger" :loading="refundSubmitting" @click="submitRefund">确认退款</el-button>
      </template>
    </el-dialog>

    <!-- M04-07 差价补款 / 套餐升级弹窗 -->
    <el-dialog v-model="upgradeVisible" title="套餐升级（差价补款）" width="560px">
      <el-alert type="info" :closable="false" style="margin-bottom:14px">
        系统将把旧套餐剩余价值折算后，抵扣新套餐价格，多退少补
      </el-alert>
      <el-form :model="upgradeForm" label-width="110px">
        <el-form-item label="当前套餐">
          <el-input :value="upgradeForm._fromLabel" disabled />
        </el-form-item>
        <el-form-item label="剩余价值">
          <el-tag type="success">¥{{ formatMoney(upgradeForm._creditValue) }}</el-tag>
          <span class="amount-tip">= 原价 ¥{{ formatMoney(upgradeForm._price) }} × (剩余 {{ upgradeForm._remaining }} / 总共 {{ upgradeForm._total }})</span>
        </el-form-item>
        <el-divider>选择目标套餐</el-divider>
        <el-form-item label="目标套餐" required>
          <el-select v-model="upgradeForm.toPackageId" placeholder="请选择目标套餐"
            filterable style="width:100%" @change="onToPackageChange">
            <el-option v-for="p in upgradeTargetPackages" :key="p.id"
              :value="p.id"
              :label="`${p.name} | ¥${formatMoney(p.price)} | ${p.totalTimes}次`" />
          </el-select>
        </el-form-item>
        <el-form-item label="目标价格">
          <el-tag type="info">¥{{ formatMoney(upgradeForm._newPrice) }}</el-tag>
        </el-form-item>
        <el-form-item label="差价">
          <el-tag :type="upgradeForm._diff > 0 ? 'warning' : upgradeForm._diff < 0 ? 'success' : 'info'">
            {{ upgradeForm._diff > 0 ? '需补差价 ¥' + formatMoney(upgradeForm._diff) : upgradeForm._diff < 0 ? '可退 ¥' + formatMoney(Math.abs(upgradeForm._diff)) : '平价，无需补退' }}
          </el-tag>
        </el-form-item>
        <el-form-item label="补缴方式" v-if="upgradeForm._diff > 0">
          <el-radio-group v-model="upgradeForm.payMethod">
            <el-radio value="wallet">储值余额</el-radio>
            <el-radio value="wechat">微信支付</el-radio>
            <el-radio value="alipay">支付宝</el-radio>
            <el-radio value="cash">现金</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="upgradeForm.remark" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="upgradeVisible = false">取消</el-button>
        <el-button type="primary" :loading="upgradeSubmitting" @click="submitUpgrade">
          {{ upgradeForm._diff > 0 ? '确认补差价' : upgradeForm._diff < 0 ? '确认降级退款' : '确认换购' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 挂起弹窗 -->
    <el-dialog v-model="suspendVisible" title="套餐挂起" width="500px">
      <el-alert type="info" :closable="false" style="margin-bottom:14px">
        挂起期间该套餐无法核销/转让/退款，但可随时恢复使用。
        不填写到期时间则永久挂起（需人工恢复）。
      </el-alert>
      <el-form :model="suspendForm" label-width="110px">
        <el-form-item label="套餐">
          <el-input :value="suspendForm._pkgLabel" disabled />
        </el-form-item>
        <el-form-item label="挂起原因" required>
          <el-input v-model="suspendForm.reason" placeholder="如：会员出差三个月 / 系统临时冻结" />
        </el-form-item>
        <el-form-item label="到期自动恢复">
          <el-date-picker
            v-model="suspendForm.suspendUntil"
            type="datetime"
            placeholder="留空则永久挂起"
            value-format="YYYY-MM-DD HH:mm:ss"
            format="YYYY-MM-DD HH:mm"
            style="width:100%"
            clearable />
          <div style="color:#909399;font-size:12px;margin-top:4px">
            到期后请手动点击「恢复」按钮重新启用套餐
          </div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="suspendForm.remark" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="suspendVisible = false">取消</el-button>
        <el-button type="warning" :loading="suspendSubmitting" @click="submitSuspend">确认挂起</el-button>
      </template>
    </el-dialog>

    <!-- 恢复弹窗 -->
    <el-dialog v-model="resumeVisible" title="恢复套餐" width="460px">
      <el-alert type="success" :closable="false" style="margin-bottom:14px">
        确认恢复后，套餐将回到「正常」状态，可正常核销/转让/退款。
      </el-alert>
      <el-form :model="resumeForm" label-width="100px">
        <el-form-item label="套餐">
          <el-input :value="resumeForm._pkgLabel" disabled />
        </el-form-item>
        <el-form-item label="恢复原因">
          <el-input v-model="resumeForm.reason" placeholder="如：会员已回归" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resumeVisible = false">取消</el-button>
        <el-button type="success" :loading="resumeSubmitting" @click="submitResume">确认恢复</el-button>
      </template>
    </el-dialog>

    <!-- 会员购买套餐弹窗 -->
    <purchase-dialog
      v-if="memberId"
      v-model="purchaseVisible"
      :member-id="memberId"
      :lock-member="true"
      @success="onPurchaseSuccess"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { UserFilled, User, Back, Plus, CreditCard, ShoppingCart, Clock, Close, Tickets, Document, DocumentRemove } from '@element-plus/icons-vue'
import { Lock as LockIcon, Unlock as UnlockIcon, Switch as SwitchIcon, Promotion as PromotionIcon, Money as MoneyIcon, ArrowUp as ArrowUpIcon, VideoPause as PauseIcon, VideoPlay as PlayIcon } from '@element-plus/icons-vue'
import { packageApi } from '@/api/package'
import { memberApi, employeeApi, walletApi } from '@/api/basic'
import PurchaseDialog from './purchase-dialog.vue'

const router = useRouter()
const memberId = ref<number | null>(null)
const activeTab = ref('orders')
const ordersLoading = ref(false)
const redeemLoading = ref(false)
const redeemSubmitting = ref(false)
const redeemVisible = ref(false)
const purchaseVisible = ref(false)
const extendVisible = ref(false)
const splitVisible = ref(false)
const transferVisible = ref(false)
const refundVisible = ref(false)
const extendSubmitting = ref(false)
const splitSubmitting = ref(false)
const transferSubmitting = ref(false)
const refundSubmitting = ref(false)
const transferCandidates = ref<any[]>([])
const employeeLoading = ref(false)
const searchLoading = ref(false)
const memberInfo = ref<any | null>(null)
const memberOpts = ref<any[]>([])
const orders = ref<any[]>([])
const redeems = ref<any[]>([])
const beauticianOpts = ref<any[]>([])
const sysStats = ref<any>({})
const recentMembers = ref<any[]>([])
const recentOrders = ref<any[]>([])
const recentRedeems = ref<any[]>([])
const recentMembersLoading = ref(false)
const recentOrdersLoading = ref(false)
const recentRedeemLoading = ref(false)

const redeemForm = reactive({
  orderId: null as number | null,
  times: 1,
  serviceItem: '',
  beauticianId: null as number | null,
  operatorId: 1,
  note: ''
})

const extendForm = reactive({
  orderId: null as number | null,
  days: 30,
  _pkgLabel: '',
  _oldExpire: '',
  _newExpire: ''
})

const splitForm = reactive({
  orderId: null as number | null,
  _pkgLabel: '',
  _remaining: 0,
  note: '',
  items: [] as Array<{ times: number; serviceItem: string; beauticianId: number | null; note: string }>
})

const transferForm = reactive({
  orderId: null as number | null,
  toMemberId: null as number | null,
  operatorId: 1,
  remark: '',
  _pkgLabel: '',
  _fromName: '',
  _remaining: 0
})

const refundForm = reactive({
  orderId: null as number | null,
  mode: 'auto' as 'auto' | 'custom',
  amount: 0 as number,
  remark: '',
  _pkgLabel: '',
  _price: 0,
  _remaining: 0,
  _total: 1,
  _expected: 0
})

const splitTotalTimes = computed(() =>
  splitForm.items.reduce((s, i) => s + Number(i.times || 0), 0)
)

// ============== M04-07：差价补款 / 套餐升级 ==============
const upgradeVisible = ref(false)
const upgradeSubmitting = ref(false)
const upgradeTargetPackages = ref<any[]>([])

const upgradeForm = reactive({
  fromOrderId: null as number | null,
  toPackageId: null as number | null,
  payMethod: 'wallet',
  remark: '',
  _fromLabel: '',
  _price: 0,
  _remaining: 0,
  _total: 1,
  _creditValue: 0,
  _newPrice: 0,
  _diff: 0
})

const openUpgrade = async (row: any) => {
  upgradeForm.fromOrderId = row.id
  upgradeForm.toPackageId = null
  upgradeForm.payMethod = 'wallet'
  upgradeForm.remark = ''
  upgradeForm._fromLabel = `${row.packageName}（订单号 ${row.orderNo}）`
  upgradeForm._price = Number(row.price || 0)
  upgradeForm._remaining = row.remainingTimes
  upgradeForm._total = row.totalTimes
  // 剩余价值 = 原价 × (剩余 / 总次数)
  upgradeForm._creditValue = Math.round(
    upgradeForm._price * upgradeForm._remaining / upgradeForm._total * 100
  ) / 100
  upgradeForm._newPrice = 0
  upgradeForm._diff = 0
  upgradeVisible.value = true
  // 加载所有上架套餐（排除当前套餐）
  try {
    const res: any = await packageApi.page({ pageNum: 1, pageSize: 200, status: 1 })
    upgradeTargetPackages.value = (res.data?.records || [])
      .filter((p: any) => p.id !== row.packageId)
  } catch {}
}

const onToPackageChange = (packageId: number | null) => {
  if (!packageId) return
  const pkg = upgradeTargetPackages.value.find((p: any) => p.id === packageId)
  if (!pkg) return
  upgradeForm._newPrice = Number(pkg.price || 0)
  upgradeForm._diff = Math.round((upgradeForm._newPrice - upgradeForm._creditValue) * 100) / 100
}

const submitUpgrade = async () => {
  if (!upgradeForm.fromOrderId) return
  if (!upgradeForm.toPackageId) { ElMessage.warning('请选择目标套餐'); return }
  await ElMessageBox.confirm(
    upgradeForm._diff > 0
      ? `确认补差价 ¥${upgradeForm._diff} 升级套餐？`
      : upgradeForm._diff < 0
        ? `确认降级并退款 ¥${Math.abs(upgradeForm._diff)} 到钱包？`
        : '确认平价换购套餐？',
    '升级确认', { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' }
  ).catch(() => null)
  if (!upgradeForm.fromOrderId) return
  upgradeSubmitting.value = true
  try {
    const res: any = await packageApi.upgrade({
      fromOrderId: upgradeForm.fromOrderId,
      toPackageId: upgradeForm.toPackageId,
      payMethod: upgradeForm.payMethod,
      operatorId: 1,
      remark: upgradeForm.remark,
      estimatedDiff: upgradeForm._diff
    })
    if (res.code === 200) {
      ElMessage.success(res.data?.message || '升级成功')
      upgradeVisible.value = false
      loadOrders()
    } else {
      ElMessage.error(res.message || '升级失败')
    }
  } finally {
    upgradeSubmitting.value = false
  }
}

const formatMoney = (v: any) => Number(v || 0).toFixed(2)
const formatDate = (v: any) => v ? new Date(v).toLocaleString('zh-CN') : '-'

const statusTagType = (s: number) =>
  s === 1 ? 'success' : s === 2 ? 'info' : s === 3 ? 'warning' :
  s === 4 ? 'danger' : s === 5 ? 'info' : s === 6 ? 'primary' :
  s === 7 ? 'warning' : s === 8 ? 'warning' : ''

// 7 天内到期判定（含已过期）
const isExpireSoon = (expireTime: any) => {
  if (!expireTime) return false
  const t = new Date(expireTime).getTime()
  const now = Date.now()
  return t >= now && (t - now) <= 7 * 24 * 60 * 60 * 1000
}

const isExpired = (expireTime: any) => {
  if (!expireTime) return false
  return new Date(expireTime).getTime() < Date.now()
}

const normalOrders = computed(() =>
  orders.value.filter((o: any) => o.status === 1 && o.remainingTimes > 0)
)

const searchMembers = async (kw: string) => {
  searchLoading.value = true
  try {
    const res: any = await memberApi.page({ pageNum: 1, pageSize: 30, keyword: kw || '', status: 1 })
    memberOpts.value = res.data?.records || []
  } finally {
    searchLoading.value = false
  }
}

const loadOrders = async () => {
  if (!memberId.value) { orders.value = []; return }
  ordersLoading.value = true
  try {
    const res: any = await packageApi.memberPackages(memberId.value, { pageNum: 1, pageSize: 50 })
    orders.value = res.data?.records || []
  } finally {
    ordersLoading.value = false
  }
}

const loadMemberInfo = async () => {
  if (!memberId.value) { memberInfo.value = null; return }
  try {
    const res: any = await memberApi.getById(memberId.value)
    if (res.code === 200 && res.data) {
      memberInfo.value = res.data
      // 余额单独拉（部分后端不返回在 member 对象里）
      try {
        const wb: any = await walletApi.getBalance(memberId.value)
        if (wb.code === 200) memberInfo.value.balance = wb.data
      } catch {}
    } else {
      memberInfo.value = null
    }
  } catch {
    memberInfo.value = null
  }
}

const loadRedeems = async () => {
  if (!memberId.value) { redeems.value = []; return }
  redeemLoading.value = true
  try {
    const res: any = await packageApi.memberRedemptions(memberId.value, { pageNum: 1, pageSize: 50 })
    redeems.value = res.data?.records || []
  } finally {
    redeemLoading.value = false
  }
}

const loadBeauticians = async () => {
  employeeLoading.value = true
  try {
    const res: any = await employeeApi.list()
    const list = (res.data || []).filter((e: any) => e.status === 1)
    beauticianOpts.value = list.length ? list : (res.data || [])
    // 默认选中第一个
    if (!redeemForm.beauticianId && beauticianOpts.value.length) {
      redeemForm.beauticianId = beauticianOpts.value[0].id
    }
  } finally {
    employeeLoading.value = false
  }
}

const openRedeem = (row?: any) => {
  if (row) {
    redeemForm.orderId = row.id
  } else {
    redeemForm.orderId = null
  }
  redeemForm.times = 1
  redeemForm.serviceItem = ''
  redeemForm.note = ''
  redeemVisible.value = true
  loadBeauticians()
}

// 行 class：即将到期 / 已过期 高亮
const rowClassName = ({ row }: any) => {
  if (isExpired(row.expireTime) && row.status === 1) return 'row-expired'
  if (isExpireSoon(row.expireTime)) return 'row-expire-soon'
  return ''
}

const openExtend = (row: any) => {
  extendForm.orderId = row.id
  extendForm.days = 30
  extendForm._pkgLabel = `${row.packageName} | 剩余 ${row.remainingTimes}/${row.totalTimes} 次`
  extendForm._oldExpire = formatDate(row.expireTime)
  extendForm._newExpire = ''
  extendVisible.value = true
}

// 监听弹窗打开 + days 变动，计算预估到期
watch([extendVisible, () => extendForm.days], () => {
  if (!extendVisible.value) return
  const oldStr = extendForm._oldExpire
  const base = oldStr && oldStr !== '-' ? new Date(oldStr) : new Date()
  extendForm._newExpire = new Date(base.getTime() + Number(extendForm.days || 0) * 86400000)
    .toLocaleString('zh-CN')
})

const submitExtend = async () => {
  if (!extendForm.orderId) { ElMessage.warning('请选择要续期的套餐'); return }
  await ElMessageBox.confirm(
    `确定要将该套餐顺延 ${extendForm.days} 天吗？`, '续期确认',
    { type: 'warning', confirmButtonText: '确认续期', cancelButtonText: '取消' }
  ).catch(() => null)
  if (!extendForm.orderId) return
  extendSubmitting.value = true
  try {
    const res: any = await packageApi.extend(extendForm.orderId, extendForm.days)
    if (res.code === 200) {
      ElMessage.success(res.message || '续期成功')
      extendVisible.value = false
      loadOrders()
    } else {
      ElMessage.error(res.message || '续期失败')
    }
  } finally {
    extendSubmitting.value = false
  }
}

// ============== M05-二期：拆分核销 ==============
const addSplitRow = () => {
  splitForm.items.push({ times: 1, serviceItem: '', beauticianId: null, note: '' })
}
const openSplit = async (row: any) => {
  splitForm.orderId = row.id
  splitForm._pkgLabel = `${row.packageName}（订单号 ${row.orderNo}）`
  splitForm._remaining = row.remainingTimes
  splitForm.note = ''
  splitForm.items = [{ times: 1, serviceItem: '', beauticianId: null, note: '' }]
  splitVisible.value = true
  if (beauticianOpts.value.length === 0) await loadBeauticians()
}
const submitSplit = async () => {
  if (!splitForm.orderId) { ElMessage.warning('请选择套餐'); return }
  if (splitForm.items.length === 0) { ElMessage.warning('请添加至少一个拆分子项'); return }
  if (splitTotalTimes.value > splitForm._remaining) {
    ElMessage.warning(`拆分总次数 ${splitTotalTimes.value} 超过剩余 ${splitForm._remaining}`); return
  }
  await ElMessageBox.confirm(
    `确认要拆分核销 ${splitForm.items.length} 项，共 ${splitTotalTimes.value} 次吗？`,
    '拆分核销确认', { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' }
  ).catch(() => null)
  if (!splitForm.orderId) return
  splitSubmitting.value = true
  try {
    const res: any = await packageApi.redeemSplit({
      orderId: splitForm.orderId,
      operatorId: 1,
      note: splitForm.note,
      items: splitForm.items
    })
    if (res.code === 200) {
      ElMessage.success(res.message || '拆分核销成功')
      splitVisible.value = false
      loadOrders()
      if (activeTab.value === 'redeems') loadRedeems()
    } else {
      ElMessage.error(res.message || '拆分核销失败')
    }
  } finally {
    splitSubmitting.value = false
  }
}

// ============== M05-二期：转赠 ==============
const loadTransferCandidates = async () => {
  try {
    const res: any = await memberApi.page({ pageNum: 1, pageSize: 200, status: 1 })
    if (res.code === 200) {
      transferCandidates.value = (res.data?.records || [])
        .filter((m: any) => m.id !== memberId.value)
    }
  } catch {}
}
const openTransfer = async (row: any) => {
  transferForm.orderId = row.id
  transferForm.toMemberId = null
  transferForm.remark = ''
  transferForm._pkgLabel = `${row.packageName} | 剩余 ${row.remainingTimes}/${row.totalTimes} 次`
  transferForm._fromName = memberInfo.value?.name || `会员#${memberId.value}`
  transferForm._remaining = row.remainingTimes
  transferVisible.value = true
  await loadTransferCandidates()
}
const submitTransfer = async () => {
  if (!transferForm.orderId) return
  if (!transferForm.toMemberId) { ElMessage.warning('请选择目标会员'); return }
  await ElMessageBox.confirm(
    `确认将「${transferForm._pkgLabel}」转赠给所选会员？此操作不可撤销。`,
    '转赠确认', { type: 'warning', confirmButtonText: '确认转赠', cancelButtonText: '取消' }
  ).catch(() => null)
  if (!transferForm.orderId) return
  transferSubmitting.value = true
  try {
    const res: any = await packageApi.transfer(transferForm.orderId, {
      toMemberId: transferForm.toMemberId,
      operatorId: 1,
      remark: transferForm.remark
    })
    if (res.code === 200) {
      ElMessage.success(res.message || '转赠成功')
      transferVisible.value = false
      loadOrders()
    } else {
      ElMessage.error(res.message || '转赠失败')
    }
  } finally {
    transferSubmitting.value = false
  }
}

// ============== M05-二期：退款 ==============
const calcExpected = () => {
  if (refundForm._total <= 0) return 0
  const r = Number(refundForm._price) * refundForm._remaining / refundForm._total
  return Math.round(r * 100) / 100
}
const openRefund = (row: any) => {
  refundForm.orderId = row.id
  refundForm.mode = 'auto'
  refundForm.amount = 0
  refundForm.remark = ''
  refundForm._pkgLabel = `${row.packageName}（订单号 ${row.orderNo}）`
  refundForm._price = Number(row.price || 0)
  refundForm._remaining = row.remainingTimes
  refundForm._total = row.totalTimes
  refundForm._expected = calcExpected()
  refundVisible.value = true
}
watch(() => [refundForm.mode, refundForm._price, refundForm._remaining, refundForm._total], () => {
  refundForm._expected = calcExpected()
})
const submitRefund = async () => {
  if (!refundForm.orderId) return
  const finalAmount = refundForm.mode === 'auto'
    ? refundForm._expected
    : Number(refundForm.amount || 0)
  if (finalAmount <= 0) { ElMessage.warning('退款金额必须大于 0'); return }
  await ElMessageBox.confirm(
    `确认退款 ¥${finalAmount.toFixed(2)} 到该会员钱包？此操作不可撤销。`,
    '退款确认', { type: 'warning', confirmButtonText: '确认退款', cancelButtonText: '取消' }
  ).catch(() => null)
  if (!refundForm.orderId) return
  refundSubmitting.value = true
  try {
    const params: any = { operatorId: 1 }
    if (refundForm.mode === 'custom') params.amount = finalAmount
    const res: any = await packageApi.refund(refundForm.orderId, params)
    if (res.code === 200) {
      ElMessage.success(res.message || '退款成功')
      refundVisible.value = false
      loadOrders()
      loadMemberInfo()
    } else {
      ElMessage.error(res.message || '退款失败')
    }
  } finally {
    refundSubmitting.value = false
  }
}

const submitRedeem = async () => {
  if (!redeemForm.orderId) { ElMessage.warning('请选择要核销的套餐'); return }
  redeemSubmitting.value = true
  try {
    const res: any = await packageApi.redeem({
      orderId: redeemForm.orderId,
      times: redeemForm.times,
      serviceItem: redeemForm.serviceItem,
      beauticianId: redeemForm.beauticianId,
      operatorId: redeemForm.operatorId,
      note: redeemForm.note
    })
    if (res.code === 200) {
      ElMessage.success(`核销成功，剩余 ${res.data.remainingTimes} 次`)
      redeemVisible.value = false
      loadOrders()
      loadRedeems()
    }
  } finally {
    redeemSubmitting.value = false
  }
}

const toggleFreeze = async (row: any) => {
  const freeze = row.status !== 4
  if (freeze && !confirm('确认冻结该套餐？冻结后将无法核销。')) return
  if (!freeze && !confirm('确认解冻该套餐？')) return
  const res: any = await packageApi.freezeWithReason(row.id, { freeze, reason: '' })
  if (res.code === 200) {
    ElMessage.success(res.message)
    loadOrders()
  }
}

// ==================== M04-04 挂起 / 恢复 ====================
const suspendVisible = ref(false)
const suspendSubmitting = ref(false)
const suspendForm = reactive({
  orderId: null as number | null,
  reason: '',
  suspendUntil: '' as string | '',
  _pkgLabel: '',
  remark: ''
})

const resumeVisible = ref(false)
const resumeSubmitting = ref(false)
const resumeForm = reactive({
  orderId: null as number | null,
  reason: '',
  _pkgLabel: ''
})

const openSuspend = (row: any) => {
  suspendForm.orderId = row.id
  suspendForm.reason = ''
  suspendForm.suspendUntil = ''
  suspendForm._pkgLabel = `${row.packageName}（${row.orderNo}）剩余 ${row.remainingTimes} 次）`
  suspendForm.remark = ''
  suspendVisible.value = true
}

const submitSuspend = async () => {
  if (!suspendForm.orderId) return
  if (!suspendForm.reason || !suspendForm.reason.trim()) {
    ElMessage.warning('请填写挂起原因')
    return
  }
  await ElMessageBox.confirm(
    suspendForm.suspendUntil
      ? `确认挂起该套餐，到期时间：${suspendForm.suspendUntil}？`
      : '确认挂起该套餐（永久挂起，需人工恢复）？',
    '挂起确认', { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' }
  ).catch(() => null)
  if (!suspendForm.orderId) return
  suspendSubmitting.value = true
  try {
    const res: any = await packageApi.suspend(suspendForm.orderId, {
      action: 'SUSPEND',
      reason: suspendForm.reason.trim(),
      suspendUntil: suspendForm.suspendUntil || undefined,
      operatorId: 1
    })
    if (res.code === 200) {
      ElMessage.success(res.message || '已挂起')
      suspendVisible.value = false
      loadOrders()
    } else {
      ElMessage.error(res.message || '挂起失败')
    }
  } finally {
    suspendSubmitting.value = false
  }
}

const openResume = (row: any) => {
  resumeForm.orderId = row.id
  resumeForm.reason = ''
  resumeForm._pkgLabel = `${row.packageName}（${row.orderNo}）剩余 ${row.remainingTimes} 次）`
  resumeVisible.value = true
}

const submitResume = async () => {
  if (!resumeForm.orderId) return
  await ElMessageBox.confirm('确认恢复该套餐？恢复后套餐将回到「正常」状态。',
    '恢复确认', { type: 'success', confirmButtonText: '确认', cancelButtonText: '取消' }
  ).catch(() => null)
  if (!resumeForm.orderId) return
  resumeSubmitting.value = true
  try {
    const res: any = await packageApi.resume(resumeForm.orderId, {
      action: 'RESUME',
      reason: resumeForm.reason || '人工恢复挂起',
      operatorId: 1
    })
    if (res.code === 200) {
      ElMessage.success(res.message || '已恢复')
      resumeVisible.value = false
      loadOrders()
    } else {
      ElMessage.error(res.message || '恢复失败')
    }
  } finally {
    resumeSubmitting.value = false
  }
}

const onPurchaseSuccess = () => {
  loadOrders()
  if (activeTab.value === 'redeems') loadRedeems()
}

const goList = () => router.push('/package')

watch(memberId, v => {
  if (v) {
    loadMemberInfo()
  } else {
    memberInfo.value = null
  }
})

watch(activeTab, (v) => {
  if (v === 'redeems') loadRedeems()
})

watch(memberId, () => {
  loadOrders()
  if (activeTab.value === 'redeems') loadRedeems()
})

const loadSysStats = async () => {
  try {
    const res: any = await packageApi.stats()
    if (res.code === 200) sysStats.value = res.data || {}
  } catch {}
}

const loadRecentMembers = async () => {
  recentMembersLoading.value = true
  try {
    const res: any = await packageApi.recentMembersWithOrders(8)
    if (res.code === 200) recentMembers.value = res.data || []
  } finally { recentMembersLoading.value = false }
}

const loadRecentOrders = async () => {
  recentOrdersLoading.value = true
  try {
    const res: any = await packageApi.recentOrders({ pageNum: 1, pageSize: 8 })
    if (res.code === 200) recentOrders.value = res.data?.records || []
  } finally { recentOrdersLoading.value = false }
}

const loadRecentRedemptions = async () => {
  recentRedeemLoading.value = true
  try {
    const res: any = await packageApi.recentRedemptions({ pageNum: 1, pageSize: 8 })
    if (res.code === 200) recentRedeems.value = res.data?.records || []
  } finally { recentRedeemLoading.value = false }
}

const quickPick = async (m: any) => {
  // 选这个会员：把 memberInfo/memberOpts 都填好，再触发原有逻辑
  if (!memberOpts.value.find((x: any) => x.id === m.id)) {
    memberOpts.value.unshift(m)
  }
  memberId.value = m.id
  await loadMemberInfo()
  await loadOrders()
  if (activeTab.value === 'redeems') await loadRedeems()
}

onMounted(() => {
  searchMembers('')
  loadSysStats()
  loadRecentMembers()
  loadRecentOrders()
  loadRecentRedemptions()
})
</script>

<style scoped>
.mp-page { padding: 0; }
.banner {
  background: linear-gradient(135deg, #f59e0b, #ef4444);
  color: #fff; border-radius: 16px; padding: 24px 32px; margin-bottom: 20px;
  display: flex; justify-content: space-between; align-items: center;
}
.banner-left { display: flex; gap: 16px; align-items: center; }
.banner h2 { margin: 0; font-size: 22px; }
.banner p { margin: 4px 0 0; font-size: 13px; opacity: 0.9; }
.filter-bar {
  display: flex; gap: 12px; align-items: center; margin-bottom: 16px;
}
.empty { padding: 30px 0; text-align: center; color: #c0c4cc; font-size: 13px; }

/* 会员信息卡 */
.member-card {
  display: flex; align-items: center; gap: 16px;
  margin-bottom: 16px;
  background: linear-gradient(135deg, #fff, #fff8f0);
  border: 1px solid #ffe0b2;
}
.member-meta { flex: 1; }
.member-meta .row1 {
  display: flex; align-items: center; gap: 12px; margin-bottom: 6px;
}
.member-meta .name { font-size: 20px; font-weight: 700; color: #303133; }
.member-meta .row2 {
  display: flex; flex-wrap: wrap; gap: 18px; color: #606266; font-size: 13px;
}
.member-meta .row2 .balance { color: #f59e0b; }

/* 即将到期 / 已过期 行高亮 */
:deep(.el-table .row-expire-soon) { background-color: #fff7e6 !important; }
:deep(.el-table .row-expire-soon td) { background-color: inherit !important; }
:deep(.el-table .row-expired)     { background-color: #fef0f0 !important; }
:deep(.el-table .row-expired td)  { background-color: inherit !important; }
.expire-soon { color: #d97706; font-weight: 600; }

.amount-tip { margin-left: 8px; color: #909399; font-size: 13px; }

.split-row {
  display: flex; gap: 8px; align-items: center; margin-bottom: 8px;
  padding: 8px; background: #fafafa; border-radius: 6px;
}

/* 套餐列表头汇总卡 */
.stats-row .el-card { background: #fffbeb; border: 1px solid #fde68a; }

/* 顶部 KPI */
.kpi-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-bottom: 16px; }
.kpi-card {
  padding: 16px 20px;
  border-radius: 8px;
  color: #fff;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
  position: relative;
  overflow: hidden;
}
.kpi-card::after {
  content: '';
  position: absolute;
  right: -20px; top: -20px;
  width: 80px; height: 80px;
  border-radius: 50%;
  background: rgba(255,255,255,0.12);
}
.kpi-label { font-size: 13px; opacity: 0.9; }
.kpi-value { font-size: 28px; font-weight: 600; margin: 6px 0 4px; letter-spacing: 0.5px; }
.kpi-sub { font-size: 12px; opacity: 0.85; }
.kpi-amber { background: linear-gradient(135deg, #f59e0b, #fbbf24); }
.kpi-blue { background: linear-gradient(135deg, #1890ff, #36cfc9); }
.kpi-purple { background: linear-gradient(135deg, #722ed1, #b37feb); }
.kpi-green { background: linear-gradient(135deg, #52c41a, #95de64); }

@media (max-width: 1100px) { .kpi-grid { grid-template-columns: repeat(2, 1fr); } }

.empty-hint {
  padding: 40px 20px 20px;
  display: flex; flex-direction: column; align-items: center; gap: 8px;
  color: #303133;
}
.empty-hint p { margin: 0; font-size: 14px; }
.empty-hint .muted { font-size: 12px; color: #909399; }

.empty-hint-inner {
  padding: 30px 20px;
  display: flex; flex-direction: column; align-items: center; gap: 6px;
  color: #909399;
}
.empty-hint-inner p { margin: 0; font-size: 13px; }

/* 侧栏：最近会员 / 全站最近订单 / 全站最近核销 */
.side-card { height: 100%; }
.side-header { display: flex; align-items: center; justify-content: space-between; font-size: 14px; font-weight: 600; }
.side-header .el-icon { margin-right: 4px; vertical-align: -2px; }

.recent-members { display: flex; flex-direction: column; gap: 6px; }
.member-pill {
  display: flex; align-items: center; gap: 10px;
  padding: 8px 10px; border-radius: 6px; cursor: pointer;
  transition: background 0.15s;
}
.member-pill:hover { background: #fff7e6; }
.member-pill .info { flex: 1; min-width: 0; }
.member-pill .info .name { font-size: 14px; font-weight: 600; color: #303133; }
.member-pill .info .name .phone { font-weight: 400; font-size: 12px; color: #909399; margin-left: 4px; }
.member-pill .info .meta { font-size: 12px; color: #909399; margin-top: 2px; }
</style>
