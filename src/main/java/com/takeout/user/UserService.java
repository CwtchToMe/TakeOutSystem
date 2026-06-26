package com.takeout.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.takeout.common.exception.BusinessException;
import com.takeout.common.result.PageResult;
import com.takeout.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;

    @Transactional(rollbackFor = Exception.class)
    public User getOrCreate(String phone) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getPhone, phone));
        if (user == null) {
            user = new User();
            user.setPhone(phone);
            user.setNickname("用户" + phone.substring(phone.length() - 4));
            user.setRole("CUSTOMER");
            user.setStatus(1);
            user.setGender(0);
            userMapper.insert(user);
            log.info("新用户注册，userId={}, phone={}", user.getId(), phone);
        }
        return user;
    }

    public UserVO getProfile(Long userId) {
        User user = getByIdOrThrow(userId);
        return toVO(user);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateProfile(Long userId, UpdateProfileRequest request) {
        User user = getByIdOrThrow(userId);
        if (StringUtils.hasText(request.nickname())) user.setNickname(request.nickname());
        if (request.avatarUrl() != null) user.setAvatarUrl(request.avatarUrl());
        if (request.gender() != null) user.setGender(request.gender());
        userMapper.updateById(user);
    }

    public PageResult<UserVO> adminList(UserPageQuery q) {
        Page<User> pageParam = new Page<>(q.page(), q.size());
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(StringUtils.hasText(q.role()), User::getRole, q.role())
                .eq(q.status() != null, User::getStatus, q.status())
                .and(StringUtils.hasText(q.keyword()), w -> w
                        .like(User::getPhone, q.keyword())
                        .or().like(User::getNickname, q.keyword()))
                .orderByDesc(User::getCreatedAt);
        Page<User> result = userMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result.getCurrent(), result.getSize(),
                result.getTotal(), result.getRecords().stream().map(this::toVO).toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long userId, Integer status) {
        if (status != 0 && status != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "状态值只能为 0 或 1");
        }
        User user = getByIdOrThrow(userId);
        user.setStatus(status);
        userMapper.updateById(user);
    }

    public User getById(Long userId) {
        return getByIdOrThrow(userId);
    }

    private User getByIdOrThrow(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        return user;
    }

    private UserVO toVO(User u) {
        return new UserVO(u.getId(), u.getPhone(), u.getNickname(), u.getAvatarUrl(),
                u.getGender(), u.getRole(), u.getStatus(), u.getCreatedAt());
    }
}
