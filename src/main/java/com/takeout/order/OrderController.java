package com.takeout.order;

import com.takeout.common.context.UserContext;
import com.takeout.common.result.PageResult;
import com.takeout.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "订单（用户端）", description = "用户提交订单、查询订单、取消订单、确认收货")
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "提交订单", description = "从购物车生成订单，提交后购物车自动清空")
    @PostMapping("/submit")
    public Result<SubmitOrderVO> submit(@Valid @RequestBody SubmitOrderRequest request) {
        return Result.success(orderService.submit(UserContext.requireUserId(), request));
    }

    @Operation(summary = "查询我的订单列表", description = "status 不传则查全部；1=待支付 2=待接单 3=备餐中 4=待取餐 5=配送中 6=已完成 7=已取消")
    @GetMapping("/list")
    public Result<PageResult<OrderVO>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.success(orderService.listMyOrders(UserContext.requireUserId(),
                new OrderPageQuery(status, page, size)));
    }

    @Operation(summary = "查询订单详情")
    @GetMapping("/{orderNo}")
    public Result<OrderVO> detail(@PathVariable String orderNo) {
        return Result.success(orderService.getDetail(UserContext.requireUserId(), orderNo));
    }

    @Operation(summary = "取消订单", description = "仅待支付和待接单状态的订单可取消")
    @PostMapping("/cancel/{orderNo}")
    public Result<Void> cancel(@PathVariable String orderNo) {
        orderService.cancel(UserContext.requireUserId(), orderNo);
        return Result.success();
    }

    @Operation(summary = "确认收货", description = "将订单状态从配送中流转到已完成")
    @PostMapping("/receive/{orderNo}")
    public Result<Void> receive(@PathVariable String orderNo) {
        orderService.receive(UserContext.requireUserId(), orderNo);
        return Result.success();
    }
}
