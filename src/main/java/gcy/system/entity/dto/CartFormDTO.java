package gcy.system.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 购物车订单提交表单 DTO，用于接收前端提交的订单创建请求，
 * 包含收货人信息、联系方式、收货地址、订单备注以及订单项明细列表。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CartFormDTO {

    /**
     * 收货人姓名
     */
    @Schema(description = "收货人姓名")
    @NotBlank(message = "收货人不能为空")
    private String consignee;

    /**
     * 收货人联系电话
     */
    @Schema(description = "收货人联系电话")
    @NotBlank(message = "联系电话不能为空")
    private String phone;

    /**
     * 收货地址
     */
    @Schema(description = "收货地址")
    @NotBlank(message = "收货地址不能为空")
    private String address;

    /**
     * 订单备注
     */
    @Schema(description = "订单备注")
    private String remark;

    /**
     * 订单项明细列表
     */
    @Schema(description = "订单项明细列表")
    @NotEmpty(message = "订单商品明细不能为空")
    private List<OrderItemDTO> itemList;

    /**
     * 使用的用户优惠券记录ID（user_coupon.id，不选则为空）
     */
    @Schema(description = "用户优惠券记录ID")
    private Long userCouponId;

    /**
     * 使用的用户优惠券记录ID列表（支持多张可叠加券，不选为空；优先于 userCouponId）
     */
    @Schema(description = "用户优惠券记录ID列表(多张可叠加)")
    private List<Long> userCouponIds;

}
