package gcy.system.service.admin.Impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import gcy.system.entity.dto.Result;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static gcy.system.utils.OrderStatus.*;

/**
 * 订单管理服务实现类
 * <p>
 * 提供订单列表分页查询、订单发货、订单CSV导出以及待发货数量统计等核心业务功能的实现。
 * 继承自 MyBatis-Plus 的 ServiceImpl，实现 {@link IOrderManageService} 接口。
 * 发货操作使用乐观锁机制防止并发重复发货，并在状态变更后通过邮件通知用户。
 * </p>
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
     * 分页查询订单列表
     * <p>
     * 根据用户ID、订单状态、收货人电话、收货人姓名等条件进行组合过滤查询，
     * 结果按创建时间降序排列。同时批量加载每个订单关联的订单项明细信息，
     * 将订单实体及其订单项封装为 {@link OrderVO} 视图对象，以分页形式返回。
     * </p>
     *
     * @param current   当前页码
     * @param size      每页显示条数
     * @param userId    用户ID（可选过滤条件，为null时不过滤）
     * @param status    订单状态编码（可选过滤条件，为null时不过滤）
     * @param phone     收货人电话（可选过滤条件，为空时不过滤，使用模糊匹配）
     * @param consignee 收货人姓名（可选过滤条件，为空时不过滤，使用模糊匹配）
     * @return 包含分页 {@link OrderVO} 列表及分页信息的 Result 对象
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
     * 对指定订单执行发货操作
     * <p>
     * 校验订单是否存在及其当前状态：仅已支付（PAID）状态的订单允许发货，
     * 已发货/已完成/已评价的订单直接返回成功（幂等处理），未支付或已取消的订单返回失败。
     * 使用乐观锁机制（在更新时同时校验状态条件）防止并发重复发货。
     * 发货成功后记录发货时间并通过邮件通知用户订单状态变更。
     * </p>
     *
     * @param id 要发货的订单ID
     * @return 操作结果的 Result 对象
     * @throws BusinessException 当发货更新失败且订单状态未变为已发货/已完成/已评价时抛出
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
     * 导出全部订单数据为CSV格式
     * <p>
     * 从数据库查询所有订单记录（按创建时间降序），将订单的各个字段以CSV格式写入输出流。
     * 每行包含：订单号、用户ID、收货人、电话、地址、金额、状态描述、备注、创建时间、支付时间、发货时间。
     * 所有文本字段均经过CSV转义处理，防止特殊字符破坏CSV格式或引发公式注入。
     * </p>
     *
     * @param w 用于输出CSV内容的 PrintWriter 字符输出流，由调用方负责关闭
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
     * 对CSV字段值进行安全转义处理
     * <p>
     * 对null值返回空字符串；对以等号、加号、减号、at符号开头的值添加制表符前缀，
     * 防止Excel等电子表格软件将其解析为公式（CSV注入防护）；
     * 对包含逗号、双引号或换行符的值使用双引号包裹并对内部双引号进行转义。
     * </p>
     *
     * @param val 原始字段值，可能为null
     * @return 转义后的CSV安全字符串
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
     * 获取当前待发货的订单数量
     * <p>
     * 统计状态为已支付（PAID）的订单总数，用于后台管理界面的待办事项角标提示。
     * 结果以键值对形式返回，键名为 "pendingShipCount"。
     * </p>
     *
     * @return 包含待发货订单数量的 Result 对象
     */
    @Override
    public Result getPendingShipCount() {
        long count = count(new LambdaQueryWrapper<Order>()
                .eq(Order::getStatus, PAID.getCode()));
        return Result.ok(java.util.Map.of("pendingShipCount", count));
    }

    /**
     * 将 LocalDateTime 格式化为CSV安全日期字符串
     * <p>
     * 使用统一格式 "yyyy-MM-dd HH:mm:ss" 进行格式化，
     * 并在日期字符串前添加制表符前缀，防止Excel将日期值自动转换或误解析。
     * </p>
     *
     * @param dt 日期时间对象，可能为null
     * @return 格式化后的日期字符串，null值返回空字符串
     */
    private String csvDate(LocalDateTime dt) {
        if (dt == null) return "";
        return "\t" + dt.format(CSV_DATE_FMT);
    }

    /**
     * 管理员同意退款申请。
     * <p>
     * 将订单状态由申请退款中(6)更新为退款审核中(7)，记录同意时间并邮件通知用户。
     * </p>
     *
     * @param orderId 订单ID
     * @return 包含操作结果的Result对象
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
     * 管理员拒绝退款申请。
     * <p>
     * 将订单状态恢复到退款前的原状态（refund_prev_status），记录拒绝原因并邮件通知用户。
     * </p>
     *
     * @param orderId 订单ID
     * @param remark  拒绝原因备注
     * @return 包含操作结果的Result对象
     */
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
        int prevStatus = order.getRefundPrevStatus() != null ? order.getRefundPrevStatus() : PAID.getCode();
        boolean success = update()
                .set("status", prevStatus)
                .set("refund_handle_remark", remark)
                .set("refund_approve_time", LocalDateTime.now())
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
     * 管理员审核退款。
     * <p>
     * 审核通过：订单变为已退款(8)，恢复库存并扣回销量；审核不通过：订单恢复到退款前原状态。
     * 两种结果均邮件通知用户。
     * </p>
     *
     * @param orderId 订单ID
     * @param passed  审核是否通过
     * @param remark  审核备注（不通过时必填原因）
     * @return 包含操作结果的Result对象
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
            // 审核通过：恢复库存 + 状态更新为已退款
            orderService.restoreStock(orderId);
            // 仅在订单曾处于"已完成(3)/已评价(5)"时扣回销量：
            // 这两类状态在确认收货时累加过 sale_count，退款需对称扣回；
            // 已支付(1)/已发货(2)订单从未累加销量，扣减会导致销量失真甚至为负。
            int prevStatus = order.getRefundPrevStatus() != null ? order.getRefundPrevStatus() : PAID.getCode();
            if (prevStatus == COMPLETED.getCode() || prevStatus == REVIEWED.getCode()) {
                // 扣回销量失败必须抛异常回滚整个事务（含已恢复的库存），
                // 避免出现"库存已恢复但销量未扣回"的台账不一致
                List<OrderItem> items = orderItemService.lambdaQuery()
                        .eq(OrderItem::getOrderId, orderId).list();
                for (OrderItem item : items) {
                    if (item.getFurnitureId() != null && item.getQuantity() != 0) {
                        furnitureMapper.incrementSaleCount(item.getFurnitureId(), -item.getQuantity());
                    }
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
            orderService.returnCouponForOrder(orderId); // 退款成功归还已用优惠券
            OrderEmailUtil.sendOrderStatus(emailService, userMapper, order, "退款成功",
                    "您的订单 #" + order.getId() + " 退款已到账，感谢您的理解与支持。",
                    "✅", null);
            log.info("退款审核通过: orderId={}", orderId);
        } else {
            // 审核不通过：恢复原状态
            int prevStatus = order.getRefundPrevStatus() != null ? order.getRefundPrevStatus() : PAID.getCode();
            boolean success = update()
                    .set("status", prevStatus)
                    .set("refund_handle_remark", remark)
                    .set("refund_audit_time", LocalDateTime.now())
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
     * 获取待处理退款数量（申请退款中 + 退款审核中）。
     *
     * @return 包含待处理退款数量的Result对象
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
     * 删除单个订单（仅允许已完结订单）。
     * <p>
     * 已完结订单（已取消/已完成/已评价/已退款）不占用库存，删除时无需恢复库存；
     * 在途订单（待支付/已支付/已发货）仍占用库存，拒绝直接删除。
     * </p>
     *
     * @param orderId 订单ID
     * @return 包含删除结果的Result对象
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
        removeById(orderId);
        log.info("管理员删除订单: orderId={}, status={}", orderId, order.getStatus());
        return Result.okMsg("删除成功");
    }

    /**
     * 批量删除订单（仅允许已完结订单）。
     * <p>
     * 逐条校验状态，仅删除已完结订单；在途订单跳过并返回被跳过的数量。
     * </p>
     *
     * @param ids 订单ID列表
     * @return 包含删除结果的Result对象
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
        removeByIds(deletableIds);
        int deleted = deletableIds.size();
        String msg = "删除成功 " + deleted + " 个订单";
        if (skipped > 0) {
            msg += "，跳过 " + skipped + " 个在途订单";
        }
        return Result.okMsg(msg);
    }

    /**
     * 判断订单状态是否允许删除（已完结订单）。
     *
     * @param status 订单状态码
     * @return 允许删除返回 true
     */
    private boolean isDeletableStatus(int status) {
        return status == CANCELLED.getCode()
                || status == COMPLETED.getCode()
                || status == REVIEWED.getCode()
                || status == REFUNDED.getCode();
    }

}
