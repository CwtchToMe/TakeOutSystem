import { ref, onUnmounted } from 'vue'

/**
 * WebSocket/STOMP 连接 composable
 * 使用原生 WebSocket 替代 SockJS（开发环境更简单）
 * 若需 SockJS 支持，改用 sockjs-client + stompjs
 */
export function useWebSocket(userId, onMessage) {
  const connected = ref(false)
  let client = null

  const connect = () => {
    try {
      // 使用 SockJS 端点
      const SockJS = window.SockJS
      const Stomp = window.Stomp

      if (!SockJS || !Stomp) {
        console.warn('SockJS/Stomp 未加载，跳过 WebSocket 连接')
        return
      }

      const socket = new SockJS('/ws/notification?userId=' + userId)
      client = Stomp.over(socket)
      client.debug = null  // 关闭调试日志

      client.connect({}, () => {
        connected.value = true
        // 订阅用户专属通知队列
        client.subscribe(`/user/${userId}/queue/notification`, (message) => {
          try {
            const data = JSON.parse(message.body)
            onMessage && onMessage(data)
          } catch (e) {
            console.error('解析通知消息失败', e)
          }
        })
      }, (err) => {
        connected.value = false
        console.warn('WebSocket 连接失败:', err)
      })
    } catch (e) {
      console.warn('WebSocket 初始化失败:', e)
    }
  }

  const disconnect = () => {
    if (client && connected.value) {
      client.disconnect()
      connected.value = false
    }
  }

  onUnmounted(disconnect)

  return { connected, connect, disconnect }
}
