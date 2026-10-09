package gcy.system.service.admin.Impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import gcy.system.entity.dto.Result;
import gcy.system.entity.dto.StockDeltaDTO;
import gcy.system.entity.pojo.Order;
import gcy.system.entity.pojo.OrderItem;
import gcy.system.entity.pojo.User;
import gcy.system.entity.vo.OrderVO;
import gcy.system.exception.BusinessException;
import gcy.system.integration.EmailService;
import gcy.system.mapper.FurnitureMapper;
import gcy.system.mapper.OrderMapper;
import gcy.system.mapper.UserMapper;
import gcy.system.service.IOrderItemService;
import gcy.system.service.IOrderService;
import gcy.system.service.admin.IOrderManageService;
import gcy.system.utils.OrderEmailUtil;
import gcy.system.utils.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static gcy.system.utils.OrderStatus.*;

/**
 * 订单管理服务实现类。
 * <p>
 * 发货用乐观锁（更新时附带 status 条件）防止并发重复发货。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderManageServiceImpl extends ServiceImpl<OrderMapper, Order>
        implements IOrderManageService {

    private final OrderMapper orderMapper;

    private final IOrderItemService orderItemService;

    private final EmailService emailService;

    private final UserMapper userMapper;

    private final IOrderService orderService;

    private final FurnitureMapper furnitureMapper;

    /**
     * 分页查询订单列表，按创建时间降序。
     */
    @Override
    public Result getOrderList(Integer current, Integer size, Integer userId,
                               String status, String phone, String consignee) {
        Page<Order> page = new Page<>(current, size);
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        if (userId != null) {
            wrapper.eq(Order::getUserId, userId);
        }
        // 状态筛选：支持逗号分隔多状态（如 "6,7"），供售后处理页使用
        if (StrUtil.isNotBlank(status)) {
            List<Integer> codes = java.util.Arrays.stream(status.split(","))
                    .map(String::trim)
                    .filter(StrUtil::isNotBlank)
                    .map(Integer::parseInt)
                    .collect(Collectors.toList());
            if (codes.size() == 1) {
                wrapper.eq(Order::getStatus, codes.get(0));
            } else if (codes.size() > 1) {
                wrapper.in(Order::getStatus, codes);
            }
        }
        if (StrUtil.isNotBlank(phone)) {
            wrapper.like(Order::getPhone, phone);
        }
        if (StrUtil.isNotBlank(consignee)) {
            wrapper.like(Order::getConsignee, consignee);
        }
        wrapper.orderByDesc(Order::getCreateTime);
        Page<Order> resultPage = orderMapper.selectPage(page, wrapper);
        List<Order> orders = resultPage.getRecords();
        Map<Long, List<OrderItem>> itemMap = new HashMap<>();
        if (!orders.isEmpty()) {
            List<Long> orderIds = orders.stream().map(Order::getId).collect(Collectors.toList());
            List<OrderItem> allItems = orderItemService.list(
                    new LambdaQueryWrapper<OrderItem>().in(OrderItem::getOrderId, orderIds));
            itemMap.putAll(allItems.stream().collect(Collectors.groupingBy(OrderItem::getOrderId)));
        }
        List<OrderVO> voList = orders.stream()
                .map(order -> OrderVO.from(order, itemMap.getOrDefault(order.getId(), Collections.emptyList())))
                .collect(Collectors.toList());
        // 批量填充用户名（供管理端展示）
        if (!orders.isEmpty()) {
            List<Long> uids = orders.stream().map(Order::getUserId).filter(java.util.Objects::nonNull).distinct().collect(Collectors.toList());
            Map<Long, String> userNameMap = userMapper.selectByIds(uids).stream()
                    .collect(Collectors.toMap(User::getId, User::getUserName, (a, b) -> a));
            voList.forEach(vo -> {
                if (vo.getUserId() != null) {
                    vo.setUserName(userNameMap.get(vo.getUserId()));
                }
            });
        }
        Page<OrderVO> voPage = new Page<>();
        voPage.setRecords(voList);
        voPage.setTotal(resultPage.getTotal());
        voPage.setSize(resultPage.getSize());
        voPage.setCurrent(resultPage.getCurrent());
        return Result.ok(voPage);
    }

    /**
     * 对指定订单发货。
     * <p>
     * 仅已支付(PAID)可发货；已发货/已完成/已评价视为幂等直接返回成功，其余状态返回失败。
     * 更新时附带 status=PAID 条件防止并发重复发货。
     */
    @Override
    @Transactional
    public Result shipOrderById(Long id) {
        Order order = getById(id);
        if (order == null) {
            return Result.fail("订单不存在！");
        }
        int status = order.getStatus();
        if (status != PAID.getCode()) {
            if (status == PENDING_PAYMENT.getCode()) {
                return Result.fail("该订单还未支付！");
            } else if (status == SHIPPED.getCode() || status == COMPLETED.getCode() || status == REVIEWED.getCode()) {
                return Result.ok();
            } else if (status == REFUND_APPLYING.getCode()
                    || status == REFUND_AUDITING.getCode()
                    || status == REFUNDED.getCode()) {
                return Result.fail("订单处于退款流程中，无法发货！");
            } else {
                return Result.fail("订单已被取消！");
            }
        }
        boolean success = update()
                .set("status", SHIPPED.getCode())
                .set("ship_time", LocalDateTime.now())
                .eq("id", id)
                .eq("status", PAID.getCode())
                .update();
        if (!success) {
            Order updated = getById(id);
            if (updated.getStatus() == SHIPPED.getCode() || updated.getStatus() == COMPLETED.getCode() || updated.getStatus() == REVIEWED.getCode()) {
                return Result.ok();
            }
            throw new BusinessException("发货失败，请联系系统管理人员检查！");
        }
        OrderEmailUtil.sendOrderStatus(emailService, userMapper, order, "订单已发货",
                "您的订单 #" + order.getId() + " 已发货，请留意收货。",
                "🚚", null);
        return Result.ok();
    }

    private static final DateTimeFormatter CSV_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 导出全部订单为 CSV 写入 w，调用方负责关闭。
     * <p>
     * 文本字段均做转义，防特殊字符破坏格式或公式注入。
     */
    @Override
    public void exportOrders(PrintWriter w) {
        w.println("订单号,用户ID,收货人,电话,地址,金额,状态,备注,创建时间,支付时间,发货时间");

        // Keyset 分页（基于自增主键 id 游标）分批加载，避免偏移量随数据量增大而性能劣化；
        // 每批写完立即 flush 及时释放输出缓冲
        int pageSize = 1000;
        Long lastId = Long.MAX_VALUE;
        while (true) {
            List<Order> orders = orderMapper.selectList(
                    new LambdaQueryWrapper<Order>()
                            .orderByDesc(Order::getId)
                            .lt(Order::getId, lastId)
                            .last("LIMIT " + pageSize));
            if (orders.isEmpty()) {
                break;
            }
            for (Order o : orders) {
                String statusText;
                try {
                    statusText = OrderStatus.fromCode(o.getStatus()).getDesc();
                } catch (IllegalArgumentException e) {
                    statusText = "未知";
                }
                w.printf("%d,%d,%s,%s,%s,%s,%s,%s,%s,%s,%s%n",
                        o.getId(),
                        o.getUserId(),
                        csvEscape(o.getConsignee()),
                        csvEscape(o.getPhone()),
                        csvEscape(o.getAddress()),
                        o.getTotalPrice(),
                        statusText,
                        csvEscape(o.getRemark()),
                        csvDate(o.getCreateTime()),
                        csvDate(o.getPayTime()),
                        csvDate(o.getShipTime()));
            }
            w.flush();
            lastId = orders.get(orders.size() - 1).getId();
        }
        w.flush();
    }

    /**
     * CSV 字段转义：null 返回空串，=+-@ 开头加制表符前缀防公式注入，含逗号/引号/换行则双引号包裹。
     */
    private String csvEscape(String val) {
        if (val == null) return "";
        if (val.startsWith("=") || val.startsWith("+") || val.startsWith("-") || val.startsWith("@")) {
            val = "\t" + val;
        }
        if (val.contains(",") || val.contains("\"") || val.contains("\n")) {
            return "\"" + val.replace("\"", "\"\"") + "\"";
        }
        return val;
    }

    /**
     * 统计待发货(PAID)订单数，返回键名 pendingShipCount。
     */
    @Override
    public Result getPendingShipCount() {
        long count = count(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, PAID.getCode()));
        return Result.ok(java.util.Map.of("pendingShipCount", count));
    }

    /**
     * 格式化为 yyyy-MM-dd HH:mm:ss，前置制表符防 Excel 误转换。
     */
    private String csvDate(LocalDateTime dt) {
        if (dt == null) return "";
        return "\t" + dt.format(CSV_DATE_FMT);
    }

    /**
     * 同意退款：申请退款中(6) → 退款审核中(7)，记录同意时间并通知用户。
     */
    @Override
    @Transactional
    public Result approveRefund(Long orderId) {
        Order order = getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在！");
        }
        if (order.getStatus() != REFUND_APPLYING.getCode()) {
            return Result.fail("订单当前状态不支持此操作");
        }
        boolean success = update()
                .set("status", REFUND_AUDITING.getCode())
                .set("refund_approve_time", LocalDateTime.now())
                .eq("id", orderId)
                .eq("status", REFUND_APPLYING.getCode())
                .update();
        if (!success) {
            return Result.fail("操作失败，请重试");
        }
        OrderEmailUtil.sendOrderStatus(emailService, userMapper, order, "退款申请已受理",
                "您的订单 #" + order.getId() + " 退款申请已受理，正在审核商品情况，请耐心等待。",
                "📦", null);
        log.info("管理员同意退款: orderId={}", orderId);
        return Result.ok();
    }

    /**
     * 拒绝退款：恢复到退款前的原状态(refund_prev_status)，记录拒绝原因并通知用户。
     */
    /**
     * 判断某个状态是否为「可回退的合法非退款态」。
     * 退款态（6/7/8）与已取消（4）都不能作为退款回退的目标状态。
     */
    private boolean isValidRefundPrevStatus(int status) {
        return status == PAID.getCode()
                || status == SHIPPED.getCode()
                || status == COMPLETED.getCode()
                || status == REVIEWED.getCode();
    }

    @Override
    @Transactional
    public Result rejectRefund(Long orderId, String remark) {
        Order order = getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在！");
        }
        if (order.getStatus() != REFUND_APPLYING.getCode()) {
            return Result.fail("订单当前状态不支持此操作");
        }
        // 退款前状态必须属于合法的非退款态，脏数据（NULL / 本身是退款态 / 已取消）
        // 会让订单回滚到一个非法状态上，这里统一兜底为「已支付」
        Integer prevRaw = order.getRefundPrevStatus();
        int prevStatus = (prevRaw != null && isValidRefundPrevStatus(prevRaw))
                ? prevRaw : PAID.getCode();
        if (prevRaw == null || !isValidRefundPrevStatus(prevRaw)) {
            log.warn("订单退款前状态异常，拒绝退款时兜底为已支付: orderId={}, refundPrevStatus={}", orderId, prevRaw);
        }
        boolean success = update()
                .set("status", prevStatus)
                .set("refund_handle_remark", remark)
                // 拒绝属审核动作，应记入 refund_audit_time，与另一条拒绝路径保持一致
                .set("refund_audit_time", LocalDateTime.now())
                .set("refund_prev_status", null)
                .eq("id", orderId)
                .eq("status", REFUND_APPLYING.getCode())
                .update();
        if (!success) {
            return Result.fail("操作失败，请重试");
        }
        OrderEmailUtil.sendOrderStatus(emailService, userMapper, order, "退款申请被拒绝",
                "您的订单 #" + order.getId() + " 退款申请被拒绝，如有疑问请联系客服。",
                "❌", remark);
        log.info("管理员拒绝退款: orderId={}, remark={}", orderId, remark);
        return Result.ok();
    }

    /**
     * 审核退款：通过则订单置已退款(8)、恢复库存并扣回销量；不通过则恢复到退款前原状态。
     */
    @Override
    @Transactional
    public Result auditRefund(Long orderId, Boolean passed, String remark) {
        Order order = getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在！");
        }
        if (order.getStatus() != REFUND_AUDITING.getCode()) {
            return Result.fail("订单当前状态不支持此操作");
        }
        if (Boolean.TRUE.equals(passed)) {
            orderService.restoreStock(orderId);
            // 仅"已完成(3)/已评价(5)"确认收货时累加过 sale_count，退款需对称扣回；
            // 已支付(1)/已发货(2)从未累加销量，扣减会导致销量失真甚至为负。
            // 退款前状态需合法性断言，脏数据兜底为「已支付」
            Integer prevRaw = order.getRefundPrevStatus();
            int prevStatus = (prevRaw != null && isValidRefundPrevStatus(prevRaw))
                    ? prevRaw : PAID.getCode();
            if (prevRaw == null || !isValidRefundPrevStatus(prevRaw)) {
                log.warn("订单退款前状态异常，退款通过时兜底为已支付: orderId={}, refundPrevStatus={}", orderId, prevRaw);
            }
            if (prevStatus == COMPLETED.getCode() || prevStatus == REVIEWED.getCode()) {
                // 扣回销量失败必须抛异常回滚整个事务（含已恢复的库存），
                // 避免出现"库存已恢复但销量未扣回"的台账不一致
                List<OrderItem> items = orderItemService.lambdaQuery()
                        .eq(OrderItem::getOrderId, orderId).list();
                // 同 id 合并后一条批量 UPDATE 扣回销量（传负增量），替代逐条 UPDATE
                Map<Long, Integer> saleBack = new HashMap<>();
                for (OrderItem item : items) {
                    if (item.getFurnitureId() != null && item.getQuantity() != 0) {
                        saleBack.merge(item.getFurnitureId(), -item.getQuantity(), Integer::sum);
                    }
                }
                if (!saleBack.isEmpty()) {
                    List<StockDeltaDTO> deltas = new ArrayList<>(saleBack.size());
                    saleBack.forEach((id, qty) -> deltas.add(new StockDeltaDTO(id, qty)));
                    furnitureMapper.batchIncrementSaleCount(deltas);
                }
            }
            boolean success = update()
                    .set("status", REFUNDED.getCode())
                    .set("refund_audit_time", LocalDateTime.now())
                    .eq("id", orderId)
                    .eq("status", REFUND_AUDITING.getCode())
                    .update();
            if (!success) {
                throw new BusinessException("退款审核失败，请重试");
            }
            orderService.returnCouponForOrder(orderId);
            OrderEmailUtil.sendOrderStatus(emailService, userMapper, order, "退款成功",
                    "您的订单 #" + order.getId() + " 退款已到账，感谢您的理解与支持。",
                    "✅", null);
            log.info("退款审核通过: orderId={}", orderId);
        } else {
            Integer prevRaw = order.getRefundPrevStatus();
            int prevStatus = (prevRaw != null && isValidRefundPrevStatus(prevRaw))
                    ? prevRaw : PAID.getCode();
            if (prevRaw == null || !isValidRefundPrevStatus(prevRaw)) {
                log.warn("订单退款前状态异常，审核不通过时兜底为已支付: orderId={}, refundPrevStatus={}", orderId, prevRaw);
            }
            boolean success = update()
                    .set("status", prevStatus)
                    .set("refund_handle_remark", remark)
                    .set("refund_audit_time", LocalDateTime.now())
                    .set("refund_prev_status", null)
                    .eq("id", orderId)
                    .eq("status", REFUND_AUDITING.getCode())
                    .update();
            if (!success) {
                return Result.fail("操作失败，请重试");
            }
            OrderEmailUtil.sendOrderStatus(emailService, userMapper, order, "退款审核未通过",
                    "您的订单 #" + order.getId() + " 退款审核未通过，如有疑问请联系客服。",
                    "❌", remark);
            log.info("退款审核不通过: orderId={}, remark={}", orderId, remark);
        }
        return Result.ok();
    }

    /**
     * 统计待处理退款数（申请退款中 + 退款审核中）。
     */
    @Override
    public Result getPendingRefundCount() {
        long count = count(new LambdaQueryWrapper<Order>()
                .in(Order::getStatus, REFUND_APPLYING.getCode(), REFUND_AUDITING.getCode()));
        return Result.ok(java.util.Map.of("pendingRefundCount", count));
    }

    @Override
    public Result getRefundStatusCounts() {
        long pending = count(new LambdaQueryWrapper<Order>()
                .in(Order::getStatus, REFUND_APPLYING.getCode(), REFUND_AUDITING.getCode()));
        long refunded = count(new LambdaQueryWrapper<Order>().eq(Order::getStatus, REFUNDED.getCode()));
        return Result.ok(java.util.Map.of(
                "pending", pending,
                "refunded", refunded,
                "all", pending + refunded));
    }

    /**
     * 删除已完结订单（取消/完成/评价/退款）；在途订单仍占库存，拒绝删除。
     */
    @Override
    @Transactional
    public Result deleteOrderById(Long orderId) {
        Order order = getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        if (!isDeletableStatus(order.getStatus())) {
            return Result.fail("在途订单仍占用库存，不能直接删除，请先取消订单或走退款流程");
        }
        // 删除前归还优惠券：订单没了但 user_coupon 仍「已用」的话券会永久失效；
        // 归还按 status=1 且 orderId 匹配，可重复调用（幂等）。
        orderService.returnCouponForOrder(orderId);
        // 同步清理订单明细，避免 order_item 变成无主孤儿数据
        orderItemService.remove(new LambdaQueryWrapper<OrderItem>()
                .eq(OrderItem::getOrderId, orderId));
        removeById(orderId);
        log.info("管理员删除订单: orderId={}, status={}", orderId, order.getStatus());
        return Result.okMsg("删除成功");
    }

    /**
     * 批量删除已完结订单，在途订单跳过并返回跳过数量。
     */
    @Override
    @Transactional
    public Result batchDeleteOrders(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.fail("请选择要删除的订单");
        }
        // 批量查询一次、批量删除一次，避免逐条 getById/removeById 造成 N+1 次数据库往返
        List<Order> orders = orderMapper.selectByIds(ids);
        List<Long> deletableIds = orders.stream()
                .filter(o -> isDeletableStatus(o.getStatus()))
                .map(Order::getId)
                .collect(Collectors.toList());
        // ids 中查不到的（如已被删除）与在途订单一并计入跳过
        int skipped = ids.size() - deletableIds.size();
        if (deletableIds.isEmpty()) {
            return Result.fail("所选订单均为在途订单，不能删除（请先取消或走退款流程）");
        }
        // 与单条删除保持一致：先归还优惠券并清理明细，再删主表
        for (Long id : deletableIds) {
            orderService.returnCouponForOrder(id);
        }
        orderItemService.remove(new LambdaQueryWrapper<OrderItem>()
                .in(OrderItem::getOrderId, deletableIds));
        removeByIds(deletableIds);
        int deleted = deletableIds.size();
        String msg = "删除成功 " + deleted + " 个订单";
        if (skipped > 0) {
            msg += "，跳过 " + skipped + " 个在途订单";
        }
        return Result.okMsg(msg);
    }

    /**
     * 是否可删除（仅已完结：取消/完成/评价/退款）。
     * <p>
     * 放宽白名单前先看 {@code OrderServiceImpl} 的 confirmPaid / doConfirmReceipt：
     * 两处在 CAS 更新 0 行后 getById 重查状态且未判 null，当前安全是因为「在途订单删不掉」
     * 这条约束钉死（用户端 deleteMyOrder 用同一套终态白名单）。
     * 一旦允许删除在途订单，那两处会立刻 NPE，需一并补 null 判断。
     */
    private boolean isDeletableStatus(int status) {
        return status == CANCELLED.getCode()
                || status == COMPLETED.getCode()
                || status == REVIEWED.getCode()
                || status == REFUNDED.getCode();
    }

}
