package org.sangoria.common.exception;

import lombok.Getter;
import org.sangoria.common.result.ErrorCode;

@Getter
public class BizException extends RuntimeException {
    private final ErrorCode errorCode;
    public BizException(ErrorCode errorCode) {
        super(errorCode.getMsg());
        this.errorCode = errorCode;
    }
}