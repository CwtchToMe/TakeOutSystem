<template>
  <el-card>
    <template #header>
      <span style="font-weight: bold;">订单总览</span>
    </template>

    <div style="margin-bottom: 16px; display: flex; gap: 12px; flex-wrap: wrap;">
      <el-input v-model="query.merchantId" placeholder="商家 ID（可选）" clearable style="width: 180px;" />
      <el-select v-model="query.status" placeholder="全部状态" clearable style="width: 130px;" @change="fetchData">
        <el-option v-for="(label, val) in ORDER_STATUS" :key="val" :label="label" :value="Number(val)" />
      </el-select>
      <el-button type="primary" @click="fetchData">搜索</el-button>
    </div>

    <el-table :data="orders" v-loading="loading" border stripe row-key="id">
      <el-table-column type="expand">
        <template #default="{ row }">
          <div style="padding: 10px 20px;">
            <p style="font-weight: bold; margin-bottom: 8px;">订单明细：</p>
            <el-table :data="row.items" border size="small" style="width: 600px;">
              <el-table-column prop="dishName" label="菜品" />
              <el-table-column prop="spec" label="规格" width="100" />
              <el-table-column prop="unitPrice" label="单价" width="80">
                <template #default="{ row: item }">¥{{ item.unitPrice }}</template>
              </el-table-column>
              <el-table-column prop="quantity" label="数量" width="70" />
              <el-table-column prop="subtotal" label="小计" width="80">
                <template #default="{ row: item }">¥{{ item.subtotal }}</template>
              </el-table-column>
            </el-table>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="orderNo" label="订单号" width="160" show-overflow-tooltip />
      <el-table-column prop="merchantName" label="商家" width="140" show-overflow-tooltip />
      <el-table-column prop="receiver" label="收货人" width="90" />
      <el-table-column prop="phone" label="手机号" width="120" />
      <el-table-column prop="actualPrice" label="实付" width="80">
        <template #default="{ row }">¥{{ row.actualPrice }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="orderTagType(row.status)" size="small">{{ ORDER_STATUS[row.status] ?? '未知' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="下单时间" width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.createdAt?.replace('T', ' ') }}</template>
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
import { getOrders } from '../api/index'

const ORDER_STATUS = {
  1: '待支付', 2: '待接单', 3: '备餐中', 4: '待取餐',
  5: '配送中', 6: '已完成', 7: '已取消', 8: '退款中', 9: '已退款'
}
const orderTagType = (s) => ({
  1: 'info', 2: 'warning', 3: 'warning', 4: 'primary',
  5: 'primary', 6: 'success', 7: 'danger', 8: 'danger', 9: 'info'
}[s] ?? '')

const orders = ref([])
const total = ref(0)
const loading = ref(false)
const query = reactive({ merchantId: '', status: null, page: 1, size: 10 })

const fetchData = async () => {
  loading.value = true
  try {
    const params = { page: query.page, size: query.size }
    if (query.merchantId) params.merchantId = query.merchantId
    if (query.status !== null && query.status !== '') params.status = query.status
    const res = await getOrders(params)
    orders.value = res.data.records
    total.value = Number(res.data.total)
  } catch {} finally {
    loading.value = false
  }
}

onMounted(fetchData)
</script>
