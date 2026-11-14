package org.sangoria.seckillorder.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("seckill_order")
public class SeckillOrder {

    /** 状态：0=PENDING 1=SUCCESS 2=FAILED */
    public static final short STATUS_PENDING  = 0;
    public static final short STATUS_SUCCESS  = 1;
    public static final short STATUS_FAILED   = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String requestId;
    private Long activityId;
    private Long productId;
    private Long userId;
    private Integer quantity;
    private BigDecimal seckillPrice;
    private BigDecimal totalAmount;
    private Short status;
    private String failReason;
    private String kafkaTopic;
    private Long kafkaOffset;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}