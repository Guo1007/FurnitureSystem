package gcy.system.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 定向发放优惠券请求体。
 * <p>
 * 一次可以发给多个用户，每人的张数由 {@code quantity} 指定。
 * </p>
 * <p>
 * <b>字段顺序是有意的：{@code userIds} 放在最后。</b>
 * 审计切面会把这个 DTO 的 {@code toString()} 整个塞进 {@code operation_log.params}，
 * 而该列是 {@code varchar(1000)} 会被截断。把长列表放最后，
 * 能保证 {@code remark}、{@code scene} 这些关键信息优先落在截断线之内。
 * </p>
 *
 * @author 郭名城
 * @date 2026-10-09
 */
@Data
public class CouponGrantDTO {

    /**
     * 要发放的券模板ID。必须是「定向发放」类型的券（issue_type=2）。
     */
    @Schema(description = "优惠券模板ID（必须是定向发放类型的券）")
    @NotNull(message = "请选择要发放的优惠券")
    private Long couponId;

    /**
     * 每个用户发放几张。默认 1。
     */
    @Schema(description = "每人发放张数，1~10")
    @Min(value = 1, message = "每人发放张数至少为 1")
    @Max(value = 10, message = "每人最多发放 10 张")
    private Integer quantity = 1;

    /**
     * 场景编码：compensation / care / general。决定邮件模板与通知口吻。
     * 取值非法时回落为 general（见 {@code CouponGrantScene.fromCode}）。
     */
    @Schema(description = "场景：compensation-补偿 / care-关怀 / general-通用")
    private String scene;

    /**
     * 是否发送站内通知，默认发。
     */
    @Schema(description = "是否发送站内通知")
    private Boolean sendNotification = true;

    /**
     * 是否同时发送邮件，默认不发。
     */
    @Schema(description = "是否同时发送邮件")
    private Boolean sendEmail = false;

    /**
     * 发放原因，可选。会写进通知与邮件正文，便于日后追溯「为什么发这张券」。
     */
    @Schema(description = "发放原因（可选，会出现在通知与邮件中）")
    private String remark;

    /**
     * 接收用户ID列表，至少一个。
     */
    @Schema(description = "接收用户ID列表")
    @NotEmpty(message = "请至少选择一个用户")
    @Size(max = 200, message = "一次最多发放给 200 个用户")
    private List<Long> userIds;
}
