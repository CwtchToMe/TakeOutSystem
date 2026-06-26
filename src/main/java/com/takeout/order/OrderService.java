package com.takeout.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.takeout.common.exception.BusinessException;
import com.takeout.common.result.PageResult;
import com.takeout.common.result.ResultCode;
import com.takeout.common.util.PageUtil;
import com.takeout.common.util.SnowflakeIdUtil;
import com.takeout.coupon.CouponService;
import com.takeout.merchant.Merchant;
import com.takeout.merchant.MerchantService;
import com.takeout.product.Dish;
import com.takeout.product.DishMapper;
import com.takeout.product.DishService;
import com.takeout.product.DishSnapshotVO;
import com.takeout.product.StockDeductItem;
import com.takeout.user.UserAddress;
import com.takeout.user.UserAddressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final CartService cartService;
    private final MerchantService merchantService;
    private final DishService dishService;
    private final DishMapper dishMapper;
    private final UserAddressService addressService;
    private final CouponService couponService;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String LOCK_KEY_PREFIX = "lock:order:";

    @Transactional(rollbackFor = Exception.class)
    public SubmitOrderVO submit(Long userId, SubmitOrderRequest request) {
        log.info("提交订单，userId={}, merchantId={}", userId, request.merchantId());

        if (request.items() == null || request.items().isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "购物车不能为空");
        }

        Merchant merchant = merchantService.getInternal(request.merchantId());
        if (merchant.getStatus() != 1) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "商家当前不营业");
        }

        // 在扣库存之前完成所有校验，防止 Redis 与 DB 不一致
        UserAddress address = addressService.getById(request.addressId());
        if (!address.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权使用此地址");
        }

        BigDecimal discount = BigDecimal.ZERO;
        if (request.userCouponId() != null) {
            List<Long> dishIds = request.items().stream().map(CartItemDTO::dishId).toList();
            List<Dish> dishes = dishMapper.selectBatchIds(dishIds);
            BigDecimal estimatedTotal = dishes.stream()
                    .map(d -> {
                        int qty = request.items().stream()
                                .filter(i -> i.dishId().equals(d.getId()))
                                .mapToInt(CartItemDTO::quantity).sum();
                        return d.getPrice().multiply(BigDecimal.valueOf(qty));
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal estimatedBase = estimatedTotal.add(merchant.getDeliveryFee());
            discount = couponService.validateAndGetDiscount(userId, request.userCouponId(), estimatedBase);
        }

        List<StockDeductItem> deductItems = request.items().stream()
                .map(i -> new StockDeductItem(i.dishId(), i.quantity()))
                .toList();
        List<DishSnapshotVO> snapshots = dishService.checkAndDeduct(deductItems);

        BigDecimal totalPrice = snapshots.stream()
                .map(s -> s.unitPrice().multiply(BigDecimal.valueOf(s.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal basePrice = totalPrice.add(merchant.getDeliveryFee());
        if (discount.compareTo(basePrice) > 0) discount = basePrice;
        BigDecimal actualPrice = basePrice.subtract(discount).max(BigDecimal.ZERO);

        String fullAddress = buildFullAddress(address);

        String orderNo = String.valueOf(SnowflakeIdUtil.generate());
        Order order = new Order();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setMerchantId(request.merchantId());
        order.setStatus(1);
        order.setTotalPrice(totalPrice);
        order.setDeliveryFee(merchant.getDeliveryFee());
        order.setActualPrice(actualPrice);
        order.setDiscount(discount);
        if (request.userCouponId() != null) {
            order.setUserCouponId(Long.parseLong(request.userCouponId()));
        }
        order.setReceiver(address.getReceiver());
        order.setPhone(address.getPhone());
        order.setAddress(fullAddress);
        order.setLongitude(address.getLongitude());
        order.setLatitude(address.getLatitude());
        order.setRemark(request.remark());
        order.setPayType(request.payType());
        orderMapper.insert(order);

        for (DishSnapshotVO snapshot : snapshots) {
            CartItemDTO cartItem = request.items().stream()
                    .filter(i -> i.dishId().equals(snapshot.dishId()))
                    .findFirst().orElseThrow();
            OrderItem item = new OrderItem();
            item.setOrderId(order.getId());
            item.setDishId(snapshot.dishId());
            item.setDishName(snapshot.dishName());
            item.setDishImage(snapshot.dishImage());
            item.setSpec(cartItem.spec());
            item.setUnitPrice(snapshot.unitPrice());
            item.setQuantity(snapshot.quantity());
            item.setSubtotal(snapshot.unitPrice().multiply(BigDecimal.valueOf(snapshot.quantity())));
            orderItemMapper.insert(item);
        }

        cartService.clearCart(userId, request.merchantId());
        if (request.userCouponId() != null) {
            couponService.markUsed(request.userCouponId());
        }
        log.info("订单提交成功，orderNo={}, actualPrice={}", orderNo, actualPrice);
        return new SubmitOrderVO(orderNo, actualPrice);
    }

    public PageResult<OrderVO> listMyOrders(Long userId, OrderPageQuery query) {
        Page<Order> page = new Page<>(query.page(), query.size());
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .eq(query.status() != null, Order::getStatus, query.status())
                .orderByDesc(Order::getCreatedAt);
        Page<Order> result = orderMapper.selectPage(page, wrapper);
        return toPageResult(result);
    }

    public OrderVO getDetail(Long userId, String orderNo) {
        Order order = getOrderOrThrow(orderNo);
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看此订单");
        }
        return toVO(order);
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long userId, String orderNo) {
        String lockKey = LOCK_KEY_PREFIX + orderNo;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, 1, Duration.ofSeconds(30));
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "订单处理中，请稍后再试");
        }
        try {
            Order order = getOrderOrThrow(orderNo);
            if (!order.getUserId().equals(userId)) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权取消此订单");
            }
            if (order.getStatus() != 1 && order.getStatus() != 2) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "当前订单状态不可取消");
            }
            int updated = orderMapper.update(null, new LambdaUpdateWrapper<Order>()
                    .eq(Order::getOrderNo, orderNo)
                    .in(Order::getStatus, java.util.List.of(1, 2))
                    .set(Order::getStatus, 7)
                    .set(Order::getCancelReason, "用户主动取消"));
            if (updated == 0) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "订单状态已变更，取消失败");
            }
            revertOrderStock(order);
            if (order.getUserCouponId() != null) {
                couponService.refund(String.valueOf(order.getUserCouponId()));
            }
            log.info("订单取消成功，orderNo={}", orderNo);
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    public PageResult<OrderVO> listMerchantOrders(Long ownerId, MerchantOrderPageQuery query) {
        checkMerchantOwner(ownerId, query.merchantId());
        Page<Order> page = new Page<>(query.page(), query.size());
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getMerchantId, query.merchantId())
                .eq(query.status() != null, Order::getStatus, query.status())
                .orderByDesc(Order::getCreatedAt);
        return toPageResult(orderMapper.selectPage(page, wrapper));
    }

    @Transactional(rollbackFor = Exception.class)
    public void accept(Long ownerId, String orderNo) {
        Order order = getOrderOrThrow(orderNo);
        checkMerchantOwner(ownerId, order.getMerchantId());
        updateStatusWithLock(order, 2, 3, null);
        log.info("商家接单，orderNo={}", orderNo);
    }

    @Transactional(rollbackFor = Exception.class)
    public void reject(Long ownerId, String orderNo, String reason) {
        Order order = getOrderOrThrow(orderNo);
        checkMerchantOwner(ownerId, order.getMerchantId());
        updateStatusWithLock(order, 2, 7, reason);
        revertOrderStock(order);
        if (order.getUserCouponId() != null) {
            couponService.refund(String.valueOf(order.getUserCouponId()));
        }
        log.info("商家拒单，orderNo={}", orderNo);
    }

    @Transactional(rollbackFor = Exception.class)
    public void ready(Long ownerId, String orderNo) {
        Order order = getOrderOrThrow(orderNo);
        checkMerchantOwner(ownerId, order.getMerchantId());
        updateStatusWithLock(order, 3, 5, null);
        log.info("商家出餐，orderNo={}", orderNo);
    }

    @Transactional(rollbackFor = Exception.class)
    public void complete(Long ownerId, String orderNo) {
        Order order = getOrderOrThrow(orderNo);
        checkMerchantOwner(ownerId, order.getMerchantId());
        updateStatusWithLock(order, 5, 6, null);
        log.info("订单完成，orderNo={}", orderNo);
    }

    public PageResult<OrderVO> listAdminOrders(Long merchantId, Integer status, Integer page, Integer size) {
        Page<Order> pageParam = new Page<>(PageUtil.page(page), PageUtil.size(size));
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(merchantId != null, Order::getMerchantId, merchantId)
                .eq(status != null, Order::getStatus, status)
                .orderByDesc(Order::getCreatedAt);
        return toPageResult(orderMapper.selectPage(pageParam, wrapper));
    }

    @Transactional(rollbackFor = Exception.class)
    public void receive(Long userId, String orderNo) {
        Order order = getOrderOrThrow(orderNo);
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作此订单");
        }
        if (order.getStatus() != 5) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "订单当前状态不可确认收货");
        }
        updateStatusWithLock(order, 5, 6, null);
        log.info("用户确认收货，orderNo={}", orderNo);
    }

    @Transactional(rollbackFor = Exception.class)
    public void payOrder(Long userId, String orderNo) {
        Order order = getOrderOrThrow(orderNo);
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作此订单");
        }
        updateStatusWithLock(order, 1, 2, null);
        log.info("订单支付成功，orderNo={}", orderNo);
    }

    public Order getByOrderNoInternal(String orderNo) {
        return getOrderOrThrow(orderNo);
    }

    private Order getOrderOrThrow(String orderNo) {
        Order order = orderMapper.selectOne(
                new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
        if (order == null) throw new BusinessException(ResultCode.NOT_FOUND, "订单不存在");
        return order;
    }

    private void updateStatusWithLock(Order order, int expected, int newStatus, String reason) {
        String lockKey = LOCK_KEY_PREFIX + order.getOrderNo();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, 1, Duration.ofSeconds(30));
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "订单处理中，请稍后再试");
        }
        try {
            LambdaUpdateWrapper<Order> wrapper = new LambdaUpdateWrapper<Order>()
                    .eq(Order::getOrderNo, order.getOrderNo())
                    .eq(Order::getStatus, expected)
                    .set(Order::getStatus, newStatus);
            if (reason != null) wrapper.set(Order::getCancelReason, reason);
            if (orderMapper.update(null, wrapper) == 0) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "订单状态已变更，操作失败");
            }
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    private void revertOrderStock(Order order) {
        List<StockDeductItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, order.getId()))
                .stream().map(i -> new StockDeductItem(i.getDishId(), i.getQuantity())).toList();
        if (!items.isEmpty()) dishService.revertStock(items);
    }

    private String buildFullAddress(UserAddress addr) {
        StringBuilder sb = new StringBuilder();
        if (addr.getProvince() != null) sb.append(addr.getProvince());
        if (addr.getCity() != null) sb.append(addr.getCity());
        if (addr.getDistrict() != null) sb.append(addr.getDistrict());
        sb.append(addr.getDetail());
        return sb.toString();
    }

    private void checkMerchantOwner(Long ownerId, Long merchantId) {
        Merchant merchant = merchantService.getInternal(merchantId);
        if (!merchant.getOwnerId().equals(ownerId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作此商家的订单");
        }
    }

    private PageResult<OrderVO> toPageResult(Page<Order> page) {
        return PageResult.of(page.getCurrent(), page.getSize(), page.getTotal(),
                page.getRecords().stream().map(this::toVO).toList());
    }

    private OrderVO toVO(Order o) {
        List<OrderItem> items = orderItemMapper.selectList(
                new LambdaQueryWrapper<OrderItem>().eq(OrderItem::getOrderId, o.getId()));
        List<OrderItemVO> itemVOs = items.stream().map(i ->
                new OrderItemVO(i.getId(), i.getDishId(), i.getDishName(), i.getDishImage(),
                        i.getSpec(), i.getUnitPrice(), i.getQuantity(), i.getSubtotal())).toList();
        String merchantName = null;
        try { merchantName = merchantService.getInternal(o.getMerchantId()).getName(); } catch (Exception ignored) {}
        return new OrderVO(o.getId(), o.getOrderNo(), o.getUserId(), o.getMerchantId(), o.getRiderId(),
                o.getStatus(), o.getTotalPrice(), o.getDeliveryFee(), o.getActualPrice(),
                o.getDiscount() != null ? o.getDiscount() : BigDecimal.ZERO,
                o.getReceiver(), o.getPhone(), o.getAddress(), o.getRemark(), o.getCancelReason(),
                o.getPayType(), o.getPayTime(), o.getEstimatedTime(), o.getDeliveryTime(),
                o.getCreatedAt(), itemVOs, merchantName);
    }
}
