-- =====================================================================
-- V1: 基础表结构（13 张核心表）
-- =====================================================================

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

-- 订单表（含优惠券相关字段，避免了原 init.sql 中的 ALTER TABLE）
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
    discount        DECIMAL(8,2)  NOT NULL DEFAULT 0.00 COMMENT '优惠金额',
    user_coupon_id  BIGINT        DEFAULT NULL COMMENT '使用的用户券ID',
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
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    user_id         BIGINT        NOT NULL,
    coupon_id       BIGINT        NOT NULL,
    title           VARCHAR(64)   NOT NULL COMMENT '券名快照',
    min_order_price DECIMAL(8,2)  NOT NULL COMMENT '最低消费快照',
    discount        DECIMAL(8,2)  NOT NULL COMMENT '面值快照',
    status          TINYINT       NOT NULL DEFAULT 0 COMMENT '0=未用 1=已用 2=过期',
    valid_end       DATETIME      NOT NULL COMMENT '过期时间快照',
    used_at         DATETIME      DEFAULT NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_user_id (user_id),
    KEY idx_coupon_id (coupon_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户领券表';
