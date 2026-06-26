import request from './request'

// 认证
export const sendSms = (phone) => request.post('/auth/sms/send', { phone })
export const login = (phone, code) => request.post('/auth/login', { phone, code })

// 我的商家
export const getMyMerchant = () => request.get('/merchant/my')
export const updateMerchantStatus = (data) => request.put('/merchant/my/status', data)

// 订单
export const getMerchantOrders = (params) => request.get('/order/merchant/list', { params })
export const acceptOrder = (orderNo) => request.post(`/order/merchant/accept/${orderNo}`)
export const rejectOrder = (orderNo, reason) => request.post(`/order/merchant/reject/${orderNo}`, { reason })
export const readyOrder = (orderNo) => request.post(`/order/merchant/ready/${orderNo}`)
export const completeOrder = (orderNo) => request.post(`/order/merchant/complete/${orderNo}`)
