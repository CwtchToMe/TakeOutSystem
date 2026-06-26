-- =====================================================================
-- 测试数据（仅供开发/测试环境使用）
-- =====================================================================

-- 用户数据
USE db_user;

INSERT IGNORE INTO t_user (id, phone, nickname, role, status) VALUES
(1, '13800000001', '测试用户A', 'USER', 1),
(2, '13800000002', '测试商家', 'MERCHANT', 1),
(3, '13800000003', '测试骑手', 'RIDER', 1),
(4, '13800000004', '管理员', 'ADMIN', 1),
(5, '13800000005', '测试用户B', 'USER', 1);

INSERT IGNORE INTO t_user_address (id, user_id, receiver, phone, province, city, district, detail, longitude, latitude, is_default) VALUES
(1, 1, '张三', '13800000001', '广东省', '深圳市', '南山区', '科技园路1号', 113.9305500, 22.5292700, 1),
(2, 1, '张三公司', '13800000001', '广东省', '深圳市', '南山区', '科兴科学园B3栋', 113.9422000, 22.5337000, 0),
(3, 5, '李四', '13800000005', '广东省', '深圳市', '福田区', '华强北路1号', 114.0923000, 22.5447000, 1);

-- 商家数据
USE db_merchant;

INSERT IGNORE INTO t_merchant (id, owner_id, name, description, province, city, district, address, longitude, latitude, phone, delivery_fee, min_order_price, delivery_time, status, open_time, close_time, sales_count, score) VALUES
(1, 2, '好味道川菜馆', '正宗川菜，麻辣鲜香', '广东省', '深圳市', '南山区', '科技园路6号', 113.9310000, 22.5298000, '0755-88888888', 3.00, 15.00, 30, 1, '10:00:00', '22:00:00', 1280, 4.8),
(2, 2, '粤式早茶', '地道粤式点心，港式风味', '广东省', '深圳市', '南山区', '粤海路8号', 113.9356000, 22.5312000, '0755-77777777', 5.00, 20.00, 30, 1, '07:00:00', '14:00:00', 560, 4.6);

-- 商品数据
USE db_product;

INSERT IGNORE INTO t_category (id, merchant_id, name, sort, status) VALUES
(1, 1, '招牌菜', 1, 1),
(2, 1, '汤类', 2, 1),
(3, 1, '主食', 3, 1),
(4, 2, '点心', 1, 1),
(5, 2, '茶水', 2, 1);

INSERT IGNORE INTO t_dish (id, merchant_id, category_id, name, description, price, stock, sales, status) VALUES
(1, 1, 1, '麻婆豆腐', '经典川味麻婆豆腐，麻辣鲜香', 18.00, 100, 358, 1),
(2, 1, 1, '回锅肉', '肥而不腻，香辣可口', 28.00, 80, 220, 1),
(3, 1, 1, '宫保鸡丁', '鲜嫩鸡肉配花生，微辣香甜', 22.00, 90, 195, 1),
(4, 1, 2, '番茄蛋花汤', '酸甜开胃，营养丰富', 8.00, 50, 145, 1),
(5, 1, 3, '白米饭', '优质东北大米', 2.00, 9999, 500, 1),
(6, 2, 4, '虾饺', '新鲜虾肉，皮薄馅靓', 16.00, 60, 280, 1),
(7, 2, 4, '叉烧包', '松软香甜，经典港式', 12.00, 70, 320, 1),
(8, 2, 5, '普洱茶', '醇厚甘润，助消化', 6.00, 200, 180, 1);

INSERT IGNORE INTO t_dish_spec (id, dish_id, name, price) VALUES
(1, 5, '小碗', 0.00),
(2, 5, '大碗', 1.00);

-- 骑手数据
USE db_delivery;

INSERT IGNORE INTO t_rider (id, user_id, name, phone, status) VALUES
(1, 3, '王骑手', '13800000003', 1);
