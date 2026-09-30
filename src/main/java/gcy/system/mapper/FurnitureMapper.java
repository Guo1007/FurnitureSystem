package gcy.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import gcy.system.entity.dto.StockDeltaDTO;
import gcy.system.entity.pojo.Furniture;
import gcy.system.entity.vo.LowStockVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 家具数据访问层 Mapper 接口。
 * <p>
 * 继承 MyBatis-Plus 的 BaseMapper，自动拥有基本的 CRUD 方法。
 * 同时定义了自定义的 SQL 操作，包括按类型查询品牌、库存增减、
 * 销量累加以及低库存查询等功能。
 * </p>
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Mapper
public interface FurnitureMapper extends BaseMapper<Furniture> {

    /**
     * 根据家具类型 ID 查询该类型下所有未删除家具的不重复品牌名称。
     * <p>
     * 使用动态 SQL：如果 typeId 不为 null 且大于 0，
     * 则追加 type_id 过滤条件，否则查询所有未删除家具的品牌。
     * </p>
     *
     * @param typeId 家具类型 ID，可为 null（表示不过滤类型）
     * @return 品牌名称列表
     */
    @Select("<script>select distinct brand from furniture where deleted = 0" +
            "<if test='typeId != null and typeId > 0'> and type_id = #{typeId}</if>" +
            "</script>")
    List<String> getFurnitureBrandsByTypeId(@Param("typeId") Long typeId);

    /**
     * 扣减指定家具的库存数量。
     * <p>
     * 仅当当前库存大于等于要扣减的数量时才会执行更新，
     * 防止库存变为负数。
     * </p>
     *
     * @param id       家具 ID
     * @param quantity 要扣减的数量
     * @return 受影响的行数（0 表示库存不足或家具不存在，1 表示扣减成功）
     */
    @Update("UPDATE furniture SET stock = stock - #{quantity} WHERE id = #{id} AND stock >= #{quantity}")
    int decrementStock(@Param("id") Long id, @Param("quantity") int quantity);

    /**
     * 增加指定家具的库存数量。
     * <p>
     * 无条件地将库存增加指定数量，通常用于入库或订单取消等场景。
     * </p>
     *
     * @param id       家具 ID
     * @param quantity 要增加的数量
     */
    @Update("UPDATE furniture SET stock = stock + #{quantity} WHERE id = #{id}")
    void incrementStock(@Param("id") Long id, @Param("quantity") int quantity);

    /**
     * 累加指定家具的销售数量。
     * <p>
     * 将 sale_count 字段增加指定数量，通常用于订单完成后的销量统计。
     * </p>
     *
     * @param id       家具 ID
     * @param quantity 要累加的销售数量
     */
    @Update("UPDATE furniture SET sale_count = sale_count + #{quantity} WHERE id = #{id}")
    void incrementSaleCount(@Param("id") Long id, @Param("quantity") int quantity);

    /**
     * 批量增加家具库存：一条 SQL 用 {@code CASE id WHEN ... THEN ...} 完成多行不同增量的更新。
     * <p>
     * 用于订单取消 / 退款的库存恢复，替代循环内逐条 {@code UPDATE}（20 件明细 = 20 次往返）。
     * 与 {@link #incrementStock(Long, int)} 一样不带 deleted 过滤，软删除商品也能正常回库。
     * </p>
     *
     * @param list 变更项（id + 增量）
     */
    @Update("<script>UPDATE furniture SET stock = stock + CASE id " +
            "<foreach collection='list' item='i'>WHEN #{i.id} THEN #{i.quantity} </foreach>" +
            "END WHERE id IN " +
            "<foreach collection='list' item='i' open='(' separator=',' close=')'>#{i.id}</foreach></script>")
    void batchIncrementStock(@Param("list") List<StockDeltaDTO> list);

    /**
     * 批量累加销量，语义同 {@link #batchIncrementStock(java.util.List)}，作用于 sale_count。
     * quantity 传负数即为对称扣回（退款场景）。
     */
    @Update("<script>UPDATE furniture SET sale_count = sale_count + CASE id " +
            "<foreach collection='list' item='i'>WHEN #{i.id} THEN #{i.quantity} </foreach>" +
            "END WHERE id IN " +
            "<foreach collection='list' item='i' open='(' separator=',' close=')'>#{i.id}</foreach></script>")
    void batchIncrementSaleCount(@Param("list") List<StockDeltaDTO> list);

    /**
     * 查询库存低于 10 且未删除的家具列表，按库存升序排列。
     * <p>
     * 只返回 id、名称、图标和库存字段，用于低库存预警展示。
     * </p>
     *
     * @return 低库存家具的 VO 列表
     */
    @Select("SELECT f.id, f.f_name, f.f_icon, f.stock, f.type_id, t.name AS type_name " +
            "FROM furniture f " +
            "LEFT JOIN furniture_type t ON f.type_id = t.id " +
            "WHERE f.stock < 10 AND f.deleted = 0 " +
            "ORDER BY f.stock ASC")
    List<LowStockVO> selectLowStock();
}
