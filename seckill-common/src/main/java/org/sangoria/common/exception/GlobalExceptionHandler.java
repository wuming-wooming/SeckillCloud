package org.sangoria.common.exception;

import org.sangoria.common.result.ErrorCode;
import org.sangoria.common.result.R;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<Void> handleBiz(BizException e) {
        return R.fail(e.getErrorCode());
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleOther(Exception e) {
        return R.fail(ErrorCode.BIZ_ERROR.getCode(), e.getMessage());
    }
}