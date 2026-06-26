package com.takeout.review;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.takeout.common.exception.BusinessException;
import com.takeout.common.result.ResultCode;
import com.takeout.merchant.MerchantService;
import com.takeout.order.Order;
import com.takeout.order.OrderService;
import com.takeout.user.User;
import com.takeout.user.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewMapper reviewMapper;
    private final OrderService orderService;
    private final MerchantService merchantService;
    private final UserMapper userMapper;

    @Transactional(rollbackFor = Exception.class)
    public void submit(Long userId, SubmitReviewRequest request) {
        Order order = orderService.getByOrderNoInternal(request.orderNo());
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权评价此订单");
        }
        if (order.getStatus() != 6) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "订单未完成，无法评价");
        }
        long exists = reviewMapper.selectCount(
                new LambdaQueryWrapper<Review>().eq(Review::getOrderNo, request.orderNo()));
        if (exists > 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "该订单已评价");
        }

        Review review = new Review();
        review.setOrderNo(request.orderNo());
        review.setUserId(userId);
        review.setMerchantId(order.getMerchantId());
        review.setScore(request.score());
        review.setContent(request.content());
        reviewMapper.insert(review);

        BigDecimal avg = reviewMapper.avgScoreByMerchant(order.getMerchantId());
        BigDecimal newScore = avg != null ? avg.setScale(1, RoundingMode.HALF_UP) : new BigDecimal("5.0");
        merchantService.updateScore(order.getMerchantId(), newScore);
        log.info("评价提交成功，orderNo={}, score={}", request.orderNo(), request.score());
    }

    public List<ReviewVO> getMerchantReviews(Long merchantId) {
        List<Review> reviews = reviewMapper.selectList(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getMerchantId, merchantId)
                        .orderByDesc(Review::getCreatedAt));
        return enrichWithNickname(reviews);
    }

    public List<ReviewVO> getMyReviews(Long userId) {
        List<Review> reviews = reviewMapper.selectList(
                new LambdaQueryWrapper<Review>()
                        .eq(Review::getUserId, userId)
                        .orderByDesc(Review::getCreatedAt));
        return enrichWithNickname(reviews);
    }

    public ReviewVO getByOrderNo(String orderNo) {
        Review review = reviewMapper.selectOne(
                new LambdaQueryWrapper<Review>().eq(Review::getOrderNo, orderNo));
        if (review == null) return null;
        return enrichWithNickname(List.of(review)).get(0);
    }

    private List<ReviewVO> enrichWithNickname(List<Review> reviews) {
        if (reviews.isEmpty()) return List.of();
        List<Long> userIds = reviews.stream().map(Review::getUserId).distinct().toList();
        Map<Long, String> nicknameMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u.getNickname() != null ? u.getNickname() : "用户"));
        return reviews.stream().map(r -> new ReviewVO(
                r.getId(), r.getOrderNo(), r.getUserId(),
                nicknameMap.getOrDefault(r.getUserId(), "用户"),
                r.getMerchantId(), r.getScore(), r.getContent(), r.getCreatedAt()
        )).toList();
    }
}
