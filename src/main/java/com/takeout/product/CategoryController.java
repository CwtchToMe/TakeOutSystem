package com.takeout.product;

import com.takeout.common.context.UserContext;
import com.takeout.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "菜品分类（商家端）", description = "商家管理自己店铺的菜品分类，需商家账号登录")
@RestController
@RequestMapping("/api/product/category")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "查询分类列表", description = "返回指定商家的所有分类，按 sort 字段排序")
    @GetMapping
    public Result<List<Category>> list(@RequestParam Long merchantId) {
        return Result.success(categoryService.list(merchantId));
    }

    @Operation(summary = "新增分类")
    @PostMapping
    public Result<Long> add(@Valid @RequestBody CategoryRequest request) {
        return Result.success(categoryService.add(UserContext.requireUserId(), request));
    }

    @Operation(summary = "修改分类")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody CategoryRequest request) {
        categoryService.update(UserContext.requireUserId(), id, request);
        return Result.success();
    }

    @Operation(summary = "删除分类", description = "分类下有菜品时不允许删除")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(UserContext.requireUserId(), id);
        return Result.success();
    }
}
