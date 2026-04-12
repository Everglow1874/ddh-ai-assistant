package com.ddh.assistant.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 全局异常处理（排除 SSE 连接，SSE 异常由 ChatController 内部处理）
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleValidationException(MethodArgumentNotValidException e,
                                            HttpServletRequest request) {
        if (isSseRequest(request)) return null;
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        log.warn("参数校验失败: {}", message);
        return Result.fail(400, message);
    }

    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleBindException(BindException e, HttpServletRequest request) {
        if (isSseRequest(request)) return null;
        String message = e.getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数绑定失败");
        log.warn("参数绑定失败: {}", message);
        return Result.fail(400, message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleIllegalArgumentException(IllegalArgumentException e,
                                                  HttpServletRequest request) {
        if (isSseRequest(request)) return null;
        log.warn("非法参数: {}", e.getMessage());
        return Result.fail(400, e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleMaxUploadSizeExceededException(MaxUploadSizeExceededException e,
                                                        HttpServletRequest request) {
        if (isSseRequest(request)) return null;
        log.warn("文件上传超过大小限制");
        return Result.fail(400, "文件大小超过限制（最大10MB）");
    }

    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Object handleRuntimeException(RuntimeException e, HttpServletRequest request) {
        if (isSseRequest(request)) return null;
        log.error("运行时异常: ", e);
        return Result.fail(500, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Object handleException(Exception e, HttpServletRequest request) {
        if (isSseRequest(request)) return null;
        log.error("系统异常: ", e);
        return Result.fail(500, "系统内部错误");
    }

    private boolean isSseRequest(HttpServletRequest request) {
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("text/event-stream");
    }
}
