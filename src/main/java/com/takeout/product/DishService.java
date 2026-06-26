package com.takeout.product;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.takeout.common.exception.BusinessException;
import com.takeout.common.result.PageResult;
import com.takeout.common.result.ResultCode;
import com.takeout.common.util.RedisUtil;
import com.takeout.merchant.Merchant;
import com.takeout.merchant.MerchantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DishService {

    private final DishMapper dishMapper;
    private final DishSpecMapper dishSpecMapper;
    private final MerchantService merchantService;
    private final RedisUtil redisUtil;
    private final StringRedisTemplate stringRedisTemplate;

    private static final String MENU_CACHE_PREFIX = "merchant:menu:";
    private static final String STOCK_KEY_PREFIX = "dish:stock:";

    private static final String STOCK_DEDUCT_LUA = """
            local n = #KEYS
            for i = 1, n do
                local stock = redis.call('GET', KEYS[i])
                if stock == false then return {-1, KEYS[i]} end
                if tonumber(stock) < tonumber(ARGV[i]) then return {-2, KEYS[i]} end
            end
            for i = 1, n do redis.call('DECRBY', KEYS[i], ARGV[i]) end
            return {1}
            """;

    public PageResult<DishVO> listDishes(DishPageQuery query) {
        Page<Dish> pageParam = new Page<>(query.page(), query.size());
        LambdaQueryWrapper<Dish> wrapper = new LambdaQueryWrapper<Dish>()
                .eq(query.merchantId() != null, Dish::getMerchantId, query.merchantId())
                .eq(query.categoryId() != null, Dish::getCategoryId, query.categoryId())
                .like(StringUtils.hasText(query.keyword()), Dish::getName, query.keyword())
                .eq(query.status() != null, Dish::getStatus, query.status())
                .orderByAsc(Dish::getSort).orderByDesc(Dish::getCreatedAt);
        Page<Dish> result = dishMapper.selectPage(pageParam, wrapper);
        return PageResult.of(result.getCurrent(), result.getSize(),
                result.getTotal(), result.getRecords().stream().map(this::toDishVO).toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public Long add(Long userId, DishRequest request) {
        checkOwner(userId, request.merchantId());
        Dish dish = new Dish();
        dish.setMerchantId(request.merchantId());
        dish.setCategoryId(request.categoryId());
        dish.setName(request.name());
        dish.setImageUrl(request.imageUrl());
        dish.setDescription(request.description());
        dish.setPrice(request.price());
        dish.setStock(request.stock() != null ? request.stock() : 999);
        dish.setSales(0);
        dish.setStatus(0);
        dish.setSort(request.sort() != null ? request.sort() : 0);
        dishMapper.insert(dish);
        if (!CollectionUtils.isEmpty(request.specs())) {
            request.specs().forEach(s -> {
                DishSpec spec = new DishSpec();
                spec.setDishId(dish.getId());
                spec.setName(s.name());
                spec.setValue(s.value());
                spec.setPrice(s.price() != null ? s.price() : BigDecimal.ZERO);
                dishSpecMapper.insert(spec);
            });
        }
        stringRedisTemplate.opsForValue().set(STOCK_KEY_PREFIX + dish.getId(), String.valueOf(dish.getStock()));
        return dish.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long userId, Long dishId, DishRequest request) {
        Dish dish = getDishAndCheckOwner(userId, dishId);
        if (request.categoryId() != null) dish.setCategoryId(request.categoryId());
        if (StringUtils.hasText(request.name())) dish.setName(request.name());
        if (request.imageUrl() != null) dish.setImageUrl(request.imageUrl());
        if (request.description() != null) dish.setDescription(request.description());
        if (request.price() != null) dish.setPrice(request.price());
        if (request.stock() != null) {
            dish.setStock(request.stock());
            stringRedisTemplate.opsForValue().set(STOCK_KEY_PREFIX + dishId, String.valueOf(request.stock()));
        }
        if (request.sort() != null) dish.setSort(request.sort());
        dishMapper.updateById(dish);
        if (request.specs() != null) {
            dishSpecMapper.delete(new LambdaQueryWrapper<DishSpec>().eq(DishSpec::getDishId, dishId));
            request.specs().forEach(s -> {
                DishSpec spec = new DishSpec();
                spec.setDishId(dishId);
                spec.setName(s.name());
                spec.setValue(s.value());
                spec.setPrice(s.price() != null ? s.price() : BigDecimal.ZERO);
                dishSpecMapper.insert(spec);
            });
        }
        redisUtil.delete(MENU_CACHE_PREFIX + dish.getMerchantId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long userId, Long dishId) {
        Dish dish = getDishAndCheckOwner(userId, dishId);
        if (dish.getStatus() == 1) throw new BusinessException(ResultCode.BUSINESS_ERROR, "请先下架菜品再删除");
        dishMapper.deleteById(dishId);
        dishSpecMapper.delete(new LambdaQueryWrapper<DishSpec>().eq(DishSpec::getDishId, dishId));
        stringRedisTemplate.delete(STOCK_KEY_PREFIX + dishId);
        redisUtil.delete(MENU_CACHE_PREFIX + dish.getMerchantId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long userId, Long dishId, UpdateDishStatusRequest request) {
        if (request.status() != 0 && request.status() != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "状态只能为 0(下架) 或 1(上架)");
        }
        Dish dish = getDishAndCheckOwner(userId, dishId);
        dish.setStatus(request.status());
        dishMapper.updateById(dish);
        redisUtil.delete(MENU_CACHE_PREFIX + dish.getMerchantId());
    }

    public List<MenuCategoryVO> getMenu(Long merchantId) {
        Object cached = redisUtil.get(MENU_CACHE_PREFIX + merchantId);
        if (cached != null) {
            @SuppressWarnings("unchecked")
            List<MenuCategoryVO> menu = (List<MenuCategoryVO>) cached;
            return menu;
        }
        List<MenuCategoryVO> menu = dishMapper.selectMenu(merchantId);
        redisUtil.set(MENU_CACHE_PREFIX + merchantId, menu, 10, TimeUnit.MINUTES);
        return menu;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<DishSnapshotVO> checkAndDeduct(List<StockDeductItem> items) {
        // 合并相同 dishId 的数量，防止购物车历史重复行导致 selectBatchIds size 不匹配
        java.util.LinkedHashMap<Long, Integer> mergedQty = new java.util.LinkedHashMap<>();
        items.forEach(i -> mergedQty.merge(i.dishId(), i.quantity(), Integer::sum));
        List<StockDeductItem> mergedItems = mergedQty.entrySet().stream()
                .map(e -> new StockDeductItem(e.getKey(), e.getValue()))
                .toList();

        List<Long> dishIds = mergedItems.stream().map(StockDeductItem::dishId).toList();
        List<Dish> dishes = dishMapper.selectBatchIds(dishIds);
        if (dishes.size() != dishIds.size()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "存在不存在的菜品");
        }
        Map<Long, Dish> dishMap = dishes.stream().collect(Collectors.toMap(Dish::getId, Function.identity()));
        for (Dish dish : dishes) {
            if (dish.getStatus() != 1) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR, "菜品[" + dish.getName() + "]已下架");
            }
        }

        List<String> keys = mergedItems.stream().map(i -> STOCK_KEY_PREFIX + i.dishId()).toList();
        List<String> args = mergedItems.stream().map(i -> String.valueOf(i.quantity())).toList();
        DefaultRedisScript<List> script = new DefaultRedisScript<>(STOCK_DEDUCT_LUA, List.class);
        List<?> result = stringRedisTemplate.execute(script, keys, args.toArray());

        if (result == null || result.isEmpty()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "库存扣减失败");
        }
        Long code = (Long) result.get(0);
        if (code == -1L) {
            syncStockToRedis(dishes);
            result = stringRedisTemplate.execute(script, keys, args.toArray());
            code = result != null && !result.isEmpty() ? (Long) result.get(0) : -1L;
        }
        if (code == -2L) throw new BusinessException(ResultCode.BUSINESS_ERROR, "菜品库存不足");
        if (code != 1L) throw new BusinessException(ResultCode.BUSINESS_ERROR, "库存扣减异常");

        for (StockDeductItem item : mergedItems) {
            dishMapper.update(null, new LambdaUpdateWrapper<Dish>()
                    .eq(Dish::getId, item.dishId())
                    .setSql("stock = stock - " + item.quantity() + ", sales = sales + " + item.quantity()));
        }
        return mergedItems.stream().map(item -> {
            Dish dish = dishMap.get(item.dishId());
            return new DishSnapshotVO(dish.getId(), dish.getName(),
                    dish.getImageUrl(), dish.getPrice(), item.quantity());
        }).toList();
    }

    public void revertStock(List<StockDeductItem> items) {
        for (StockDeductItem item : items) {
            stringRedisTemplate.opsForValue().increment(STOCK_KEY_PREFIX + item.dishId(), item.quantity());
            dishMapper.update(null, new LambdaUpdateWrapper<Dish>()
                    .eq(Dish::getId, item.dishId())
                    .setSql("stock = stock + " + item.quantity() + ", sales = sales - " + item.quantity()));
        }
    }

    private void checkOwner(Long userId, Long merchantId) {
        Merchant merchant = merchantService.getInternal(merchantId);
        if (!merchant.getOwnerId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作此商家的数据");
        }
    }

    private Dish getDishAndCheckOwner(Long userId, Long dishId) {
        Dish dish = dishMapper.selectById(dishId);
        if (dish == null) throw new BusinessException(ResultCode.NOT_FOUND, "菜品不存在");
        checkOwner(userId, dish.getMerchantId());
        return dish;
    }

    private void syncStockToRedis(List<Dish> dishes) {
        dishes.forEach(d -> stringRedisTemplate.opsForValue()
                .set(STOCK_KEY_PREFIX + d.getId(), String.valueOf(d.getStock())));
    }

    private DishVO toDishVO(Dish d) {
        List<DishSpec> specs = dishSpecMapper.selectList(
                new LambdaQueryWrapper<DishSpec>().eq(DishSpec::getDishId, d.getId()));
        return new DishVO(d.getId(), d.getMerchantId(), d.getCategoryId(),
                d.getName(), d.getImageUrl(), d.getDescription(),
                d.getPrice(), d.getStock(), d.getSales(), d.getStatus(), d.getSort(),
                d.getCreatedAt(),
                specs.stream().map(s -> new DishSpecVO(s.getId(), s.getName(), s.getValue(), s.getPrice())).toList());
    }
}
