package gcy.system.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 批量库存 / 销量变更项。
 * <p>
 * 用于把「循环内逐条 UPDATE」压成一条 {@code CASE WHEN ... THEN ... END} 批量 UPDATE：
 * 订单的库存恢复、销量累加（以及退款时的对称扣回）原本是每件明细一条 SQL，
 * 20 件商品就是 20~40 次往返，且都发生在事务内。
 * </p>
 *
 * @author 郭名城
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StockDeltaDTO {

    /**
     * 记录主键（furniture.id 或 sku.id）
     */
    private Long id;

    /**
     * 增量，可为负（退款扣回销量时传负数）
     */
    private Integer quantity;
}
