import request from './request'

export const sendSms = (phone) => request.post('/auth/sms/send', { phone })
export const login = (phone, code) => request.post('/auth/login', { phone, code })

// 商家管理
export const getMerchants = (params) => request.get('/admin/merchant/list', { params })
export const auditMerchant = (id, data) => request.post(`/admin/merchant/${id}/audit`, data)

// 用户管理
export const getUsers = (params) => request.get('/admin/user/list', { params })
export const updateUserStatus = (id, status) => request.put(`/admin/user/${id}/status`, { status })

// 订单总览
export const getOrders = (params) => request.get('/admin/order/list', { params })
