import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
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

async function tryRefreshToken() {
  const rt = localStorage.getItem('admin_refresh_token')
  if (!rt) return null
  try {
    const resp = await axios.post('/api/auth/refresh', { refreshToken: rt })
    if (resp.data?.code === 200 && resp.data?.data) {
      const newToken = resp.data.data
      localStorage.setItem('admin_token', newToken)
      return newToken
    }
    return null
  } catch {
    return null
  }
}

request.interceptors.request.use(config => {
  const token = localStorage.getItem('admin_token')
  if (token) config.headers['Authorization'] = `Bearer ${token}`
  return config
})

request.interceptors.response.use(
  response => {
    const data = response.data
    if (data.code !== 200) {
      if (data.code === 401) {
        if (!_refreshing) {
          _refreshing = true
          return tryRefreshToken().then(newToken => {
            _refreshing = false
            if (newToken) {
              onRefreshed(newToken)
              const newConfig = { ...response.config }
              newConfig.headers['Authorization'] = `Bearer ${newToken}`
              return request(newConfig)
            }
            clearAuthAndRedirect()
            return Promise.reject(new Error(data.message))
          }).catch(() => {
            _refreshing = false
            _refreshSubscribers = []
            clearAuthAndRedirect()
            return Promise.reject(new Error(data.message))
          })
        } else {
          return new Promise(resolve => {
            addRefreshSubscriber(newToken => {
              response.config.headers['Authorization'] = `Bearer ${newToken}`
              resolve(request(response.config))
            })
          })
        }
      }
      ElMessage.error(data.message || '请求失败')
      return Promise.reject(new Error(data.message))
    }
    return data
  },
  error => {
    if (error.response) {
      const status = error.response.status
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
      } else if (status >= 500) {
        ElMessage.error(error.response.data?.message || '服务器异常，请检查控制台服务状态')
      } else {
        ElMessage.error(error.response.data?.message || `请求失败(${status})`)
      }
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请检查后端服务是否正常（http://localhost:8080/api/health）')
    } else {
      ElMessage.error('网络错误，请确认后端是否已启动（端口 8080）')
    }
    return Promise.reject(error)
  }
)

function clearAuthAndRedirect() {
  localStorage.removeItem('admin_token')
  localStorage.removeItem('admin_refresh_token')
  localStorage.removeItem('admin_userId')
  if (router.currentRoute.value.path !== '/login') router.push('/login')
}

export default request
