package gcy.system.entity.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 购物车项展示对象，含实时价格与库存。
 *
 * @author 郭名城
 * @date 2026-10-23
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartItemVO {

    /** 购物车项ID，删除/改数量时用 */
    @Schema(description = "购物车项ID")
    private Long id;

    /** 家具ID */
    @Schema(description = "家具ID")
    private Long furnitureId;

    /** SKU ID，无规格商品为空 */
    @Schema(description = "SKU ID")
    private Long skuId;

    /**
     * 家具名称。
     * <p>
     * 必须显式声明 JSON 名：Jackson 的默认命名会把 getFName() 压成 fname，
     * 与 {@link gcy.system.entity.pojo.Furniture} 及前端约定的 fName 对不上。
     */
    @JsonProperty("fName")
    @Schema(description = "家具名称")
    private String fName;

    /** 展示图；SKU 有独立图时优先用 SKU 图，否则为商品主图 */
    @JsonProperty("fIcon")
    @Schema(description = "展示图")
    private String fIcon;

    /** 实时单价，取库而非加购时的快照 */
    @Schema(description = "实时单价")
    private BigDecimal price;

    /** 实时库存 */
    @Schema(description = "实时库存")
    private Integer stock;

    /** 购买数量 */
    @Schema(description = "购买数量")
    private Integer quantity;

    /** 结算勾选状态 */
    @Schema(description = "结算勾选状态")
    private Boolean checked;

    /** 分类ID，分类券核算小计要用 */
    @Schema(description = "分类ID")
    private Long typeId;

    /** 规格文案，如「颜色:胡桃色, 尺寸:三人位」 */
    @Schema(description = "规格文案")
    private String specText;

    /** 是否仍可购买；下架、售罄、SKU 停用时为 false */
    @Schema(description = "是否仍可购买")
    private Boolean available;

    /** 不可购买原因 */
    @Schema(description = "不可购买原因")
    private String unavailableReason;
}
