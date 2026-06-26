package com.takeout.favorite;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.takeout.merchant.Merchant;
import com.takeout.merchant.MerchantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final MerchantService merchantService;

    @Transactional(rollbackFor = Exception.class)
    public void add(Long userId, Long merchantId) {
        merchantService.getInternal(merchantId);
        Favorite fav = new Favorite();
        fav.setUserId(userId);
        fav.setMerchantId(merchantId);
        try {
            favoriteMapper.insert(fav);
        } catch (DuplicateKeyException e) {
            // 已收藏，幂等处理
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void remove(Long userId, Long merchantId) {
        favoriteMapper.delete(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getMerchantId, merchantId));
    }

    public List<FavoriteVO> getMyFavorites(Long userId) {
        List<Favorite> favorites = favoriteMapper.selectList(
                new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId)
                        .orderByDesc(Favorite::getCreatedAt));
        return favorites.stream().map(f -> {
            Merchant m = merchantService.getInternal(f.getMerchantId());
            return new FavoriteVO(f.getId(), m.getId(), m.getName(), m.getLogoUrl(),
                    m.getScore(), m.getDescription(), m.getDeliveryFee(),
                    m.getDeliveryTime(), f.getCreatedAt());
        }).toList();
    }

    public boolean isFavorite(Long userId, Long merchantId) {
        return favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>()
                .eq(Favorite::getUserId, userId)
                .eq(Favorite::getMerchantId, merchantId)) > 0;
    }

    public long countByUser(Long userId) {
        return favoriteMapper.selectCount(new LambdaQueryWrapper<Favorite>().eq(Favorite::getUserId, userId));
    }
}
