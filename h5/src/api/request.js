import axios from 'axios'
import { showToast } from 'vant'
import router from '../router'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// ---- Token 刷新状态 ----
let _refreshing = false
let _refreshSubscribers = []

function onRefreshed(newToken) {
  _refreshSubscribers.forEach(cb => cb(newToken))
  _refreshSubscribers = []
}

function addRefreshSubscriber(cb) {
  _refreshSubscribers.push(cb)
}

// 尝试用 refreshToken 换取新 accessToken
async function tryRefreshToken() {
  const rt = localStorage.getItem('h5_refresh_token')
  if (!rt) return null
  try {
    const resp = await axios.post('/api/auth/refresh', { refreshToken: rt })
    if (resp.data?.code === 200 && resp.data?.data) {
      const newToken = resp.data.data
      localStorage.setItem('h5_token', newToken)
      return newToken
    }
    return null
  } catch {
    return null
  }
}

// 请求拦截：附加 JWT token
request.interceptors.request.use(config => {
  const token = localStorage.getItem('h5_token')
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  }
  return config
})

// 响应拦截（含自动刷新 token 逻辑）
request.interceptors.response.use(
  response => {
    const data = response.data
    if (data.code !== undefined && data.code !== 200) {
      if (data.code === 401) {
        // 尝试刷新 token
        if (!_refreshing) {
          _refreshing = true
          return tryRefreshToken().then(newToken => {
            _refreshing = false
            if (newToken) {
              onRefreshed(newToken)
              // 重试原始请求
              const newConfig = { ...response.config }
              newConfig.headers['Authorization'] = `Bearer ${newToken}`
              return request(newConfig)
            }
            // 刷新失败 → 登出
            clearAuthAndRedirect()
            return Promise.reject(new Error(data.message || '登录已过期'))
          }).catch(() => {
            _refreshing = false
            _refreshSubscribers = []
            clearAuthAndRedirect()
            return Promise.reject(new Error(data.message || '登录已过期'))
          })
        } else {
          // 正在刷新中，排队等待
          return new Promise(resolve => {
            addRefreshSubscriber(newToken => {
              response.config.headers['Authorization'] = `Bearer ${newToken}`
              resolve(request(response.config))
            })
          })
        }
      }
      showToast(data.message || '请求失败')
      return Promise.reject(new Error(data.message))
    }
    return data
  },
  error => {
    if (error.response) {
      const status = error.response.status
      // HTTP 401：尝试刷新 token
      if (status === 401) {
        if (!_refreshing) {
          _refreshing = true
          return tryRefreshToken().then(newToken => {
            _refreshing = false
            if (newToken) {
              onRefreshed(newToken)
              const newConfig = { ...error.config }
              newConfig.headers['Authorization'] = `Bearer ${newToken}`
              return request(newConfig)
            }
            clearAuthAndRedirect()
            return Promise.reject(error)
          }).catch(() => {
            _refreshing = false
            _refreshSubscribers = []
            clearAuthAndRedirect()
            return Promise.reject(error)
          })
        } else {
          return new Promise(resolve => {
            addRefreshSubscriber(newToken => {
              error.config.headers['Authorization'] = `Bearer ${newToken}`
              resolve(request(error.config))
            })
          })
        }
      }
      if (status === 403) {
        showToast('没有操作权限')
        return Promise.reject(error)
      }
      if (status === 404) {
        showToast('接口不存在，请联系管理员')
        return Promise.reject(error)
      }
      if (status >= 500) {
        showToast({ message: error.response.data?.message || '服务器异常，请稍后重试', duration: 3000 })
        return Promise.reject(error)
      }
      const msg = error.response.data?.message || `请求失败(${status})`
      showToast(msg)
    } else if (error.code === 'ECONNABORTED') {
      showToast({ message: '请求超时，请检查后端服务是否正常（http://localhost:8080/api/health）', duration: 3000 })
    } else {
      showToast({ message: '网络错误，请确认后端是否已启动（端口 8080）', duration: 3000 })
    }
    return Promise.reject(error)
  }
)

function clearAuthAndRedirect() {
  localStorage.removeItem('h5_token')
  localStorage.removeItem('h5_refresh_token')
  localStorage.removeItem('h5_userId')
  localStorage.removeItem('h5_nickname')
  if (router.currentRoute.value.path !== '/login') {
    showToast({ message: '登录已过期，请重新登录', position: 'top' })
    router.push('/login')
  }
}

export default request
