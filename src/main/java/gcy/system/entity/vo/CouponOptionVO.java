package gcy.system.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 单张券在「当前这单」下的可用性与抵扣评估。
 * <p>
 * 按 {@code userCouponId} 与前端已持有的券列表对齐，
 * 前端据此把券分成「可用 / 不可用」两个分组并展示原因。
 * </p>
 *
 * @author 郭名城
 * @date 2026-10-09
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CouponOptionVO {

    /**
     * 用户优惠券记录ID（user_coupon.id），当前端券列表的键
     */
    @Schema(description = "用户优惠券记录ID")
    private Long userCouponId;

    /**
     * 本单是否可用
     */
    @Schema(description = "是否可用于本单")
    private Boolean usable;

    /**
     * 不可用原因（可用时为空串）。
     * 如「差 ¥50 可用」「本单无该分类商品」「已过期」
     */
    @Schema(description = "不可用原因，可用时为空")
    private String reason;

    /**
     * 该券在当前已选组合基础上的边际抵扣额。
     * <p>
     * 口径与前端卡片上的「本单可抵 ¥X」一致：
     * {@code 抵扣(已选其它 + 本券) - 抵扣(已选其它)}。
     * 不可用或加进去也不能多省时为 0。
     * </p>
     */
    @Schema(description = "本单可抵金额（边际）")
    private BigDecimal estimate;
}
