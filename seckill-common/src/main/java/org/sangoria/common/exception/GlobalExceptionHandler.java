package org.sangoria.common.exception;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.sangoria.common.result.ErrorCode;
import org.sangoria.common.result.R;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<Void> handleBiz(BizException e) {
        log.warn("biz exception: code={}, msg={}",
                e.getErrorCode().getCode(), e.getErrorCode().getMsg());
        return R.fail(e.getErrorCode());
    }

    /** &#064;Valid  校验 @RequestBody DTO 失败 → 400 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("param validation failed: {}", msg);
        return R.fail(ErrorCode.PARAM_ERROR.getCode(), msg);
    }

    /** &#064;Validated  校验 @RequestParam / @PathVariable 上的 @Min/@NotBlank → 400 */
    @ExceptionHandler(ConstraintViolationException.class)
    public R<Void> handleConstraint(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        log.warn("constraint violation: {}", msg);
        return R.fail(ErrorCode.PARAM_ERROR.getCode(), msg);
    }

    /** JSON 反序列化失败（字段类型不匹配、非法 JSON） → 400 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("body not readable: {}", e.getMessage());
        return R.fail(ErrorCode.PARAM_ERROR.getCode(), "请求体格式错误");
    }

    /** 缺少必填请求参数 → 400 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public R<Void> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("missing param: {}", e.getParameterName());
        return R.fail(ErrorCode.PARAM_ERROR.getCode(), "缺少参数: " + e.getParameterName());
    }

    /** 参数类型不匹配（如 /id 传了 abc） → 400 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public R<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("type mismatch: {}", e.getName());
        return R.fail(ErrorCode.PARAM_ERROR.getCode(), "参数类型错误: " + e.getName());
    }

    /** 兜底：必须有 log，且不向前端泄露 e.getMessage() */
    @ExceptionHandler(Exception.class)
    public R<Void> handleOther(Exception e) {
        log.error("unhandled exception", e);
        return R.fail(ErrorCode.BIZ_ERROR.getCode(), "系统繁忙，请稍后重试");
    }
}