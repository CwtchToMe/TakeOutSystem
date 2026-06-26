package com.takeout.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.takeout.common.exception.BusinessException;
import com.takeout.common.result.ResultCode;
import com.takeout.product.Dish;
import com.takeout.product.DishMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartMapper cartMapper;
    private final DishMapper dishMapper;

    @Transactional(rollbackFor = Exception.class)
    public void addToCart(Long userId, AddCartRequest request) {
        String spec = request.spec();
        Cart existing = cartMapper.selectOne(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getMerchantId, request.merchantId())
                .eq(Cart::getDishId, request.dishId())
                .and(w -> { if (spec != null) w.eq(Cart::getSpec, spec); else w.isNull(Cart::getSpec); }));

        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + request.quantity());
            cartMapper.updateById(existing);
        } else {
            String dishName = request.dishName();
            String dishImage = request.dishImage();
            BigDecimal unitPrice = request.unitPrice();
            if (dishName == null || unitPrice == null) {
                Dish dish = dishMapper.selectById(request.dishId());
                if (dish == null) throw new BusinessException(ResultCode.NOT_FOUND, "菜品不存在");
                if (dishName == null) dishName = dish.getName();
                if (dishImage == null) dishImage = dish.getImageUrl();
                if (unitPrice == null) unitPrice = dish.getPrice();
            }

            Cart cart = new Cart();
            cart.setUserId(userId);
            cart.setMerchantId(request.merchantId());
            cart.setDishId(request.dishId());
            cart.setDishName(dishName);
            cart.setDishImage(dishImage);
            cart.setUnitPrice(unitPrice);
            cart.setSpec(request.spec());
            cart.setQuantity(request.quantity());
            cartMapper.insert(cart);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateQuantity(Long userId, Long cartId, Integer quantity) {
        Cart cart = getAndCheckOwner(userId, cartId);
        if (quantity <= 0) {
            cartMapper.deleteById(cartId);
        } else {
            cart.setQuantity(quantity);
            cartMapper.updateById(cart);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeItem(Long userId, Long cartId) {
        getAndCheckOwner(userId, cartId);
        cartMapper.deleteById(cartId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void clearCart(Long userId, Long merchantId) {
        cartMapper.delete(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getMerchantId, merchantId));
    }

    public List<CartVO> getCart(Long userId, Long merchantId) {
        List<Cart> carts = cartMapper.selectList(new LambdaQueryWrapper<Cart>()
                .eq(Cart::getUserId, userId)
                .eq(Cart::getMerchantId, merchantId)
                .orderByAsc(Cart::getCreatedAt));
        return carts.stream().map(c -> new CartVO(c.getId(), c.getMerchantId(), c.getDishId(),
                c.getDishName(), c.getDishImage(), c.getUnitPrice(), c.getSpec(),
                c.getQuantity(), c.getUpdatedAt())).toList();
    }

    private Cart getAndCheckOwner(Long userId, Long cartId) {
        Cart cart = cartMapper.selectById(cartId);
        if (cart == null) throw new BusinessException(ResultCode.NOT_FOUND, "购物车记录不存在");
        if (!cart.getUserId().equals(userId)) throw new BusinessException(ResultCode.FORBIDDEN, "无权操作");
        return cart;
    }
}
