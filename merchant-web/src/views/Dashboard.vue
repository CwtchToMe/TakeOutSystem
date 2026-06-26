<template>
  <div>
    <el-card>
      <template #header>商家信息</template>
      <el-skeleton v-if="!merchant" :rows="3" />
      <el-descriptions v-else :column="2" border>
        <el-descriptions-item label="店铺名称">{{ merchant.name }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="merchant.status === 1 ? 'success' : merchant.status === 3 ? 'danger' : 'warning'">
            {{ { 0: '待审核', 1: '营业中', 2: '打烊', 3: '封禁', 4: '审核拒绝' }[merchant.status] ?? '未知' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="地址">{{ merchant.address }}</el-descriptions-item>
        <el-descriptions-item label="评分">{{ merchant.score }}</el-descriptions-item>
        <el-descriptions-item label="月销量">{{ merchant.salesCount }}</el-descriptions-item>
        <el-descriptions-item label="配送费">¥{{ merchant.deliveryFee }}</el-descriptions-item>
      </el-descriptions>

      <div style="margin-top: 16px" v-if="merchant">
        <el-button
          :type="merchant.status === 1 ? 'warning' : 'success'"
          @click="toggleStatus"
        >
          {{ merchant.status === 1 ? '打烊（暂停营业）' : '开始营业' }}
        </el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getMyMerchant, updateMerchantStatus } from '../api'

const merchant = ref(null)

onMounted(async () => {
  const res = await getMyMerchant()
  merchant.value = res.data
})

const toggleStatus = async () => {
  const newStatus = merchant.value.status === 1 ? 2 : 1
  await updateMerchantStatus({ status: newStatus })
  merchant.value.status = newStatus
  ElMessage.success(newStatus === 1 ? '已开始营业' : '已打烊')
}
</script>
