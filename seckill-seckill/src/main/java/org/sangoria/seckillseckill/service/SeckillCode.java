package org.sangoria.seckillseckill.service;

import org.sangoria.common.result.ErrorCode;

public enum SeckillCode {
    DUPLICATE(-1, ErrorCode.SECKILL_DUPLICATE),
    STOCK_EMPTY(0, ErrorCode.SECKILL_STOCK_EMPTY),
    SUCCESS(1, null);

    private final long luaCode;
    private final ErrorCode errorCode;

    SeckillCode(long luaCode, ErrorCode errorCode) {
        this.luaCode = luaCode;
        this.errorCode = errorCode;
    }

    public static SeckillCode from(long code) {
//        for (SeckillCode c : values()) {
//            if (c.luaCode == code) return c;
//        }
//        throw new IllegalArgumentException("未知秒杀返回码: " + code);
        // 加速写法
        if (Math.abs(code) > 1) throw new IllegalArgumentException("未知秒杀返回码: " + code);
        return values()[(int) code + 1];
    }

    public ErrorCode errorCode() { return errorCode; }
}