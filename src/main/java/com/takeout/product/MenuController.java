package com.takeout.product;

import com.takeout.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "菜单", description = "获取商家菜单，按分类分组返回，公开接口无需登录")
@RestController
@RequestMapping("/api/product/menu")
@RequiredArgsConstructor
public class MenuController {

    private final DishService dishService;

    @Operation(summary = "获取商家菜单", description = "返回该商家所有分类及其下的菜品列表，仅返回上架菜品")
    @GetMapping("/{merchantId}")
    public Result<List<MenuCategoryVO>> getMenu(@PathVariable Long merchantId) {
        return Result.success(dishService.getMenu(merchantId));
    }
}
