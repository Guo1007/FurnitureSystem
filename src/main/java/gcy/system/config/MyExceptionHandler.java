package gcy.system.config;

import gcy.system.entity.dto.Result;
import gcy.system.exception.BusinessException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器，将 Controller 层异常统一转换为 {@link Result} 响应。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Slf4j
@RestControllerAdvice
public class MyExceptionHandler {

    /**
     * 处理 {@link BusinessException}，返回其自带的错误码与消息。
     */
    @ExceptionHandler(BusinessException.class)
    public Result handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 处理 {@link IllegalArgumentException}，返回 400。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("IllegalArgumentException 非法参数: {}", e.getMessage());
        return Result.fail(400, e.getMessage());
    }

    /**
     * 处理 {@code @RequestBody} 参数校验失败，返回第一个字段错误（422）。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public Result handleValidationException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldErrors().get(0);
        log.warn("RequestBody 参数校验失败: {} ({})", fieldError.getDefaultMessage(), fieldError.getField());
        return Result.fail(422, fieldError.getDefaultMessage());
    }

    /**
     * 处理 GET 表单参数绑定失败，返回第一个字段错误（422）。
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public Result handleBindException(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldErrors().get(0);
        log.warn("参数绑定失败: {} ({})", fieldError.getDefaultMessage(), fieldError.getField());
        return Result.fail(422, fieldError.getDefaultMessage());
    }

    /**
     * 处理方法参数约束校验失败，拼接全部违规消息返回（422）。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public Result handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
        log.warn("ConstraintViolation 约束违反: {}", message);
        return Result.fail(422, message);
    }

    /**
     * 处理请求体不可读（如 JSON 格式错误），返回 400。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体不可读: {}", e.getMessage());
        return Result.fail(400, "请求参数格式错误");
    }

    /**
     * 处理缺少必要请求参数，返回 400 并附缺失参数名。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少必要参数: {}", e.getParameterName());
        return Result.fail(400, "缺少必要参数: " + e.getParameterName());
    }

    /**
     * 处理参数类型不匹配，返回 400。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: {} -> {}", e.getName(), e.getRequiredType());
        return Result.fail(400, "参数 " + e.getName() + " 类型不匹配");
    }

    /**
     * 处理不支持的 HTTP 请求方法，返回 405。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("不支持的请求方法: {}", e.getMethod());
        return Result.fail(405, "不支持的请求方法: " + e.getMethod());
    }

    /**
     * 处理资源不存在，返回 404。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result handleNotFound(NoResourceFoundException e) {
        log.warn("资源不存在: {}", e.getResourcePath());
        return Result.fail(404, "请求的资源不存在");
    }

    /**
     * 处理上传文件大小超限，提示不超过 5MB（413）。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public Result handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        log.warn("文件大小超出限制: {}", e.getMessage());
        return Result.fail(413, "上传文件过大，请选择小于 5MB 的文件");
    }

    /**
     * 处理数据库完整性约束冲突（唯一键/外键等），返回 500，不向前端暴露细节。
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.error("数据库操作异常", e);
        return Result.fail(500, "数据操作失败，请稍后重试");
    }

    /**
     * 兜底处理未捕获的异常，返回 500 通用提示。
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result handleException(Exception e) {
        log.error("系统内部错误", e);
        return Result.fail(500, "系统繁忙，请稍后再试");
    }
}
