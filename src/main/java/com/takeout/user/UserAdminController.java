package com.takeout.user;

import com.takeout.common.context.UserContext;
import com.takeout.common.enums.UserRole;
import com.takeout.common.exception.BusinessException;
import com.takeout.common.result.PageResult;
import com.takeout.common.result.Result;
import com.takeout.common.result.ResultCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户管理（管理员）", description = "查询所有用户、启用/禁用账号，需管理员账号登录（13800000001）")
@RestController
@RequestMapping("/api/admin/user")
@RequiredArgsConstructor
public class UserAdminController {

    private final UserService userService;

    @Operation(summary = "查询所有用户", description = "可按 role、status、keyword（手机号或昵称）筛选")
    @GetMapping("/list")
    public Result<PageResult<UserVO>> list(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        requireAdmin();
        return Result.success(userService.adminList(new UserPageQuery(role, status, keyword, page, size)));
    }

    @Operation(summary = "启用/禁用用户账号", description = "status=1 启用，status=0 禁用")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateUserStatusRequest request) {
        requireAdmin();
        userService.updateStatus(id, request.status());
        return Result.success();
    }

    private void requireAdmin() {
        if (UserContext.getUserRole() != UserRole.ADMIN) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员可访问");
        }
    }
}
