package com.takeout.common.context;

import com.takeout.common.enums.UserRole;
import com.takeout.common.exception.BusinessException;
import com.takeout.common.result.ResultCode;

public final class UserContext {

    private UserContext() {}

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<UserRole> USER_ROLE = new ThreadLocal<>();

    public static void setUserId(Long userId) { USER_ID.set(userId); }
    public static Long getUserId() { return USER_ID.get(); }

    public static Long requireUserId() {
        Long id = USER_ID.get();
        if (id == null) throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录");
        return id;
    }

    public static void setUserRole(UserRole role) { USER_ROLE.set(role); }
    public static UserRole getUserRole() { return USER_ROLE.get(); }

    public static void clear() {
        USER_ID.remove();
        USER_ROLE.remove();
    }
}
