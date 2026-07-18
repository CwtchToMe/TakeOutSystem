<template>
  <div>
    <el-card>
      <template #header>
        <div style="display: flex; align-items: center; gap: 12px">
          <span>订单管理</span>
          <el-select
            v-model="statusFilter"
            clearable
            placeholder="状态筛选（全部）"
            style="width: 150px"
            @change="onStatusFilterChange"
          >
            <el-option label="待接单" :value="2" />
            <el-option label="备餐中" :value="3" />
            <el-option label="配送中" :value="5" />
            <el-option label="已完成" :value="6" />
            <el-option label="已取消" :value="7" />
          </el-select>
          <el-button type="primary" @click="loadOrders">查询</el-button>
          <el-button :icon="Refresh" circle @click="loadOrders" title="刷新" />
        </div>
      </template>

      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="待接单" name="pending">
          <el-table :data="pendingOrders" v-loading="loading" border stripe>
            <el-table-column prop="orderNo" label="订单号" width="200" />
            <el-table-column label="菜品" min-width="200">
              <template #default="{ row }">
                <div v-for="item in row.items" :key="item.id" style="font-size: 12px">
                  {{ item.dishName }}{{ item.spec ? '(' + item.spec + ')' : '' }} ×{{ item.quantity }}
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="actualPrice" label="金额" width="90">
              <template #default="{ row }">¥{{ row.actualPrice }}</template>
            </el-table-column>
            <el-table-column prop="receiver" label="收件人" width="100" />
            <el-table-column prop="remark" label="备注" width="120" show-overflow-tooltip />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180">
              <template #default="{ row }">
                <template v-if="row.status === 2">
                  <el-button size="small" type="primary" @click="handleAccept(row)">接单</el-button>
                  <el-button size="small" type="danger" @click="handleReject(row)">拒单</el-button>
                </template>
                <span v-else style="color: #909399; font-size: 12px">无操作</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="备餐中" name="cooking">
          <el-table :data="cookingOrders" v-loading="loading" border stripe>
            <el-table-column prop="orderNo" label="订单号" width="200" />
            <el-table-column label="菜品" min-width="200">
              <template #default="{ row }">
                <div v-for="item in row.items" :key="item.id" style="font-size: 12px">
                  {{ item.dishName }}{{ item.spec ? '(' + item.spec + ')' : '' }} ×{{ item.quantity }}
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="actualPrice" label="金额" width="90">
              <template #default="{ row }">¥{{ row.actualPrice }}</template>
            </el-table-column>
            <el-table-column prop="receiver" label="收件人" width="100" />
            <el-table-column prop="remark" label="备注" width="120" show-overflow-tooltip />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180">
              <template #default="{ row }">
                <el-button v-if="row.status === 3" size="small" type="success" @click="handleReady(row)">出餐完成</el-button>
                <el-button v-else-if="row.status === 5" size="small" type="primary" @click="handleComplete(row)">完成配送</el-button>
                <span v-else style="color: #909399; font-size: 12px">无操作</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="全部" name="all">
          <el-table :data="allOrders" v-loading="loading" border stripe>
            <el-table-column prop="orderNo" label="订单号" width="200" />
            <el-table-column label="菜品" min-width="200">
              <template #default="{ row }">
                <div v-for="item in row.items" :key="item.id" style="font-size: 12px">
                  {{ item.dishName }}{{ item.spec ? '(' + item.spec + ')' : '' }} ×{{ item.quantity }}
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="actualPrice" label="金额" width="90">
              <template #default="{ row }">¥{{ row.actualPrice }}</template>
            </el-table-column>
            <el-table-column prop="receiver" label="收件人" width="100" />
            <el-table-column prop="remark" label="备注" width="120" show-overflow-tooltip />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180">
              <template #default="{ row }">
                <template v-if="row.status === 2">
                  <el-button size="small" type="primary" @click="handleAccept(row)">接单</el-button>
                  <el-button size="small" type="danger" @click="handleReject(row)">拒单</el-button>
                </template>
                <el-button v-else-if="row.status === 3" size="small" type="success" @click="handleReady(row)">出餐完成</el-button>
                <el-button v-else-if="row.status === 5" size="small" type="primary" @click="handleComplete(row)">完成配送</el-button>
                <span v-else style="color: #909399; font-size: 12px">无操作</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>

      <el-pagination
        style="margin-top: 12px"
        v-model:current-page="page"
        :total="Number(total)"
        layout="total, prev, pager, next"
        @change="loadOrders"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { getMerchantOrders, acceptOrder, rejectOrder, readyOrder, completeOrder, getMyMerchant } from '../api'

