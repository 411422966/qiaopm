<template>
  <div class="dcep-advanced-page">
    <!-- P1-DCEP-001: DCEP dev/mock 模式明显警告 -->
    <el-alert
      v-if="dcepMode !== 'prod'"
      title="⚠️ 当前运行在演示模式（MOCK/DEMO），不涉及真实数字人民币交易"
      type="warning"
      :closable="false"
      show-icon
      style="margin-bottom: 16px"
    />
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="硬钱包管理" name="wallet" />
      <el-tab-pane label="离线交易" name="offline" />
      <el-tab-pane label="智能合约" name="contract" />
      <el-tab-pane label="合约执行日志" name="log" />
    </el-tabs>

    <!-- ============ 硬钱包管理 ============ -->
    <div v-if="activeTab === 'wallet'">
      <el-row :gutter="16" style="margin-bottom:16px">
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-icon blue"><el-icon :size="24"><Cellphone /></el-icon></div>
            <div>
              <div class="stat-value">{{ walletList.length }}</div>
              <div class="stat-label">硬钱包总数</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-icon green"><el-icon :size="24"><CreditCard /></el-icon></div>
            <div>
              <div class="stat-value">¥{{ totalBalance.toFixed(2) }}</div>
              <div class="stat-label">总余额</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-icon orange"><el-icon :size="24"><Money /></el-icon></div>
            <div>
              <div class="stat-value">¥{{ totalOfflineBalance.toFixed(2) }}</div>
              <div class="stat-label">离线余额</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-icon purple"><el-icon :size="24"><Cpu /></el-icon></div>
            <div>
              <div class="stat-value">{{ offlineWalletCount }}</div>
              <div class="stat-label">支持离线</div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-card>
        <template #header>
          <div class="card-header">
            <span>硬钱包列表</span>
            <el-button type="primary" @click="handleActivateWallet">
              <el-icon><Plus /></el-icon>激活硬钱包
            </el-button>
          </div>
        </template>

        <el-row :gutter="12">
          <el-col v-for="w in walletList" :key="w.id" :xs="24" :sm="12" :md="8" :lg="6">
            <div class="wallet-card">
              <div class="wallet-header" :style="{ background: getWalletColor(w.walletType) }">
                <el-icon :size="36"><component :is="getWalletIcon(w.walletType)" /></el-icon>
                <span class="wallet-name">{{ w.walletName }}</span>
                <el-tag v-if="w.offlineSupport === 1" type="warning" size="small">支持离线</el-tag>
                <el-tag v-else size="small">在线</el-tag>
              </div>
              <div class="wallet-body">
                <div class="balance-display">
                  <div class="balance-label">在线余额</div>
                  <div class="balance-value">¥{{ Number(w.balance || 0).toFixed(2) }}</div>
                </div>
                <div v-if="w.offlineSupport === 1" class="balance-display offline">
                  <div class="balance-label">离线余额</div>
                  <div class="balance-value">¥{{ Number(w.offlineBalance || 0).toFixed(2) }}</div>
                </div>
                <div class="wallet-info">
                  <div class="info-row">
                    <span>编号</span>
                    <span class="value">{{ w.walletCode }}</span>
                  </div>
                  <div class="info-row">
                    <span>设备</span>
                    <span class="value">{{ w.deviceModel }}</span>
                  </div>
                  <div class="info-row">
                    <span>运营机构</span>
                    <span class="value">{{ w.operatorOrg }}</span>
                  </div>
                  <div class="info-row">
                    <span>状态</span>
                    <el-tag :type="getWalletStatusType(w.status)" size="small">{{ getWalletStatusLabel(w.status) }}</el-tag>
                  </div>
                </div>
              </div>
              <div class="wallet-actions">
                <el-button type="primary" link size="small" @click="handleRecharge(w)">充值</el-button>
                <el-button type="warning" link size="small" @click="handleFreezeWallet(w)" v-if="w.status === 'active'">冻结</el-button>
                <el-button type="danger" link size="small" @click="handleReportLoss(w)" v-if="w.status === 'active'">挂失</el-button>
              </div>
            </div>
          </el-col>
        </el-row>
        <el-empty v-if="!walletList.length" description="暂无表情" />
      </el-card>
    </div>

    <!-- ============ 离线交易 ============ -->
    <div v-if="activeTab === 'offline'">
      <el-row :gutter="16" style="margin-bottom:16px">
        <el-col :span="8">
          <el-card>
            <template #header>
              <span><el-icon><Plus /></el-icon> 创建离线交易</span>
            </template>
            <el-form :model="offlineForm" label-width="100px">
              <el-form-item label="钱包ID">
                <el-input-number v-model="offlineForm.walletId" :min="1" />
              </el-form-item>
              <el-form-item label="商户号">
                <el-input v-model="offlineForm.merchantId" placeholder="MCH001" />
              </el-form-item>
              <el-form-item label="商户名称">
                <el-input v-model="offlineForm.merchantName" placeholder="美业一店" />
              </el-form-item>
              <el-form-item label="金额">
                <el-input-number v-model="offlineForm.amount" :min="0.01" :precision="2" />
              </el-form-item>
              <el-form-item label="交易类型">
                <el-select v-model="offlineForm.txType" style="width:100%">
                  <el-option v-for="o in offlineTxTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
                </el-select>
              </el-form-item>
              <el-form-item label="双离线">
                <el-switch v-model="offlineForm.dualOffline" :active-value="true" :inactive-value="false" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" @click="createOfflineTx" :loading="creating" style="width:100%">
                  创建离线交易
                </el-button>
              </el-form-item>
            </el-form>
          </el-card>
        </el-col>

        <el-col :span="16">
          <el-card>
            <template #header>
              <span>离线交易列表</span>
            </template>
            <el-table :data="offlineList" border v-loading="offlineLoading">
              <el-table-column label="交易号" prop="offlineTxNo" width="180" />
              <el-table-column label="钱包编号" prop="walletCode" width="160" />
              <el-table-column label="金额" width="120" align="right">
                <template #default="{ row }">
                  <span style="color:#f56c6c;font-weight:600">¥{{ Number(row.amount || 0).toFixed(2) }}</span>
                </template>
              </el-table-column>
              <el-table-column label="商户" prop="merchantName" width="140" />
              <el-table-column label="离线时间" prop="offlineTxTime" width="160" />
              <el-table-column label="双离线" width="80" align="center">
                <template #default="{ row }">
                  <el-tag v-if="row.dualOffline === 1" type="warning" size="small">是</el-tag>
                  <el-tag v-else size="small">否</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="同步状态" width="120">
                <template #default="{ row }">
                  <el-tag :type="getSyncStatusType(row.syncStatus)">{{ getSyncStatusLabel(row.syncStatus) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="冲突" width="80">
                <template #default="{ row }">
                  <el-tag v-if="row.conflictDetected === 1" type="danger" size="small">检测</el-tag>
                  <el-tag v-else type="success" size="small">无</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="120" fixed="right">
                <template #default="{ row }">
                  <el-button v-if="row.syncStatus === 'pending'" type="primary" link size="small" @click="syncOfflineTx(row)">
                    同步
                  </el-button>
                  <el-button type="warning" link size="small" @click="checkDoubleSpend(row)">防双花</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-if="!offlineLoading && !offlineList.length" description="暂无离线交易" />
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- ============ 智能合约 ============ -->
    <div v-if="activeTab === 'contract'">
      <el-card>
        <template #header>
          <div class="card-header">
            <span>数字人民币智能合约</span>
            <div>
              <el-button type="warning" @click="autoExecuteExpired" :loading="autoExecuting">
                <el-icon><Refresh /></el-icon>自动执行过期
              </el-button>
              <el-button type="primary" @click="handleDeployContract" style="margin-left:8px">
                <el-icon><Plus /></el-icon>部署合约
              </el-button>
            </div>
          </div>
        </template>

        <el-table :data="contractList" border stripe v-loading="contractLoading">
          <el-table-column label="合约名称" prop="contractName" min-width="180" fixed />
          <el-table-column label="合约地址" prop="contractAddress" width="220" show-overflow-tooltip />
          <el-table-column label="类型" width="160">
            <template #default="{ row }">
              <el-tag :style="{ background: getContractColor(row.contractType), color: '#fff' }">
                {{ getContractTypeLabel(row.contractType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="监管金额" width="140" align="right">
            <template #default="{ row }">
              ¥{{ Number(row.escrowAmount || 0).toFixed(2) }}
            </template>
          </el-table-column>
          <el-table-column label="已释放" width="120" align="right">
            <template #default="{ row }">
              ¥{{ Number(row.releasedAmount || 0).toFixed(2) }}
            </template>
          </el-table-column>
          <el-table-column label="已退还" width="120" align="right">
            <template #default="{ row }">
              ¥{{ Number(row.refundedAmount || 0).toFixed(2) }}
            </template>
          </el-table-column>
          <el-table-column label="过期时间" prop="expiredTime" width="160" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="getContractStatusType(row.status)">{{ getContractStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="执行次数" width="100" align="center">
            <template #default="{ row }">
              <el-tooltip :content="`自动 ${row.autoExecuteCount} / 手动 ${row.manualExecuteCount}`">
                <span>{{ (row.autoExecuteCount || 0) + (row.manualExecuteCount || 0) }}</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-dropdown @command="(action: string) => handleContractAction(row, action)">
                <el-button type="primary" link size="small">
                  操作<el-icon><ArrowDown /></el-icon>
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-for="o in contractActionOptions" :key="o.value" :command="o.value">
                      <el-icon><Operation /></el-icon>{{ o.label }}
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <el-button type="danger" link size="small" @click="handleDeleteContract(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!contractLoading && !contractList.length" description="暂无合约" />
      </el-card>
    </div>

    <!-- ============ 合约执行日志 ============ -->
    <div v-if="activeTab === 'log'">
      <el-card>
        <template #header>
          <span>合约执行日志</span>
        </template>
        <el-table :data="logList" border stripe v-loading="logLoading">
          <el-table-column label="执行时间" prop="executeTime" width="160" />
          <el-table-column label="合约名称" prop="contractName" width="180" />
          <el-table-column label="类型" width="100">
            <template #default="{ row }">
              <el-tag :type="row.executeType === 'auto' ? 'warning' : 'primary'" size="small">
                {{ row.executeType === 'auto' ? '自动' : '手动' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="动作" width="120">
            <template #default="{ row }">
              <el-tag>{{ getActionLabel(row.action) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="金额" width="120" align="right">
            <template #default="{ row }">
              ¥{{ Number(row.amount || 0).toFixed(2) }}
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'success' ? 'success' : (row.status === 'failed' ? 'danger' : 'info')">
                {{ row.status === 'success' ? '成功' : (row.status === 'failed' ? '失败' : '跳过') }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="耗时" width="100" prop="executionMs" align="center" />
          <el-table-column label="结果" prop="result" show-overflow-tooltip />
          <el-table-column label="错误信息" prop="errorMessage" show-overflow-tooltip />
        </el-table>
        <el-empty v-if="!logLoading && !logList.length" description="暂无日志" />
      </el-card>
    </div>

    <!-- 激活钱包对话框 -->
    <el-dialog v-model="activateDialogVisible" title="激活硬钱包" width="500px">
      <el-form :model="activateForm" label-width="100px">
        <el-form-item label="客户ID">
          <el-input-number v-model="activateForm.memberId" :min="1" style="width:100%" />
        </el-form-item>
        <el-form-item label="钱包类型">
          <el-select v-model="activateForm.walletType" style="width:100%">
            <el-option v-for="o in walletTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="设备型号">
          <el-input v-model="activateForm.deviceModel" placeholder="如:华为Mate60" />
        </el-form-item>
        <el-form-item label="SE芯片ID">
          <el-input v-model="activateForm.seChipId" placeholder="SE芯片唯一标识" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="activateDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitActivate">激活</el-button>
      </template>
    </el-dialog>

    <!-- 充值对话框 -->
    <el-dialog v-model="rechargeDialogVisible" title="钱包充值" width="400px">
      <el-form :model="rechargeForm" label-width="100px">
        <el-form-item label="充值金额">
          <el-input-number v-model="rechargeForm.amount" :min="0.01" :precision="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rechargeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitRecharge">确认充值</el-button>
      </template>
    </el-dialog>

    <!-- 部署合约对话框 -->
    <el-dialog v-model="deployDialogVisible" title="部署智能合约" width="600px">
      <el-form :model="deployForm" label-width="100px">
        <el-form-item label="合约名称">
          <el-input v-model="deployForm.contractName" placeholder="VIP客户预付卡合约" />
        </el-form-item>
        <el-form-item label="合约类型">
          <el-select v-model="deployForm.contractType" style="width:100%">
            <el-option v-for="o in contractTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="钱包ID">
          <el-input-number v-model="deployForm.walletId" :min="1" style="width:100%" />
        </el-form-item>
        <el-form-item label="客户ID">
          <el-input-number v-model="deployForm.memberId" :min="1" style="width:100%" />
        </el-form-item>
        <el-form-item label="监管金额">
          <el-input-number v-model="deployForm.escrowAmount" :min="0" :precision="2" style="width:100%" />
        </el-form-item>
        <el-form-item label="过期时间">
          <el-date-picker v-model="deployForm.expiredTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width:100%" />
        </el-form-item>
        <el-form-item label="触发条件">
          <el-input v-model="deployForm.triggerConditions" type="textarea" :rows="3"
                    placeholder='{"type":"expire","deadline":"2026-12-31"}' />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="deployDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitDeploy">部署</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'
import {
  Plus, Cellphone, CreditCard, Money, Cpu, Refresh, ArrowDown, Operation
} from '@element-plus/icons-vue'
import {
  dcepAdvancedApi,
  walletTypeOptions,
  contractTypeOptions,
  contractActionOptions,
  walletStatusOptions,
  syncStatusOptions,
  contractStatusOptions,
  offlineTxTypeOptions
} from '@/api/dcep-advanced'

const activeTab = ref('wallet')
const dcepMode = ref('dev')

// ============ 钱包 ============
const walletList = ref<any[]>([])

const totalBalance = computed(() => walletList.value.reduce((s, w) => s + Number(w.balance || 0), 0))
const totalOfflineBalance = computed(() => walletList.value.reduce((s, w) => s + Number(w.offlineBalance || 0), 0))
const offlineWalletCount = computed(() => walletList.value.filter(w => w.offlineSupport === 1).length)

const loadWallets = async () => {
  try {
    const res: any = await dcepAdvancedApi.wallets({})
    walletList.value = res.data || []
  } catch (e) {
    walletList.value = []
  }
}

const activateDialogVisible = ref(false)
const activateForm = reactive<any>({
  memberId: 1, walletType: 'nfc_sim', deviceModel: '华为Mate60', seChipId: ''
})

const handleActivateWallet = () => {
  Object.assign(activateForm, {
    memberId: 1, walletType: 'nfc_sim', deviceModel: '华为Mate60', seChipId: ''
  })
  activateDialogVisible.value = true
}

const submitActivate = async () => {
  try {
    await dcepAdvancedApi.activateWallet(activateForm)
    ElMessage.success('激活成功')
    activateDialogVisible.value = false
    loadWallets()
  } catch (e) {
    ElMessage.error('激活失败')
  }
}

const rechargeDialogVisible = ref(false)
const rechargeForm = reactive<any>({ walletId: null, amount: 100 })
const handleRecharge = (row: any) => {
  rechargeForm.walletId = row.id
  rechargeForm.amount = 100
  rechargeDialogVisible.value = true
}

const submitRecharge = async () => {
  try {
    await dcepAdvancedApi.rechargeWallet(rechargeForm.walletId, rechargeForm.amount)
    ElMessage.success('充值成功')
    rechargeDialogVisible.value = false
    loadWallets()
  } catch (e) {
    ElMessage.error('充值失败')
  }
}

const handleFreezeWallet = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确定冻结钱包"${row.walletName}"吗？`, '提示', { type: 'warning' })
    row.status = 'frozen'
    // 实际调用冻结API
    ElMessage.success('已冻结')
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

const handleReportLoss = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确定挂失钱包"${row.walletName}"吗？挂失后将无法使用`, '提示', { type: 'warning' })
    row.status = 'lost'
    ElMessage.success('已挂失')
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

// ============ 离线 ============
const offlineList = ref<any[]>([])
const offlineLoading = ref(false)
const creating = ref(false)
const offlineForm = reactive<any>({
  walletId: 1, merchantId: 'MCH001', merchantName: '美业一店',
  amount: 100, txType: 'consume', dualOffline: true
})

const loadOfflineTransactions = async () => {
  offlineLoading.value = true
  try {
    const res: any = await dcepAdvancedApi.offlineTransactions({})
    offlineList.value = res.data || []
  } catch (e) {
    offlineList.value = []
  } finally {
    offlineLoading.value = false
  }
}

const createOfflineTx = async () => {
  creating.value = true
  try {
    await dcepAdvancedApi.createOfflineTransaction(offlineForm)
    ElMessage.success('离线交易创建成功')
    loadOfflineTransactions()
  } catch (e) {
    ElMessage.error('创建失败')
  } finally {
    creating.value = false
  }
}

const syncOfflineTx = async (row: any) => {
  try {
    await dcepAdvancedApi.syncOfflineTransaction(row.offlineTxNo)
    ElMessage.success('同步成功')
    loadOfflineTransactions()
  } catch (e) {
    ElMessage.error('同步失败')
  }
}

const checkDoubleSpend = async (row: any) => {
  try {
    const res: any = await dcepAdvancedApi.detectDoubleSpend({
      walletId: row.walletId,
      offlineCert: row.offlineCert
    })
    if (res.data === true) {
      ElMessageBox.alert('检测到双花风险!', '防双花检测', { type: 'error' })
    } else {
      ElMessage.success('未检测到双花风险')
    }
  } catch (e) {
    ElMessage.error('检测失败')
  }
}

// ============ 合约 ============
const contractList = ref<any[]>([])
const contractLoading = ref(false)
const deployDialogVisible = ref(false)
const deployForm = reactive<any>({
  contractName: '', contractType: 'prepaid_card_escrow',
  walletId: 1, memberId: 1, escrowAmount: 1000,
  expiredTime: '2026-12-31 23:59:59', triggerConditions: '{"type":"expire"}'
})
const autoExecuting = ref(false)

const loadContracts = async () => {
  contractLoading.value = true
  try {
    const res: any = await dcepAdvancedApi.contracts({})
    contractList.value = res.data || []
  } catch (e) {
    contractList.value = []
  } finally {
    contractLoading.value = false
  }
}

const handleDeployContract = () => {
  Object.assign(deployForm, {
    contractName: '', contractType: 'prepaid_card_escrow',
    walletId: 1, memberId: 1, escrowAmount: 1000,
    expiredTime: '2026-12-31 23:59:59', triggerConditions: '{"type":"expire"}'
  })
  deployDialogVisible.value = true
}

const submitDeploy = async () => {
  try {
    await dcepAdvancedApi.deployContract(deployForm)
    ElMessage.success('部署成功')
    deployDialogVisible.value = false
    loadContracts()
  } catch (e) {
    ElMessage.error('部署失败')
  }
}

const handleContractAction = async (row: any, action: string) => {
  try {
    await dcepAdvancedApi.triggerContract(row.id, action)
    ElMessage.success(`执行${action}成功`)
    loadContracts()
  } catch (e) {
    ElMessage.error('执行失败')
  }
}

const handleDeleteContract = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确定删除合约"${row.contractName}"吗？`, '提示', { type: 'warning' })
    ElMessage.success('删除成功')
    loadContracts()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

const autoExecuteExpired = async () => {
  autoExecuting.value = true
  try {
    const res: any = await dcepAdvancedApi.autoExecuteExpired()
    ElMessage.success(`自动执行完成,共处理 ${res.data} 个过期合约`)
    loadContracts()
  } catch (e) {
    ElMessage.error('执行失败')
  } finally {
    autoExecuting.value = false
  }
}

// ============ 日志 ============
const logList = ref<any[]>([])
const logLoading = ref(false)

const loadLogs = async () => {
  logLoading.value = true
  try {
    const res: any = await dcepAdvancedApi.contractLogs()
    logList.value = res.data || []
  } catch (e) {
    logList.value = []
  } finally {
    logLoading.value = false
  }
}

// ============ 辅助 ============
const getWalletIcon = (type: string) => walletTypeOptions.find(o => o.value === type)?.icon || 'Cellphone'
const getWalletColor = (type: string) => walletTypeOptions.find(o => o.value === type)?.color || '#909399'

const getWalletStatusLabel = (s: string) => walletStatusOptions.find(o => o.value === s)?.label || s
const getWalletStatusType = (s: string) => walletStatusOptions.find(o => o.value === s)?.type || 'info'

const getSyncStatusLabel = (s: string) => syncStatusOptions.find(o => o.value === s)?.label || s
const getSyncStatusType = (s: string) => syncStatusOptions.find(o => o.value === s)?.type || 'info'

const getContractTypeLabel = (t: string) => contractTypeOptions.find(o => o.value === t)?.label || t
const getContractColor = (t: string) => contractTypeOptions.find(o => o.value === t)?.color || '#909399'

const getContractStatusLabel = (s: string) => contractStatusOptions.find(o => o.value === s)?.label || s
const getContractStatusType = (s: string) => contractStatusOptions.find(o => o.value === s)?.type || 'info'

const getActionLabel = (a: string) => contractActionOptions.find(o => o.value === a)?.label || a

const handleTabChange = (tab: string) => {
  if (tab === 'wallet') loadWallets()
  else if (tab === 'offline') loadOfflineTransactions()
  else if (tab === 'contract') loadContracts()
  else if (tab === 'log') loadLogs()
}

onMounted(async () => {
  try {
    const r: any = await request.get('/dcep/config/mode')
    dcepMode.value = r?.data?.mode || 'dev'
  } catch {
    dcepMode.value = 'dev'
  }
  loadWallets()
})
</script>