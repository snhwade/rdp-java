package com.riskplatform.bff.adapter;

import com.riskplatform.bff.domain.DownstreamClient.DownstreamException;
import com.riskplatform.common.error.CommonErrorCode;
import com.riskplatform.common.error.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * BFF 统一错误映射（R14.2）。
 *
 * <p>将下游服务返回的结构化错误体 {@code { code, message, fields }} 原样透传给前端，
 * 保留字段级校验错误（{@code fields}），保证表单项错误定位与用户输入保留。
 * 下游状态码同样透传，使前端按 4xx/5xx 区分处理。
 */
@RestControllerAdvice
public class BffExceptionHandler {

    @ExceptionHandler(DownstreamException.class)
    public ResponseEntity<ErrorResponse> handleDownstream(DownstreamException ex) {
        String code = ex.code() != null ? ex.code() : CommonErrorCode.INTERNAL_ERROR.code();
        ErrorResponse body = ErrorResponse.of(code, ex.getMessage(), ex.fields());
        HttpStatus status = HttpStatus.resolve(ex.status());
        if (status == null) {
            status = HttpStatus.BAD_GATEWAY;
        }
        return ResponseEntity.status(status).body(body);
    }
}
