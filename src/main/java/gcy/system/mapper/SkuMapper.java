package gcy.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import gcy.system.entity.dto.StockDeltaDTO;
import gcy.system.entity.pojo.Sku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

/**
 * SKU（库存量单位）数据访问层接口。
 * <p>
 * 继承 MyBatis-Plus 的 BaseMapper，提供 SKU 表的基础 CRUD 操作，
 * 并额外定义了库存扣减、库存回增、按家具查询总库存及最低价格等自定义数据库操作。
 * </p>
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Mapper
public interface SkuMapper extends BaseMapper<Sku> {

    /**
     * 扣减指定 SKU 的库存数量。
     * <p>
     * 执行 SQL：{@code UPDATE sku SET stock = stock - #{quantity} WHERE id = #{id} AND stock >= #{quantity}}。
     * 只有当当前库存大于等于扣减数量时才执行更新，避免库存变为负数。
     * </p>
     *
     * @param id       SKU 的唯一标识ID
     * @param quantity 要扣减的数量
     * @return 受影响的行数（1 表示扣减成功，0 表示库存不足或 SKU 不存在）
     */
    @Update("UPDATE sku SET stock = stock - #{quantity} WHERE id = #{id} AND stock >= #{quantity}")
    int decrementStock(@Param("id") Long id, @Param("quantity") int quantity);

    /**
     * 回增指定 SKU 的库存数量。
     * <p>
     * 执行 SQL：{@code UPDATE sku SET stock = stock + #{quantity} WHERE id = #{id}}。
     * 通常用于订单取消、退货等场景下的库存恢复。
     * </p>
     *
     * @param id       SKU 的唯一标识ID
     * @param quantity 要回增的数量
     * @return 受影响的行数（1 表示回增成功，0 表示 SKU 不存在）
     */
    @Update("UPDATE sku SET stock = stock + #{quantity} WHERE id = #{id}")
    int incrementStock(@Param("id") Long id, @Param("quantity") int quantity);

    /**
     * 批量回增 SKU 库存：一条 SQL 用 {@code CASE id WHEN ... THEN ...} 完成多行不同增量的更新。
     * 用于订单取消 / 退款的库存恢复，替代循环内逐条 UPDATE。
     *
     * @param list 变更项（skuId + 增量）
     */
    @Update("<script>UPDATE sku SET stock = stock + CASE id " +
            "<foreach collection='list' item='i'>WHEN #{i.id} THEN #{i.quantity} </foreach>" +
            "END WHERE id IN " +
            "<foreach collection='list' item='i' open='(' separator=',' close=')'>#{i.id}</foreach></script>")
    void batchIncrementStock(@Param("list") List<StockDeltaDTO> list);

    /**
     * 查询指定家具下所有有效 SKU 的总库存数量。
     * <p>
     * 执行 SQL：{@code SELECT COALESCE(SUM(stock), 0) FROM sku WHERE furniture_id = #{furnitureId} AND status = 1}。
     * 仅统计状态为 1（有效/上架）的 SKU，若无匹配记录则返回 0。
     * </p>
     *
     * @param furnitureId 家具的唯一标识ID
     * @return 该家具下所有有效 SKU 的库存总数，无数据时返回 0
     */
    @Select("SELECT COALESCE(SUM(stock), 0) FROM sku WHERE furniture_id = #{furnitureId} AND status = 1")
    int sumStockByFurnitureId(@Param("furnitureId") Long furnitureId);

    /**
     * 查询指定家具下所有有效且有库存的 SKU 中的最低价格。
     * <p>
     * 执行 SQL：{@code SELECT COALESCE(MIN(price), 0) FROM sku WHERE furniture_id = #{furnitureId} AND status = 1 AND stock > 0}。
     * 仅统计状态为 1（有效/上架）且库存大于 0 的 SKU，若无匹配记录则返回 0。
     * </p>
     *
     * @param furnitureId 家具的唯一标识ID
     * @return 该家具下有效且有库存 SKU 的最低价格，无数据时返回 BigDecimal.ZERO（即 0）
     */
    @Select("SELECT COALESCE(MIN(price), 0) FROM sku WHERE furniture_id = #{furnitureId} AND status = 1 AND stock > 0")
    BigDecimal minPriceByFurnitureId(@Param("furnitureId") Long furnitureId);
}