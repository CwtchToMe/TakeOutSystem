package com.takeout.coupon;

import com.takeout.common.context.UserContext;
import com.takeout.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "优惠券", description = "领取优惠券、查询我的优惠券、查询可用优惠券")
@RestController
@RequestMapping("/api/coupon")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @Operation(summary = "查询可领取的优惠券列表", description = "返回平台当前发放中、且当前用户尚未领取的优惠券")
    @GetMapping("/available")
    public Result<List<CouponVO>> available() {
        return Result.success(couponService.getAvailable());
    }

    @Operation(summary = "领取优惠券", description = "每人每种优惠券限领一次")
    @PostMapping("/receive/{couponId}")
    public Result<Void> receive(@PathVariable Long couponId) {
        couponService.receive(UserContext.requireUserId(), couponId);
        return Result.success();
    }

    @Operation(summary = "我的优惠券列表", description = "返回当前用户已领取的全部优惠券（含已使用和已过期）")
    @GetMapping("/my")
    public Result<List<UserCouponVO>> my() {
        return Result.success(couponService.getMyCoupons(UserContext.requireUserId()));
    }

    @Operation(summary = "查询结算可用优惠券", description = "传入订单金额，返回满足使用条件的优惠券列表")
    @GetMapping("/usable")
    public Result<List<UserCouponVO>> usable(@RequestParam(defaultValue = "0") BigDecimal orderPrice) {
        return Result.success(couponService.getUsable(UserContext.requireUserId(), orderPrice));
    }
}
