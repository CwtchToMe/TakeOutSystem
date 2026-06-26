import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '../router'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

request.interceptors.request.use(config => {
  const token = localStorage.getItem('merchant_token')
  if (token) config.headers['Authorization'] = `Bearer ${token}`
  return config
})

request.interceptors.response.use(
  response => {
    const data = response.data
    if (data.code !== 200) {
      ElMessage.error(data.message || '请求失败')
      if (data.code === 401) {
        localStorage.removeItem('merchant_token')
        router.push('/login')
      }
      return Promise.reject(new Error(data.message))
    }
    return data
  },
  error => {
    if (error.response) {
      const status = error.response.status
      if (status === 401) {
        localStorage.removeItem('merchant_token')
        router.push('/login')
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

export default request
