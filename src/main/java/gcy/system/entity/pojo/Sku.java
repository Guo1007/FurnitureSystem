package gcy.system.entity.pojo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SKU实体类，映射数据库sku表，用于存储家具商品的SKU库存单位信息，包括价格、库存、状态等。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("sku")
public class Sku {

    /**
     * SKU主键ID，自增
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属家具ID，关联家具表
     */
    private Long furnitureId;

    /**
     * SKU价格
     */
    private BigDecimal price;

    /**
     * 库存数量
     */
    private Integer stock;

    /**
     * SKU图片地址
     */
    private String skuImage;

    /**
     * 状态标识
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
}