const statusLabel = (s) => ({ 1: '待支付', 2: '待接单', 3: '备餐中', 4: '待取餐', 5: '配送中', 6: '已完成', 7: '已取消' })[s] ?? '未知'
const statusTagType = (s) => s === 6 ? 'success' : s === 7 ? 'danger' : 'warning'

const merchantId = ref('')
const statusFilter = ref(null)
const activeTab = ref('pending')
const allOrders = ref([])
const loading = ref(false)
const page = ref(1)
const total = ref(0)

const pendingOrders = computed(() => allOrders.value.filter(o => o.status === 2))
const cookingOrders = computed(() => allOrders.value.filter(o => o.status === 3 || o.status === 5))

const onTabChange = () => {
  statusFilter.value = null
  page.value = 1
  loadOrders()
}

const onStatusFilterChange = () => {
  if (statusFilter.value !== null) {
    activeTab.value = 'all'
  }
  page.value = 1
  loadOrders()
}

const loadOrders = async () => {
  if (!merchantId.value) return
  loading.value = true
  // 构造查询参数，null 值不传避免 Axios 序列化异常
  const params = { merchantId: merchantId.value, page: page.value, size: 20 }
  if (activeTab.value === 'pending') {
    params.status = 2
  } else if (activeTab.value === 'cooking') {
    // 备餐中 tab 只查 status=3 和 5，不拉全部再前端过滤
    const [r3, r5] = await Promise.all([
      getMerchantOrders({ ...params, status: 3 }),
      getMerchantOrders({ ...params, status: 5 })
    ])
    const r3list = r3.data.records || []
    const r5list = r5.data.records || []
    allOrders.value = [...r3list, ...r5list]
      .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
    total.value = (r3.data.total || 0) + (r5.data.total || 0)
    loading.value = false
    return
  } else if (statusFilter.value !== null) {
    params.status = statusFilter.value
  }
  try {
    const res = await getMerchantOrders(params)
    allOrders.value = res.data.records || []
    total.value = res.data.total
  } catch (e) {
    ElMessage.error(e?.message || '查询失败，请重试')
    allOrders.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const handleAccept = async (order) => {
  try {
    await acceptOrder(order.orderNo)
    ElMessage.success('已接单')
    loadOrders()
  } catch (e) {
    ElMessage.error(e?.message || '接单失败，请重试')
  }
}

const handleReject = async (order) => {
  try {
    const { value: reason } = await ElMessageBox.prompt('请输入拒单原因', '拒单确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPlaceholder: '拒单原因'
    })
    await rejectOrder(order.orderNo, reason)
    ElMessage.warning('已拒单')
    loadOrders()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e?.message || '拒单失败，请重试')
  }
}

const handleReady = async (order) => {
  try {
    await readyOrder(order.orderNo)
    ElMessage.success('出餐完成，已通知配送')
    loadOrders()
  } catch (e) {
    ElMessage.error(e?.message || '操作失败，请重试')
  }
}

const handleComplete = async (order) => {
  try {
    await completeOrder(order.orderNo)
    ElMessage.success('配送完成')
    loadOrders()
  } catch (e) {
    ElMessage.error(e?.message || '操作失败，请重试')
  }
}

let pollTimer = null

onMounted(async () => {
  try {
    const res = await getMyMerchant()
    if (res.data?.id) {
      merchantId.value = String(res.data.id)
      await loadOrders()
    }
  } catch (e) {
    ElMessage.warning('请先登录商家账号')
  }
  pollTimer = setInterval(() => { if (merchantId.value) loadOrders() }, 15000)
})

onUnmounted(() => { clearInterval(pollTimer) })
</script>
