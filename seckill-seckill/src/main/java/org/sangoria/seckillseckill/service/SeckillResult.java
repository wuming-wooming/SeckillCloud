package org.sangoria.seckillseckill.service;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import org.sangoria.common.result.ErrorCode;

import java.time.LocalDateTime;

/**
 * T3.5 秒杀结果领域对象（同时作为 Redis 存储格式与 API 返回体）。
 * <p>
 * 状态机：QUEUING → SUCCESS / FAIL（后两者为终态，幂等返回）。
 * <p>
 * 时间字段遵循项目统一契约：yyyy-MM-dd HH:mm:ss。
 */
@Data
public class SeckillResult {

    public enum Status {
        /** 处理中：首次 SETNX 成功后到 Lua 完成前 */
        QUEUING,
        /** 成功：Lua 预减库存成功（T3.5 无 Kafka，仅占位） */
        SUCCESS,
        /** 业务失败：确定性失败，携带 errorCode */
        FAIL
    }

    private String requestId;
    private Long activityId;
    private Long userId;
    private Status status;
    /** 仅 FAIL 时非空 */
    private Integer errorCode;
    private String errorMsg;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    /** 工厂：初始 QUEUING */
    public static SeckillResult queuing(String requestId, Long activityId, Long userId) {
        SeckillResult r = new SeckillResult();
        r.requestId = requestId;
        r.activityId = activityId;
        r.userId = userId;
        r.status = Status.QUEUING;
        LocalDateTime now = LocalDateTime.now();
        r.createdAt = now;
        r.updatedAt = now;
        return r;
    }

    /** 状态迁移：QUEUING → SUCCESS */
    public SeckillResult success() {
        this.status = Status.SUCCESS;
        this.updatedAt = LocalDateTime.now();
        return this;
    }

    /** 状态迁移：QUEUING → FAIL */
    public SeckillResult fail(ErrorCode errorCode) {
        this.status = Status.FAIL;
        this.errorCode = errorCode.getCode();
        this.errorMsg = errorCode.getMsg();
        this.updatedAt = LocalDateTime.now();
        return this;
    }

    /** 是否终态（SUCCESS 或 FAIL） */
    public boolean isTerminal() {
        return status == Status.SUCCESS || status == Status.FAIL;
    }
}