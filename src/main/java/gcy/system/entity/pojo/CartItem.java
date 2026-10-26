package gcy.system.entity.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 购物车项实体，映射 cart_item 表。
 *
 * @author 郭名城
 * @date 2026-10-23
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("cart_item")
public class CartItem {

    /** 购物车项主键ID，自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户ID */
    private Long userId;

    /** 家具ID */
    private Long furnitureId;

    /** SKU ID；0 表示无规格商品，用 0 而非 null 才能让唯一索引真正去重 */
    private Long skuId;

    /** 购买数量 */
    private Integer quantity;

    /** 结算勾选状态：0 未勾选，1 勾选 */
    private Integer checked;

    /** 加入购物车时间 */
    private LocalDateTime createTime;

    /** 最后更新时间 */
    private LocalDateTime updateTime;
}
