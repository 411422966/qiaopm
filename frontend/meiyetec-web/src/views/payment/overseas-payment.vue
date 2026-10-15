<template>
  <div class="overseas-payment-page">
    <el-tabs v-model="activeTab" @tab-change="handleTabChange">
      <el-tab-pane label="网关配置" name="gateway" />
      <el-tab-pane label="支付交易" name="payment" />
      <el-tab-pane label="路由规则" name="route" />
      <el-tab-pane label="钱包支付" name="wallet" />
      <el-tab-pane label="Webhook日志" name="webhook" />
    </el-tabs>

    <!-- ============ 网关配置 ============ -->
    <div v-if="activeTab === 'gateway'">
      <el-row :gutter="12">
        <el-col v-for="g in gatewayList" :key="g.id" :xs="24" :sm="12" :md="8" :lg="6">
          <div class="gateway-card">
            <div class="gateway-header" :style="{ background: getGatewayColor(g.gatewayCode) }">
              <el-icon :size="32"><component :is="getGatewayIcon(g.gatewayCode)" /></el-icon>
              <span class="gateway-name">{{ getGatewayLabel(g.gatewayCode) }}</span>
              <el-tag v-if="g.environment === 'sandbox'" type="warning" size="small">沙箱</el-tag>
              <el-tag v-else type="danger" size="small">生产</el-tag>
            </div>
            <div class="gateway-body">
              <div class="info-row">
                <span class="info-label">商户ID</span>
                <span class="info-value">{{ g.merchantId || '-' }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">API Base</span>
                <span class="info-value">{{ g.apiBaseUrl || '-' }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">手续费率</span>
                <span class="info-value">{{ ((g.feeRate || 0) * 100).toFixed(2) }}%</span>
              </div>
              <div class="info-row">
                <span class="info-label">健康状态</span>
                <el-tag :type="g.healthStatus === 'healthy' ? 'success' : (g.healthStatus === 'unhealthy' ? 'danger' : 'info')" size="small">
                  {{ g.healthStatus === 'healthy' ? '健康' : (g.healthStatus === 'unhealthy' ? '异常' : '未知') }}
                </el-tag>
              </div>
              <div class="info-row">
                <span class="info-label">启用状态</span>
                <el-switch v-model="g.status" :active-value="1" :inactive-value="0" @change="toggleGateway(g)" />
              </div>
            </div>
            <div class="gateway-actions">
              <el-button type="primary" link size="small" @click="handleEditGateway(g)">编辑</el-button>
              <el-button type="warning" link size="small" @click="handleHealthCheck(g)" :loading="healthChecking === g.id">健康检查</el-button>
              <el-button type="danger" link size="small" @click="handleDeleteGateway(g)">删除</el-button>
            </div>
          </div>
        </el-col>
      </el-row>

      <div style="margin-top: 16px">
        <el-button type="primary" @click="handleAddGateway">
          <el-icon><Plus /></el-icon>新增网关
        </el-button>
      </div>
    </div>

    <!-- ============ 支付交易 ============ -->
    <div v-if="activeTab === 'payment'">
      <el-row :gutter="16" style="margin-bottom:16px">
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-icon blue"><el-icon :size="24"><CreditCard /></el-icon></div>
            <div>
              <div class="stat-value">{{ totalTransactions }}</div>
              <div class="stat-label">交易笔数</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-icon green"><el-icon :size="24"><Money /></el-icon></div>
            <div>
              <div class="stat-value">${{ totalAmount }}</div>
              <div class="stat-label">交易金额</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-icon orange"><el-icon :size="24"><Coin /></el-icon></div>
            <div>
              <div class="stat-value">${{ totalFee }}</div>
              <div class="stat-label">手续费</div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-icon purple"><el-icon :size="24"><Wallet /></el-icon></div>
            <div>
              <div class="stat-value">${{ totalNet }}</div>
              <div class="stat-label">实际到账</div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-card>
        <template #header>
          <div class="card-header">
            <span>海外支付交易</span>
            <div class="filter-form">
              <el-select v-model="gatewayFilter" placeholder="网关" clearable style="width:140px" @change="loadPayments">
                <el-option v-for="o in gatewayOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
              <el-select v-model="statusFilter" placeholder="状态" clearable style="width:120px" @change="loadPayments">
                <el-option v-for="o in paymentStatusOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </div>
          </div>
        </template>

        <el-table :data="paymentList" border stripe v-loading="paymentLoading">
          <el-table-column label="订单号" prop="orderNo" width="180" fixed />
          <el-table-column label="网关" width="120">
            <template #default="{ row }">
              <el-tag :style="{ background: getGatewayColor(row.gatewayCode), color: '#fff' }">
                {{ getGatewayLabel(row.gatewayCode) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="客户" prop="memberName" width="120" />
          <el-table-column label="金额" width="120" align="right">
            <template #default="{ row }">
              <span style="color:#f56c6c;font-weight:600">
                {{ row.currency }} {{ Number(row.amount || 0).toFixed(2) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="手续费" width="120" align="right">
            <template #default="{ row }">
              {{ row.currency }} {{ Number(row.fee || 0).toFixed(2) }}
            </template>
          </el-table-column>
          <el-table-column label="支付方式" prop="paymentMethod" width="120" />
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="getStatusType(row.status)">{{ getStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="支付时间" prop="paidAt" width="160" />
          <el-table-column label="Gateway Payment ID" prop="gatewayPaymentId" width="200" show-overflow-tooltip />
          <el-table-column label="创建时间" prop="createTime" width="160" />
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button v-if="row.status === 'succeeded'" type="warning" link size="small" @click="handleRefund(row)">退款</el-button>
              <el-button type="primary" link size="small" @click="handleViewPayment(row)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!paymentLoading && !paymentList.length" description="暂无交易" />
      </el-card>
    </div>

    <!-- ============ 路由规则 ============ -->
    <div v-if="activeTab === 'route'">
      <el-card>
        <template #header>
          <div class="card-header">
            <span>支付路由规则</span>
            <el-button type="primary" @click="handleAddRoute">
              <el-icon><Plus /></el-icon>新增规则
            </el-button>
          </div>
        </template>

        <el-table :data="routeList" border stripe v-loading="routeLoading">
          <el-table-column label="规则名称" prop="ruleName" min-width="200" fixed />
          <el-table-column label="优先级" prop="priority" width="80" align="center" />
          <el-table-column label="货币" prop="currency" width="80" />
          <el-table-column label="国家" prop="countryCode" width="80" />
          <el-table-column label="支付方式" prop="paymentMethod" width="120" />
          <el-table-column label="目标网关" width="120">
            <template #default="{ row }">
              <el-tag :style="{ background: getGatewayColor(row.gatewayCode), color: '#fff' }">
                {{ getGatewayLabel(row.gatewayCode) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="费率" prop="feeRate" width="100" align="right">
            <template #default="{ row }">
              {{ ((row.feeRate || 0) * 100).toFixed(2) }}%
            </template>
          </el-table-column>
          <el-table-column label="策略" width="120">
            <template #default="{ row }">
              <el-tag>{{ getStrategyLabel(row.priorityStrategy) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-switch v-model="row.status" :active-value="1" :inactive-value="0" @change="toggleRoute(row)" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="handleEditRoute(row)">编辑</el-button>
              <el-button type="danger" link size="small" @click="handleDeleteRoute(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!routeLoading && !routeList.length" description="暂无规则" />
      </el-card>
    </div>

    <!-- ============ 钱包支付（Apple Pay / Google Pay） ============ -->
    <div v-if="activeTab === 'wallet'">
      <el-row :gutter="16" style="margin-bottom:16px">
        <el-col :span="12">
          <el-card class="wallet-card apple">
            <div class="wallet-header">
              <el-icon :size="48" color="#000"><Iphone /></el-icon>
              <div>
                <h3>Apple Pay</h3>
                <p class="wallet-subtitle">基于 Stripe · iOS / Safari 一键支付</p>
              </div>
            </div>
            <el-divider />
            <div class="wallet-config">
              <div class="info-row">
                <span class="info-label">Merchant ID</span>
                <span class="info-value">{{ applePayGateway?.merchantId || '未配置' }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">环境</span>
                <el-tag :type="applePayGateway?.environment === 'production' ? 'danger' : 'warning'" size="small">
                  {{ applePayGateway?.environment === 'production' ? '生产' : '沙箱' }}
                </el-tag>
              </div>
              <div class="info-row">
                <span class="info-label">健康状态</span>
                <el-tag :type="applePayGateway?.healthStatus === 'healthy' ? 'success' : (applePayGateway?.healthStatus === 'unhealthy' ? 'danger' : 'info')" size="small">
                  {{ applePayGateway?.healthStatus === 'healthy' ? '健康' : (applePayGateway?.healthStatus === 'unhealthy' ? '异常' : '未检查') }}
                </el-tag>
              </div>
              <div class="info-row">
                <span class="info-label">启用状态</span>
                <el-tag :type="applePayGateway?.status === 1 ? 'success' : 'info'" size="small">
                  {{ applePayGateway?.status === 1 ? '已启用' : '已停用' }}
                </el-tag>
              </div>
            </div>
            <div class="wallet-actions">
              <el-button v-if="!applePayGateway" type="primary" @click="handleAddApplePayGateway">
                <el-icon><Plus /></el-icon>配置 Apple Pay
              </el-button>
              <el-button v-else type="warning" @click="handleTestWallet('apple_pay')" :loading="walletTesting === 'apple_pay'">
                测试 PaymentIntent
              </el-button>
              <el-button v-if="applePayGateway" type="success" plain @click="handleHealthCheck(applePayGateway)">
                健康检查
              </el-button>
            </div>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card class="wallet-card google">
            <div class="wallet-header">
              <el-icon :size="48" color="#4285F4"><Cellphone /></el-icon>
              <div>
                <h3>Google Pay</h3>
                <p class="wallet-subtitle">基于 Stripe · Android / Chrome 一键支付</p>
              </div>
            </div>
            <el-divider />
            <div class="wallet-config">
              <div class="info-row">
                <span class="info-label">Merchant ID</span>
                <span class="info-value">{{ googlePayGateway?.merchantId || '未配置' }}</span>
              </div>
              <div class="info-row">
                <span class="info-label">环境</span>
                <el-tag :type="googlePayGateway?.environment === 'production' ? 'danger' : 'warning'" size="small">
                  {{ googlePayGateway?.environment === 'production' ? '生产' : '沙箱' }}
                </el-tag>
              </div>
              <div class="info-row">
                <span class="info-label">健康状态</span>
                <el-tag :type="googlePayGateway?.healthStatus === 'healthy' ? 'success' : (googlePayGateway?.healthStatus === 'unhealthy' ? 'danger' : 'info')" size="small">
                  {{ googlePayGateway?.healthStatus === 'healthy' ? '健康' : (googlePayGateway?.healthStatus === 'unhealthy' ? '异常' : '未检查') }}
                </el-tag>
              </div>
              <div class="info-row">
                <span class="info-label">启用状态</span>
                <el-tag :type="googlePayGateway?.status === 1 ? 'success' : 'info'" size="small">
                  {{ googlePayGateway?.status === 1 ? '已启用' : '已停用' }}
                </el-tag>
              </div>
            </div>
            <div class="wallet-actions">
              <el-button v-if="!googlePayGateway" type="primary" @click="handleAddGooglePayGateway">
                <el-icon><Plus /></el-icon>配置 Google Pay
              </el-button>
              <el-button v-else type="warning" @click="handleTestWallet('google_pay')" :loading="walletTesting === 'google_pay'">
                测试 PaymentIntent
              </el-button>
              <el-button v-if="googlePayGateway" type="success" plain @click="handleHealthCheck(googlePayGateway)">
                健康检查
              </el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <el-card>
        <template #header>
          <div class="card-header">
            <span>近期钱包支付交易</span>
            <el-button text @click="loadWalletPayments">
              <el-icon><Refresh /></el-icon>刷新
            </el-button>
          </div>
        </template>
        <el-table :data="walletPayments" border stripe v-loading="walletLoading">
          <el-table-column label="订单号" prop="orderNo" width="180" fixed />
          <el-table-column label="钱包" width="120">
            <template #default="{ row }">
              <el-tag :style="{ background: getGatewayColor(row.gatewayCode), color: '#fff' }">
                {{ getGatewayLabel(row.gatewayCode) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="客户" prop="memberName" width="120" />
          <el-table-column label="金额" width="120" align="right">
            <template #default="{ row }">
              <span style="color:#f56c6c;font-weight:600">
                {{ row.currency }} {{ Number(row.amount || 0).toFixed(2) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="120">
            <template #default="{ row }">
              <el-tag :type="getStatusType(row.status)">{{ getStatusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="Gateway Payment ID" prop="gatewayPaymentId" show-overflow-tooltip />
          <el-table-column label="创建时间" prop="createTime" width="160" />
        </el-table>
        <el-empty v-if="!walletLoading && !walletPayments.length" description="暂无钱包支付交易" />
      </el-card>

      <el-card style="margin-top:16px">
        <template #header>
          <span>钱包支付集成说明（P3.6 2026-09-05 实装）</span>
        </template>
        <el-alert type="info" :closable="false" show-icon>
          <template #title>
            <strong>底层通道：</strong>Stripe PaymentIntents API · <code>payment_method_types=card</code>
          </template>
          <div style="margin-top: 8px; line-height: 1.6">
            <p>• Apple Pay 走 Apple Pay JS API（Safari / iOS），由 Stripe 客户端 tokenize；后端仅创建 PaymentIntent。</p>
            <p>• Google Pay 走 Google Pay API（Android / Chrome），由 Stripe 客户端 tokenize；后端仅创建 PaymentIntent。</p>
            <p>• <strong>Webhook 元数据标记</strong>：每个 PaymentIntent 注入 <code>metadata[wallet_type]=apple_pay|google_pay</code> 用于通道溯源。</p>
            <p>• <strong>金额约定</strong>：Stripe 以最小货币单位计（JPY/KRW 等零小数货币保留原值）。</p>
            <p>• <strong>必填配置</strong>：publishableKey（pk_test_xxx）+ secretKey（sk_test_xxx）+ webhookSecret（whsec_xxx）+ merchantId。</p>
          </div>
        </el-alert>
      </el-card>
    </div>

    <!-- ============ Webhook日志 ============ -->
    <div v-if="activeTab === 'webhook'">
      <el-card>
        <template #header>
          <span>Webhook 回调日志</span>
        </template>
        <el-table :data="webhookLogs" border stripe v-loading="webhookLoading">
          <el-table-column label="接收时间" prop="receiveTime" width="160" />
          <el-table-column label="网关" width="120">
            <template #default="{ row }">
              <el-tag>{{ getGatewayLabel(row.gatewayCode) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="事件类型" prop="eventType" width="200" />
          <el-table-column label="事件ID" prop="gatewayEventId" width="200" show-overflow-tooltip />
          <el-table-column label="签名验证" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.verifyStatus === 1 ? 'success' : 'danger'" size="small">
                {{ row.verifyStatus === 1 ? '通过' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="处理状态" width="120">
            <template #default="{ row }">
              <el-tag :type="getProcessStatusType(row.processStatus)">{{ getProcessStatusLabel(row.processStatus) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="处理时间" prop="processTime" width="160" />
          <el-table-column label="重试" prop="retryCount" width="60" align="center" />
          <el-table-column label="源IP" prop="sourceIp" width="120" />
          <el-table-column label="错误信息" prop="errorMessage" show-overflow-tooltip />
        </el-table>
        <el-empty v-if="!webhookLoading && !webhookLogs.length" description="暂无Webhook记录" />
      </el-card>
    </div>

    <!-- 网关编辑对话框 -->
    <el-dialog v-model="gatewayDialogVisible" :title="gatewayDialogTitle" width="700px">
      <el-form :model="gatewayForm" label-width="120px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="网关">
              <el-select v-model="gatewayForm.gatewayCode" style="width:100%" :disabled="!!gatewayForm.id">
                <el-option v-for="o in gatewayOptions" :key="o.value" :label="`${o.label} - ${o.description}`" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="环境">
              <el-radio-group v-model="gatewayForm.environment">
                <el-radio v-for="o in environmentOptions" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="API Base URL">
          <el-input v-model="gatewayForm.apiBaseUrl" placeholder="https://api.stripe.com" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="Publishable Key">
              <el-input v-model="gatewayForm.publishableKey" placeholder="pk_..." />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="Secret Key">
              <el-input v-model="gatewayForm.secretKey" type="password" show-password placeholder="sk_..." />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="Webhook Secret">
              <el-input v-model="gatewayForm.webhookSecret" type="password" show-password />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="商户ID">
              <el-input v-model="gatewayForm.merchantId" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="手续费率">
              <el-input-number v-model="gatewayForm.feeRate" :min="0" :max="1" :step="0.001" :precision="3" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="固定费用">
              <el-input-number v-model="gatewayForm.fixedFee" :min="0" :precision="2" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="单笔限额">
              <el-input-number v-model="gatewayForm.maxAmountPerTransaction" :min="0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单日限额">
              <el-input-number v-model="gatewayForm.maxAmountPerDay" :min="0" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="支持货币">
          <el-select v-model="gatewayForm.supportedCurrencies" multiple style="width:100%">
            <el-option v-for="o in currencyOptions" :key="o.value" :label="`${o.value} (${o.label})`" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="支付方式">
          <el-select v-model="gatewayForm.paymentMethods" multiple style="width:100%">
            <el-option v-for="o in paymentMethodOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="启用状态">
          <el-radio-group v-model="gatewayForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="gatewayDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveGateway">保存</el-button>
      </template>
    </el-dialog>

    <!-- 路由规则对话框 -->
    <el-dialog v-model="routeDialogVisible" :title="routeDialogTitle" width="600px">
      <el-form :model="routeForm" label-width="100px">
        <el-form-item label="规则名称">
          <el-input v-model="routeForm.ruleName" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="优先级">
              <el-input-number v-model="routeForm.priority" :min="0" :max="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="货币">
              <el-select v-model="routeForm.currency" style="width:100%">
                <el-option v-for="o in currencyOptions" :key="o.value" :label="o.value" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="国家代码">
              <el-input v-model="routeForm.countryCode" placeholder="如:US" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="支付方式">
              <el-select v-model="routeForm.paymentMethod" style="width:100%">
                <el-option label="信用卡" value="card" />
                <el-option label="Apple Pay" value="apple_pay" />
                <el-option label="Google Pay" value="google_pay" />
                <el-option label="PayPal" value="paypal" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="目标网关">
              <el-select v-model="routeForm.gatewayCode" style="width:100%">
                <el-option v-for="o in gatewayOptions" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="费率">
              <el-input-number v-model="routeForm.feeRate" :min="0" :max="1" :step="0.001" :precision="3" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="策略">
          <el-radio-group v-model="routeForm.priorityStrategy">
            <el-radio v-for="o in routeStrategyOptions" :key="o.value" :value="o.value">{{ o.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="启用状态">
          <el-radio-group v-model="routeForm.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="routeDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSaveRoute">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, CreditCard, Money, Wallet, Coin, Connection,
  Iphone, Cellphone, Refresh
} from '@element-plus/icons-vue'
import {
  overseasPaymentApi,
  gatewayOptions,
  paymentMethodOptions,
  currencyOptions,
  paymentStatusOptions,
  routeStrategyOptions,
  environmentOptions
} from '@/api/overseas-payment'

const activeTab = ref('gateway')

// ============ 钱包支付（Apple Pay / Google Pay） ============
const applePayGateway = computed(() => gatewayList.value.find(g => g.gatewayCode === 'apple_pay'))
const googlePayGateway = computed(() => gatewayList.value.find(g => g.gatewayCode === 'google_pay'))
const walletPayments = ref<any[]>([])
const walletLoading = ref(false)
const walletTesting = ref<string | null>(null)

const loadWalletPayments = async () => {
  walletLoading.value = true
  try {
    const [apple, google] = await Promise.all([
      overseasPaymentApi.payments({ gatewayCode: 'apple_pay' }).catch(() => ({ data: [] })),
      overseasPaymentApi.payments({ gatewayCode: 'google_pay' }).catch(() => ({ data: [] }))
    ])
    walletPayments.value = [
      ...((apple as any)?.data || []),
      ...((google as any)?.data || [])
    ].sort((a, b) => (b.createTime || '').localeCompare(a.createTime || '')).slice(0, 50)
  } catch (e) {
    walletPayments.value = []
  } finally {
    walletLoading.value = false
  }
}

const handleAddApplePayGateway = () => {
  Object.assign(gatewayForm, {
    id: null,
    gatewayCode: 'apple_pay',
    gatewayName: 'Apple Pay (Stripe)',
    apiBaseUrl: 'https://api.stripe.com',
    publishableKey: '',
    secretKey: '',
    webhookSecret: '',
    merchantId: '',
    feeRate: 0.029,
    fixedFee: 0.30,
    supportedCurrencies: ['USD', 'EUR', 'GBP', 'CAD', 'AUD', 'JPY', 'SGD', 'HKD'],
    paymentMethods: ['apple_pay'],
    environment: 'sandbox',
    status: 1
  })
  gatewayDialogTitle.value = '新增 Apple Pay 网关'
  gatewayDialogVisible.value = true
}

const handleAddGooglePayGateway = () => {
  Object.assign(gatewayForm, {
    id: null,
    gatewayCode: 'google_pay',
    gatewayName: 'Google Pay (Stripe)',
    apiBaseUrl: 'https://api.stripe.com',
    publishableKey: '',
    secretKey: '',
    webhookSecret: '',
    merchantId: '',
    feeRate: 0.029,
    fixedFee: 0.30,
    supportedCurrencies: ['USD', 'EUR', 'GBP', 'CAD', 'AUD', 'JPY', 'SGD', 'HKD'],
    paymentMethods: ['google_pay'],
    environment: 'sandbox',
    status: 1
  })
  gatewayDialogTitle.value = '新增 Google Pay 网关'
  gatewayDialogVisible.value = true
}

const handleTestWallet = async (gatewayCode: string) => {
  walletTesting.value = gatewayCode
  try {
    // 创建一笔 1.00 USD 的测试 PaymentIntent
    const res: any = await overseasPaymentApi.createPayment({
      orderType: 'service_order',
      orderId: 0,
      orderNo: 'TEST-' + Date.now(),
      memberId: 0,
      memberName: '测试客户',
      memberEmail: 'test@example.com',
      amount: 1.00,
      currency: 'USD',
      paymentMethod: gatewayCode === 'apple_pay' ? 'apple_pay' : 'google_pay'
    })
    if (res.data?.id) {
      ElMessage.success(`PaymentIntent 已创建：${res.data.gatewayIntentId}`)
    } else {
      ElMessage.warning('创建失败：' + (res.message || '未知错误'))
    }
    loadWalletPayments()
  } catch (e: any) {
    ElMessage.error('测试失败：' + (e?.message || '请检查 Stripe 凭据'))
  } finally {
    walletTesting.value = null
  }
}

// ============ 网关 ============
const gatewayList = ref<any[]>([])
const gatewayFilter = ref('')
const gatewayDialogVisible = ref(false)
const gatewayDialogTitle = ref('新增网关')
const gatewayForm = reactive<any>({
  id: null,
  gatewayCode: 'stripe',
  gatewayName: '',
  apiBaseUrl: '',
  publishableKey: '',
  secretKey: '',
  webhookSecret: '',
  merchantId: '',
  feeRate: 0.029,
  fixedFee: 0.30,
  maxAmountPerTransaction: 999999,
  maxAmountPerDay: 9999999,
  supportedCurrencies: ['USD', 'EUR', 'GBP'],
  paymentMethods: ['card'],
  environment: 'sandbox',
  status: 1,
  supportedCurrencies: [],
  paymentMethods: []
})

const loadGateways = async () => {
  try {
    const res: any = await overseasPaymentApi.gateways({})
    gatewayList.value = res.data || []
  } catch (e) {
    gatewayList.value = []
  }
}

const handleAddGateway = () => {
  Object.assign(gatewayForm, {
    id: null,
    gatewayCode: 'stripe',
    gatewayName: '',
    apiBaseUrl: '',
    publishableKey: '',
    secretKey: '',
    webhookSecret: '',
    merchantId: '',
    feeRate: 0.029,
    fixedFee: 0.30,
    supportedCurrencies: ['USD', 'EUR', 'GBP'],
    paymentMethods: ['card'],
    environment: 'sandbox',
    status: 1
  })
  gatewayDialogTitle.value = '新增网关'
  gatewayDialogVisible.value = true
}

const handleEditGateway = (row: any) => {
  Object.assign(gatewayForm, row)
  gatewayDialogTitle.value = '编辑网关'
  gatewayDialogVisible.value = true
}

const handleSaveGateway = async () => {
  try {
    if (gatewayForm.id) {
      await overseasPaymentApi.updateGateway(gatewayForm)
    } else {
      await overseasPaymentApi.createGateway(gatewayForm)
    }
    ElMessage.success('保存成功')
    gatewayDialogVisible.value = false
    loadGateways()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

const handleDeleteGateway = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确定删除网关"${getGatewayLabel(row.gatewayCode)}"吗？`, '提示', { type: 'warning' })
    await overseasPaymentApi.deleteGateway(row.id)
    ElMessage.success('删除成功')
    loadGateways()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

const handleToggleGateway = async (row: any) => {
  try {
    await overseasPaymentApi.updateGateway(row)
  } catch (e) {
    ElMessage.error('更新失败')
  }
}

// 健康检查
const healthChecking = ref<number | null>(null)
const handleHealthCheck = async (row: any) => {
  healthChecking.value = row.id
  try {
    const res: any = await overseasPaymentApi.gatewayHealthCheck(row.id)
    if (res.data?.healthy) {
      ElMessage.success('健康检查正常')
    } else {
      ElMessage.warning('健康检查异常: ' + (res.data?.message || ''))
    }
    loadGateways()
  } catch (e) {
    ElMessage.error('健康检查失败')
  } finally {
    healthChecking.value = null
  }
}

const toggleGateway = (row: any) => {
  handleToggleGateway(row)
}

// ============ 支付 ============
const paymentList = ref<any[]>([])
const paymentLoading = ref(false)
const statusFilter = ref('')

const loadPayments = async () => {
  paymentLoading.value = true
  try {
    const res: any = await overseasPaymentApi.payments({
      gatewayCode: gatewayFilter.value || undefined,
      status: statusFilter.value || undefined
    })
    paymentList.value = res.data || []
  } catch (e) {
    paymentList.value = []
  } finally {
    paymentLoading.value = false
  }
}

const totalTransactions = computed(() => paymentList.value.length)
const totalAmount = computed(() => paymentList.value.reduce((s, p) => s + Number(p.amount || 0), 0).toFixed(2))
const totalFee = computed(() => paymentList.value.reduce((s, p) => s + Number(p.fee || 0), 0).toFixed(2))
const totalNet = computed(() => paymentList.value.reduce((s, p) => s + Number(p.netAmount || 0), 0).toFixed(2))

const handleViewPayment = (row: any) => {
  ElMessageBox.alert(JSON.stringify(row, null, 2), '交易详情', { 'custom-class': 'payment-detail' })
}

const handleRefund = async (row: any) => {
  try {
    const { value: amount } = await ElMessageBox.prompt('退款金额', '退款', {
      inputValue: String(row.amount),
      inputPattern: /^\d+(\.\d{1,2})?$/,
      inputErrorMessage: '请输入有效金额'
    })
    await overseasPaymentApi.refundPayment(row.id, Number(amount), '客户申请')
    ElMessage.success('退款成功')
    loadPayments()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

// ============ 路由 ============
const routeList = ref<any[]>([])
const routeLoading = ref(false)
const routeDialogVisible = ref(false)
const routeDialogTitle = ref('新增规则')
const routeForm = reactive<any>({
  id: null,
  ruleName: '',
  priority: 5,
  currency: 'USD',
  countryCode: '',
  paymentMethod: 'card',
  gatewayCode: 'stripe',
  feeRate: 0.029,
  priorityStrategy: 'cost',
  status: 1
})

const loadRoutes = async () => {
  routeLoading.value = true
  try {
    const res: any = await overseasPaymentApi.routeRules()
    routeList.value = res.data || []
  } catch (e) {
    routeList.value = []
  } finally {
    routeLoading.value = false
  }
}

const handleAddRoute = () => {
  Object.assign(routeForm, {
    id: null,
    ruleName: '',
    priority: 5,
    currency: 'USD',
    countryCode: '',
    paymentMethod: 'card',
    gatewayCode: 'stripe',
    feeRate: 0.029,
    priorityStrategy: 'cost',
    status: 1
  })
  routeDialogTitle.value = '新增规则'
  routeDialogVisible.value = true
}

const handleEditRoute = (row: any) => {
  Object.assign(routeForm, row)
  routeDialogTitle.value = '编辑规则'
  routeDialogVisible.value = true
}

const handleSaveRoute = async () => {
  try {
    if (routeForm.id) {
      await overseasPaymentApi.updateRoute(routeForm)
    } else {
      await overseasPaymentApi.createRoute(routeForm)
    }
    ElMessage.success('保存成功')
    routeDialogVisible.value = false
    loadRoutes()
  } catch (e) {
    ElMessage.error('保存失败')
  }
}

const handleDeleteRoute = async (row: any) => {
  try {
    await ElMessageBox.confirm('确定删除该规则吗？', '提示', { type: 'warning' })
    await overseasPaymentApi.deleteRoute(row.id)
    ElMessage.success('删除成功')
    loadRoutes()
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

const toggleRoute = async (row: any) => {
  try {
    await overseasPaymentApi.updateRoute(row)
  } catch (e) {
    ElMessage.error('更新失败')
  }
}

// ============ Webhook ============
const webhookLogs = ref<any[]>([])
const webhookLoading = ref(false)

const loadWebhookLogs = async () => {
  webhookLoading.value = true
  try {
    const res: any = await overseasPaymentApi.webhookLogs({})
    webhookLogs.value = res.data || []
  } catch (e) {
    webhookLogs.value = []
  } finally {
    webhookLoading.value = false
  }
}

// ============ 辅助 ============
const getGatewayLabel = (code: string) => gatewayOptions.find(o => o.value === code)?.label || code
const getGatewayColor = (code: string) => gatewayOptions.find(o => o.value === code)?.color || '#909399'
const getGatewayIcon = (code: string) => gatewayOptions.find(o => o.value === code)?.icon || 'CreditCard'

const getStatusLabel = (s: string) => paymentStatusOptions.find(o => o.value === s)?.label || s
const getStatusType = (s: string) => paymentStatusOptions.find(o => o.value === s)?.type || 'info'

const getStrategyLabel = (s: string) => routeStrategyOptions.find(o => o.value === s)?.label || s

const getProcessStatusLabel = (s: string) => {
  const map: any = { pending: '待处理', processed: '已处理', failed: '失败', skipped: '跳过' }
  return map[s] || s
}

const getProcessStatusType = (s: string) => {
  const map: any = { pending: 'warning', processed: 'success', failed: 'danger', skipped: 'info' }
  return map[s] || 'info'
}

const handleTabChange = (tab: string) => {
  if (tab === 'gateway') loadGateways()
  else if (tab === 'payment') loadPayments()
  else if (tab === 'route') loadRoutes()
  else if (tab === 'wallet') { loadGateways(); loadWalletPayments() }
  else if (tab === 'webhook') loadWebhookLogs()
}

onMounted(() => {
  loadGateways()
})
</script>

<style scoped>
.overseas-payment-page {
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

/* 网关卡片 */
.gateway-card {
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 16px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
}

.gateway-header {
  height: 80px;
  display: flex;
  align-items: center;
  padding: 0 16px;
  color: #fff;
  gap: 12px;
  position: relative;
}

.gateway-name {
  font-size: 18px;
  font-weight: 600;
  flex: 1;
}

.gateway-body {
  padding: 16px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  padding: 6px 0;
  font-size: 13px;
  border-bottom: 1px solid #f5f7fa;
}

.info-row:last-child {
  border-bottom: none;
}

.info-label {
  color: #909399;
}

.info-value {
  color: #303133;
  font-weight: 500;
}

.gateway-actions {
  padding: 8px 16px;
  border-top: 1px solid #f5f7fa;
  text-align: right;
}

/* 统计卡片 */
.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 16px;
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.stat-icon.blue { background: linear-gradient(135deg, #409eff, #79bbff); }
.stat-icon.green { background: linear-gradient(135deg, #67c23a, #95d475); }
.stat-icon.orange { background: linear-gradient(135deg, #e6a23c, #f3d19e); }
.stat-icon.purple { background: linear-gradient(135deg, #9c27b0, #ce93d8); }

.stat-value { font-size: 24px; font-weight: 700; color: #303133; }
.stat-label { font-size: 13px; color: #909399; }

/* 钱包支付卡片 */
.wallet-card {
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.wallet-card.apple {
  border-top: 4px solid #000;
}

.wallet-card.google {
  border-top: 4px solid #4285F4;
}

.wallet-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 8px 0;
}

.wallet-header h3 {
  margin: 0 0 4px 0;
  font-size: 22px;
  font-weight: 700;
}

.wallet-subtitle {
  margin: 0;
  font-size: 13px;
  color: #909399;
}

.wallet-config {
  padding: 8px 0;
}

.wallet-actions {
  margin-top: 12px;
  display: flex;
  gap: 8px;
}
</style>