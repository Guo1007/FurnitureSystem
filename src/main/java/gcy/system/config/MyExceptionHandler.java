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
 * 全局异常处理器。
 * <p>
 * 使用 {@link RestControllerAdvice} 统一拦截 Controller 层抛出的各类异常，
 * 将异常信息转换为统一的 {@link Result} 响应格式返回给客户端。
 * 涵盖业务异常、参数校验异常、HTTP 方法不匹配、资源不存在、文件上传超限、
 * 数据库操作异常以及未知系统异常等多种场景。
 * </p>
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Slf4j
@RestControllerAdvice
public class MyExceptionHandler {

    /**
     * 处理业务异常 {@link BusinessException}。
     *
     * @param e 业务异常对象，包含自定义的错误码和错误消息
     * @return 包含业务异常错误码和错误消息的统一响应结果
     */
    @ExceptionHandler(BusinessException.class)
    public Result handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 处理非法参数异常 {@link IllegalArgumentException}。
     *
     * @param e 非法参数异常对象
     * @return 包含 HTTP 400 状态码和异常消息的统一响应结果
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("IllegalArgumentException 非法参数: {}", e.getMessage());
        return Result.fail(400, e.getMessage());
    }

    /**
     * 处理 {@code @RequestBody} 注解的参数校验失败异常 {@link MethodArgumentNotValidException}。
     *
     * @param e 方法参数校验异常对象，包含校验失败的字段信息
     * @return 包含 HTTP 422 状态码和第一个校验失败字段错误消息的统一响应结果
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public Result handleValidationException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldErrors().get(0);
        log.warn("RequestBody 参数校验失败: {} ({})", fieldError.getDefaultMessage(), fieldError.getField());
        return Result.fail(422, fieldError.getDefaultMessage());
    }

    /**
     * 处理参数绑定异常 {@link BindException}（通常发生在 GET 请求表单参数绑定失败时）。
     *
     * @param e 参数绑定异常对象，包含绑定失败的字段信息
     * @return 包含 HTTP 422 状态码和第一个绑定失败字段错误消息的统一响应结果
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public Result handleBindException(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldErrors().get(0);
        log.warn("参数绑定失败: {} ({})", fieldError.getDefaultMessage(), fieldError.getField());
        return Result.fail(422, fieldError.getDefaultMessage());
    }

    /**
     * 处理约束违反异常 {@link ConstraintViolationException}（通常发生在方法参数校验失败时，如 {@code @Validated} 在类级别使用）。
     *
     * @param e 约束违反异常对象，包含所有违反约束的详细信息
     * @return 包含 HTTP 422 状态码和所有约束违反消息（以逗号分隔）的统一响应结果
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
     * 处理 HTTP 消息不可读异常 {@link HttpMessageNotReadableException}（如请求体 JSON 格式错误）。
     *
     * @param e HTTP 消息不可读异常对象
     * @return 包含 HTTP 400 状态码和通用错误提示的统一响应结果
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体不可读: {}", e.getMessage());
        return Result.fail(400, "请求参数格式错误");
    }

    /**
     * 处理缺少必要请求参数异常 {@link MissingServletRequestParameterException}。
     *
     * @param e 缺少请求参数异常对象，包含缺失的参数名
     * @return 包含 HTTP 400 状态码和缺失参数名称的统一响应结果
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("缺少必要参数: {}", e.getParameterName());
        return Result.fail(400, "缺少必要参数: " + e.getParameterName());
    }

    /**
     * 处理方法参数类型不匹配异常 {@link MethodArgumentTypeMismatchException}。
     *
     * @param e 方法参数类型不匹配异常对象，包含参数名称和期望的类型
     * @return 包含 HTTP 400 状态码和参数类型不匹配错误提示的统一响应结果
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e) {
        log.warn("参数类型不匹配: {} -> {}", e.getName(), e.getRequiredType());
        return Result.fail(400, "参数 " + e.getName() + " 类型不匹配");
    }

    /**
     * 处理不支持的 HTTP 请求方法异常 {@link HttpRequestMethodNotSupportedException}。
     *
     * @param e 不支持的请求方法异常对象，包含客户端使用的 HTTP 方法
     * @return 包含 HTTP 405 状态码和错误提示的统一响应结果
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("不支持的请求方法: {}", e.getMethod());
        return Result.fail(405, "不支持的请求方法: " + e.getMethod());
    }

    /**
     * 处理资源不存在异常 {@link NoResourceFoundException}。
     *
     * @param e 资源不存在异常对象，包含请求的资源路径
     * @return 包含 HTTP 404 状态码和通用错误提示的统一响应结果
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result handleNotFound(NoResourceFoundException e) {
        log.warn("资源不存在: {}", e.getResourcePath());
        return Result.fail(404, "请求的资源不存在");
    }

    /**
     * 处理文件上传大小超限异常 {@link MaxUploadSizeExceededException}。
     *
     * @param e 文件上传大小超限异常对象
     * @return 包含 HTTP 413 状态码和文件大小限制提示的统一响应结果
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.PAYLOAD_TOO_LARGE)
    public Result handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        log.warn("文件大小超出限制: {}", e.getMessage());
        return Result.fail(413, "上传文件过大，请选择小于 5MB 的文件");
    }

    /**
     * 处理数据库数据完整性违反异常 {@link DataIntegrityViolationException}（如唯一约束冲突、外键约束冲突等）。
     *
     * @param e 数据完整性违反异常对象
     * @return 包含 HTTP 500 状态码和通用错误提示的统一响应结果
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.error("数据库操作异常", e);
        return Result.fail(500, "数据操作失败，请稍后重试");
    }

    /**
     * 处理所有未被上述处理器捕获的未知异常 {@link Exception}，作为全局兜底异常处理器。
     *
     * @param e 未知异常对象
     * @return 包含 HTTP 500 状态码和通用系统繁忙提示的统一响应结果
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result handleException(Exception e) {
        log.error("系统内部错误", e);
        return Result.fail(500, "系统繁忙，请稍后再试");
    }
}
