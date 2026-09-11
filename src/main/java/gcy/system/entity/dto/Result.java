package gcy.system.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 通用API响应结果封装类，用于统一后端接口返回的数据格式。
 * 包含操作状态、消息、数据体、分页总数和业务状态码等字段，
 * 并提供了一系列静态工厂方法以便快速构造成功或失败的响应对象。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {

    /**
     * 操作是否成功
     */
    @Schema(description = "操作是否成功")
    private Boolean success;

    /**
     * 提示消息，通常用于向客户端返回操作结果描述
     */
    @Schema(description = "提示消息")
    private String msg;

    /**
     * 响应携带的数据体，可为任意类型对象或集合
     */
    @Schema(description = "响应数据")
    private Object data;

    /**
     * 分页查询时的总记录数，非分页场景下可为空
     */
    @Schema(description = "分页总记录数")
    private Long total;

    /**
     * 业务状态码，如200表示成功，0表示业务失败，500表示系统错误
     */
    @Schema(description = "业务状态码")
    private Integer code;

    public static Result ok() {
        return new Result(true, null, null, null, 200);
    }

    public static Result ok(Object data) {
        return new Result(true, null, data, null, 200);
    }

    public static Result okMsg(String msg) {
        return new Result(true, msg, null, null, 200);
    }

    public static Result okMsg(String msg, Object data) {
        return new Result(true, msg, data, null, 200);
    }

    public static Result ok(List<?> data, Long total) {
        return new Result(true, null, data, total, 200);
    }

    public static Result fail(String msg) {
        return new Result(false, msg, null, null, 0);
    }

    public static Result fail(Integer code, String msg) {
        return new Result(false, msg, null, null, code);
    }
}
