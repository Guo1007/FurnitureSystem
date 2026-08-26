package gcy.system.entity.vo.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 通知配置视图对象（VO），用于前端渲染某一通知类型当前的启用状态及接收管理员列表。
 *
 * @author 郭名城
 * @date 2026-08-26
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotifyConfigVO {

    /**
     * 通知类型（new_order-新订单，refund-售后退款，stock_alert-库存预警）
     */
    private String notifyType;

    /**
     * 是否启用该通知
     */
    private Boolean enabled;

    /**
     * 接收该通知的管理员ID列表
     */
    private List<Long> adminIds;
}