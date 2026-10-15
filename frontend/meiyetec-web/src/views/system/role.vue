<template>
  <div class="role-page">
    <!-- 统计卡片 -->
    <el-row :gutter="12" class="stat-row">
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <el-icon class="stat-icon" style="background:#409EFF"><User /></el-icon>
            <div>
              <div class="stat-value">{{ totalRoles }}</div>
              <div class="stat-label">角色总数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <el-icon class="stat-icon" style="background:#67C23A"><CircleCheck /></el-icon>
            <div>
              <div class="stat-value">{{ enabledRoles }}</div>
              <div class="stat-label">启用中</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <el-icon class="stat-icon" style="background:#E6A23C"><Lock /></el-icon>
            <div>
              <div class="stat-value">{{ platformRoles }}</div>
              <div class="stat-label">平台级</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <el-icon class="stat-icon" style="background:#909399"><OfficeBuilding /></el-icon>
            <div>
              <div class="stat-value">{{ tenantRoles }}</div>
              <div class="stat-label">租户级</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 操作工具栏 -->
    <el-card style="margin-top:12px">
      <template #header>
        <div class="toolbar">
          <span class="toolbar-title">角色列表</span>
          <div style="flex:1"></div>
          <el-button type="primary" @click="openAdd"><el-icon><Plus /></el-icon>新增角色</el-button>
        </div>
      </template>
      <el-table :data="tableData" stripe v-loading="loading">
        <el-table-column label="角色" min-width="180">
          <template #default="{ row }">
            <div style="display:flex;align-items:center;gap:8px">
              <el-tag :type="row.tenantId ? 'primary' : 'warning'" size="small">
                {{ row.tenantId ? '租户级' : '平台级' }}
              </el-tag>
              <div>
                <div style="font-weight:600">{{ row.roleName }}</div>
                <div style="font-size:12px;color:#909399">{{ row.roleCode }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="success" @click="openPermission(row)">分配权限</el-button>
            <el-button
              v-if="!builtIn(row)"
              link type="danger"
              @click="handleDelete(row)"
            >删除</el-button>
            <el-tag v-else type="info" size="small">内置</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="formVisible" :title="isEdit ? '编辑角色' : '新增角色'" width="520">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100">
        <el-form-item label="角色编码" prop="roleCode">
          <el-input v-model="form.roleCode" placeholder="如 ROLE_MANAGER" :disabled="isEdit" />
        </el-form-item>
        <el-form-item label="角色名称" prop="roleName">
          <el-input v-model="form.roleName" placeholder="如 门店管理员" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="描述角色职责..." />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确定</el-button>
      </template>
    </el-dialog>

    <!-- 权限分配弹窗 -->
    <el-dialog v-model="permVisible" :title="`分配权限 — ${currentRole?.roleName}`" width="640">
      <div v-if="loadingPerms" style="text-align:center;padding:40px">
        <el-icon class="is-loading" :size="24"><Loading /></el-icon> 加载中...
      </div>
      <div v-else>
        <div class="perm-tip">
          <el-icon><InfoFilled /></el-icon>
          勾选该角色拥有的权限。取消勾选将移除对应权限。
        </div>
        <el-tree
          ref="permTreeRef"
          :data="permTree"
          :props="{ label: 'title', children: 'children' }"
          show-checkbox
          node-key="id"
          default-expand-all
          style="max-height:480px;overflow-y:auto"
        >
          <template #default="{ data }">
            <span class="perm-node">
              <span>{{ data.title }}</span>
              <el-tag size="small" type="info" style="margin-left:6px">{{ data.permissionCode }}</el-tag>
            </span>
          </template>
        </el-tree>
      </div>
      <template #footer>
        <el-button @click="permVisible = false">取消</el-button>
        <el-button type="primary" @click="handleAssignPerm" :loading="savingPerm">保存权限</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, User, Lock, CircleCheck, OfficeBuilding, Loading, InfoFilled } from '@element-plus/icons-vue'
import { rbacApi } from '@/api/rbac'

const loading = ref(false)
const tableData = ref<any[]>([])
const totalRoles = ref(0)
const enabledRoles = ref(0)
const platformRoles = ref(0)
const tenantRoles = ref(0)

// 新增/编辑
const formVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref()
const form = reactive({
  id: null as number | null,
  roleCode: '',
  roleName: '',
  description: '',
  status: 1
})
const rules = {
  roleCode: [{ required: true, message: '请输入角色编码', trigger: 'blur' }],
  roleName: [{ required: true, message: '请输入角色名称', trigger: 'blur' }]
}

