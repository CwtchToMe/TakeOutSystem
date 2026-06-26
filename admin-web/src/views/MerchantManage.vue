<template>
  <el-card>
    <template #header>
      <span style="font-weight: bold;">商家管理</span>
    </template>

    <div style="margin-bottom: 16px; display: flex; gap: 12px; flex-wrap: wrap;">
      <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 140px;" @change="fetchData">
        <el-option label="待审核" :value="0" />
        <el-option label="营业中" :value="1" />
        <el-option label="打烊" :value="2" />
        <el-option label="封禁" :value="3" />
        <el-option label="审核拒绝" :value="4" />
      </el-select>
      <el-input v-model="query.keyword" placeholder="搜索商家名称" clearable style="width: 200px;" @keyup.enter="fetchData" />
      <el-button type="primary" @click="fetchData">搜索</el-button>
    </div>

    <el-table :data="merchants" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="80" show-overflow-tooltip />
      <el-table-column prop="name" label="商家名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="phone" label="联系电话" width="130" />
      <el-table-column prop="address" label="地址" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.province }}{{ row.city }}{{ row.district }}{{ row.address }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="注册时间" width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.createdAt?.replace('T', ' ') }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <template v-if="row.status === 0">
            <el-button size="small" type="success" @click="openAudit(row, true)">通过</el-button>
            <el-button size="small" type="danger" @click="openAudit(row, false)">拒绝</el-button>
          </template>
          <span v-else style="color: #c0c4cc; font-size: 13px;">—</span>
        </template>
      </el-table-column>
    </el-table>

    <div style="margin-top: 16px; display: flex; justify-content: flex-end;">
      <el-pagination
        v-model:current-page="query.page"
        v-model:page-size="query.size"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @change="fetchData"
      />
    </div>
  </el-card>

  <el-dialog v-model="auditDialog.visible" :title="auditDialog.approved ? '审核通过' : '审核拒绝'" width="400px">
    <p>商家：{{ auditDialog.merchant?.name }}</p>
    <el-input
      v-if="!auditDialog.approved"
      v-model="auditDialog.reason"
      type="textarea"
      :rows="3"
      placeholder="请填写拒绝原因"
      style="margin-top: 10px;"
    />
    <template #footer>
      <el-button @click="auditDialog.visible = false">取消</el-button>
      <el-button :type="auditDialog.approved ? 'success' : 'danger'" :loading="auditDialog.loading" @click="submitAudit">确认</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getMerchants, auditMerchant } from '../api/index'

const merchants = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ status: null, keyword: '', page: 1, size: 10 })

const statusLabel = (s) => ({ 0: '待审核', 1: '营业中', 2: '打烊', 3: '封禁', 4: '审核拒绝' }[s] ?? '未知')
const statusTagType = (s) => ({ 0: 'warning', 1: 'success', 2: 'info', 3: 'danger', 4: 'danger' }[s] ?? '')

const fetchData = async () => {
  loading.value = true
  try {
    const params = { page: query.page, size: query.size }
    if (query.status !== null && query.status !== '') params.status = query.status
    if (query.keyword) params.keyword = query.keyword
    const res = await getMerchants(params)
    merchants.value = res.data.records
    total.value = Number(res.data.total)
  } catch {} finally {
    loading.value = false
  }
}

const auditDialog = reactive({ visible: false, merchant: null, approved: true, reason: '', loading: false })

const openAudit = (row, approved) => {
  auditDialog.merchant = row
  auditDialog.approved = approved
  auditDialog.reason = ''
  auditDialog.visible = true
}

const submitAudit = async () => {
  if (!auditDialog.approved && !auditDialog.reason.trim()) {
    ElMessage.warning('请填写拒绝原因')
    return
  }
  auditDialog.loading = true
  try {
    await auditMerchant(auditDialog.merchant.id, {
      approved: auditDialog.approved,
      reason: auditDialog.reason || null
    })
    ElMessage.success(auditDialog.approved ? '已通过审核' : '已拒绝')
    auditDialog.visible = false
    fetchData()
  } catch {} finally {
    auditDialog.loading = false
  }
}

onMounted(fetchData)
</script>
