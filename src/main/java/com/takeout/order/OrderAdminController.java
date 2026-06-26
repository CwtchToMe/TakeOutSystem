package com.takeout.order;

import com.takeout.common.context.UserContext;
import com.takeout.common.enums.UserRole;
import com.takeout.common.exception.BusinessException;
import com.takeout.common.result.PageResult;
import com.takeout.common.result.Result;
import com.takeout.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/order")
@RequiredArgsConstructor
public class OrderAdminController {

    private final OrderService orderService;

    @GetMapping("/list")
    public Result<PageResult<OrderVO>> list(
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        if (UserContext.getUserRole() != UserRole.ADMIN) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员可访问");
        }
        return Result.success(orderService.listAdminOrders(merchantId, status, page, size));
    }
}
