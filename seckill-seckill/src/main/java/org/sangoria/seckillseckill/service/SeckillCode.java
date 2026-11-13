package org.sangoria.seckillseckill.service;

import org.sangoria.common.result.ErrorCode;

public enum SeckillCode {
    // ---- Lua 返回码（-1 / 0 / 1） ----
    DUPLICATE(-1, ErrorCode.SECKILL_DUPLICATE),
    STOCK_EMPTY(0, ErrorCode.SECKILL_STOCK_EMPTY),
    SUCCESS(1, null),

    // ---- Java 侧校验码（-2 以下，不来自 Lua） ----
    ACTIVITY_NOT_FOUND(-2, ErrorCode.SECKILL_ACTIVITY_NOT_FOUND),
    NOT_STARTED(-3, ErrorCode.SECKILL_NOT_STARTED),
    ENDED(-4, ErrorCode.SECKILL_ENDED),
    ;

    private final long code;
    private final ErrorCode errorCode;

    SeckillCode(long code, ErrorCode errorCode) {
        this.code = code;
        this.errorCode = errorCode;
    }

    /** 仅供 Lua 返回值使用：-1 / 0 / 1 → 枚举 */
    public static SeckillCode from(long luaCode) {
        if (luaCode < -1 || luaCode > 1) {
            throw new IllegalArgumentException("未知 Lua 返回码: " + luaCode);
        }
        return values()[(int) luaCode + 1];
    }

    public ErrorCode errorCode() { return errorCode; }
}