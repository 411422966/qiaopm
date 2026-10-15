<template>
  <div class="lbs-page">
    <!-- KPI 看板 -->
    <el-card class="kpi-card">
      <el-row :gutter="12">
        <el-col :span="4">
          <div class="kpi-item">
            <div class="kpi-value" style="color:#409eff">{{ dash.totalTechs }}</div>
            <div class="kpi-label">技师总数</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="kpi-item">
            <div class="kpi-value" style="color:#67c23a">{{ dash.onlineTechs }}</div>
            <div class="kpi-label">在线技师</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="kpi-item">
            <div class="kpi-value" style="color:#e6a23c">{{ dash.todayOrders }}</div>
            <div class="kpi-label">今日订单</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="kpi-item">
            <div class="kpi-value" style="color:#909399">{{ dash.pendingOrders }}</div>
            <div class="kpi-label">待派单</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="kpi-item">
            <div class="kpi-value" style="color:#409eff">{{ dash.dispatchedToday }}</div>
            <div class="kpi-label">已派单</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="kpi-item">
            <div class="kpi-value" style="color:#67c23a">{{ dash.avgDispatchScore != null ? (dash.avgDispatchScore as number).toFixed(1) : '—' }}</div>
            <div class="kpi-label">平均评分</div>
          </div>
        </el-col>
      </el-row>
    </el-card>

    <el-tabs v-model="activeTab" class="lbs-tabs" @tab-change="onTabChange">
      <!-- 技师管理 -->
      <el-tab-pane label="上门技师" name="tech">
        <el-card>
          <div class="toolbar">
            <el-button type="primary" @click="openTechDialog()">+ 注册技师</el-button>
            <el-select v-model="techFilter.status" placeholder="状态" clearable style="width:120px" @change="loadTechs">
              <el-option label="在线" value="ONLINE" />
              <el-option label="离线" value="OFFLINE" />
              <el-option label="繁忙" value="BUSY" />
            </el-select>
            <el-button @click="loadTechs">刷新</el-button>
          </div>
          <el-table :data="filteredTechs" v-loading="loading" border stripe>
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="name" label="姓名" width="110" />
            <el-table-column prop="phone" label="电话" width="130" />
            <el-table-column label="评分" width="90">
              <template #default="{ row }">
                <el-tag :type="(Number(row.rating) || 0) >= 4.5 ? 'success' : 'info'" size="small">
                  {{ row.rating || '5.0' }} ★
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="最大半径" width="110">
              <template #default="{ row }">{{ row.maxRadiusKm || 10 }} km</template>
            </el-table-column>
            <el-table-column label="技能标签" min-width="160">
              <template #default="{ row }">
                <span v-if="row.skillTags" style="font-size:11px;color:#666">
                  {{ row.skillTags.replace(/[\[\]"]/g,'') }}
                </span>
                <span v-else style="color:#c0c4cc">—</span>
              </template>
            </el-table-column>
            <el-table-column label="实时位置" width="180">
              <template #default="{ row }">
                <span v-if="row.currentLat" style="font-size:11px">
                  {{ row.currentLat }}, {{ row.currentLng }}
                </span>
                <span v-else style="color:#909399;font-size:11px">未上报</span>
              </template>
            </el-table-column>
            <el-table-column label="最后上报" width="160">
              <template #default="{ row }">{{ row.lastLocTime || '—' }}</template>
            </el-table-column>
            <el-table-column label="今日单" width="80" align="center">
              <template #default="{ row }">
                <el-tag size="small" type="info">{{ row.todayOrderCount || 0 }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="在线" width="80">
              <template #default="{ row }">
                <el-tag :type="row.onlineStatus === 'ONLINE' ? 'success' : row.onlineStatus === 'BUSY' ? 'warning' : 'info'" size="small">
                  {{ onlineLabel(row.onlineStatus) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="260" fixed="right">
              <template #default="{ row }">
                <el-button link size="small" @click="openLocDialog(row)">报位置</el-button>
                <el-button link size="small" @click="openStatusDialog(row)">切状态</el-button>
                <el-button link size="small" @click="openSkillDialog(row)">技能</el-button>
                <el-button link size="small" @click="openRouteDialog(row)">路线</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 派单 -->
      <el-tab-pane label="创建派单" name="dispatch">
        <el-card>
          <el-form :model="dispatchForm" label-width="100px" style="max-width:720px">
            <el-row :gutter="16">
              <el-col :span="12"><el-form-item label="会员 ID" required><el-input-number v-model="dispatchForm.memberId" :min="1" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="会员姓名"><el-input v-model="dispatchForm.memberName" /></el-form-item></el-col>
            </el-row>
            <el-form-item label="联系电话"><el-input v-model="dispatchForm.memberPhone" /></el-form-item>
            <el-row :gutter="16">
              <el-col :span="12"><el-form-item label="服务 ID" required><el-input-number v-model="dispatchForm.serviceId" :min="1" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="服务名称"><el-input v-model="dispatchForm.serviceName" placeholder="如：上门美甲" /></el-form-item></el-col>
            </el-row>
            <el-form-item label="技能标签"><el-input v-model="dispatchForm.skillTags" placeholder="如：MANICURE,PEDICURE（逗号分隔）" /></el-form-item>
            <el-form-item label="服务地址"><el-input v-model="dispatchForm.address" placeholder="如：朝阳区 XX 小区" /></el-form-item>
            <el-row :gutter="16">
              <el-col :span="8"><el-form-item label="纬度" required><el-input-number v-model="dispatchForm.lat" :min="-90" :max="90" :precision="6" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="经度" required><el-input-number v-model="dispatchForm.lng" :min="-180" :max="180" :precision="6" /></el-form-item></el-col>
              <el-col :span="8"><el-form-item label="金额"><el-input-number v-model="dispatchForm.amount" :min="0" /></el-form-item></el-col>
            </el-row>
            <el-form-item label="预约时间" required>
              <el-date-picker v-model="dispatchForm.scheduledTime" type="datetime" placeholder="上门时间" style="width:240px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="submitDispatch" :loading="dispatching">立即派单</el-button>
              <el-button @click="fillTestData">填入测试数据</el-button>
            </el-form-item>
          </el-form>

          <el-divider />
          <h4>最近派单结果</h4>
          <el-table :data="recentDispatches" border stripe size="small" max-height="300">
            <el-table-column prop="orderNo" label="订单号" width="170" />
            <el-table-column label="结果" width="80">
              <template #default="{ row }">
                <el-tag :type="row.success ? 'success' : 'danger'" size="small">{{ row.success ? '成功' : '失败' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="技师" min-width="150">
              <template #default="{ row }">
                <span v-if="row.technicianName">{{ row.technicianName }} · {{ row.phone }}</span>
                <span v-else style="color:#f56c6c">{{ row.reason }}</span>
              </template>
            </el-table-column>
            <el-table-column label="距离" width="90">
              <template #default="{ row }">{{ row.distanceKm ? row.distanceKm + ' km' : '—' }}</template>
            </el-table-column>
            <el-table-column label="综合分" width="80" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.totalScore" size="small" :type="(row.totalScore as number) >= 70 ? 'success' : (row.totalScore as number) >= 50 ? 'warning' : 'danger'">
                  {{ (row.totalScore as number).toFixed(1) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="5 维度" min-width="220">
              <template #default="{ row }">
                <div v-if="row.skillScore" class="mini-dims">
                  <span class="dim-pill" title="技能分">技 {{ (row.skillScore as number).toFixed(0) }}</span>
                  <span class="dim-pill" title="距离分">距 {{ (row.distanceScore as number).toFixed(0) }}</span>
                  <span class="dim-pill" title="评分分">评 {{ (row.ratingScore as number).toFixed(0) }}</span>
                  <span class="dim-pill" title="负荷分">负 {{ (row.loadScore as number).toFixed(0) }}</span>
                  <span class="dim-pill" title="时间窗分">时 {{ (row.timeScore as number).toFixed(0) }}</span>
                </div>
                <span v-else>—</span>
              </template>
            </el-table-column>
            <el-table-column label="上门费" width="90">
              <template #default="{ row }">{{ row.travelFee ? '¥' + row.travelFee : '—' }}</template>
            </el-table-column>
            <el-table-column label="ETA" width="70" align="center">
              <template #default="{ row }">{{ row.etaMinutes ? row.etaMinutes + 'min' : '—' }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 派单预览 -->
      <el-tab-pane label="派单预览" name="preview">
        <el-card>
          <el-form :model="previewForm" label-width="100px" style="max-width:680px">
            <el-alert type="info" :closable="false" style="margin-bottom:12px">
              在实际派单前预览候选技师排名，<strong>不写库、不扣减</strong>。
            </el-alert>
            <el-row :gutter="16">
              <el-col :span="12"><el-form-item label="纬度" required><el-input-number v-model="previewForm.lat" :min="-90" :max="90" :precision="6" /></el-form-item></el-col>
              <el-col :span="12"><el-form-item label="经度" required><el-input-number v-model="previewForm.lng" :min="-180" :max="180" :precision="6" /></el-form-item></el-col>
            </el-row>
            <el-form-item label="技能标签"><el-input v-model="previewForm.skillTags" placeholder="逗号分隔，如：MANICURE,PEDICURE" /></el-form-item>
            <el-form-item label="预约时间"><el-date-picker v-model="previewForm.scheduledTime" type="datetime" style="width:240px" /></el-form-item>
            <el-form-item>
              <el-button type="primary" @click="runPreview" :loading="previewing">执行预览</el-button>
              <el-button @click="fillPreviewTest">填入测试</el-button>
            </el-form-item>
          </el-form>

          <template v-if="previewResult.scored && previewResult.scored.length > 0">
            <el-divider />
            <div class="preview-summary">
              <el-tag type="success" size="large">候选 {{ previewResult.candidatesCount }} 人 · 合格 {{ previewResult.eligibleCount }} 人</el-tag>
              <el-tag v-if="previewResult.config" size="small" style="margin-left:8px">
                权重：距{{ ((previewResult.config as any).wDistance * 100).toFixed(0) }}%
                技{{ ((previewResult.config as any).wSkill * 100).toFixed(0) }}%
                评{{ ((previewResult.config as any).wRating * 100).toFixed(0) }}%
                负{{ ((previewResult.config as any).wLoad * 100).toFixed(0) }}%
                时{{ ((previewResult.config as any).wTime * 100).toFixed(0) }}%
              </el-tag>
            </div>
            <el-table :data="previewResult.scored as any[]" border stripe size="small" max-height="420">
              <el-table-column label="排名" width="60" align="center">
                <template #default="{ row, $index }">
                  <el-tag :type="$index === 0 ? 'danger' : $index < 3 ? 'warning' : 'info'" size="small">#{{ $index + 1 }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="technicianName" label="技师" width="110" />
              <el-table-column label="综合分" width="90" align="center">
                <template #default="{ row }">
                  <el-tag :type="(row.totalScore as number) >= 70 ? 'success' : (row.totalScore as number) >= 50 ? 'warning' : 'danger'" effect="dark" size="default">
                    {{ (row.totalScore as number).toFixed(1) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="5 维度" min-width="300">
                <template #default="{ row }">
                  <div class="dim-bars">
                    <div class="dim-bar-row">
                      <span class="dim-lbl">距</span>
                      <el-progress :percentage="(row.distanceScore as number)" :stroke-width="8" :show-text="false" color="#409eff" style="flex:1" />
                      <span class="dim-val">{{ (row.distanceScore as number).toFixed(0) }}</span>
                    </div>
                    <div class="dim-bar-row">
                      <span class="dim-lbl">技</span>
                      <el-progress :percentage="(row.skillScore as number)" :stroke-width="8" :show-text="false" color="#67c23a" style="flex:1" />
                      <span class="dim-val">{{ (row.skillScore as number).toFixed(0) }}</span>
                    </div>
                    <div class="dim-bar-row">
                      <span class="dim-lbl">评</span>
                      <el-progress :percentage="(row.ratingScore as number)" :stroke-width="8" :show-text="false" color="#e6a23c" style="flex:1" />
                      <span class="dim-val">{{ (row.ratingScore as number).toFixed(0) }}</span>
                    </div>
                    <div class="dim-bar-row">
                      <span class="dim-lbl">负</span>
                      <el-progress :percentage="(row.loadScore as number)" :stroke-width="8" :show-text="false" color="#f56c6c" style="flex:1" />
                      <span class="dim-val">{{ (row.loadScore as number).toFixed(0) }}</span>
                    </div>
                    <div class="dim-bar-row">
                      <span class="dim-lbl">时</span>
                      <el-progress :percentage="(row.timeScore as number)" :stroke-width="8" :show-text="false" color="#909399" style="flex:1" />
                      <span class="dim-val">{{ (row.timeScore as number).toFixed(0) }}</span>
                    </div>
                  </div>
                </template>
              </el-table-column>
              <el-table-column label="距离(km)" width="100" align="center">
                <template #default="{ row }">{{ row.distanceKm ? (row.distanceKm as number).toFixed(2) : '—' }}</template>
              </el-table-column>
              <el-table-column label="状态" width="80" align="center">
                <template #default="{ row }">
                  <el-tag :type="row.eligible ? 'success' : 'danger'" size="small">
                    {{ row.eligible ? '合格' : '不合格' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="rejectReason" label="原因" min-width="150" show-overflow-tooltip />
            </el-table>
          </template>
        </el-card>
      </el-tab-pane>

      <!-- 服务区域 -->
      <el-tab-pane label="服务区域" name="zone">
        <el-card>
          <div class="toolbar">
            <el-button type="primary" @click="openZoneDialog()">+ 新增区域</el-button>
            <el-button @click="loadZones">刷新</el-button>
          </div>
          <el-table :data="zones" v-loading="loading" border stripe>
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="zoneCode" label="编码" width="120" />
            <el-table-column prop="zoneName" label="名称" width="150" />
            <el-table-column prop="zoneType" label="类型" width="90">
              <template #default="{ row }">
                <el-tag size="small">{{ row.zoneType === 'CIRCLE' ? '圆形' : '多边形' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="中心点" width="160">
              <template #default="{ row }">{{ row.centerLat }}, {{ row.centerLng }}</template>
            </el-table-column>
            <el-table-column label="半径" width="80">
              <template #default="{ row }">{{ row.radiusKm }} km</template>
            </el-table-column>
            <el-table-column label="上门费" width="200">
              <template #default="{ row }">
                基础 ¥{{ row.baseFee }} + ¥{{ row.serviceFeePerKm }}/km
                · 范围 ¥{{ row.minFee }}~¥{{ row.maxFee }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="70">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
                  {{ row.enabled ? '启用' : '停用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button link size="small" @click="editZone(row)">编辑</el-button>
                <el-button link size="small" type="danger" @click="deleteZone(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 路线规划 -->
      <el-tab-pane label="路线规划" name="route">
        <el-card>
          <el-form :model="routeForm" inline style="margin-bottom:12px">
            <el-form-item label="技师">
              <el-select v-model="routeForm.techId" placeholder="选择技师" style="width:180px">
                <el-option v-for="t in techs" :key="t.id" :label="t.name" :value="t.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="日期">
              <el-date-picker v-model="routeForm.date" type="date" value-format="YYYY-MM-DD" style="width:150px" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadRoute" :loading="routing">查询路线</el-button>
              <el-button @click="generateRoute" :loading="routing" v-if="routeForm.techId">生成优化路线</el-button>
            </el-form-item>
          </el-form>

          <template v-if="currentRoute">
            <el-divider />
            <el-row :gutter="12" class="route-summary">
              <el-col :span="4"><el-statistic title="订单数" :value="currentRoute.totalOrders" /></el-col>
              <el-col :span="4"><el-statistic title="总里程" :value="(currentRoute.totalDistanceKm as number).toFixed(1) + ' km'" /></el-col>
              <el-col :span="4"><el-statistic title="行驶分钟" :value="currentRoute.totalTravelMinutes + ' min'" /></el-col>
              <el-col :span="4"><el-statistic title="总服务分钟" :value="currentRoute.totalServiceMinutes + ' min'" /></el-col>
              <el-col :span="4">
                <el-statistic title="节省里程" :value="(currentRoute.gainKmVsRandom as number[])?.[0]?.toFixed(1) + ' km'" />
              </el-col>
            </el-row>

            <el-table :data="(currentRoute.nodes || []) as any[]" border stripe size="small">
              <el-table-column label="顺序" width="70" align="center">
                <template #default="{ row }">
                  <el-tag type="primary" size="small">{{ row.visitOrder }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="orderNo" label="订单号" width="160" />
              <el-table-column label="位置" min-width="200">
                <template #default="{ row }">{{ row.lat }}, {{ row.lng }}</template>
              </el-table-column>
              <el-table-column label="距上站" width="90" align="center">
                <template #default="{ row }">{{ (row.distanceFromPrev as number).toFixed(2) }} km</template>
              </el-table-column>
              <el-table-column label="行驶分钟" width="100" align="center">
                <template #default="{ row }">{{ row.travelMinutesFromPrev }} min</template>
              </el-table-column>
              <el-table-column label="预计到达" width="170">
                <template #default="{ row }">{{ row.etaArrival }}</template>
              </el-table-column>
              <el-table-column label="预计完成" width="170">
                <template #default="{ row }">{{ row.etaComplete }}</template>
              </el-table-column>
            </el-table>
          </template>
          <el-empty v-else description="请先选择技师和日期，查询路线" />
        </el-card>
      </el-tab-pane>

      <!-- 派单日志 -->
      <el-tab-pane label="派单日志" name="log">
        <el-card>
          <div class="toolbar">
            <el-select v-model="logFilter.limit" style="width:100px" @change="loadLogs">
              <el-option :value="20" label="20条" />
              <el-option :value="50" label="50条" />
              <el-option :value="100" label="100条" />
            </el-select>
            <el-button @click="loadLogs">刷新</el-button>
          </div>
          <el-table :data="dispatchLogs" v-loading="loading" border stripe size="small">
            <el-table-column prop="id" label="ID" width="60" />
            <el-table-column prop="orderNo" label="订单号" width="160" />
            <el-table-column prop="triggerSource" label="触发" width="80">
              <template #default="{ row }">
                <el-tag size="small">{{ row.triggerSource }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="techName" label="选中技师" width="110" />
            <el-table-column label="综合分" width="80" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.chosenScore" size="small" :type="(row.chosenScore as number) >= 70 ? 'success' : 'warning'">
                  {{ (row.chosenScore as number).toFixed(1) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="维度明细" min-width="220">
              <template #default="{ row }">
                <template v-if="row.breakdownJson">
                  <span class="dim-pill" title="技能分">技 {{ ((row.breakdownJson as any)?.skill || 0).toFixed(0) }}</span>
                  <span class="dim-pill" title="距离分">距 {{ ((row.breakdownJson as any)?.distance || 0).toFixed(0) }}</span>
                  <span class="dim-pill" title="评分分">评 {{ ((row.breakdownJson as any)?.rating || 0).toFixed(0) }}</span>
                  <span class="dim-pill" title="负荷分">负 {{ ((row.breakdownJson as any)?.load || 0).toFixed(0) }}</span>
                  <span class="dim-pill" title="时间窗分">时 {{ ((row.breakdownJson as any)?.time || 0).toFixed(0) }}</span>
                </template>
                <span v-else>—</span>
              </template>
            </el-table-column>
            <el-table-column prop="decision" label="决策" width="90">
              <template #default="{ row }">
                <el-tag :type="row.decision === 'DISPATCHED' ? 'success' : 'danger'" size="small">
                  {{ row.decision === 'DISPATCHED' ? '已派' : '拒绝' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="rejectReason" label="拒绝原因" min-width="150" show-overflow-tooltip />
            <el-table-column label="耗时" width="80" align="center">
              <template #default="{ row }">{{ row.durationMs }}ms</template>
            </el-table-column>
            <el-table-column prop="createTime" label="时间" width="170" />
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- 派单配置 -->
      <el-tab-pane label="派单配置" name="config">
        <el-card>
          <el-alert type="info" :closable="false" style="margin-bottom:12px">
            5 个维度权重之和必须为 1.0（±0.01），保存后立即生效。
          </el-alert>
          <el-form :model="configForm" label-width="180px" style="max-width:720px">
            <el-divider content-position="left">5 维度权重</el-divider>
            <el-form-item label="距离权重（distance）">
              <el-slider v-model="configForm.wDistance" :min="0" :max="1" :step="0.05" show-input input-size="small" />
            </el-form-item>
            <el-form-item label="技能匹配权重（skill）">
              <el-slider v-model="configForm.wSkill" :min="0" :max="1" :step="0.05" show-input input-size="small" />
            </el-form-item>
            <el-form-item label="技师评分权重（rating）">
              <el-slider v-model="configForm.wRating" :min="0" :max="1" :step="0.05" show-input input-size="small" />
            </el-form-item>
            <el-form-item label="负荷权重（load）">
              <el-slider v-model="configForm.wLoad" :min="0" :max="1" :step="0.05" show-input input-size="small" />
            </el-form-item>
            <el-form-item label="时间窗权重（time）">
              <el-slider v-model="configForm.wTime" :min="0" :max="1" :step="0.05" show-input input-size="small" />
            </el-form-item>
            <el-alert v-if="weightSumValid" type="success" :closable="false">
              权重和 = {{ (configForm.wDistance + configForm.wSkill + configForm.wRating + configForm.wLoad + configForm.wTime).toFixed(2) }} ✅
            </el-alert>
            <el-alert v-else type="warning" :closable="false">
              权重和 = {{ (configForm.wDistance + configForm.wSkill + configForm.wRating + configForm.wLoad + configForm.wTime).toFixed(2) }}，应为 1.0
            </el-alert>
            <el-divider content-position="left">策略参数</el-divider>
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="派单最低分阈值">
                  <el-input-number v-model="configForm.minDispatchScore" :min="0" :max="100" :step="5" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="单技师日最大订单">
                  <el-input-number v-model="configForm.maxOrdersPerDay" :min="1" :max="50" style="width:100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="12">
              <el-col :span="12">
                <el-form-item label="默认服务时长(分钟)">
                  <el-input-number v-model="configForm.defaultServiceMinutes" :min="15" :max="480" :step="15" style="width:100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="订单缓冲(分钟)">
                  <el-input-number v-model="configForm.bufferMinutes" :min="0" :max="60" :step="5" style="width:100%" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item>
              <el-button type="primary" @click="saveConfig" :loading="savingConfig">保存配置</el-button>
              <el-button @click="loadConfig">重置</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-tab-pane>

      <!-- 订单列表（保留） -->
      <el-tab-pane label="上门订单" name="orders">
        <el-card>
          <div class="toolbar">
            <el-select v-model="orderFilter.status" placeholder="派单状态" clearable style="width:140px" @change="loadOrders">
              <el-option label="待派单" value="PENDING" />
              <el-option label="已派单" value="DISPATCHED" />
              <el-option label="已接单" value="ACCEPTED" />
              <el-option label="已到达" value="ARRIVED" />
              <el-option label="服务中" value="SERVING" />
              <el-option label="已完成" value="COMPLETED" />
              <el-option label="已取消" value="CANCELLED" />
            </el-select>
            <el-button @click="loadOrders">刷新</el-button>
          </div>
          <el-table :data="orders" v-loading="loading" border stripe>
            <el-table-column prop="orderNo" label="订单号" width="170" />
            <el-table-column prop="memberName" label="会员" width="100" />
            <el-table-column prop="serviceName" label="服务" width="160" />
            <el-table-column prop="address" label="地址" min-width="200" show-overflow-tooltip />
            <el-table-column label="坐标" width="150">
              <template #default="{ row }">{{ row.lat }}, {{ row.lng }}</template>
            </el-table-column>
            <el-table-column prop="scheduledTime" label="预约时间" width="170" />
            <el-table-column label="派单分" width="80" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.dispatchScore" size="small">{{ (row.dispatchScore as number).toFixed(1) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="dispatchStatus" label="状态" width="90">
              <template #default="{ row }">
                <el-tag size="small">{{ orderStatusLabel(row.dispatchStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="技师" width="100">
              <template #default="{ row }">
                <span v-if="row.techName">{{ row.techName }}</span>
                <span v-else style="color:#c0c4cc">—</span>
              </template>
            </el-table-column>
            <el-table-column label="路线序" width="70" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.routeSeq" size="small" type="info">#{{ row.routeSeq }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="ETA" width="70" align="center">
              <template #default="{ row }">{{ row.etaMinutes ? row.etaMinutes + 'min' : '—' }}</template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button link size="small" @click="changeOrderStatus(row)" :disabled="row.dispatchStatus === 'COMPLETED' || row.dispatchStatus === 'CANCELLED'">改状态</el-button>
                <el-button link size="small" type="warning" @click="reassignOrder(row)" :disabled="row.dispatchStatus === 'COMPLETED' || row.dispatchStatus === 'CANCELLED'">重新派单</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 注册技师弹窗 -->
    <el-dialog v-model="techDialog.visible" title="注册上门技师" width="480px">
      <el-form :model="techDialog.form" label-width="100px">
        <el-form-item label="技师 ID" required><el-input-number v-model="techDialog.form.technicianId" :min="1" /></el-form-item>
        <el-form-item label="姓名"><el-input v-model="techDialog.form.name" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="techDialog.form.phone" /></el-form-item>
        <el-form-item label="评分">
          <el-input-number v-model="techDialog.form.rating" :min="1" :max="5" :step="0.1" :precision="1" />
        </el-form-item>
        <el-form-item label="最大半径(km)"><el-input-number v-model="techDialog.form.maxRadiusKm" :min="1" :max="50" /></el-form-item>
        <el-form-item label="技能标签"><el-input v-model="techDialog.form.skillTags" placeholder="逗号分隔，如：MANICURE,PEDICURE" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="techDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitTech" :loading="techDialog.loading">注册</el-button>
      </template>
    </el-dialog>

    <!-- 技能管理弹窗 -->
    <el-dialog v-model="skillDialog.visible" title="技师技能管理" width="520px">
      <el-table :data="skillDialog.skills" border size="small" max-height="300">
        <el-table-column prop="skillCode" label="技能编码" width="160" />
        <el-table-column prop="skillName" label="名称" width="120" />
        <el-table-column prop="skillLevel" label="等级" width="80">
          <template #default="{ row }">{{ row.skillLevel }} / 5</template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button link size="small" type="danger" @click="removeSkill(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-divider />
      <h4>添加技能</h4>
      <el-form :model="skillDialog.addForm" inline>
        <el-form-item label="技能编码"><el-input v-model="skillDialog.addForm.code" placeholder="如：MANICURE" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="skillDialog.addForm.name" placeholder="如：美甲" /></el-form-item>
        <el-form-item label="等级">
          <el-input-number v-model="skillDialog.addForm.level" :min="1" :max="5" />
        </el-form-item>
        <el-form-item><el-button type="primary" @click="addSkill">添加</el-button></el-form-item>
      </el-form>
    </el-dialog>

    <!-- 上报位置弹窗 -->
    <el-dialog v-model="locDialog.visible" title="上报实时位置" width="400px">
      <el-form :model="locDialog.form" label-width="80px">
        <el-form-item label="纬度" required><el-input-number v-model="locDialog.form.lat" :min="-90" :max="90" :precision="6" /></el-form-item>
        <el-form-item label="经度" required><el-input-number v-model="locDialog.form.lng" :min="-180" :max="180" :precision="6" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="locDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitLoc" :loading="locDialog.loading">上报</el-button>
      </template>
    </el-dialog>

    <!-- 切换状态弹窗 -->
    <el-dialog v-model="statusDialog.visible" title="切换技师状态" width="400px">
      <el-form label-width="80px">
        <el-form-item label="当前"><el-tag>{{ onlineLabel(statusDialog.target?.onlineStatus) }}</el-tag></el-form-item>
        <el-form-item label="新状态">
          <el-radio-group v-model="statusDialog.newStatus">
            <el-radio-button value="ONLINE">在线</el-radio-button>
            <el-radio-button value="OFFLINE">离线</el-radio-button>
            <el-radio-button value="BUSY">繁忙</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="statusDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitStatus" :loading="statusDialog.loading">切换</el-button>
      </template>
    </el-dialog>

    <!-- 路线弹窗 -->
    <el-dialog v-model="routeDialog.visible" title="技师路线" width="800px" top="3vh">
      <el-form inline>
        <el-form-item label="日期"><el-date-picker v-model="routeDialog.date" type="date" value-format="YYYY-MM-DD" style="width:150px" @change="loadTechRoute" /></el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadTechRoute">查询</el-button>
          <el-button @click="genTechRoute" :loading="routing">生成优化路线</el-button>
        </el-form-item>
      </el-form>
      <template v-if="routeDialog.route">
        <el-divider />
        <el-row :gutter="8" class="route-summary">
          <el-col :span="4"><el-statistic title="订单" :value="(routeDialog.route as any).totalOrders" /></el-col>
          <el-col :span="4"><el-statistic title="总里程" :value="((routeDialog.route as any).totalDistanceKm as number).toFixed(1) + 'km'" /></el-col>
          <el-col :span="4"><el-statistic title="行驶分钟" :value="(routeDialog.route as any).totalTravelMinutes + 'min'" /></el-col>
          <el-col :span="4"><el-statistic title="节省" :value="(((routeDialog.route as any).gainKmVsRandom as number[])?.[0] || 0).toFixed(1) + 'km'" /></el-col>
        </el-row>
        <el-table :data="(routeDialog.route as any).nodes || []" border stripe size="small" max-height="400">
          <el-table-column label="#" width="50" align="center">
            <template #default="{ row }"><el-tag type="primary" size="small">{{ row.visitOrder }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="orderNo" label="订单号" width="160" />
          <el-table-column label="位置" min-width="180">{{ (r: any) => r.lat + ', ' + r.lng }}</el-table-column>
          <el-table-column label="距上站" width="90">{{ (r: any) => (r.distanceFromPrev as number).toFixed(2) + 'km' }}</el-table-column>
          <el-table-column label="ETA到达" width="170">{{ (r: any) => r.etaArrival }}</el-table-column>
          <el-table-column label="ETA完成" width="170">{{ (r: any) => r.etaComplete }}</el-table-column>
        </el-table>
      </template>
      <el-empty v-else description="暂无路线数据" />
    </el-dialog>

    <!-- 区域编辑弹窗 -->
    <el-dialog v-model="zoneDialog.visible" :title="zoneDialog.form.id ? '编辑区域' : '新增区域'" width="520px">
      <el-form :model="zoneDialog.form" label-width="120px">
        <el-form-item label="区域编码" required><el-input v-model="zoneDialog.form.zoneCode" /></el-form-item>
        <el-form-item label="区域名称" required><el-input v-model="zoneDialog.form.zoneName" /></el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="zoneDialog.form.zoneType">
            <el-radio-button value="CIRCLE">圆形</el-radio-button>
            <el-radio-button value="POLYGON">多边形</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="中心纬度"><el-input-number v-model="zoneDialog.form.centerLat" :min="-90" :max="90" :precision="6" style="width:100%" /></el-form-item>
        <el-form-item label="中心经度"><el-input-number v-model="zoneDialog.form.centerLng" :min="-180" :max="180" :precision="6" style="width:100%" /></el-form-item>
        <el-form-item label="半径(km)"><el-input-number v-model="zoneDialog.form.radiusKm" :min="0.1" :max="200" :precision="1" style="width:100%" /></el-form-item>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="基础上门费"><el-input-number v-model="zoneDialog.form.baseFee" :min="0" :precision="2" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="每公里费"><el-input-number v-model="zoneDialog.form.serviceFeePerKm" :min="0" :precision="2" style="width:100%" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="最低上门费"><el-input-number v-model="zoneDialog.form.minFee" :min="0" :precision="2" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="最高上门费"><el-input-number v-model="zoneDialog.form.maxFee" :min="0" :precision="2" style="width:100%" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="zoneDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="saveZone" :loading="zoneDialog.saving">保存</el-button>
      </template>
    </el-dialog>

    <!-- 改订单状态弹窗 -->
    <el-dialog v-model="orderStatusDialog.visible" title="更新订单状态" width="400px">
      <el-form label-width="80px">
        <el-form-item label="当前状态"><el-tag>{{ orderStatusLabel(orderStatusDialog.currentStatus) }}</el-tag></el-form-item>
        <el-form-item label="新状态">
          <el-select v-model="orderStatusDialog.newStatus" style="width:100%">
            <el-option label="已派单" value="DISPATCHED" />
            <el-option label="已接单" value="ACCEPTED" />
            <el-option label="已到达" value="ARRIVED" />
            <el-option label="服务中" value="SERVING" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="orderStatusDialog.visible = false">取消</el-button>
        <el-button type="primary" @click="submitOrderStatus" :loading="orderStatusDialog.loading">更新</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { lbsApi } from '@/api/lbs'

const loading = ref(false)
const dispatching = ref(false)
const previewing = ref(false)
const routing = ref(false)
const savingConfig = ref(false)
const activeTab = ref('tech')

const techs = ref<any[]>([])
const techFilter = reactive({ status: '' as string })
const orders = ref<any[]>([])
const orderFilter = reactive({ status: '' as string })
const recentDispatches = ref<any[]>([])
const zones = ref<any[]>([])
const dispatchLogs = ref<any[]>([])
const logFilter = reactive({ limit: 50 as number })
const dash = reactive<any>({})
const previewResult = reactive<any>({})

const dispatchForm = reactive({
  memberId: null as number | null, memberName: '', memberPhone: '',
  serviceId: 1, serviceName: '', skillTags: '', address: '',
  lat: 39.9042, lng: 116.4074, amount: 0, scheduledTime: null as Date | null
})

const previewForm = reactive({
  lat: 39.9042, lng: 116.4074, skillTags: '', scheduledTime: null as Date | null
})

const routeForm = reactive({ techId: null as number | null, date: '' })
const currentRoute = ref<any>(null)

const configForm = reactive({
  wDistance: 0.35, wSkill: 0.25, wRating: 0.15, wLoad: 0.15, wTime: 0.10,
  minDispatchScore: 40, maxOrdersPerDay: 8, defaultServiceMinutes: 90, bufferMinutes: 15
})

const techDialog = reactive({
  visible: false, loading: false,
  form: { technicianId: null as number | null, name: '', phone: '', rating: 5.0, maxRadiusKm: 10, skillTags: '' }
})
const skillDialog = reactive({
  visible: false, loading: false, techId: null as number | null, skills: [] as any[],
  addForm: { code: '', name: '', level: 3 }
})
const locDialog = reactive({ visible: false, loading: false, form: { lat: 39.9042, lng: 116.4074 } })
const statusDialog = reactive({ visible: false, loading: false, target: null as any, newStatus: 'ONLINE' as string })
const routeDialog = reactive({ visible: false, loading: false, techId: null as number | null, date: '', route: null as any })
const zoneDialog = reactive({ visible: false, saving: false, form: {} as any })
const orderStatusDialog = reactive({ visible: false, loading: false, orderId: null as number | null, currentStatus: '', newStatus: 'COMPLETED' as string })

const filteredTechs = computed(() => techFilter.status ? techs.value.filter(t => t.onlineStatus === techFilter.status) : techs.value)

const weightSumValid = computed(() => {
  const sum = configForm.wDistance + configForm.wSkill + configForm.wRating + configForm.wLoad + configForm.wTime
  return Math.abs(sum - 1.0) <= 0.01
})

function onlineLabel(s?: string) {
  const m: Record<string, string> = { ONLINE: '在线', OFFLINE: '离线', BUSY: '繁忙' }
  return m[s || ''] || s || '—'
}
function orderStatusLabel(s?: string) {
  const m: Record<string, string> = { PENDING: '待派单', DISPATCHED: '已派单', ACCEPTED: '已接单', ARRIVED: '已到达', SERVING: '服务中', COMPLETED: '已完成', CANCELLED: '已取消', REJECTED: '已拒绝' }
  return m[s || ''] || s || '—'
}

async function loadDashboard() {
  try { const r: any = await lbsApi.dashboard(); Object.assign(dash, r.data || {}) } catch {}
}
async function loadTechs() {
  loading.value = true
  try { const r: any = await lbsApi.listTechs(); techs.value = r.data || [] } finally { loading.value = false }
}
async function loadOrders() {
  loading.value = true
  try {
    const params: any = {}
    if (orderFilter.status) params.status = orderFilter.status
    const r: any = await lbsApi.listOrders(params)
    orders.value = r.data || []
  } finally { loading.value = false }
}
async function loadZones() {
  loading.value = true
  try { const r: any = await lbsApi.listZones(); zones.value = r.data || [] } finally { loading.value = false }
}
async function loadLogs() {
  loading.value = true
  try { const r: any = await lbsApi.dispatchLog({ limit: logFilter.limit }); dispatchLogs.value = r.data || [] } finally { loading.value = false }
}
async function loadConfig() {
  try {
    const r: any = await lbsApi.getConfig()
    if (r.data) {
      configForm.wDistance = r.data.wDistance || 0.35
      configForm.wSkill = r.data.wSkill || 0.25
      configForm.wRating = r.data.wRating || 0.15
      configForm.wLoad = r.data.wLoad || 0.15
      configForm.wTime = r.data.wTime || 0.10
      configForm.minDispatchScore = r.data.minDispatchScore || 40
      configForm.maxOrdersPerDay = r.data.maxOrdersPerDay || 8
      configForm.defaultServiceMinutes = r.data.defaultServiceMinutes || 90
      configForm.bufferMinutes = r.data.bufferMinutes || 15
    }
  } catch {}
}
function loadAll() { loadTechs(); loadOrders(); loadDashboard() }
function onTabChange(tab: string) {
  if (tab === 'orders') loadOrders()
  if (tab === 'tech') loadTechs()
  if (tab === 'zone') loadZones()
  if (tab === 'log') loadLogs()
  if (tab === 'route') { loadTechs(); routeForm.date = new Date().toISOString().slice(0, 10) }
}

function openTechDialog() {
  techDialog.form = { technicianId: null, name: '', phone: '', rating: 5.0, maxRadiusKm: 10, skillTags: '' }
  techDialog.visible = true
}
async function submitTech() {
  if (!techDialog.form.technicianId) { ElMessage.warning('请填写技师 ID'); return }
  techDialog.loading = true
  try {
    await lbsApi.registerTech(techDialog.form)
    ElMessage.success('已注册')
    techDialog.visible = false
    loadTechs()
  } finally { techDialog.loading = false }
}

function openLocDialog(row: any) {
  locDialog.form = {
    lat: row.currentLat ? Number(row.currentLat) : 39.9042,
    lng: row.currentLng ? Number(row.currentLng) : 116.4074
  }
  ;(locDialog as any)._targetId = row.id
  locDialog.visible = true
}
async function submitLoc() {
  const targetId = (locDialog as any)._targetId
  if (!targetId) return
  locDialog.loading = true
  try {
    await lbsApi.updateTechLocation(targetId, locDialog.form)
    ElMessage.success('位置已上报，技师已设为在线')
    locDialog.visible = false
    loadTechs()
  } finally { locDialog.loading = false }
}

function openStatusDialog(row: any) {
  statusDialog.target = row
  statusDialog.newStatus = row.onlineStatus || 'ONLINE'
  statusDialog.visible = true
}
async function submitStatus() {
  statusDialog.loading = true
  try {
    await lbsApi.updateTechStatus(statusDialog.target.id, { onlineStatus: statusDialog.newStatus })
    ElMessage.success('状态已切换')
    statusDialog.visible = false
    loadTechs()
  } finally { statusDialog.loading = false }
}

async function openSkillDialog(row: any) {
  skillDialog.techId = row.id
  skillDialog.skills = []
  skillDialog.addForm = { code: '', name: '', level: 3 }
  try {
    const r: any = await lbsApi.getTech(row.id)
    skillDialog.skills = (r.data?.skills || [])
  } catch {}
  skillDialog.visible = true
}
async function addSkill() {
  if (!skillDialog.addForm.code) { ElMessage.warning('请填技能编码'); return }
  try {
    await lbsApi.addTechSkill(skillDialog.techId, skillDialog.addForm)
    ElMessage.success('技能已添加')
    openSkillDialog({ id: skillDialog.techId })
  } catch {}
}
async function removeSkill(row: any) {
  await lbsApi.removeTechSkill(row.id)
  ElMessage.success('技能已移除')
  openSkillDialog({ id: skillDialog.techId })
}

function openRouteDialog(row: any) {
  routeDialog.techId = row.id
  routeDialog.date = new Date().toISOString().slice(0, 10)
  routeDialog.route = null
  routeDialog.visible = true
  loadTechRoute()
}
async function loadTechRoute() {
  if (!routeDialog.techId) return
  routing.value = true
  try {
    const r: any = await lbsApi.getTechRoute(routeDialog.techId, routeDialog.date)
    routeDialog.route = r.data?.route || null
  } catch { routeDialog.route = null } finally { routing.value = false }
}
async function genTechRoute() {
  if (!routeDialog.techId) return
  routing.value = true
  try {
    const r: any = await lbsApi.generateTechRoute(routeDialog.techId, { date: routeDialog.date })
    ElMessage.success('路线已生成')
    routeDialog.route = r.data?.route || null
  } finally { routing.value = false }
}

function openZoneDialog() {
  zoneDialog.form = { zoneType: 'CIRCLE', centerLat: 39.9042, centerLng: 116.4074, radiusKm: 10, baseFee: 30, serviceFeePerKm: 2, minFee: 30, maxFee: 200 }
  zoneDialog.visible = true
}
function editZone(row: any) { zoneDialog.form = { ...row }; zoneDialog.visible = true }
async function saveZone() {
  zoneDialog.saving = true
  try {
    if (zoneDialog.form.id) { await lbsApi.updateZone(zoneDialog.form.id, zoneDialog.form); ElMessage.success('已更新') }
    else { await lbsApi.createZone(zoneDialog.form); ElMessage.success('已创建') }
    zoneDialog.visible = false
    loadZones()
  } finally { zoneDialog.saving = false }
}
async function deleteZone(row: any) {
  await lbsApi.deleteZone(row.id)
  ElMessage.success('已删除')
  loadZones()
}

async function loadRoute() {
  if (!routeForm.techId) { ElMessage.warning('请选择技师'); return }
  routing.value = true
  try {
    const r: any = await lbsApi.getTechRoute(routeForm.techId, routeForm.date)
    currentRoute.value = r.data?.route || null
  } catch { currentRoute.value = null } finally { routing.value = false }
}
async function generateRoute() {
  if (!routeForm.techId) return
  routing.value = true
  try {
    const r: any = await lbsApi.generateTechRoute(routeForm.techId, { date: routeForm.date })
    ElMessage.success('路线已生成')
    currentRoute.value = r.data?.route || null
  } finally { routing.value = false }
}

function changeOrderStatus(row: any) {
  orderStatusDialog.orderId = row.id
  orderStatusDialog.currentStatus = row.dispatchStatus
  orderStatusDialog.newStatus = 'COMPLETED'
  orderStatusDialog.visible = true
}
async function submitOrderStatus() {
  orderStatusDialog.loading = true
  try {
    await lbsApi.updateOrderStatus(orderStatusDialog.orderId, { status: orderStatusDialog.newStatus })
    ElMessage.success('状态已更新')
    orderStatusDialog.visible = false
    loadOrders()
  } finally { orderStatusDialog.loading = false }
}
async function reassignOrder(row: any) {
  try {
    const r: any = await lbsApi.reassign(row.id)
    const d = r.data || {}
    if (d.success) ElMessage.success('已重新派单：' + d.technicianName)
    else ElMessage.warning('重新派单失败：' + d.reason)
    loadOrders()
  } catch { ElMessage.error('重新派单失败') }
}

function fillTestData() {
  dispatchForm.memberId = 10001; dispatchForm.memberName = '张女士'
  dispatchForm.memberPhone = '13800138000'; dispatchForm.serviceId = 1
  dispatchForm.serviceName = '上门美甲'; dispatchForm.address = '北京市朝阳区 XX 路 1 号'
  dispatchForm.lat = 39.9042; dispatchForm.lng = 116.4074; dispatchForm.amount = 268
  dispatchForm.skillTags = 'MANICURE,PEDICURE'
  dispatchForm.scheduledTime = new Date(Date.now() + 60 * 60 * 1000)
}
function fillPreviewTest() {
  previewForm.lat = 39.9142; previewForm.lng = 116.4174
  previewForm.skillTags = 'MANICURE'; previewForm.scheduledTime = new Date(Date.now() + 2 * 60 * 60 * 1000)
}
async function submitDispatch() {
  if (!dispatchForm.memberId || !dispatchForm.serviceId || !dispatchForm.scheduledTime) {
    ElMessage.warning('请填写必填项')
    return
  }
  dispatching.value = true
  try {
    const tags = dispatchForm.skillTags ? dispatchForm.skillTags.split(',').map(s => s.trim()) : []
    const res: any = await lbsApi.dispatch({
      memberId: dispatchForm.memberId, memberName: dispatchForm.memberName,
      memberPhone: dispatchForm.memberPhone, serviceId: dispatchForm.serviceId,
      serviceName: dispatchForm.serviceName, skillTags: tags, address: dispatchForm.address,
      lat: dispatchForm.lat, lng: dispatchForm.lng, amount: dispatchForm.amount,
      scheduledTime: dispatchForm.scheduledTime.toISOString().slice(0, 19).replace('T', ' ')
    })
    const d = res.data || {}
    if (d.success) {
      ElMessage.success(`已派单：${d.technicianName}，距离 ${d.distanceKm} km，综合分 ${(d.totalScore as number).toFixed(1)}，上门费 ¥${d.travelFee}，ETA ${d.etaMinutes}min`)
    } else {
      ElMessage.warning(`派单失败：${d.reason}`)
    }
    recentDispatches.value.unshift(d)
    if (recentDispatches.value.length > 20) recentDispatches.value.pop()
    loadOrders(); loadDashboard()
  } finally { dispatching.value = false }
}
async function runPreview() {
  previewing.value = true
  try {
    const tags = previewForm.skillTags ? previewForm.skillTags.split(',').map(s => s.trim()) : []
    const res: any = await lbsApi.preview({
      lat: previewForm.lat, lng: previewForm.lng, skillTags: tags,
      scheduledTime: previewForm.scheduledTime ? previewForm.scheduledTime.toISOString().slice(0, 19).replace('T', ' ') : null
    })
    Object.assign(previewResult, res.data || {})
  } finally { previewing.value = false }
}
async function saveConfig() {
  if (!weightSumValid.value) { ElMessage.warning('权重之和必须为 1.0'); return }
  savingConfig.value = true
  try {
    await lbsApi.updateConfig(configForm)
    ElMessage.success('配置已保存，立即生效')
  } finally { savingConfig.value = false }
}

onMounted(() => { loadAll(); loadConfig() })
</script>

<style scoped>
.lbs-page { padding: 16px; }
.kpi-card { margin-bottom: 12px; }
.kpi-item { text-align: center; }
.kpi-value { font-size: 28px; font-weight: 700; line-height: 1.2; }
.kpi-label { font-size: 12px; color: #909399; margin-top: 4px; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.title { font-size: 16px; font-weight: 600; }
.toolbar { display: flex; gap: 8px; margin-bottom: 12px; align-items: center; }
.mini-dims { display: flex; gap: 4px; flex-wrap: wrap; }
.dim-pill {
  display: inline-block; font-size: 10px; padding: 1px 4px;
  background: #f0f2f5; border-radius: 3px; color: #606266;
}
.dim-bars { display: flex; flex-direction: column; gap: 3px; }
.dim-bar-row { display: flex; align-items: center; gap: 6px; }
.dim-lbl { font-size: 11px; color: #909399; width: 14px; text-align: right; flex-shrink: 0; }
.dim-val { font-size: 11px; color: #606266; width: 22px; flex-shrink: 0; }
.route-summary { margin-bottom: 12px; }
.preview-summary { display: flex; align-items: center; margin-bottom: 10px; }
</style>
