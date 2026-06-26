<template>
  <el-container style="height: 100vh">
    <el-aside width="200px" style="background: #304156">
      <div class="logo">商家工作台</div>
      <el-menu background-color="#304156" text-color="#bfcbd9" active-text-color="#409eff" :default-active="route.path" router>
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>仪表盘
        </el-menu-item>
        <el-menu-item index="/orders">
          <el-icon><List /></el-icon>
          订单管理
          <el-badge v-if="newOrderCount > 0" :value="newOrderCount" style="margin-left: auto" />
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header style="background: #fff; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #eee; padding: 0 20px">
        <div>
          <el-tag :type="wsConnected ? 'success' : 'danger'" size="small">
            {{ wsConnected ? '实时推送已连接' : '推送未连接' }}
          </el-tag>
        </div>
        <el-button link @click="handleLogout">退出登录</el-button>
      </el-header>

      <el-main style="background: #f5f7fa">
        <router-view />
      </el-main>
    </el-container>
  </el-container>

  <!-- 新订单通知 -->
  <el-notification
    v-if="latestNotification"
    :title="latestNotification.title"
    :message="latestNotification.content"
    type="warning"
    position="top-right"
    :duration="10000"
    @close="latestNotification = null"
  />
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElNotification } from 'element-plus'
import { useWebSocket } from '../composables/useWebSocket'

const route = useRoute()
const router = useRouter()

const newOrderCount = ref(0)
const latestNotification = ref(null)
const wsConnected = ref(false)

const userId = localStorage.getItem('merchant_userId')

const { connected, connect } = useWebSocket(
  'merchant:' + userId,  // 商家专属订阅 key
  (notification) => {
    latestNotification.value = notification
    if (notification.type === 'NEW_ORDER') {
      newOrderCount.value++
      // 播放提示音（浏览器 API）
      try { new Audio('/notification.mp3').play() } catch (e) {}
    }
    ElNotification({
      title: notification.title,
      message: notification.content,
      type: notification.type === 'NEW_ORDER' ? 'warning' : 'info',
      duration: 8000
    })
  }
)

onMounted(() => {
  connect()
  wsConnected.value = connected.value
})

const handleLogout = () => {
  localStorage.removeItem('merchant_token')
  localStorage.removeItem('merchant_userId')
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<style scoped>
.logo { color: #fff; font-size: 16px; font-weight: bold; text-align: center; padding: 18px 0; border-bottom: 1px solid #435162; }
</style>
