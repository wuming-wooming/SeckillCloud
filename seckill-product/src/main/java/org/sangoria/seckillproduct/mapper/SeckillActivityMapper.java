package org.sangoria.seckillproduct.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.sangoria.seckillproduct.entity.SeckillActivity;

@Mapper
public interface SeckillActivityMapper extends BaseMapper<SeckillActivity> {

    /**
     * T2.6 原子扣减活动库存（本轮不调用，T2.6 落地时使用）。
     * 条件 UPDATE 由 PG 行锁保证原子性，无需乐观锁 version 列。
     */
    @Update("UPDATE seckill_activity " +
            "SET available_stock = available_stock - #{qty}, " +
            "    updated_at = NOW() " +
            "WHERE id = #{id} AND available_stock >= #{qty}")
    int deductStock(@Param("id") Long id, @Param("qty") int qty);
}