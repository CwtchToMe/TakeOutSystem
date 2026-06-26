package com.takeout.order;

import com.takeout.common.context.UserContext;
import com.takeout.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "购物车", description = "购物车的增删改查，每个用户每家商家独立一个购物车")
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @Operation(summary = "获取购物车列表", description = "返回当前用户在指定商家的所有购物车条目")
    @GetMapping("/{merchantId}")
    public Result<List<CartVO>> getCart(@PathVariable Long merchantId) {
        return Result.success(cartService.getCart(UserContext.requireUserId(), merchantId));
    }

    @Operation(summary = "加入购物车", description = "若该菜品（含规格）已存在则累加数量，否则新建条目")
    @PostMapping("/add")
    public Result<Void> add(@Valid @RequestBody AddCartRequest request) {
        cartService.addToCart(UserContext.requireUserId(), request);
        return Result.success();
    }

    @Operation(summary = "修改购物车数量", description = "quantity <= 0 时自动删除该条目")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestParam Integer quantity) {
        cartService.updateQuantity(UserContext.requireUserId(), id, quantity);
        return Result.success();
    }

    @Operation(summary = "删除购物车单条记录")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        cartService.removeItem(UserContext.requireUserId(), id);
        return Result.success();
    }

    @Operation(summary = "清空购物车", description = "清空当前用户在指定商家的全部购物车条目")
    @DeleteMapping("/clear/{merchantId}")
    public Result<Void> clear(@PathVariable Long merchantId) {
        cartService.clearCart(UserContext.requireUserId(), merchantId);
        return Result.success();
    }
}
