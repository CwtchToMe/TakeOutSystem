package com.takeout.favorite;

import com.takeout.common.context.UserContext;
import com.takeout.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "收藏", description = "用户收藏/取消收藏商家，查询收藏列表")
@RestController
@RequestMapping("/api/favorite")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    @Operation(summary = "收藏商家")
    @PostMapping("/{merchantId}")
    public Result<Void> add(@PathVariable Long merchantId) {
        favoriteService.add(UserContext.requireUserId(), merchantId);
        return Result.success();
    }

    @Operation(summary = "取消收藏商家")
    @DeleteMapping("/{merchantId}")
    public Result<Void> remove(@PathVariable Long merchantId) {
        favoriteService.remove(UserContext.requireUserId(), merchantId);
        return Result.success();
    }

    @Operation(summary = "我的收藏列表")
    @GetMapping
    public Result<List<FavoriteVO>> list() {
        return Result.success(favoriteService.getMyFavorites(UserContext.requireUserId()));
    }

    @Operation(summary = "检查是否已收藏", description = "返回 true/false，用于页面收藏按钮状态判断")
    @GetMapping("/check/{merchantId}")
    public Result<Boolean> check(@PathVariable Long merchantId) {
        return Result.success(favoriteService.isFavorite(UserContext.requireUserId(), merchantId));
    }
}
