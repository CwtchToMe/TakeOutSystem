package com.takeout.merchant;

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

@Tag(name = "商家管理（管理员）", description = "查询所有商家、审核商家入驻，需管理员账号登录（13800000001）")
@RestController
@RequestMapping("/api/admin/merchant")
@RequiredArgsConstructor
public class MerchantAdminController {

    private final MerchantService merchantService;

    @Operation(summary = "查询所有商家", description = "可按状态和关键词筛选；status：0=待审核 1=已通过 2=已拒绝")
    @GetMapping("/list")
    public Result<PageResult<MerchantVO>> list(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        requireAdmin();
        return Result.success(merchantService.adminList(new MerchantPageQuery(status, keyword, page, size)));
    }

    @Operation(summary = "审核商家", description = "status=1 通过，status=2 拒绝；通过后商家可正式营业")
    @PostMapping("/{id}/audit")
    public Result<Void> audit(@PathVariable Long id, @Valid @RequestBody AuditRequest request) {
        requireAdmin();
        merchantService.audit(id, request);
        return Result.success();
    }

    private void requireAdmin() {
        if (UserContext.getUserRole() != UserRole.ADMIN) {
            throw new BusinessException(ResultCode.FORBIDDEN, "仅管理员可访问");
        }
    }
}
