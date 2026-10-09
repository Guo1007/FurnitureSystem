package gcy.system.service;

import com.baomidou.mybatisplus.extension.service.IService;
import gcy.system.entity.dto.Result;
import gcy.system.entity.pojo.OrderItem;

/**
 * 订单明细服务接口。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
public interface IOrderItemService extends IService<OrderItem> {

    /**
     * 查询订单详情，含订单基本信息与明细列表。
     */
    Result getOrderDetail(Long orderId);

}
