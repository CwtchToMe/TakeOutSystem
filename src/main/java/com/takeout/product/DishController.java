package com.takeout.product;

import com.takeout.common.context.UserContext;
import com.takeout.common.result.PageResult;
import com.takeout.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "菜品管理（商家端）", description = "商家管理自己店铺的菜品，需商家账号登录")
@RestController
@RequestMapping("/api/product/dish")
@RequiredArgsConstructor
public class DishController {

    private final DishService dishService;

    @Operation(summary = "查询菜品列表", description = "可按商家、分类、关键词、状态筛选；商家管理自己菜品时传 merchantId")
    @GetMapping
    public Result<PageResult<DishVO>> list(
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(dishService.listDishes(new DishPageQuery(merchantId, categoryId, keyword, status, page, size)));
    }

    @Operation(summary = "新增菜品", description = "只能给自己的店铺新增菜品")
    @PostMapping
    public Result<Long> add(@Valid @RequestBody DishRequest request) {
        return Result.success(dishService.add(UserContext.requireUserId(), request));
    }

    @Operation(summary = "修改菜品", description = "只能修改自己店铺的菜品")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody DishRequest request) {
        dishService.update(UserContext.requireUserId(), id, request);
        return Result.success();
    }

    @Operation(summary = "删除菜品", description = "只能删除自己店铺的菜品")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        dishService.delete(UserContext.requireUserId(), id);
        return Result.success();
    }

    @Operation(summary = "上架/下架菜品", description = "status=1 上架，status=0 下架")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateDishStatusRequest request) {
        dishService.updateStatus(UserContext.requireUserId(), id, request);
        return Result.success();
    }
}
