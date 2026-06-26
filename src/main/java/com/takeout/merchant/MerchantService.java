package com.takeout.merchant;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.takeout.common.exception.BusinessException;
import com.takeout.common.result.PageResult;
import com.takeout.common.result.ResultCode;
import com.takeout.common.util.PageUtil;
import com.takeout.common.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantService {

    private final MerchantMapper merchantMapper;
    private final RedisUtil redisUtil;

    private static final String CACHE_KEY = "merchant:info:";
    private static final int DEFAULT_RADIUS = 5000;

    @Transactional(rollbackFor = Exception.class)
    public Long register(Long ownerId, RegisterMerchantRequest request) {
        long count = merchantMapper.selectCount(
                new LambdaQueryWrapper<Merchant>().eq(Merchant::getOwnerId, ownerId));
        if (count > 0) throw new BusinessException(ResultCode.BUSINESS_ERROR, "您已注册过商家");

        Merchant m = new Merchant();
        m.setOwnerId(ownerId);
        m.setName(request.name());
        m.setLogoUrl(request.logoUrl());
        m.setDescription(request.description());
        m.setPhone(request.phone());
        m.setProvince(request.province());
        m.setCity(request.city());
        m.setDistrict(request.district());
        m.setAddress(request.address());
        m.setLongitude(request.longitude());
        m.setLatitude(request.latitude());
        m.setMinOrderPrice(request.minOrderPrice() != null ? request.minOrderPrice() : BigDecimal.ZERO);
        m.setDeliveryFee(request.deliveryFee() != null ? request.deliveryFee() : BigDecimal.ZERO);
        m.setDeliveryTime(request.deliveryTime() != null ? request.deliveryTime() : 30);
        m.setScore(new BigDecimal("5.0"));
        m.setSalesCount(0);
        m.setOpenTime(request.openTime());
        m.setCloseTime(request.closeTime());
        m.setStatus(0);
        merchantMapper.insert(m);
        log.info("商家注册成功，merchantId={}", m.getId());
        return m.getId();
    }

    public MerchantVO getMy(Long ownerId) {
        return toVO(getByOwnerOrThrow(ownerId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateMy(Long ownerId, UpdateMerchantRequest request) {
        Merchant m = getByOwnerOrThrow(ownerId);
        if (StringUtils.hasText(request.name())) m.setName(request.name());
        if (request.logoUrl() != null) m.setLogoUrl(request.logoUrl());
        if (request.description() != null) m.setDescription(request.description());
        if (StringUtils.hasText(request.phone())) m.setPhone(request.phone());
        if (request.minOrderPrice() != null) m.setMinOrderPrice(request.minOrderPrice());
        if (request.deliveryFee() != null) m.setDeliveryFee(request.deliveryFee());
        if (request.deliveryTime() != null) m.setDeliveryTime(request.deliveryTime());
        if (request.openTime() != null) m.setOpenTime(request.openTime());
        if (request.closeTime() != null) m.setCloseTime(request.closeTime());
        merchantMapper.updateById(m);
        redisUtil.delete(CACHE_KEY + m.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long ownerId, UpdateStatusRequest request) {
        Merchant m = getByOwnerOrThrow(ownerId);
        if (m.getStatus() != 1 && m.getStatus() != 2) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "当前状态不允许切换营业状态");
        }
        if (request.status() != 1 && request.status() != 2) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "状态只能为 1(营业中) 或 2(打烊)");
        }
        m.setStatus(request.status());
        merchantMapper.updateById(m);
        redisUtil.delete(CACHE_KEY + m.getId());
    }

    public MerchantVO getDetail(Long merchantId) {
        try {
            Object cached = redisUtil.get(CACHE_KEY + merchantId);
            if (cached instanceof MerchantVO vo) return vo;
        } catch (Exception e) {
            log.warn("商家缓存反序列化失败，清除并从DB加载，merchantId={}", merchantId);
            try { redisUtil.delete(CACHE_KEY + merchantId); } catch (Exception ignored) {}
        }
        Merchant m = merchantMapper.selectById(merchantId);
        if (m == null) throw new BusinessException(ResultCode.NOT_FOUND, "商家不存在");
        MerchantVO vo = toVO(m);
        redisUtil.set(CACHE_KEY + merchantId, vo, 30, TimeUnit.MINUTES);
        return vo;
    }

    public PageResult<MerchantSimpleVO> nearby(BigDecimal longitude, BigDecimal latitude,
                                               Integer radius, Integer page, Integer size) {
        int r = (radius != null && radius > 0) ? radius : DEFAULT_RADIUS;
        int p = PageUtil.page(page);
        int s = PageUtil.size(size);
        long offset = (long) (p - 1) * s;
        long total = merchantMapper.countNearby(longitude, latitude, r);
        List<MerchantSimpleVO> records = merchantMapper.selectNearby(longitude, latitude, r, offset, s);
        return PageResult.of(p, s, total, records);
    }

    public PageResult<MerchantVO> search(String keyword, Integer page, Integer size) {
        int p = PageUtil.page(page);
        int s = PageUtil.size(size, 20);
        Page<Merchant> pageParam = new Page<>(p, s);
        LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getStatus, 1)
                .and(StringUtils.hasText(keyword), w -> w
                        .like(Merchant::getName, keyword)
                        .or().like(Merchant::getDescription, keyword))
                .orderByDesc(Merchant::getSalesCount);
        Page<Merchant> result = merchantMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result.getCurrent(), result.getSize(),
                result.getTotal(), result.getRecords().stream().map(this::toVO).toList());
    }

    public PageResult<MerchantVO> adminList(MerchantPageQuery query) {
        Page<Merchant> pageParam = new Page<>(query.page(), query.size());
        LambdaQueryWrapper<Merchant> wrapper = new LambdaQueryWrapper<Merchant>()
                .eq(query.status() != null, Merchant::getStatus, query.status())
                .like(StringUtils.hasText(query.keyword()), Merchant::getName, query.keyword())
                .orderByDesc(Merchant::getCreatedAt);
        Page<Merchant> result = merchantMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result.getCurrent(), result.getSize(),
                result.getTotal(), result.getRecords().stream().map(this::toVO).toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public void audit(Long merchantId, AuditRequest request) {
        Merchant m = merchantMapper.selectById(merchantId);
        if (m == null) throw new BusinessException(ResultCode.NOT_FOUND, "商家不存在");
        if (m.getStatus() != 0) throw new BusinessException(ResultCode.BUSINESS_ERROR, "该商家不在审核中状态");
        m.setStatus(Boolean.TRUE.equals(request.approved()) ? 1 : 4);
        merchantMapper.updateById(m);
        redisUtil.delete(CACHE_KEY + merchantId);
    }

    public Merchant getInternal(Long merchantId) {
        Merchant m = merchantMapper.selectById(merchantId);
        if (m == null) throw new BusinessException(ResultCode.NOT_FOUND, "商家不存在");
        return m;
    }

    public void evictCache(Long merchantId) {
        redisUtil.delete(CACHE_KEY + merchantId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateScore(Long merchantId, BigDecimal score) {
        merchantMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Merchant>()
                .eq(Merchant::getId, merchantId)
                .set(Merchant::getScore, score));
        evictCache(merchantId);
    }

    private Merchant getByOwnerOrThrow(Long ownerId) {
        Merchant m = merchantMapper.selectOne(new LambdaQueryWrapper<Merchant>()
                .eq(Merchant::getOwnerId, ownerId).last("LIMIT 1"));
        if (m == null) throw new BusinessException(ResultCode.NOT_FOUND, "您尚未注册商家");
        return m;
    }

    private MerchantVO toVO(Merchant m) {
        return new MerchantVO(m.getId(), m.getOwnerId(), m.getName(), m.getLogoUrl(),
                m.getDescription(), m.getPhone(),
                m.getProvince(), m.getCity(), m.getDistrict(), m.getAddress(),
                m.getLongitude(), m.getLatitude(),
                m.getStatus(), m.getMinOrderPrice(), m.getDeliveryFee(),
                m.getDeliveryTime(), m.getScore(), m.getSalesCount(),
                m.getOpenTime(), m.getCloseTime(), m.getCreatedAt());
    }
}
