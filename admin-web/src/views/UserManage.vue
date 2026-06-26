<template>
  <el-card>
    <template #header>
      <span style="font-weight: bold;">用户管理</span>
    </template>

    <div style="margin-bottom: 16px; display: flex; gap: 12px; flex-wrap: wrap;">
      <el-select v-model="query.role" placeholder="全部角色" clearable style="width: 130px;" @change="fetchData">
        <el-option label="普通用户" value="CUSTOMER" />
        <el-option label="商家" value="MERCHANT" />
        <el-option label="骑手" value="RIDER" />
        <el-option label="管理员" value="ADMIN" />
      </el-select>
      <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 120px;" @change="fetchData">
        <el-option label="正常" :value="1" />
        <el-option label="禁用" :value="0" />
      </el-select>
      <el-input v-model="query.keyword" placeholder="手机号 / 昵称" clearable style="width: 180px;" @keyup.enter="fetchData" />
      <el-button type="primary" @click="fetchData">搜索</el-button>
    </div>

    <el-table :data="users" v-loading="loading" border stripe>
      <el-table-column prop="id" label="ID" width="80" show-overflow-tooltip />
      <el-table-column prop="phone" label="手机号" width="130" />
      <el-table-column prop="nickname" label="昵称" min-width="120" show-overflow-tooltip />
      <el-table-column label="角色" width="100">
        <template #default="{ row }">
          <el-tag :type="roleTagType(row.role)" size="small">{{ roleLabel(row.role) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '正常' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="注册时间" width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.createdAt?.replace('T', ' ') }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-popconfirm
            :title="row.status === 1 ? `确定禁用「${row.nickname}」？` : `确定启用「${row.nickname}」？`"
            @confirm="toggleStatus(row)"
          >
            <template #reference>
              <el-button size="small" :type="row.status === 1 ? 'danger' : 'success'">
                {{ row.status === 1 ? '禁用' : '启用' }}
              </el-button>
            </template>
          </el-popconfirm>
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
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getUsers, updateUserStatus } from '../api/index'

const users = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ role: null, status: null, keyword: '', page: 1, size: 10 })

const roleLabel = (r) => ({ CUSTOMER: '普通用户', MERCHANT: '商家', RIDER: '骑手', ADMIN: '管理员' }[r] ?? r)
const roleTagType = (r) => ({ CUSTOMER: '', MERCHANT: 'warning', RIDER: 'success', ADMIN: 'danger' }[r] ?? 'info')

const fetchData = async () => {
  loading.value = true
  try {
    const params = { page: query.page, size: query.size }
    if (query.role) params.role = query.role
    if (query.status !== null && query.status !== '') params.status = query.status
    if (query.keyword) params.keyword = query.keyword
    const res = await getUsers(params)
    users.value = res.data.records
    total.value = Number(res.data.total)
  } catch {} finally {
    loading.value = false
  }
}

const toggleStatus = async (row) => {
  const newStatus = row.status === 1 ? 0 : 1
  try {
    await updateUserStatus(row.id, newStatus)
    ElMessage.success(newStatus === 1 ? '已启用' : '已禁用')
    fetchData()
  } catch {}
}

onMounted(fetchData)
</script>
