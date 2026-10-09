package gcy.system.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 下单试算请求体。
 * <p>
 * 与 {@link CartFormDTO} 的区别：试算阶段还没有收货信息，也不需要它。
 * 这里刻意不复用 {@code CartFormDTO} —— 那个类上的
 * {@code @NotBlank consignee/phone/address} 会强制前端先填收货地址才能试算。
 * </p>
 * <p>
 * 价格不从这里来：{@code itemList} 只带「买什么、买几件」，单价一律由后端查库，
 * 与下单走同一套算价逻辑，保证试算金额与实际抵扣一致。
 * </p>
 *
 * @author 郭名城
 * @date 2026-10-09
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OrderEstimateDTO {

    /**
     * 参与试算的商品明细（字段与下单请求一致，直接复用 OrderItemDTO）
     */
    @Schema(description = "商品明细列表")
    @NotEmpty(message = "商品明细不能为空")
    private List<OrderItemDTO> itemList;

    /**
     * 当前已勾选的用户优惠券记录ID（可为空，表示还没选券）
     */
    @Schema(description = "已选用户优惠券记录ID列表")
    private List<Long> userCouponIds;
}
