package com.takeout.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.takeout.common.exception.BusinessException;
import com.takeout.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserAddressService {

    private final UserAddressMapper addressMapper;

    public List<UserAddressVO> list(Long userId) {
        List<UserAddress> addresses = addressMapper.selectList(
                new LambdaQueryWrapper<UserAddress>()
                        .eq(UserAddress::getUserId, userId)
                        .orderByDesc(UserAddress::getIsDefault)
                        .orderByDesc(UserAddress::getCreatedAt));
        return addresses.stream().map(this::toVO).toList();
    }

    @Transactional(rollbackFor = Exception.class)
    public Long add(Long userId, AddressRequest request) {
        boolean isDefault = Boolean.TRUE.equals(request.isDefault());
        if (isDefault) clearDefault(userId);
        UserAddress address = new UserAddress();
        address.setUserId(userId);
        address.setReceiver(request.receiver());
        address.setPhone(request.phone());
        address.setProvince(request.province());
        address.setCity(request.city());
        address.setDistrict(request.district());
        address.setDetail(request.detail());
        address.setLongitude(request.longitude());
        address.setLatitude(request.latitude());
        address.setIsDefault(isDefault ? 1 : 0);
        addressMapper.insert(address);
        return address.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long userId, Long addressId, AddressRequest request) {
        UserAddress address = getAndCheckOwner(userId, addressId);
        boolean isDefault = Boolean.TRUE.equals(request.isDefault());
        if (isDefault) clearDefault(userId);
        address.setReceiver(request.receiver());
        address.setPhone(request.phone());
        address.setProvince(request.province());
        address.setCity(request.city());
        address.setDistrict(request.district());
        address.setDetail(request.detail());
        address.setLongitude(request.longitude());
        address.setLatitude(request.latitude());
        address.setIsDefault(request.isDefault() != null ? (isDefault ? 1 : 0) : address.getIsDefault());
        addressMapper.updateById(address);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long addressId) {
        getAndCheckOwner(userId, addressId);
        addressMapper.deleteById(addressId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Long userId, Long addressId) {
        getAndCheckOwner(userId, addressId);
        clearDefault(userId);
        addressMapper.update(null, new LambdaUpdateWrapper<UserAddress>()
                .eq(UserAddress::getId, addressId)
                .set(UserAddress::getIsDefault, 1));
    }

    public UserAddress getById(Long addressId) {
        UserAddress address = addressMapper.selectById(addressId);
        if (address == null) throw new BusinessException(ResultCode.NOT_FOUND, "地址不存在");
        return address;
    }

    private void clearDefault(Long userId) {
        addressMapper.update(null, new LambdaUpdateWrapper<UserAddress>()
                .eq(UserAddress::getUserId, userId)
                .set(UserAddress::getIsDefault, 0));
    }

    private UserAddress getAndCheckOwner(Long userId, Long addressId) {
        UserAddress address = addressMapper.selectById(addressId);
        if (address == null) throw new BusinessException(ResultCode.NOT_FOUND, "地址不存在");
        if (!address.getUserId().equals(userId)) throw new BusinessException(ResultCode.FORBIDDEN, "无权操作");
        return address;
    }

    private UserAddressVO toVO(UserAddress a) {
        return new UserAddressVO(a.getId(), a.getUserId(), a.getReceiver(), a.getPhone(),
                a.getProvince(), a.getCity(), a.getDistrict(), a.getDetail(),
                a.getLongitude(), a.getLatitude(), a.getIsDefault(), a.getCreatedAt());
    }
}
