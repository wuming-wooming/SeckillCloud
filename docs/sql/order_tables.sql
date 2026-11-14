-- ============================================================
-- SeckillCloud - Order 表初始化
-- 执行: docker exec -i pg-dev psql -U dev -d micro_db < docs/sql/order_tables.sql
-- 幂等: 可重复执行
-- ============================================================
SET search_path TO temp;

-- ---------- 秒杀订单表 ----------
CREATE TABLE IF NOT EXISTS seckill_order (
                                             id            BIGSERIAL PRIMARY KEY,
                                             request_id    VARCHAR(64)    NOT NULL,
    activity_id   BIGINT         NOT NULL,
    product_id    BIGINT         NOT NULL,
    user_id       BIGINT         NOT NULL,
    quantity      INT            NOT NULL CHECK (quantity > 0),
    seckill_price NUMERIC(10, 2) NOT NULL CHECK (seckill_price >= 0),
    total_amount  NUMERIC(10, 2) NOT NULL CHECK (total_amount >= 0),
    status        SMALLINT       NOT NULL DEFAULT 0 CHECK (status IN (0, 1, 2)),
    fail_reason   VARCHAR(128),
    kafka_topic   VARCHAR(128),
    kafka_offset  BIGINT,
    created_at    TIMESTAMP      NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP      NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_request_id UNIQUE (request_id)
    );


CREATE INDEX IF NOT EXISTS idx_order_user_activity  ON seckill_order(user_id, activity_id);
CREATE INDEX IF NOT EXISTS idx_order_activity       ON seckill_order(activity_id);
CREATE INDEX IF NOT EXISTS idx_order_status_created ON seckill_order(status, created_at);

COMMENT ON TABLE  seckill_order              IS '秒杀订单表；uk_request_id 是幂等核心';
COMMENT ON COLUMN seckill_order.status       IS '0=PENDING 1=SUCCESS 2=FAILED';
COMMENT ON COLUMN seckill_order.fail_reason  IS 'FAILED 时填错误信息（如"库存不足"）';
COMMENT ON COLUMN seckill_order.kafka_offset IS '消息 offset，便于排查对账';