// 权限分配
const permVisible = ref(false)
const loadingPerms = ref(false)
const savingPerm = ref(false)
const permTreeRef = ref()
const permTree = ref<any[]>([])
const allPermissions = ref<any[]>([])
const currentRole = ref<any>(null)
const checkedPermIds = ref<number[]>([])

// 内置角色不可删除/编辑编码
const BUILT_IN = ['SUPER_ADMIN', 'PLATFORM_ADMIN', 'ADMIN', 'BOSS', 'MANAGER', 'CLERK', 'OPERATOR']
const builtIn = (row: any) => BUILT_IN.includes(row.roleCode)

const openAdd = () => {
  isEdit.value = false
  Object.assign(form, { id: null, roleCode: '', roleName: '', description: '', status: 1 })
  formVisible.value = true
}

const openEdit = (row: any) => {
  isEdit.value = true
  Object.assign(form, row)
  formVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitting.value = true
    try {
      if (isEdit.value) {
        await rbacApi.updateRole(form.id!, form)
        ElMessage.success('更新成功')
      } else {
        await rbacApi.createRole(form)
        ElMessage.success('创建成功')
      }
      formVisible.value = false
      await loadData()
    } catch (e: any) {
      ElMessage.error(e.message || '操作失败')
    } finally {
      submitting.value = false
    }
  })
}

const handleDelete = async (row: any) => {
  await ElMessageBox.confirm(
    `确认删除角色 "${row.roleName}"（${row.roleCode}）？此操作不可恢复。`,
    '删除角色', { type: 'warning' }
  )
  try {
    await rbacApi.deleteRole(row.id)
    ElMessage.success('删除成功')
    await loadData()
  } catch (e: any) {
    ElMessage.error(e.message || '删除失败')
  }
}

// 权限分配
const openPermission = async (row: any) => {
  currentRole.value = row
  permVisible.value = true
  loadingPerms.value = true
  try {
    const [permRes, rolePermRes] = await Promise.all([
      rbacApi.listPermissions(),
      rbacApi.getRolePermissions(row.id)
    ])
    allPermissions.value = permRes.data || []
    checkedPermIds.value = rolePermRes.data || []
    permTree.value = buildPermTree(allPermissions.value)
    if (permTreeRef.value) {
      permTreeRef.value.setCheckedKeys(checkedPermIds.value)
    }
  } catch (e: any) {
    ElMessage.error(e.message || '加载权限失败')
  } finally {
    loadingPerms.value = false
  }
}

const buildPermTree = (perms: any[]): any[] => {
  const map = new Map<number | string, any>()
  const roots: any[] = []
  for (const p of perms) {
    map.set(p.id, { id: p.id, title: p.permissionName, permissionCode: p.permissionCode, children: [] })
  }
  for (const p of perms) {
    const node = map.get(p.id)!
    const pid = p.parentId
    if (!pid || pid === 0 || !map.has(pid)) {
      roots.push(node)
    } else {
      const parent = map.get(pid)!
      parent.children.push(node)
    }
  }
  return roots
}

const handleAssignPerm = async () => {
  if (!currentRole.value) return
  const checked = permTreeRef.value?.getCheckedKeys(true) || []
  savingPerm.value = true
  try {
    await rbacApi.assignPermissions(currentRole.value.id, checked)
    ElMessage.success('权限分配成功')
    permVisible.value = false
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    savingPerm.value = false
  }
}

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await rbacApi.listRoles()
    tableData.value = res.data || []
    totalRoles.value = tableData.value.length
    enabledRoles.value = tableData.value.filter(r => r.status === 1).length
    platformRoles.value = tableData.value.filter(r => !r.tenantId).length
    tenantRoles.value = tableData.value.filter(r => r.tenantId).length
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.role-page { padding: 12px; }
.stat-row .el-card { cursor: default; }
.stat-item { display:flex; align-items:center; gap:12px; }
.stat-icon {
  width:40px; height:40px; border-radius:8px;
  display:flex; align-items:center; justify-content:center;
  color:#fff; font-size:20px;
}
.stat-value { font-size:24px; font-weight:bold; }
.stat-label { font-size:12px; color:#909399; }
.toolbar { display:flex; align-items:center; }
.toolbar-title { font-weight:600; font-size:15px; }
.perm-tip {
  display:flex; align-items:center; gap:6px;
  color:#909399; font-size:13px; margin-bottom:12px;
  padding:8px 12px; background:#f5f7fa; border-radius:4px;
}
.perm-node { display:flex; align-items:center; }
</style>
