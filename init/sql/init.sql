-- =====================================================================
-- 外卖系统单体数据库初始化
-- 数据库: db_takeout（单库，原多个微服务库合并）
-- =====================================================================

CREATE DATABASE IF NOT EXISTS db_takeout DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE db_takeout;

-- 用户表
CREATE TABLE IF NOT EXISTS t_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    phone       VARCHAR(20)  NOT NULL COMMENT '手机号',
    nickname    VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '昵称',
    avatar_url  VARCHAR(512) DEFAULT NULL COMMENT '头像URL',
    gender      TINYINT      NOT NULL DEFAULT 0 COMMENT '性别：0=未知 1=男 2=女',
    role        VARCHAR(20)  NOT NULL DEFAULT 'CUSTOMER' COMMENT '角色：CUSTOMER/MERCHANT/RIDER/ADMIN',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0=禁用 1=正常',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 用户地址表
CREATE TABLE IF NOT EXISTS t_user_address (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    user_id     BIGINT        NOT NULL,
    receiver    VARCHAR(64)   NOT NULL,
    phone       VARCHAR(20)   NOT NULL,
    province    VARCHAR(32)   DEFAULT NULL,
    city        VARCHAR(32)   DEFAULT NULL,
    district    VARCHAR(32)   DEFAULT NULL,
    detail      VARCHAR(256)  NOT NULL,
    longitude   DECIMAL(10,7) NOT NULL,
    latitude    DECIMAL(10,7) NOT NULL,
    is_default  TINYINT       NOT NULL DEFAULT 0,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户地址表';

-- 商家表
CREATE TABLE IF NOT EXISTS t_merchant (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    owner_id        BIGINT        NOT NULL COMMENT '商家owner用户ID',
    name            VARCHAR(128)  NOT NULL,
    logo_url        VARCHAR(512)  DEFAULT NULL,
    description     VARCHAR(512)  DEFAULT NULL,
    phone           VARCHAR(20)   NOT NULL,
    province        VARCHAR(30)   DEFAULT NULL,
    city            VARCHAR(30)   DEFAULT NULL,
    district        VARCHAR(30)   DEFAULT NULL,
    address         VARCHAR(256)  NOT NULL,
    longitude       DECIMAL(10,7) NOT NULL,
    latitude        DECIMAL(10,7) NOT NULL,
    delivery_fee    DECIMAL(8,2)  NOT NULL DEFAULT 0.00,
    min_order_price DECIMAL(8,2)  NOT NULL DEFAULT 0.00,
    delivery_time   INT           NOT NULL DEFAULT 30 COMMENT '预计配送时间(分钟)',
    status          TINYINT       NOT NULL DEFAULT 0 COMMENT '0=审核中 1=营业中 2=打烊 3=封禁 4=审核拒绝',
    open_time       TIME          DEFAULT NULL,
    close_time      TIME          DEFAULT NULL,
    sales_count     INT           NOT NULL DEFAULT 0,
    score           DECIMAL(3,1)  NOT NULL DEFAULT 5.0,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_owner_id (owner_id),
    KEY idx_status (status),
    KEY idx_location (longitude, latitude)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家表';

-- 菜品分类表
CREATE TABLE IF NOT EXISTS t_category (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    merchant_id BIGINT      NOT NULL,
    name        VARCHAR(64) NOT NULL,
    sort        INT         NOT NULL DEFAULT 0,
    status      TINYINT     NOT NULL DEFAULT 1 COMMENT '0=隐藏 1=显示',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_merchant_id (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品分类表';

-- 菜品表
CREATE TABLE IF NOT EXISTS t_dish (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    merchant_id BIGINT        NOT NULL,
    category_id BIGINT        NOT NULL,
    name        VARCHAR(128)  NOT NULL,
    image_url   VARCHAR(512)  DEFAULT NULL,
    description VARCHAR(256)  DEFAULT NULL,
    price       DECIMAL(8,2)  NOT NULL,
    stock       INT           NOT NULL DEFAULT 999 COMMENT '-1=无限',
    sales       INT           NOT NULL DEFAULT 0,
    status      TINYINT       NOT NULL DEFAULT 1 COMMENT '0=下架 1=上架',
    sort        INT           NOT NULL DEFAULT 0,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_merchant_id (merchant_id),
    KEY idx_category_id (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品表';

-- 菜品规格表
CREATE TABLE IF NOT EXISTS t_dish_spec (
    id      BIGINT       NOT NULL AUTO_INCREMENT,
    dish_id BIGINT       NOT NULL,
    name    VARCHAR(64)  NOT NULL,
    value   VARCHAR(64)  DEFAULT NULL,
    price   DECIMAL(8,2) NOT NULL DEFAULT 0.00,
    PRIMARY KEY (id),
    KEY idx_dish_id (dish_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品规格表';

-- 订单表
CREATE TABLE IF NOT EXISTS t_order (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    order_no        VARCHAR(32)   NOT NULL COMMENT '订单号',
    user_id         BIGINT        NOT NULL,
    merchant_id     BIGINT        NOT NULL,
    rider_id        BIGINT        DEFAULT NULL,
    status          TINYINT       NOT NULL DEFAULT 2 COMMENT '2=待接单 3=备餐中 5=配送中 6=已完成 7=已取消',
    total_price     DECIMAL(10,2) NOT NULL,
    delivery_fee    DECIMAL(8,2)  NOT NULL DEFAULT 0.00,
    actual_price    DECIMAL(10,2) NOT NULL,
    receiver        VARCHAR(64)   NOT NULL,
    phone           VARCHAR(20)   NOT NULL,
    address         VARCHAR(256)  NOT NULL,
    longitude       DECIMAL(10,7) DEFAULT NULL,
    latitude        DECIMAL(10,7) DEFAULT NULL,
    remark          VARCHAR(256)  DEFAULT NULL,
    cancel_reason   VARCHAR(128)  DEFAULT NULL,
    pay_type        TINYINT       DEFAULT NULL,
    pay_time        DATETIME      DEFAULT NULL,
    estimated_time  DATETIME      DEFAULT NULL,
    delivery_time   DATETIME      DEFAULT NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user_id (user_id),
    KEY idx_merchant_id (merchant_id),
    KEY idx_status (status),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- 订单明细表
CREATE TABLE IF NOT EXISTS t_order_item (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    order_id    BIGINT        NOT NULL,
    dish_id     BIGINT        NOT NULL,
    dish_name   VARCHAR(128)  NOT NULL,
    dish_image  VARCHAR(512)  DEFAULT NULL,
    spec        VARCHAR(64)   DEFAULT NULL,
    unit_price  DECIMAL(8,2)  NOT NULL,
    quantity    INT           NOT NULL,
    subtotal    DECIMAL(10,2) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';

-- 购物车表
CREATE TABLE IF NOT EXISTS t_cart (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    merchant_id BIGINT       NOT NULL,
    dish_id     BIGINT       NOT NULL,
    dish_name   VARCHAR(128) NOT NULL,
    dish_image  VARCHAR(512) DEFAULT NULL,
    unit_price  DECIMAL(8,2) NOT NULL,
    spec        VARCHAR(64)  DEFAULT NULL,
    quantity    INT          NOT NULL DEFAULT 1,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_merchant_dish_spec (user_id, merchant_id, dish_id, spec),
    KEY idx_user_merchant (user_id, merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车表';

-- 评价表
CREATE TABLE IF NOT EXISTS t_review (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    order_no    VARCHAR(32)  NOT NULL COMMENT '订单号',
    user_id     BIGINT       NOT NULL,
    merchant_id BIGINT       NOT NULL,
    score       TINYINT      NOT NULL COMMENT '1-5分',
    content     VARCHAR(512) DEFAULT NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_merchant_id (merchant_id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单评价表';

-- 收藏表（物理删除，无逻辑删除字段）
CREATE TABLE IF NOT EXISTS t_favorite (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    user_id     BIGINT   NOT NULL,
    merchant_id BIGINT   NOT NULL,
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_merchant (user_id, merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家收藏表';

-- 优惠券模板表
CREATE TABLE IF NOT EXISTS t_coupon (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    title           VARCHAR(64)   NOT NULL COMMENT '券名称',
    type            TINYINT       NOT NULL DEFAULT 1 COMMENT '1=满减',
    min_order_price DECIMAL(8,2)  NOT NULL DEFAULT 0.00 COMMENT '最低消费',
    discount        DECIMAL(8,2)  NOT NULL COMMENT '优惠金额',
    total_count     INT           NOT NULL COMMENT '总发行量',
    received_count  INT           NOT NULL DEFAULT 0 COMMENT '已领数量',
    valid_start     DATETIME      NOT NULL COMMENT '生效开始时间',
    valid_end       DATETIME      NOT NULL COMMENT '生效结束时间',
    status          TINYINT       NOT NULL DEFAULT 1 COMMENT '1=有效 0=无效',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券模板表';

-- 用户领券表
CREATE TABLE IF NOT EXISTS t_user_coupon (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    user_id     BIGINT        NOT NULL,
    coupon_id   BIGINT        NOT NULL,
    title       VARCHAR(64)   NOT NULL COMMENT '券名快照',
    min_order_price DECIMAL(8,2) NOT NULL COMMENT '最低消费快照',
    discount    DECIMAL(8,2)  NOT NULL COMMENT '面值快照',
    status      TINYINT       NOT NULL DEFAULT 0 COMMENT '0=未用 1=已用 2=过期',
    valid_end   DATETIME      NOT NULL COMMENT '过期时间快照',
    used_at     DATETIME      DEFAULT NULL,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_coupon_id (coupon_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户领券表';

-- 为订单表增加优惠相关字段
ALTER TABLE t_order
    ADD COLUMN IF NOT EXISTS discount       DECIMAL(8,2) NOT NULL DEFAULT 0.00 COMMENT '优惠金额' AFTER actual_price,
    ADD COLUMN IF NOT EXISTS user_coupon_id BIGINT DEFAULT NULL COMMENT '使用的用户券ID' AFTER discount;

-- 测试优惠券数据
INSERT IGNORE INTO t_coupon (id, title, type, min_order_price, discount, total_count, received_count,
    valid_start, valid_end, status) VALUES
(1, '新人专享券', 1, 20.00, 5.00, 1000, 0, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), 1),
(2, '满30减8', 1, 30.00, 8.00, 500, 0, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), 1),
(3, '无门槛2元券', 1, 0.00, 2.00, 2000, 0, NOW(), DATE_ADD(NOW(), INTERVAL 7 DAY), 1);

-- =====================================================================
-- 测试数据
-- =====================================================================

-- 管理员账号（手机号登录，验证码模拟）
INSERT IGNORE INTO t_user (id, phone, nickname, role, status) VALUES
(1, '13800000001', '管理员', 'ADMIN', 1),
(2, '13800000002', '测试商家', 'MERCHANT', 1),
(3, '13800000003', '测试用户', 'CUSTOMER', 1);

-- 测试商家（已审核通过，营业中）
INSERT IGNORE INTO t_merchant (id, owner_id, name, logo_url, description, phone, province, city, district,
    address, longitude, latitude, delivery_fee, min_order_price, delivery_time, status, score, sales_count)
VALUES
(1, 2, '香辣料理', '/images/merchants/spicy-cuisine-logo.jpg', '招牌川菜，麻辣鲜香', '13900000001', '北京市', '北京市', '朝阳区',
    '朝阳区建国路88号', 116.4630, 39.9210, 3.00, 20.00, 30, 1, 4.8, 1256),
(2, 2, '快乐汉堡', '/images/merchants/happy-burger-logo.jpg', '美式快餐，现做现卖', '13900000002', '北京市', '北京市', '海淀区',
    '海淀区中关村大街1号', 116.3170, 39.9830, 0.00, 15.00, 20, 1, 4.6, 890);

-- 测试分类
INSERT IGNORE INTO t_category (id, merchant_id, name, sort, status) VALUES
(1, 1, '主食', 1, 1),
(2, 1, '小炒', 2, 1),
(3, 1, '饮品', 3, 1),
(4, 2, '汉堡套餐', 1, 1),
(5, 2, '炸鸡', 2, 1);

-- 测试菜品
INSERT IGNORE INTO t_dish (id, merchant_id, category_id, name, description, price, stock, sales, status, sort, image_url) VALUES
(1, 1, 1, '川味红烧肉饭', '软糯入味，米饭香浓', 22.00, 100, 356, 1, 1, '/images/dishes/braised-pork-rice.jpg'),
(2, 1, 1, '麻婆豆腐饭', '麻辣鲜香，豆腐嫩滑', 18.00, 100, 289, 1, 2, '/images/dishes/mapo-tofu-rice.jpg'),
(3, 1, 2, '宫保鸡丁', '酸甜微辣，经典川菜', 28.00, 50, 178, 1, 1, '/images/dishes/kung-pao-chicken.jpg'),
(4, 1, 3, '冰镇柠檬茶', '清爽解腻', 8.00, 200, 445, 1, 1, '/images/dishes/iced-lemon-tea.jpg'),
(5, 2, 4, '经典双层牛肉堡套餐', '含薯条+可乐', 35.00, 50, 234, 1, 1, '/images/dishes/beef-burger.jpg'),
(6, 2, 5, '香辣炸鸡腿', '外酥里嫩，香辣过瘾', 16.00, 80, 567, 1, 1, '/images/dishes/fried-chicken.jpg');

-- 测试收货地址
INSERT IGNORE INTO t_user_address (id, user_id, receiver, phone, province, city, district, detail,
    longitude, latitude, is_default) VALUES
(1, 3, '张三', '13800000003', '北京市', '北京市', '朝阳区', '朝阳区建国路100号1单元101', 116.4650, 39.9200, 1);
