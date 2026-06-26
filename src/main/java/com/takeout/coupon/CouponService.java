package com.takeout.coupon;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.takeout.common.exception.BusinessException;
import com.takeout.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String LOCK_KEY_PREFIX = "lock:coupon:receive:";

    public List<CouponVO> getAvailable() {
        LocalDateTime now = LocalDateTime.now();
        List<Coupon> coupons = couponMapper.selectList(new LambdaQueryWrapper<Coupon>()
                .eq(Coupon::getStatus, 1)
                .le(Coupon::getValidStart, now)
                .ge(Coupon::getValidEnd, now)
                .orderByAsc(Coupon::getMinOrderPrice));
        return coupons.stream().map(this::toVO).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public void receive(Long userId, Long couponId) {
        String lockKey = LOCK_KEY_PREFIX + userId + ":" + couponId;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, 1, Duration.ofSeconds(10));
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "请勿重复领取");
        }
        try {
            Coupon coupon = couponMapper.selectById(couponId);
            if (coupon == null || coupon.getStatus() != 1) {
                throw new BusinessException(ResultCode.NOT_FOUND, "优惠券不存在或已下线");
            }
            LocalDateTime now = LocalDateTime.now();
            if (now.isBefore(coupon.getValidStart()) || now.isAfter(coupon.getValidEnd())) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "优惠券不在有效期内");
            }
            long alreadyReceived = userCouponMapper.selectCount(
                    new LambdaQueryWrapper<UserCoupon>()
                            .eq(UserCoupon::getUserId, userId)
                            .eq(UserCoupon::getCouponId, couponId)
                            .eq(UserCoupon::getStatus, 0));
            if (alreadyReceived > 0) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "您已领取过此优惠券，请先使用");
            }
            int affected = couponMapper.incrementReceivedCount(couponId);
            if (affected == 0) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "优惠券已领完");
            }
            UserCoupon uc = new UserCoupon();
            uc.setUserId(userId);
            uc.setCouponId(couponId);
            uc.setTitle(coupon.getTitle());
            uc.setMinOrderPrice(coupon.getMinOrderPrice());
            uc.setDiscount(coupon.getDiscount());
            uc.setStatus(0);
            uc.setValidEnd(coupon.getValidEnd());
            userCouponMapper.insert(uc);
            log.info("用户领券成功，userId={}, couponId={}", userId, couponId);
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    public List<UserCouponVO> getMyCoupons(Long userId) {
        List<UserCoupon> list = userCouponMapper.selectList(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .orderByDesc(UserCoupon::getCreatedAt));
        return list.stream().map(this::toUserCouponVO).toList();
    }

    public List<UserCouponVO> getUsable(Long userId, BigDecimal orderPrice) {
        LocalDateTime now = LocalDateTime.now();
        List<UserCoupon> list = userCouponMapper.selectList(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .eq(UserCoupon::getStatus, 0)
                        .ge(UserCoupon::getValidEnd, now)
                        .le(UserCoupon::getMinOrderPrice, orderPrice)
                        .orderByDesc(UserCoupon::getDiscount));
        return list.stream().map(this::toUserCouponVO).toList();
    }

    public BigDecimal validateAndGetDiscount(Long userId, String userCouponIdStr, BigDecimal orderPrice) {
        Long userCouponId = Long.parseLong(userCouponIdStr);
        UserCoupon uc = userCouponMapper.selectById(userCouponId);
        if (uc == null || !uc.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无效的优惠券");
        }
        if (uc.getStatus() != 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "优惠券已使用或已过期");
        }
        if (LocalDateTime.now().isAfter(uc.getValidEnd())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "优惠券已过期");
        }
        if (orderPrice.compareTo(uc.getMinOrderPrice()) < 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR,
                    "订单金额不满足优惠券使用条件（满" + uc.getMinOrderPrice() + "元可用）");
        }
        return uc.getDiscount();
    }

    @Transactional(rollbackFor = Exception.class)
    public void markUsed(String userCouponIdStr) {
        Long userCouponId = Long.parseLong(userCouponIdStr);
        int affected = userCouponMapper.markUsed(userCouponId);
        if (affected == 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "优惠券核销失败，请重试");
        }
    }

    public void refund(String userCouponIdStr) {
        if (userCouponIdStr == null) return;
        try {
            Long userCouponId = Long.parseLong(userCouponIdStr);
            userCouponMapper.refund(userCouponId);
        } catch (Exception e) {
            log.warn("退券失败，userCouponId={}", userCouponIdStr, e);
        }
    }

    private CouponVO toVO(Coupon c) {
        return new CouponVO(c.getId(), c.getTitle(), c.getType(), c.getMinOrderPrice(),
                c.getDiscount(), c.getTotalCount(), c.getReceivedCount(),
                c.getValidStart(), c.getValidEnd());
    }

    private UserCouponVO toUserCouponVO(UserCoupon uc) {
        return new UserCouponVO(String.valueOf(uc.getId()), uc.getCouponId(), uc.getTitle(),
                uc.getMinOrderPrice(), uc.getDiscount(), uc.getStatus(),
                uc.getValidEnd(), uc.getCreatedAt());
    }
}
