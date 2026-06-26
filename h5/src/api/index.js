import request from './request'

// 认证
export const sendSms = (phone) => request.post('/auth/sms/send', { phone })
export const login = (phone, code) => request.post('/auth/login', { phone, code })

// 商家（后端 @RequestMapping("/api/merchant")）
export const getNearbyMerchants = (params) => request.get('/merchant/nearby', { params })
export const getMerchantDetail = (id) => request.get(`/merchant/${id}`)
export const searchMerchants = (keyword, page = 1, size = 20) =>
  request.get('/merchant/search', { params: { keyword, page, size } })
export const searchDishes = (keyword, page = 1, size = 20) =>
  request.get('/product/dish', { params: { keyword, page, size } })

// 菜单（后端 @RequestMapping("/api/product/menu")）
export const getMenu = (merchantId) => request.get(`/product/menu/${merchantId}`)

// 购物车（后端 @RequestMapping("/api/cart")）
export const getCart = (merchantId) => request.get(`/cart/${merchantId}`)
export const addToCart = (data) => request.post('/cart/add', data)
export const updateCartItem = (cartItemId, quantity) =>
  request.put(`/cart/${cartItemId}`, null, { params: { quantity } })
export const removeCartItem = (id) => request.delete(`/cart/${id}`)
export const clearCart = (merchantId) => request.delete(`/cart/clear/${merchantId}`)

// 地址（后端 @RequestMapping("/api/user/address")）
export const getAddresses = () => request.get('/user/address')
export const addAddress = (data) => request.post('/user/address', data)
export const updateAddress = (id, data) => request.put(`/user/address/${id}`, data)
export const deleteAddress = (id) => request.delete(`/user/address/${id}`)
export const setDefaultAddress = (id) => request.put(`/user/address/${id}/default`)

// 订单（后端 @RequestMapping("/api/order")）
export const submitOrder = (data) => request.post('/order/submit', data)
export const getMyOrders = (params) => request.get('/order/list', { params })
export const getOrderDetail = (orderNo) => request.get(`/order/${orderNo}`)
export const cancelOrder = (orderNo) => request.post(`/order/cancel/${orderNo}`)
export const receiveOrder = (orderNo) => request.post(`/order/receive/${orderNo}`)

// 个人信息（后端 @RequestMapping("/api/user")）
export const getMyProfile = () => request.get('/user/profile')
export const updateProfile = (data) => request.put('/user/profile', data)

// 评价（后端 @RequestMapping("/api/review")）
export const submitReview = (data) => request.post('/review', data)
export const getMerchantReviews = (merchantId) => request.get(`/review/merchant/${merchantId}`)
export const getMyReviews = () => request.get('/review/my')
export const getOrderReview = (orderNo) => request.get(`/review/order/${orderNo}`)

// 收藏（后端 @RequestMapping("/api/favorite")）
export const addFavorite = (merchantId) => request.post(`/favorite/${merchantId}`)
export const removeFavorite = (merchantId) => request.delete(`/favorite/${merchantId}`)
export const getMyFavorites = () => request.get('/favorite')
export const checkFavorite = (merchantId) => request.get(`/favorite/check/${merchantId}`)

// 优惠券（后端 @RequestMapping("/api/coupon")）
export const getAvailableCoupons = () => request.get('/coupon/available')
export const receiveCoupon = (couponId) => request.post(`/coupon/receive/${couponId}`)
export const getMyCoupons = () => request.get('/coupon/my')
export const getUsableCoupons = (orderPrice) => request.get('/coupon/usable', { params: { orderPrice } })

// 模拟支付（后端 @RequestMapping("/api/pay")）
export const createPayment = (data) => request.post('/pay/create', data)
export const getPayStatus = (orderNo) => request.get(`/pay/status/${orderNo}`)
export const mockCallback = (data) => request.post('/pay/callback', data)
