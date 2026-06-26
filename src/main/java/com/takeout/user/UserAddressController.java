package com.takeout.user;

import com.takeout.common.context.UserContext;
import com.takeout.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "收货地址", description = "用户收货地址的增删改查，支持设置默认地址")
@RestController
@RequestMapping("/api/user/address")
@RequiredArgsConstructor
public class UserAddressController {

    private final UserAddressService addressService;

    @Operation(summary = "获取地址列表", description = "返回当前用户的全部收货地址，默认地址排在最前")
    @GetMapping
    public Result<List<UserAddressVO>> list() {
        return Result.success(addressService.list(UserContext.requireUserId()));
    }

    @Operation(summary = "新增收货地址")
    @PostMapping
    public Result<Long> add(@Valid @RequestBody AddressRequest request) {
        return Result.success(addressService.add(UserContext.requireUserId(), request));
    }

    @Operation(summary = "修改收货地址")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody AddressRequest request) {
        addressService.update(UserContext.requireUserId(), id, request);
        return Result.success();
    }

    @Operation(summary = "删除收货地址")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        addressService.delete(UserContext.requireUserId(), id);
        return Result.success();
    }

    @Operation(summary = "设为默认地址", description = "同时将其他地址的默认标记取消")
    @PutMapping("/{id}/default")
    public Result<Void> setDefault(@PathVariable Long id) {
        addressService.setDefault(UserContext.requireUserId(), id);
        return Result.success();
    }
}
