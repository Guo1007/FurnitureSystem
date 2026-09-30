package gcy.system.task;

import gcy.system.mapper.OrderMapper;
import gcy.system.service.IOrderService;
import gcy.system.utils.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static gcy.system.utils.RedisConstants.ORDER_AUTO_RECEIVE_TASK_KEY;

/**
 * 已发货订单自动确认收货调度器。
 * <p>
 * 背景：{@link OrderStatus#SHIPPED}（已发货）此前没有任何自动流转出口，
 * 用户不点「确认收货」时订单会永久停留在该状态——不结算、不能评价、
 * 售后窗口也一直开着（发货半年后仍可发起全额退款）。
 * 本调度器在发货超过配置天数后代为确认收货，让订单正常进入已完成。
 * </p>
 * <p>
 * 自动收货天数可通过 {@code order.auto-receive-days} 配置，默认 10 天。
 * 使用 Redisson 分布式锁确保多实例环境下只有一个实例执行。
 * </p>
 *
 * @author 郭名城
 * @date 2026-09-30
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoReceiveScheduler {

    private final OrderMapper orderMapper;

    private final IOrderService orderService;

    private final RedissonClient redissonClient;

    /**
     * 发货后超过该天数即自动确认收货。
     */
    @Value("${order.auto-receive-days:10}")
    private int autoReceiveDays;

    /**
     * 每小时执行一次：扫描发货超时且仍未确认收货的订单。
     */
    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Shanghai")
    public void autoConfirmReceipt() {
        RLock lock = redissonClient.getLock(ORDER_AUTO_RECEIVE_TASK_KEY);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, 120, TimeUnit.SECONDS);
            if (!locked) {
                return;
            }
            LocalDateTime cutoff = LocalDateTime.now().minusDays(autoReceiveDays);
            List<Long> orderIds = orderMapper.selectShippedOrdersBefore(cutoff);
            if (orderIds.isEmpty()) {
                return;
            }
            log.info("发现 {} 个发货超 {} 天未确认收货的订单，准备自动确认", orderIds.size(), autoReceiveDays);
            int success = 0;
            int failed = 0;
            for (Long orderId : orderIds) {
                try {
                    var result = orderService.autoConfirmReceipt(orderId);
                    if (result.getSuccess()) {
                        success++;
                    } else {
                        // 状态已变更属于正常跳过，不计为失败
                        log.info("跳过自动确认收货: orderId={}, msg={}", orderId, result.getMsg());
                    }
                } catch (Exception e) {
                    failed++;
                    log.error("自动确认收货异常: orderId={}", orderId, e);
                }
            }
            log.info("自动确认收货完成: 成功={}, 失败={}, 总数={}", success, failed, orderIds.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("自动确认收货任务获取锁被中断", e);
        } catch (Exception e) {
            log.error("自动确认收货任务执行失败", e);
        } finally {
            if (locked) {
                try {
                    lock.unlock();
                } catch (Exception ignored) {
                }
            }
        }
    }
}
