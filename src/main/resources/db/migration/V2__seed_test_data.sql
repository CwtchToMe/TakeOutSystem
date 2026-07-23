-- =====================================================================
-- V2: 测试数据（开发环境）
-- 每次迁移幂等执行：相同数据用 REPLACE INTO 覆盖，不会重复插入
-- =====================================================================

-- 管理员账号（手机号登录，验证码模拟）
REPLACE INTO t_user (id, phone, nickname, role, status) VALUES
(1, '13800000001', '管理员', 'ADMIN', 1),
(2, '13800000002', '测试商家', 'MERCHANT', 1),
(3, '13800000003', '测试用户', 'CUSTOMER', 1),
(4, '13800000004', '快乐汉堡商家', 'MERCHANT', 1);

-- 测试商家
REPLACE INTO t_merchant (id, owner_id, name, logo_url, description, phone, province, city, district,
    address, longitude, latitude, delivery_fee, min_order_price, delivery_time, status, score, sales_count)
VALUES
(1, 2, '香辣料理', '/images/merchants/spicy-cuisine-logo.jpg', '招牌川菜，麻辣鲜香', '13900000001', '北京市', '北京市', '朝阳区',
    '朝阳区建国路88号', 116.4630, 39.9210, 3.00, 20.00, 30, 1, 4.8, 1256),
(2, 4, '快乐汉堡', '/images/merchants/happy-burger-logo.jpg', '美式快餐，现做现卖', '13900000002', '北京市', '北京市', '海淀区',
    '海淀区中关村大街1号', 116.3170, 39.9830, 0.00, 15.00, 20, 1, 4.6, 890);

-- 测试分类
REPLACE INTO t_category (id, merchant_id, name, sort, status) VALUES
(1, 1, '主食', 1, 1),
(2, 1, '小炒', 2, 1),
(3, 1, '饮品', 3, 1),
(4, 2, '汉堡套餐', 1, 1),
(5, 2, '炸鸡', 2, 1);

-- 测试菜品
REPLACE INTO t_dish (id, merchant_id, category_id, name, description, price, stock, sales, status, sort, image_url) VALUES
(1, 1, 1, '川味红烧肉饭', '软糯入味，米饭香浓', 22.00, 100, 356, 1, 1, '/images/dishes/braised-pork-rice.jpg'),
(2, 1, 1, '麻婆豆腐饭', '麻辣鲜香，豆腐嫩滑', 18.00, 100, 289, 1, 2, '/images/dishes/mapo-tofu-rice.jpg'),
(3, 1, 2, '宫保鸡丁', '酸甜微辣，经典川菜', 28.00, 50, 178, 1, 1, '/images/dishes/kung-pao-chicken.jpg'),
(4, 1, 3, '冰镇柠檬茶', '清爽解腻', 8.00, 200, 445, 1, 1, '/images/dishes/iced-lemon-tea.jpg'),
(5, 2, 4, '经典双层牛肉堡套餐', '含薯条+可乐', 35.00, 50, 234, 1, 1, '/images/dishes/beef-burger.jpg'),
(6, 2, 5, '香辣炸鸡腿', '外酥里嫩，香辣过瘾', 16.00, 80, 567, 1, 1, '/images/dishes/fried-chicken.jpg');

-- 测试收货地址
REPLACE INTO t_user_address (id, user_id, receiver, phone, province, city, district, detail,
    longitude, latitude, is_default) VALUES
(1, 3, '张三', '13800000003', '北京市', '北京市', '朝阳区', '朝阳区建国路100号1单元101', 116.4650, 39.9200, 1);

-- 测试优惠券
REPLACE INTO t_coupon (id, title, type, min_order_price, discount, total_count, received_count,
    valid_start, valid_end, status) VALUES
(1, '新人专享券', 1, 20.00, 5.00, 1000, 0, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), 1),
(2, '满30减8', 1, 30.00, 8.00, 500, 0, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), 1),
(3, '无门槛2元券', 1, 0.00, 2.00, 2000, 0, NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 1);
