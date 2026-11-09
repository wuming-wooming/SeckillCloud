-- ============================================================
-- SeckillCloud - Product & Seckill Activity 表初始化
-- 执行: docker exec -i pg-dev psql -U dev -d micro_db < docs/sql/product_tables.sql
-- 幂等: 可重复执行
-- ============================================================
SET search_path TO temp;  -- 开发用；生产部署时注释掉，靠 JDBC currentSchema 指定

-- ---------- 商品表 ----------
CREATE TABLE IF NOT EXISTS product (
                                       id          BIGSERIAL PRIMARY KEY,
                                       name        VARCHAR(128)  NOT NULL,
    description TEXT,
    price       NUMERIC(10, 2) NOT NULL CHECK (price >= 0),
    stock       INT            NOT NULL DEFAULT 0 CHECK (stock >= 0),
    status      SMALLINT       NOT NULL DEFAULT 1 CHECK (status IN (0, 1)),
    created_at  TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP      NOT NULL DEFAULT NOW()
    );

CREATE INDEX IF NOT EXISTS idx_product_status ON product(status);

COMMENT ON TABLE  product            IS '商品主表';
COMMENT ON COLUMN product.stock      IS '商品总库存快照；秒杀扣减不改本字段，活动结束后由对账回写';
COMMENT ON COLUMN product.status     IS '1=上架 0=下架';

-- ---------- 秒杀活动表 ----------
CREATE TABLE IF NOT EXISTS seckill_activity (
                                                id              BIGSERIAL PRIMARY KEY,
                                                product_id      BIGINT         NOT NULL,
                                                seckill_price   NUMERIC(10, 2) NOT NULL CHECK (seckill_price >= 0),
    total_stock     INT            NOT NULL CHECK (total_stock > 0),
    available_stock INT            NOT NULL CHECK (available_stock >= 0),
    start_time      TIMESTAMP      NOT NULL,
    end_time        TIMESTAMP      NOT NULL,
    status          SMALLINT       NOT NULL DEFAULT 1 CHECK (status IN (0, 1)),
    created_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_activity_stock_le_total CHECK (available_stock <= total_stock),
    CONSTRAINT ck_activity_time_order     CHECK (end_time > start_time)
    );

CREATE INDEX IF NOT EXISTS idx_activity_product  ON seckill_activity(product_id);
CREATE INDEX IF NOT EXISTS idx_activity_time     ON seckill_activity(start_time, end_time);
CREATE INDEX IF NOT EXISTS idx_activity_status   ON seckill_activity(status);

COMMENT ON TABLE  seckill_activity                   IS '秒杀活动表';
COMMENT ON COLUMN seckill_activity.available_stock   IS 'DB 侧可用库存；Redis 预热值来源于此，扣减走条件 UPDATE';
COMMENT ON COLUMN seckill_activity.status            IS '1=启用 0=禁用';

-- ---------- 样例数据（幂等） ----------
-- 商品：iPhone 17
INSERT INTO product (name, description, price, stock, status)
SELECT 'iPhone 17', '新款旗舰秒杀', 6999.00, 200, 1
    WHERE NOT EXISTS (SELECT 1 FROM product WHERE name = 'iPhone 17');

-- 活动 A：进行中（用于联调 / 压测）
INSERT INTO seckill_activity
(product_id, seckill_price, total_stock, available_stock, start_time, end_time, status)
SELECT p.id, 3999.00, 100, 100, NOW() - INTERVAL '1 hour', NOW() + INTERVAL '1 day', 1
FROM product p
WHERE p.name = 'iPhone 17'
  AND NOT EXISTS (
    SELECT 1 FROM seckill_activity a
    WHERE a.product_id = p.id
  AND a.start_time < NOW() + INTERVAL '1 day'
  AND a.end_time   > NOW() - INTERVAL '1 hour'
    );

-- 活动 B：未来开始（用于测 T2.5 预热：now < start_time 必须允许预热）
INSERT INTO seckill_activity
(product_id, seckill_price, total_stock, available_stock, start_time, end_time, status)
SELECT p.id, 4599.00, 50, 50, NOW() + INTERVAL '1 day', NOW() + INTERVAL '2 days', 1
FROM product p
WHERE p.name = 'iPhone 17'
  AND NOT EXISTS (
    SELECT 1 FROM seckill_activity a
    WHERE a.product_id = p.id
  AND a.start_time >= NOW() + INTERVAL '20 hours'
    );