-- =====================================================================
-- db_auth
-- =====================================================================
USE db_auth;

CREATE TABLE IF NOT EXISTS t_refresh_token (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT       NOT NULL COMMENT '用户 ID',
    token       VARCHAR(512) NOT NULL COMMENT 'refresh token',
    expires_at  DATETIME     NOT NULL COMMENT '过期时间',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_token (token),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='refresh token 表';

-- =====================================================================
-- db_user
-- =====================================================================
USE db_user;

CREATE TABLE IF NOT EXISTS t_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    phone       VARCHAR(20)  NOT NULL COMMENT '手机号',
    nickname    VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '昵称',
    avatar_url  VARCHAR(512) DEFAULT NULL COMMENT '头像 URL',
    gender      TINYINT      NOT NULL DEFAULT 0 COMMENT '性别：0=未知 1=男 2=女',
    role        VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色：USER/MERCHANT/RIDER/ADMIN',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0=禁用 1=正常',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常 1=删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE IF NOT EXISTS t_user_address (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT       NOT NULL COMMENT '用户 ID',
    receiver    VARCHAR(64)  NOT NULL COMMENT '收件人姓名',
    phone       VARCHAR(20)  NOT NULL COMMENT '联系电话',
    province    VARCHAR(32)  DEFAULT NULL COMMENT '省',
    city        VARCHAR(32)  DEFAULT NULL COMMENT '市',
    district    VARCHAR(32)  DEFAULT NULL COMMENT '区',
    detail      VARCHAR(256) NOT NULL COMMENT '详细地址',
    longitude   DECIMAL(10,7) NOT NULL COMMENT '经度',
    latitude    DECIMAL(10,7) NOT NULL COMMENT '纬度',
    is_default  TINYINT      NOT NULL DEFAULT 0 COMMENT '是否默认地址',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户地址表';

-- =====================================================================
-- db_merchant
-- =====================================================================
USE db_merchant;

CREATE TABLE IF NOT EXISTS t_merchant (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    owner_id        BIGINT        NOT NULL COMMENT '商家 owner 用户 ID',
    name            VARCHAR(128)  NOT NULL COMMENT '店铺名称',
    logo_url        VARCHAR(512)  DEFAULT NULL COMMENT '商家 logo URL',
    description     VARCHAR(512)  DEFAULT NULL COMMENT '店铺简介',
    phone           VARCHAR(20)   NOT NULL COMMENT '联系电话',
    province        VARCHAR(30)   DEFAULT NULL COMMENT '省',
    city            VARCHAR(30)   DEFAULT NULL COMMENT '市',
    district        VARCHAR(30)   DEFAULT NULL COMMENT '区',
    address         VARCHAR(256)  NOT NULL COMMENT '店铺详细地址',
    longitude       DECIMAL(10,7) NOT NULL COMMENT '经度',
    latitude        DECIMAL(10,7) NOT NULL COMMENT '纬度',
    delivery_fee    DECIMAL(8,2)  NOT NULL DEFAULT 0.00 COMMENT '配送费',
    min_order_price DECIMAL(8,2)  NOT NULL DEFAULT 0.00 COMMENT '起送价',
    delivery_time   INT           NOT NULL DEFAULT 30 COMMENT '预计配送时间（分钟）',
    status          TINYINT       NOT NULL DEFAULT 0 COMMENT '状态：0=审核中 1=营业中 2=打烊 3=封禁 4=审核拒绝',
    open_time       TIME          DEFAULT NULL COMMENT '营业开始时间',
    close_time      TIME          DEFAULT NULL COMMENT '营业结束时间',
    sales_count     INT           NOT NULL DEFAULT 0 COMMENT '月销量',
    score           DECIMAL(3,1)  NOT NULL DEFAULT 5.0 COMMENT '评分',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_owner_id (owner_id),
    KEY idx_status (status),
    KEY idx_location (longitude, latitude)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家表';

-- =====================================================================
-- db_product
-- =====================================================================
USE db_product;

CREATE TABLE IF NOT EXISTS t_category (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    merchant_id BIGINT       NOT NULL COMMENT '商家 ID',
    name        VARCHAR(64)  NOT NULL COMMENT '分类名称',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '排序（升序）',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：0=隐藏 1=显示',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_merchant_id (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品分类表';

CREATE TABLE IF NOT EXISTS t_dish (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    merchant_id BIGINT        NOT NULL COMMENT '商家 ID',
    category_id BIGINT        NOT NULL COMMENT '分类 ID',
    name        VARCHAR(128)  NOT NULL COMMENT '菜品名称',
    image_url   VARCHAR(512)  DEFAULT NULL COMMENT '菜品图片 URL',
    description VARCHAR(256)  DEFAULT NULL COMMENT '描述',
    price       DECIMAL(8,2)  NOT NULL COMMENT '价格',
    stock       INT           NOT NULL DEFAULT 9999 COMMENT '库存（-1=无限）',
    sales       INT           NOT NULL DEFAULT 0 COMMENT '销量',
    status      TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：0=下架 1=上架',
    sort        INT           NOT NULL DEFAULT 0 COMMENT '排序（升序）',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_merchant_id (merchant_id),
    KEY idx_category_id (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品表';

CREATE TABLE IF NOT EXISTS t_dish_spec (
    id      BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    dish_id BIGINT       NOT NULL COMMENT '菜品 ID',
    name    VARCHAR(64)  NOT NULL COMMENT '规格名称（如：杯型）',
    value   VARCHAR(64)  DEFAULT NULL COMMENT '规格值（如：大杯）',
    price   DECIMAL(8,2) NOT NULL COMMENT '规格附加价格（0 表示默认）',
    PRIMARY KEY (id),
    KEY idx_dish_id (dish_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='菜品规格表';

-- =====================================================================
-- db_order
-- =====================================================================
USE db_order;

CREATE TABLE IF NOT EXISTS t_order (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no        VARCHAR(32)   NOT NULL COMMENT '订单号',
    user_id         BIGINT        NOT NULL COMMENT '用户 ID',
    merchant_id     BIGINT        NOT NULL COMMENT '商家 ID',
    rider_id        BIGINT        DEFAULT NULL COMMENT '骑手 ID',
    status          TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1=待支付 2=待接单 3=备餐中 4=待取餐 5=配送中 6=已完成 7=已取消 8=退款中 9=已退款',
    total_price     DECIMAL(10,2) NOT NULL COMMENT '商品总价',
    delivery_fee    DECIMAL(8,2)  NOT NULL DEFAULT 0.00 COMMENT '配送费',
    actual_price    DECIMAL(10,2) NOT NULL COMMENT '实付金额',
    receiver        VARCHAR(64)   NOT NULL COMMENT '收件人',
    phone           VARCHAR(20)   NOT NULL COMMENT '联系电话',
    address         VARCHAR(256)  NOT NULL COMMENT '收货地址',
    longitude       DECIMAL(10,7) DEFAULT NULL COMMENT '收货地址经度',
    latitude        DECIMAL(10,7) DEFAULT NULL COMMENT '收货地址纬度',
    remark          VARCHAR(256)  DEFAULT NULL COMMENT '备注',
    cancel_reason   VARCHAR(128)  DEFAULT NULL COMMENT '取消原因',
    pay_type        TINYINT       DEFAULT NULL COMMENT '支付方式：1=微信 2=支付宝',
    pay_time        DATETIME      DEFAULT NULL COMMENT '支付时间',
    estimated_time  DATETIME      DEFAULT NULL COMMENT '预计送达时间',
    delivery_time   DATETIME      DEFAULT NULL COMMENT '实际送达时间',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user_id (user_id),
    KEY idx_merchant_id (merchant_id),
    KEY idx_status (status),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

CREATE TABLE IF NOT EXISTS t_order_item (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_id    BIGINT        NOT NULL COMMENT '订单 ID',
    dish_id     BIGINT        NOT NULL COMMENT '菜品 ID',
    dish_name   VARCHAR(128)  NOT NULL COMMENT '菜品名称（快照）',
    dish_image  VARCHAR(512)  DEFAULT NULL COMMENT '菜品图片（快照）',
    spec        VARCHAR(64)   DEFAULT NULL COMMENT '规格（快照）',
    unit_price  DECIMAL(8,2)  NOT NULL COMMENT '单价（快照）',
    quantity    INT           NOT NULL COMMENT '数量',
    subtotal    DECIMAL(10,2) NOT NULL COMMENT '小计',
    PRIMARY KEY (id),
    KEY idx_order_id (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';

CREATE TABLE IF NOT EXISTS t_cart (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT       NOT NULL COMMENT '用户 ID',
    merchant_id BIGINT       NOT NULL COMMENT '商家 ID',
    dish_id     BIGINT       NOT NULL COMMENT '菜品 ID',
    dish_name   VARCHAR(128) NOT NULL COMMENT '菜品名称',
    dish_image  VARCHAR(512) DEFAULT NULL COMMENT '菜品图片',
    unit_price  DECIMAL(8,2) NOT NULL COMMENT '单价',
    spec        VARCHAR(64)  DEFAULT NULL COMMENT '规格',
    quantity    INT          NOT NULL DEFAULT 1 COMMENT '数量',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_merchant_dish_spec (user_id, merchant_id, dish_id, spec),
    KEY idx_user_merchant (user_id, merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='购物车表';

-- =====================================================================
-- db_pay
-- =====================================================================
USE db_pay;

CREATE TABLE IF NOT EXISTS t_payment (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    payment_no      VARCHAR(32)   NOT NULL COMMENT '支付单号',
    order_no        VARCHAR(32)   NOT NULL COMMENT '订单号',
    user_id         BIGINT        NOT NULL COMMENT '用户 ID',
    amount          DECIMAL(10,2) NOT NULL COMMENT '支付金额',
    pay_type        TINYINT       NOT NULL COMMENT '支付方式：1=微信 2=支付宝',
    status          TINYINT       NOT NULL DEFAULT 0 COMMENT '状态：0=待支付 1=支付成功 2=支付失败 3=已退款',
    pay_url         VARCHAR(512)  DEFAULT NULL COMMENT '支付跳转链接',
    third_party_no  VARCHAR(128)  DEFAULT NULL COMMENT '第三方支付流水号',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    pay_time        DATETIME      DEFAULT NULL COMMENT '支付时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_no (payment_no),
    KEY idx_order_no (order_no),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付记录表';

CREATE TABLE IF NOT EXISTS t_refund (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    refund_no   VARCHAR(32)   NOT NULL COMMENT '退款单号',
    payment_no  VARCHAR(32)   NOT NULL COMMENT '原支付单号',
    order_no    VARCHAR(32)   NOT NULL COMMENT '订单号',
    amount      DECIMAL(10,2) NOT NULL COMMENT '退款金额',
    reason      VARCHAR(256)  DEFAULT NULL COMMENT '退款原因',
    status      TINYINT       NOT NULL DEFAULT 0 COMMENT '状态：0=处理中 1=退款成功 2=退款失败',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_no (refund_no),
    KEY idx_order_no (order_no),
    KEY idx_payment_no (payment_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款记录表';

-- =====================================================================
-- db_delivery
-- =====================================================================
USE db_delivery;

CREATE TABLE IF NOT EXISTS t_rider (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id     BIGINT       NOT NULL COMMENT '关联用户 ID',
    name        VARCHAR(64)  NOT NULL COMMENT '骑手姓名',
    phone       VARCHAR(20)  NOT NULL COMMENT '手机号',
    avatar      VARCHAR(512) DEFAULT NULL COMMENT '头像 URL',
    status      TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0=待审核 1=空闲 2=配送中 3=下线 4=封禁',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_id (user_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='骑手表';

CREATE TABLE IF NOT EXISTS t_delivery_order (
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no        VARCHAR(32)   NOT NULL COMMENT '订单号',
    order_id        BIGINT        NOT NULL COMMENT '订单 ID',
    rider_id        BIGINT        DEFAULT NULL COMMENT '骑手 ID',
    merchant_id     BIGINT        NOT NULL COMMENT '商家 ID',
    merchant_name   VARCHAR(128)  NOT NULL COMMENT '商家名称',
    user_address    VARCHAR(256)  NOT NULL COMMENT '用户收货地址',
    user_longitude  DECIMAL(10,7) NOT NULL COMMENT '用户经度',
    user_latitude   DECIMAL(10,7) NOT NULL COMMENT '用户纬度',
    status          TINYINT       NOT NULL DEFAULT 1 COMMENT '状态：1=待抢单 2=已接单/配送中 3=已送达 4=异常',
    accept_time     DATETIME      DEFAULT NULL COMMENT '接单时间',
    delivery_time   DATETIME      DEFAULT NULL COMMENT '送达时间',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_rider_id (rider_id),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='配送单表';
