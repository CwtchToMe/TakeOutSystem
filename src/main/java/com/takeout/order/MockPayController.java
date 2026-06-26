package com.takeout.order;

import com.takeout.common.context.UserContext;
import com.takeout.common.exception.BusinessException;
import com.takeout.common.result.Result;
import com.takeout.common.result.ResultCode;
import com.takeout.common.util.SnowflakeIdUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Tag(name = "模拟支付", description = "开发/测试环境模拟支付，生产环境替换为真实支付网关")
@RestController
@RequestMapping("/api/pay")
@RequiredArgsConstructor
public class MockPayController {

    private final OrderService orderService;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String KEY_ORDER = "mock:pay:order:";
    private static final String KEY_PNO   = "mock:pay:pno:";
    private static final long   TTL_SEC   = 900L; // 15分钟

    @Operation(summary = "创建支付单", description = "幂等接口，同一订单重复调用返回相同 paymentNo")
    @PostMapping("/create")
    public Result<Map<String, String>> create(@Valid @RequestBody CreatePayRequest req) {
        Long userId = UserContext.requireUserId();
        Order order = orderService.getByOrderNoInternal(req.orderNo());
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作此订单");
        }
        if (order.getStatus() != 1) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "订单不在待支付状态");
        }
        String existing = stringRedisTemplate.opsForValue().get(KEY_ORDER + req.orderNo());
        if (existing != null) {
            return Result.success(Map.of("paymentNo", existing));
        }
        String paymentNo = "PAY" + SnowflakeIdUtil.generate();
        stringRedisTemplate.opsForValue().set(KEY_ORDER + req.orderNo(), paymentNo, TTL_SEC, TimeUnit.SECONDS);
        stringRedisTemplate.opsForValue().set(KEY_PNO + paymentNo, req.orderNo(), TTL_SEC, TimeUnit.SECONDS);
        return Result.success(Map.of("paymentNo", paymentNo));
    }

    @Operation(summary = "查询支付状态", description = "返回支付单号和应付金额")
    @GetMapping("/status/{orderNo}")
    public Result<PayStatusVO> status(@PathVariable String orderNo) {
        Long userId = UserContext.requireUserId();
        Order order = orderService.getByOrderNoInternal(orderNo);
        if (!order.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权查看此支付信息");
        }
        String paymentNo = stringRedisTemplate.opsForValue().get(KEY_ORDER + orderNo);
        return Result.success(new PayStatusVO(paymentNo, order.getActualPrice().toPlainString()));
    }

    @Operation(summary = "模拟支付回调", description = "模拟第三方回调，成功则将订单状态推进为待接单")
    @PostMapping("/callback")
    public Result<Void> callback(@Valid @RequestBody PayCallbackRequest req) {
        Long userId = UserContext.requireUserId();
        String orderNo = stringRedisTemplate.opsForValue().get(KEY_PNO + req.paymentNo());
        if (orderNo == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "支付单不存在或已过期（超过15分钟）");
        }
        if (!Boolean.TRUE.equals(req.success())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "支付失败");
        }
        orderService.payOrder(userId, orderNo);
        stringRedisTemplate.delete(KEY_ORDER + orderNo);
        stringRedisTemplate.delete(KEY_PNO + req.paymentNo());
        return Result.success();
    }
}
