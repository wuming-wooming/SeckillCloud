package org.sangoria.common.result;

import lombok.Getter;

@Getter
public enum ErrorCode {
    // Common
    SUCCESS(200, "success"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "认证失败"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    BIZ_ERROR(500, "业务异常"),

    // User
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_PASSWORD_ERROR(1002, "用户名或密码错误"),
    USER_ALREADY_EXISTS(1003, "用户名已存在"),
    USER_DISABLED(1004, "用户已被禁用"),
    REFRESH_TOKEN_INVALID(1005, "刷新令牌无效或已过期"),
    TOKEN_INVALID(1006, "令牌无效"),

    // Seckill
    SECKILL_STOCK_EMPTY(2001, "库存不足"),
    SECKILL_NOT_STARTED(2002, "活动未开始"),   // T3.4 预留
    SECKILL_ENDED(2003, "活动已结束"),         // T3.4 预留
    SECKILL_DUPLICATE(2004, "请勿重复秒杀"),   // T3.3 预留
    SECKILL_ACTIVITY_NOT_FOUND(2005, "活动不存在"); // T3.4 预留

    private final int code;
    private final String msg;
    ErrorCode(int code, String msg) { this.code = code; this.msg = msg; }
}