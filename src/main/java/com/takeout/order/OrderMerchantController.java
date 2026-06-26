package com.takeout.order;

import com.takeout.common.context.UserContext;
import com.takeout.common.result.PageResult;
import com.takeout.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "订单（商家端）", description = "商家接单、拒单、出餐、完成配送，需登录商家账号")
@RestController
@RequestMapping("/api/order/merchant")
@RequiredArgsConstructor
public class OrderMerchantController {

    private final OrderService orderService;

    @Operation(summary = "查询商家订单列表", description = "status 不传查全部；merchantId 必填，且只能查自己店的订单")
    @GetMapping("/list")
    public Result<PageResult<OrderVO>> list(
            @RequestParam Long merchantId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(orderService.listMerchantOrders(UserContext.requireUserId(),
                new MerchantOrderPageQuery(merchantId, status, page, size)));
    }

    @Operation(summary = "接单", description = "将订单从待接单流转到备餐中")
    @PostMapping("/accept/{orderNo}")
    public Result<Void> accept(@PathVariable String orderNo) {
        orderService.accept(UserContext.requireUserId(), orderNo);
        return Result.success();
    }

    @Operation(summary = "拒单", description = "拒绝订单，可附拒单原因")
    @PostMapping("/reject/{orderNo}")
    public Result<Void> reject(@PathVariable String orderNo, @RequestBody(required = false) RejectOrderRequest request) {
        String reason = request != null ? request.reason() : null;
        orderService.reject(UserContext.requireUserId(), orderNo, reason);
        return Result.success();
    }

    @Operation(summary = "出餐完成", description = "将订单从备餐中流转到待取餐")
    @PostMapping("/ready/{orderNo}")
    public Result<Void> ready(@PathVariable String orderNo) {
        orderService.ready(UserContext.requireUserId(), orderNo);
        return Result.success();
    }

    @Operation(summary = "完成配送", description = "将订单从待取餐/配送中流转到已完成")
    @PostMapping("/complete/{orderNo}")
    public Result<Void> complete(@PathVariable String orderNo) {
        orderService.complete(UserContext.requireUserId(), orderNo);
        return Result.success();
    }
}
