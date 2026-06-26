package com.takeout.product;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.takeout.common.exception.BusinessException;
import com.takeout.common.result.ResultCode;
import com.takeout.merchant.Merchant;
import com.takeout.merchant.MerchantService;
import com.takeout.product.DishMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryMapper categoryMapper;
    private final MerchantService merchantService;
    private final DishMapper dishMapper;

    public List<Category> list(Long merchantId) {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .eq(Category::getMerchantId, merchantId)
                .orderByAsc(Category::getSort));
    }

    @Transactional(rollbackFor = Exception.class)
    public Long add(Long userId, CategoryRequest request) {
        checkOwner(userId, request.merchantId());
        Category category = new Category();
        category.setMerchantId(request.merchantId());
        category.setName(request.name());
        category.setSort(request.sort() != null ? request.sort() : 0);
        category.setStatus(request.status() != null ? request.status() : 1);
        categoryMapper.insert(category);
        return category.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long userId, Long categoryId, CategoryRequest request) {
        Category category = getCategoryOrThrow(categoryId);
        checkOwner(userId, category.getMerchantId());
        if (request.name() != null) category.setName(request.name());
        if (request.sort() != null) category.setSort(request.sort());
        if (request.status() != null) category.setStatus(request.status());
        categoryMapper.updateById(category);
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long categoryId) {
        Category category = getCategoryOrThrow(categoryId);
        checkOwner(userId, category.getMerchantId());
        long dishCount = dishMapper.selectCount(
                new LambdaQueryWrapper<Dish>().eq(Dish::getCategoryId, categoryId));
        if (dishCount > 0) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "分类下还有 " + dishCount + " 道菜品，请先删除或移走菜品");
        }
        categoryMapper.deleteById(categoryId);
    }

    private void checkOwner(Long userId, Long merchantId) {
        Merchant merchant = merchantService.getInternal(merchantId);
        if (!merchant.getOwnerId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作此商家的数据");
        }
    }

    private Category getCategoryOrThrow(Long categoryId) {
        Category category = categoryMapper.selectById(categoryId);
        if (category == null) throw new BusinessException(ResultCode.NOT_FOUND, "分类不存在");
        return category;
    }
}
