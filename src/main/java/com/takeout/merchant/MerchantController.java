package com.takeout.merchant;

import com.takeout.common.context.UserContext;
import com.takeout.common.result.PageResult;
import com.takeout.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@Tag(name = "商家", description = "商家搜索（公开）与商家自身信息管理（需商家登录）")
@RestController
@RequestMapping("/api/merchant")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;

    @Operation(summary = "附近商家", description = "按经纬度查询一定范围内的商家，radius 单位米，默认 5000")
    @GetMapping("/nearby")
    public Result<PageResult<MerchantSimpleVO>> nearby(
            @RequestParam BigDecimal longitude,
            @RequestParam BigDecimal latitude,
            @RequestParam(required = false) Integer radius,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(merchantService.nearby(longitude, latitude, radius, page, size));
    }

    @Operation(summary = "搜索商家", description = "按名称关键词模糊搜索，不传 keyword 返回全部")
    @GetMapping("/search")
    public Result<PageResult<MerchantVO>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return Result.success(merchantService.search(keyword, page, size));
    }

    @Operation(summary = "商家详情", description = "公开接口，无需登录")
    @GetMapping("/{id}")
    public Result<MerchantVO> detail(@PathVariable Long id) {
        return Result.success(merchantService.getDetail(id));
    }

    @Operation(summary = "注册商家", description = "当前登录用户注册为商家，注册后需管理员审核通过才能营业")
    @PostMapping("/register")
    public Result<Long> register(@Valid @RequestBody RegisterMerchantRequest request) {
        return Result.success(merchantService.register(UserContext.requireUserId(), request));
    }

    @Operation(summary = "查询我的商家信息", description = "返回当前商家账号绑定的店铺信息")
    @GetMapping("/my")
    public Result<MerchantVO> getMy() {
        return Result.success(merchantService.getMy(UserContext.requireUserId()));
    }

    @Operation(summary = "修改我的商家信息", description = "修改店名、描述、配送费等基本信息")
    @PutMapping("/my")
    public Result<Void> updateMy(@RequestBody UpdateMerchantRequest request) {
        merchantService.updateMy(UserContext.requireUserId(), request);
        return Result.success();
    }

    @Operation(summary = "切换营业状态", description = "0=休息中 1=营业中，只有审核通过的商家才能切换")
    @PutMapping("/my/status")
    public Result<Void> updateStatus(@Valid @RequestBody UpdateStatusRequest request) {
        merchantService.updateStatus(UserContext.requireUserId(), request);
        return Result.success();
    }
}
