package gcy.system.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 加购请求体。
 *
 * @author 郭名城
 * @date 2026-10-23
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CartAddDTO {

    /** 家具ID */
    @Schema(description = "家具ID")
    @NotNull(message = "家具ID不能为空")
    private Long furnitureId;

    /** SKU ID；无规格商品留空 */
    @Schema(description = "SKU ID，无规格商品留空")
    private Long skuId;

    /** 购买数量，为空按 1 处理 */
    @Schema(description = "购买数量")
    @Min(value = 1, message = "数量至少为1")
    private Integer quantity;
}
