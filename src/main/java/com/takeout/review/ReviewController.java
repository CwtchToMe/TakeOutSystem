package com.takeout.review;

import com.takeout.common.context.UserContext;
import com.takeout.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "评价", description = "用户对已完成订单发表评价，查看商家评价")
@RestController
@RequestMapping("/api/review")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @Operation(summary = "提交评价", description = "只能对已完成且未评价的订单提交评价，每单限评一次")
    @PostMapping
    public Result<Void> submit(@Valid @RequestBody SubmitReviewRequest request) {
        reviewService.submit(UserContext.requireUserId(), request);
        return Result.success();
    }

    @Operation(summary = "查询商家评价列表", description = "公开接口，无需登录")
    @GetMapping("/merchant/{merchantId}")
    public Result<List<ReviewVO>> getMerchantReviews(@PathVariable Long merchantId) {
        return Result.success(reviewService.getMerchantReviews(merchantId));
    }

    @Operation(summary = "查询我的评价列表")
    @GetMapping("/my")
    public Result<List<ReviewVO>> getMyReviews() {
        return Result.success(reviewService.getMyReviews(UserContext.requireUserId()));
    }

    @Operation(summary = "查询订单评价详情", description = "根据订单号查询该订单的评价，未评价时返回 null")
    @GetMapping("/order/{orderNo}")
    public Result<ReviewVO> getByOrderNo(@PathVariable String orderNo) {
        return Result.success(reviewService.getByOrderNo(orderNo));
    }
}
