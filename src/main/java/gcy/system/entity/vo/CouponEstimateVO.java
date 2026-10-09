package gcy.system.entity.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 下单试算结果。
 * <p>
 * 前端「购物车页 / 购物车抽屉 / 商品详情立即购买 / 选券弹窗」全部改用这个接口取金额，
 * 前端不再保留任何抵扣算法 —— 后端是唯一实现，试算与下单共用同一段算价逻辑。
 * </p>
 *
 * @author 郭名城
 * @date 2026-10-09
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CouponEstimateVO {

    /**
     * 商品总额（后端按数据库价格算出，不信任前端传来的购物车价格）
     */
    @Schema(description = "商品总额")
    private BigDecimal goodsTotal;

    /**
     * 当前已选券的总抵扣
     */
    @Schema(description = "当前所选券抵扣合计")
    private BigDecimal totalDiscount;

    /**
     * 实付金额 = goodsTotal - totalDiscount
     */
    @Schema(description = "实付金额")
    private BigDecimal payable;

    /**
     * 后台配置的最大叠加张数，供前端限制勾选张数
     */
    @Schema(description = "最大叠加张数")
    private Integer maxStackCount;

    /**
     * 当前用户每张券在本单下的可用性与抵扣评估
     */
    @Schema(description = "每张券的可用性评估")
    private List<CouponOptionVO> couponOptions;

    /**
     * 当前这单的最优券组合（供「最优组合」按钮一键选择）。
     * 没有能省钱的组合时为空数组。
     */
    @Schema(description = "最优券组合的 userCouponId 列表")
    private List<Long> bestUserCouponIds;
}
