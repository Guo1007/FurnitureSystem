package gcy.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import gcy.system.entity.pojo.Order;
import gcy.system.entity.vo.OrderTrendDataVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单数据访问层接口，提供订单相关的自定义数据库查询操作。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 按日期统计指定时间之后的每日新增订单数量（排除已删除的订单），
     * 返回日期字符串与对应计数的列表，按日期升序排列。
     *
     * @param since 统计的起始时间
     * @return 每日订单趋势数据列表，每条记录包含日期字符串和当日订单数量
     */
    @Select("SELECT DATE(create_time) AS date_str, COUNT(*) AS count FROM `order` " +
            "WHERE create_time >= #{since} AND deleted = 0 GROUP BY DATE(create_time) ORDER BY date_str")
    List<OrderTrendDataVO> selectOrderTrend(@Param("since") LocalDateTime since);

    /**
     * 查询当前所有有效交易订单的总收入金额，仅统计状态为已支付、已发货、已完成、已评价的订单，
     * 排除已删除的订单。若无匹配记录则返回 0。
     *
     * @return 有效订单的总收入金额
     */
    @Select("SELECT COALESCE(SUM(total_price), 0) FROM `order` WHERE status IN (1, 2, 3, 5) AND deleted = 0")
    BigDecimal selectTotalRevenue();

    /**
     * 查询超过支付时限的待支付订单ID列表，用于超时订单的自动取消处理。
     * 仅查询状态为待支付且未被删除、且创建时间早于截止时间的订单。
     *
     * @param cutoffTime 支付截止时间
     * @return 超时未支付订单的ID列表
     */
    @Select("SELECT id FROM `order` WHERE status = 0 AND deleted = 0 AND create_time < #{cutoffTime}")
    List<Long> selectTimeoutOrders(@Param("cutoffTime") LocalDateTime cutoffTime);
}
