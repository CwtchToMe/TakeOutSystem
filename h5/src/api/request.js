import axios from 'axios'
import { showToast } from 'vant'
import router from '../router'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// 请求拦截：附加 JWT token
request.interceptors.request.use(config => {
  const token = localStorage.getItem('h5_token')
  if (token) {
    config.headers['Authorization'] = `Bearer ${token}`
  }
  return config
})

// 清除 auth 并跳登录
function handleUnauthorized() {
  localStorage.removeItem('h5_token')
  localStorage.removeItem('h5_userId')
  localStorage.removeItem('h5_nickname')
  if (router.currentRoute.value.path !== '/login') {
    showToast({ message: '登录已过期，请重新登录', position: 'top' })
    router.push('/login')
  }
}

// 响应拦截
request.interceptors.response.use(
  response => {
    const data = response.data
    // 业务层返回非 200
    if (data.code !== undefined && data.code !== 200) {
      // 业务 401：token 失效
      if (data.code === 401) {
        handleUnauthorized()
        return Promise.reject(new Error(data.message || '登录已过期'))
      }
      showToast(data.message || '请求失败')
      return Promise.reject(new Error(data.message))
    }
    return data
  },
  error => {
    if (error.response) {
      const status = error.response.status
      // HTTP 401：网关鉴权失败
      if (status === 401) {
        handleUnauthorized()
        return Promise.reject(error)
      }
      // HTTP 403
      if (status === 403) {
        showToast('没有操作权限')
        return Promise.reject(error)
      }
      // HTTP 404
      if (status === 404) {
        showToast('接口不存在，请联系管理员')
        return Promise.reject(error)
      }
      // HTTP 500
      if (status >= 500) {
        showToast({ message: error.response.data?.message || '服务器异常，请稍后重试', duration: 3000 })
        return Promise.reject(error)
      }
      // 其他 HTTP 错误：优先使用后端返回的 message
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

export default request
