package gcy.system.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import gcy.system.entity.dto.CartFormDTO;
import gcy.system.entity.dto.OrderItemDTO;
import gcy.system.entity.dto.Result;
import gcy.system.entity.dto.UserDTO;
import gcy.system.entity.pojo.*;
import gcy.system.entity.vo.OrderVO;
import gcy.system.exception.BusinessException;
import gcy.system.integration.EmailService;
import gcy.system.mapper.*;
import gcy.system.service.IOrderItemService;
import gcy.system.service.IOrderService;
import gcy.system.service.admin.AdminNotifyService;
import gcy.system.service.admin.Impl.NotifySettingServiceImpl;
import gcy.system.utils.OrderEmailUtil;
import gcy.system.utils.RedisData;
import gcy.system.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static gcy.system.utils.OrderStatus.*;
import static gcy.system.utils.RedisConstants.CACHE_FURNITURE_KEY;
import static gcy.system.utils.RedisConstants.ORDER_CREATE_KEY;

/**
 * 订单服务实现类，负责订单的创建、支付、取消、删除、确认收货、超时取消及库存管理等核心业务流程。
 * 采用 Redisson 分布式锁防止重复下单，使用 CAS（Compare And Set）乐观锁保证状态变更的并发安全。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrderService {

    private final FurnitureMapper furnitureMapper;

    private final IOrderItemService orderItemService;

    private final StringRedisTemplate stringRedisTemplate;

    private final EmailService emailService;

    private final UserMapper userMapper;

    private final AdminNotifyService adminNotifyService;

    private final SkuMapper skuMapper;

    private final SkuSpecMapper skuSpecMapper;

    private final SpecGroupMapper specGroupMapper;

    private final SpecValueMapper specValueMapper;

    private final CouponMapper couponMapper;

    private final UserCouponMapper userCouponMapper;

    private final RedissonClient redissonClient;

    /**
     * 创建订单。
     * 使用 Redisson 分布式锁防止同一用户并发重复下单，校验收货信息完整性后，
     * 遍历购物车商品列表：校验商品是否存在、库存是否充足（支持 SKU 规格模式和无规格模式），
     * 扣减库存并计算订单总金额，最终保存订单主体及订单明细。
     *
     * @param dto 购物车下单数据传输对象，包含收货人、收货地址、联系电话和商品列表
     * @return Result 成功时返回订单 ID，失败时返回错误提示信息
     * @throws BusinessException 当商品数量无效、商品不存在或已下架、规格不匹配、库存不足、订单明细保存失败时抛出
     */
    @Override
    @Transactional
    public Result createOrder(CartFormDTO dto) {
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        String lockKey = ORDER_CREATE_KEY + userId;
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked;
        try {
            locked = lock.tryLock(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取下单锁被中断: userId={}", userId, e);
            return Result.fail("系统繁忙，请稍后重试");
        }
        if (!locked) {
            return Result.fail("操作处理中，请勿重复提交");
        }
        try {
            if (StrUtil.isBlank(dto.getConsignee()) || StrUtil.isBlank(dto.getAddress()) || StrUtil.isBlank(dto.getPhone())) {
                return Result.fail("请填写完整的收货信息");
            }
            List<OrderItemDTO> items = dto.getItemList();
            if (items == null || items.isEmpty()) {
                return Result.fail("购物车为空");
            }
            Order order = BeanUtil.copyProperties(dto, Order.class);
            order.setCreateTime(LocalDateTime.now());
            order.setStatus(PENDING_PAYMENT.getCode());
            order.setUserId(userId);
            BigDecimal totalAmount = BigDecimal.ZERO;
            Set<Long> itemTypeIds = new HashSet<>();
            List<OrderItem> orderItems = new ArrayList<>();
            for (OrderItemDTO itemDto : items) {
                Long furnitureId = itemDto.getFurnitureId();
                Long skuId = itemDto.getSkuId();
                int quantity = itemDto.getQuantity();
                if (quantity <= 0) {
                    throw new BusinessException("商品数量必须大于0");
                }
                Furniture furniture = furnitureMapper.selectById(furnitureId);
                if (furniture == null) {
                    throw new BusinessException("商品不存在或已下架");
                }
                if (furniture.getTypeId() != null) {
                    itemTypeIds.add(furniture.getTypeId());
                }
                BigDecimal itemPrice;
                if (skuId != null) {
                    Sku sku = skuMapper.selectById(skuId);
                    if (sku == null || !sku.getFurnitureId().equals(furnitureId)) {
                        throw new BusinessException("商品规格不存在");
                    }
                    // 校验规格可售状态（status=1 为可售）
                    if (sku.getStatus() != null && sku.getStatus() != 1) {
                        throw new BusinessException("商品 " + furniture.getFName() + " 该规格已停售");
                    }
                    if (sku.getStock() < quantity) {
                        throw new BusinessException("商品 " + furniture.getFName() + " 该规格库存不足，当前库存: " + sku.getStock());
                    }
                    int rows = skuMapper.decrementStock(skuId, quantity);
                    if (rows == 0) {
                        throw new BusinessException("商品 " + furniture.getFName() + " 库存发生变化，请重新下单");
                    }
                    // 同步扣减家具总库存，失败则回滚整个下单事务（防止 SKU 已扣而总库存未扣的台账不一致）
                    int furRows = furnitureMapper.decrementStock(furnitureId, quantity);
                    if (furRows == 0) {
                        throw new BusinessException("商品 " + furniture.getFName() + " 库存发生变化，请重新下单");
                    }
                    itemPrice = sku.getPrice();
                } else {
                    if (skuMapper.selectCount(
                            new LambdaQueryWrapper<Sku>().eq(Sku::getFurnitureId, furnitureId)) > 0) {
                        throw new BusinessException("商品「" + furniture.getFName() + "」有多个规格，请选择具体规格后下单");
                    }
                    if (furniture.getStock() < quantity) {
                        throw new BusinessException("商品 " + furniture.getFName() + " 库存不足，当前库存: " + furniture.getStock());
                    }
                    int rows = furnitureMapper.decrementStock(furnitureId, quantity);
                    if (rows == 0) {
                        throw new BusinessException("商品 " + furniture.getFName() + " 库存发生变化，请重新下单");
                    }
                    itemPrice = furniture.getPrice();
                }

                Furniture latestFurniture = furnitureMapper.selectById(furnitureId);
                if (latestFurniture != null) {
                    updateFurnitureCache(latestFurniture);
                }
                BigDecimal itemTotal = itemPrice.multiply(new BigDecimal(quantity));
                totalAmount = totalAmount.add(itemTotal);
                OrderItem orderItem = new OrderItem();
                orderItem.setFurnitureId(furnitureId);
                orderItem.setSkuId(skuId);
                orderItem.setPrice(itemPrice);
                orderItem.setQuantity(quantity);
                orderItem.setItemTotalPrice(itemTotal);
                orderItem.setFurnitureName(furniture.getFName());
                orderItem.setFurnitureIcon(furniture.getFIcon());
                if (skuId != null) {
                    orderItem.setSkuSpec(buildSkuSpecText(skuId));
                }
                orderItems.add(orderItem);
            }
            order.setTotalPrice(totalAmount);
            LocalDateTime now = LocalDateTime.now();
            // 优惠券抵扣（支持多张：可叠加的券可同时使用，不可叠加的券只能单独用）
            List<Long> userCouponIds = collectCouponIds(dto);
            if (!userCouponIds.isEmpty()) {
                List<Coupon> usedCoupons = validateCoupons(userId, userCouponIds, totalAmount, itemTypeIds, now);
                BigDecimal discount = calcCouponsDiscount(usedCoupons, totalAmount);
                order.setCouponId(usedCoupons.get(0).getId());
                order.setCouponDiscount(discount);
                order.setTotalPrice(totalAmount.subtract(discount).max(BigDecimal.ZERO));
            }
            save(order);
            Long orderId = order.getId();
            for (OrderItem item : orderItems) {
                item.setOrderId(orderId);
            }
            boolean success = orderItemService.saveBatch(orderItems);
            if (!success) {
                throw new BusinessException("订单明细保存失败");
            }
            // 订单创建成功后，将已用优惠券置为已用并关联订单
            for (Long userCouponId : userCouponIds) {
                markCouponUsed(userCouponId, orderId, now);
            }
            log.info("订单创建成功: orderId={}, userId={}, amount={}", orderId, userId, totalAmount);
            // 通知管理员有新订单
            adminNotifyService.sendNotification(NotifySettingServiceImpl.TYPE_NEW_ORDER, "🛒 新订单通知",
                    "系统产生了新订单，请及时处理。\n订单号：" + orderId + "\n金额：¥" + totalAmount);
            return Result.ok(orderId);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 校验优惠券是否可用于本单：归属、未用、有效期内、门槛、适用范围。不满足抛异常。
     */
    private Coupon validateCoupon(Long userId, Long userCouponId, BigDecimal goodsTotal, Set<Long> itemTypeIds, LocalDateTime now) {
        UserCoupon uc = userCouponMapper.selectById(userCouponId);
        if (uc == null || !uc.getUserId().equals(userId)) {
            throw new BusinessException("优惠券不存在");
        }
        if (uc.getStatus() != null && uc.getStatus() != 0) {
            throw new BusinessException("优惠券不可用");
        }
        if (uc.getExpireTime() != null && uc.getExpireTime().isBefore(now)) {
            throw new BusinessException("优惠券已过期");
        }
        Coupon c = couponMapper.selectById(uc.getCouponId());
        if (c == null || c.getStatus() == null || c.getStatus() != 1) {
            throw new BusinessException("优惠券已停用");
        }
        BigDecimal threshold = c.getMinThreshold() == null ? BigDecimal.ZERO : c.getMinThreshold();
        if (goodsTotal.compareTo(threshold) < 0) {
            throw new BusinessException("未满足优惠券使用门槛");
        }
        if (c.getScope() != null && c.getScope() == 1 && c.getTypeId() != null
                && (itemTypeIds == null || !itemTypeIds.contains(c.getTypeId()))) {
            throw new BusinessException("该优惠券不适用于所选商品");
        }
        // 按券类型校验关键字段，避免收到的券配置异常导致抵扣失真
        if (c.getType() == null || (c.getType() == 2 && c.getDiscount() == null)
                || (c.getType() != 2 && c.getAmount() == null)) {
            throw new BusinessException("优惠券配置异常，请联系管理员");
        }
        return c;
    }

    /**
     * 计算优惠金额：满减/无门槛取面额；折扣券按折扣率并受最高优惠上限约束，均不超过商品总额。
     */
    private BigDecimal calcCouponDiscount(Coupon c, BigDecimal goodsTotal) {
        BigDecimal discount;
        if (c.getType() != null && c.getType() == 2 && c.getDiscount() != null) {
            discount = goodsTotal.multiply(BigDecimal.ONE.subtract(c.getDiscount()));
            if (c.getCapAmount() != null && discount.compareTo(c.getCapAmount()) > 0) {
                discount = c.getCapAmount();
            }
        } else {
            discount = c.getAmount() == null ? BigDecimal.ZERO : c.getAmount();
        }
        discount = discount.max(BigDecimal.ZERO).min(goodsTotal);
        return discount.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    /**
     * 下单成功后，将使用的优惠券置为已用并关联订单。
     */
    private void markCouponUsed(Long userCouponId, Long orderId, LocalDateTime now) {
        userCouponMapper.update(null, new LambdaUpdateWrapper<UserCoupon>()
                .eq(UserCoupon::getId, userCouponId)
                .eq(UserCoupon::getStatus, 0)
                .set(UserCoupon::getStatus, 1)
                .set(UserCoupon::getUseTime, now)
                .set(UserCoupon::getOrderId, orderId));
    }

    /**
     * 从下单表单提取优惠券ID列表：优先 userCouponIds（多张），否则回退 userCouponId（单张）。
     */
    private List<Long> collectCouponIds(CartFormDTO dto) {
        if (dto.getUserCouponIds() != null && !dto.getUserCouponIds().isEmpty()) {
            return dto.getUserCouponIds().stream().distinct().collect(Collectors.toList());
        }
        return dto.getUserCouponId() == null ? new ArrayList<>() : List.of(dto.getUserCouponId());
    }

    /**
     * 批量校验多张优惠券，并施加叠加规则：不可叠加券只能单独用一张；可叠加券可与其他可叠加券同用。
     */
    private List<Coupon> validateCoupons(Long userId, List<Long> userCouponIds, BigDecimal goodsTotal,
                                         Set<Long> itemTypeIds, LocalDateTime now) {
        List<Coupon> coupons = new ArrayList<>();
        boolean hasExclusive = false;
        for (Long id : userCouponIds) {
            Coupon c = validateCoupon(userId, id, goodsTotal, itemTypeIds, now);
            boolean stackable = c.getStackable() != null && c.getStackable() == 1;
            if (!stackable) {
                hasExclusive = true;
            }
            coupons.add(c);
        }
        if (coupons.size() > 1 && hasExclusive) {
            throw new BusinessException("不可叠加类优惠券不能与其他优惠券同时使用");
        }
        return coupons;
    }

    /**
     * 计算多张券的总抵扣：按顺序对剩余应付金额逐个抵扣（可叠加太简单稳步累加），总抵扣不超过商品总额。
     */
    private BigDecimal calcCouponsDiscount(List<Coupon> coupons, BigDecimal goodsTotal) {
        BigDecimal payable = goodsTotal;
        BigDecimal total = BigDecimal.ZERO;
        for (Coupon c : coupons) {
            BigDecimal discount = calcCouponDiscount(c, payable);
            total = total.add(discount);
            payable = payable.subtract(discount).max(BigDecimal.ZERO);
        }
        return total.max(BigDecimal.ZERO).min(goodsTotal).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 订单取消/超时/退款等环节归还已用优惠券。
     */
    private void returnCoupon(Order order) {
        if (order == null) {
            return;
        }
        List<UserCoupon> list = userCouponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, order.getUserId())
                .eq(UserCoupon::getOrderId, order.getId())
                .eq(UserCoupon::getStatus, 1));
        for (UserCoupon uc : list) {
            userCouponMapper.update(null, new LambdaUpdateWrapper<UserCoupon>()
                    .eq(UserCoupon::getId, uc.getId())
                    .set(UserCoupon::getStatus, 0)
                    .set(UserCoupon::getUseTime, null)
                    .set(UserCoupon::getOrderId, null));
            log.info("归还优惠券: userCouponId={}, orderId={}", uc.getId(), order.getId());
        }
    }

    /**
     * 对外归还某订单的已用优惠券（退款成功等场景调用）。
     */
    @Override
    public void returnCouponForOrder(Long orderId) {
        if (orderId == null) {
            return;
        }
        Order order = getById(orderId);
        returnCoupon(order);
    }

    /**
     * 根据当前登录用户 ID 分页查询订单列表。
     * 仅返回未被用户删除的订单，按创建时间倒序排列，同时批量加载每个订单的明细并组装为 VO 返回。
     *
     * @param current 当前页码，为 null 时默认第 1 页
     * @param size    每页记录数，为 null 时默认 10 条
     * @return Result 包含分页订单 VO 列表的成功结果
     */
    @Override
    public Result getOrderByUserId(Long current, Long size, String status) {
        Page<Order> page = new Page<>(current != null ? current : 1L, size != null ? size : 10L);
        UserDTO user = UserHolder.getUser();
        Long userId = user.getId();
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Order::getUserId, userId)
                .eq(Order::getUserDeleted, 0);
        // 状态筛选：支持逗号分隔多状态（如 "6,7,8"）
        if (StrUtil.isNotBlank(status)) {
            List<Integer> codes = Arrays.stream(status.split(","))
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
        wrapper.orderByDesc(Order::getCreateTime);
        Page<Order> resultPage = this.page(page, wrapper);
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
        Page<OrderVO> voPage = new Page<>();
        voPage.setRecords(voList);
        voPage.setTotal(resultPage.getTotal());
        voPage.setSize(resultPage.getSize());
        voPage.setCurrent(resultPage.getCurrent());
        return Result.ok(voPage);
    }

    /**
     * 软删除指定订单（将 user_deleted 标记置为 1）。
     * 仅当订单状态为已取消、已完成或已评价时允许删除，且仅允许订单所属用户操作。
     *
     * @param id 订单 ID
     * @return Result 操作结果，成功或包含错误提示
     */
    @Override
    @Transactional
    public Result deleteMyOrder(Long id) {
        Long userId = UserHolder.getUser().getId();
        Order order = getById(id);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        if (!order.getUserId().equals(userId)) {
            return Result.fail("无权操作该订单");
        }
        int status = order.getStatus();
        if (status == REFUND_APPLYING.getCode() || status == REFUND_AUDITING.getCode()) {
            return Result.fail("订单退款处理中，暂不能删除");
        }
        if (status != CANCELLED.getCode()
                && status != COMPLETED.getCode()
                && status != REVIEWED.getCode()
                && status != REFUNDED.getCode()) {
            return Result.fail("该订单状态不允许删除，请先取消或完成订单");
        }
        update().set("user_deleted", 1).eq("id", id).update();
        log.info("用户删除订单: orderId={}, userId={}", id, userId);
        return Result.ok();
    }

    /**
     * 支付指定订单。
     * 校验订单归属和状态后，使用 CAS 乐观锁（eq status）将待支付状态更新为已支付，
     * 并记录支付时间。支付成功后异步发送邮件通知用户。
     *
     * @param id 订单 ID
     * @return Result 支付成功返回 ok，失败返回错误提示；若订单已支付或已发货则幂等返回成功
     */
    @Override
    @Transactional
    public Result payOrderById(Long id) {
        Order order = getById(id);
        if (order == null) {
            return Result.fail("订单不存在！");
        }
        Long userId = UserHolder.getUser().getId();
        int status = order.getStatus();
        if (!order.getUserId().equals(userId)) {
            return Result.fail("无权支付该订单！");
        }
        if (status != PENDING_PAYMENT.getCode()) {
            if (status == PAID.getCode() || status == SHIPPED.getCode()) {
                return Result.ok();
            }
            return Result.fail("订单状态异常，请稍后重新支付或取消订单！");
        }
        boolean success = update()
                .set("status", PAID.getCode())
                .set("pay_time", LocalDateTime.now())
                .eq("id", id)
                .eq("status", PENDING_PAYMENT.getCode())
                .update();
        if (!success) {
            Order updated = getById(id);
            if (updated.getStatus() == PAID.getCode() || updated.getStatus() == SHIPPED.getCode()) {
                return Result.ok();
            }
            return Result.fail("支付失败，请重试");
        }
        OrderEmailUtil.sendOrderStatus(emailService, userMapper, order, "订单支付成功",
                "您的订单 #" + order.getId() + " 已支付成功，我们将尽快为您发货。",
                "💳", null);
        log.info("订单支付成功: orderId={}, userId={}", id, order.getUserId());
        return Result.ok();
    }

    /**
     * 支付成功确认订单（支付宝异步回调触发）。
     * <p>
     * 该方法不依赖当前登录用户，由支付网关回调验签、金额核对通过后调用，
     * 使用 CAS 乐观锁将待支付订单更新为已支付，并记录支付时间；
     * 若订单已支付或已发货则幂等返回成功。
     * </p>
     *
     * @param orderId 待确认的订单ID
     * @return Result 支付成功返回 ok，已支付则幂等返回成功，状态异常返回失败
     */
    @Override
    @Transactional
    public Result confirmPaid(Long orderId) {
        Order order = getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在！");
        }
        int status = order.getStatus();
        if (status != PENDING_PAYMENT.getCode()) {
            if (status == PAID.getCode() || status == SHIPPED.getCode()) {
                return Result.ok();
            }
            return Result.fail("订单状态异常，无法确认支付！");
        }
        boolean success = update()
                .set("status", PAID.getCode())
                .set("pay_time", LocalDateTime.now())
                .eq("id", orderId)
                .eq("status", PENDING_PAYMENT.getCode())
                .update();
        if (!success) {
            Order updated = getById(orderId);
            if (updated.getStatus() == PAID.getCode() || updated.getStatus() == SHIPPED.getCode()) {
                return Result.ok();
            }
            return Result.fail("支付确认失败，请稍后重试");
        }
        OrderEmailUtil.sendOrderStatus(emailService, userMapper, order, "订单支付成功",
                "您的订单 #" + orderId + " 已支付成功，我们将尽快为您发货。",
                "💳", null);
        log.info("支付回调确认到账，订单已支付: orderId={}", orderId);
        return Result.ok();
    }

    /**
     * 用户手动取消订单。
     * 仅允许订单所属用户在待支付状态下取消，取消时恢复库存并更新订单状态。
     *
     * @param id 订单 ID
     * @return Result 取消成功返回 ok，失败返回错误提示（如订单已支付、无权操作等）
     */
    @Override
    @Transactional
    public Result cancelOrder(Long id) {
        Order order = getById(id);
        if (order == null) {
            return Result.fail("订单不存在！");
        }
        Long userId = UserHolder.getUser().getId();
        if (!order.getUserId().equals(userId)) {
            return Result.fail("无权取消该订单！");
        }
        int status = order.getStatus();
        if (status != PENDING_PAYMENT.getCode()) {
            if (status == PAID.getCode() || status == SHIPPED.getCode()) {
                return Result.fail("订单已支付！");
            }
            return Result.fail("订单状态异常，请稍后重试！");
        }
        doCancelOrder(id);
        returnCoupon(order); // 取消后归还已用优惠券
        log.info("用户取消订单: orderId={}, userId={}", id, userId);
        return Result.ok();
    }

    /**
     * 系统自动取消超时未支付订单。
     * 无用户上下文，因此跳过用户归属校验；仅在订单仍处于待支付状态时执行取消操作。
     *
     * @param id 订单 ID
     * @return Result 取消成功返回 ok；若订单状态已变更则幂等返回成功并记录日志
     */
    @Transactional
    public Result cancelTimeoutOrder(Long id) {
        Order order = getById(id);
        if (order == null) {
            return Result.fail("订单不存在");
        }
        if (order.getStatus() != PENDING_PAYMENT.getCode()) {
            log.info("超时取消时订单状态已变更，跳过: orderId={}, status={}", id, order.getStatus());
            return Result.ok();
        }
        doCancelOrder(id);
        returnCoupon(order); // 超时取消后归还已用优惠券
        log.info("超时未支付订单已自动取消: orderId={}, userId={}", id, order.getUserId());
        return Result.ok();
    }

    /**
     * 取消订单的核心操作：恢复库存后使用 CAS 乐观锁将订单状态更新为已取消。
     * 调用方需自行完成权限校验和锁控制。
     *
     * @param orderId 订单 ID
     * @throws BusinessException 当商品不存在、库存恢复失败或订单状态更新失败时抛出
     */
    private void doCancelOrder(Long orderId) {
        // 先 CAS 更新状态，确保只有一条线程能成功
        boolean success = update()
                .set("status", CANCELLED.getCode())
                .eq("id", orderId)
                .eq("status", PENDING_PAYMENT.getCode())
                .update();
        if (!success) {
            throw new BusinessException("订单状态更新失败！");
        }
        // 状态更新成功后再恢复库存，避免重复恢复
        restoreStock(orderId);
    }

    /**
     * 恢复指定订单占用的库存：遍历订单明细恢复 SKU 库存和家具总库存，
     * 并同步更新 Redis 缓存。供订单取消和退款审核通过复用。
     *
     * @param orderId 订单 ID
     * @throws BusinessException 当商品不存在或库存恢复失败时抛出
     */
    @Override
    public void restoreStock(Long orderId) {
        LambdaQueryWrapper<OrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderItem::getOrderId, orderId);
        List<OrderItem> items = orderItemService.list(wrapper);
        for (OrderItem item : items) {
            Long furnitureId = item.getFurnitureId();
            Long skuId = item.getSkuId();
            int quantity = item.getQuantity();
            if (quantity == 0) continue;
            if (skuId != null) {
                // SKU模式：恢复SKU库存 + 同步恢复furniture表总库存
                // incrementStock 为自定义 SQL（无 deleted 过滤），软删除商品也能正常恢复
                skuMapper.incrementStock(skuId, quantity);
                furnitureMapper.incrementStock(furnitureId, quantity);
            } else {
                // 兼容旧模式
                furnitureMapper.incrementStock(furnitureId, quantity);
            }
            // 刷新缓存：软删商品 selectById 返回 null，仅告警跳过，不影响库存恢复
            try {
                Furniture latestFurniture = furnitureMapper.selectById(furnitureId);
                if (latestFurniture != null) {
                    updateFurnitureCache(latestFurniture);
                }
            } catch (Exception e) {
                log.warn("恢复库存后刷新家具缓存失败: furnitureId={}", furnitureId, e);
            }
        }
    }

    /**
     * 用户申请退款。
     * <p>
     * 校验订单归属和状态（仅已支付/已发货/已完成/已评价可申请），
     * 使用 CAS 乐观锁将状态更新为申请退款中(6)，并记录退款原因、原状态和申请时间。
     * 已处于退款流程中的订单幂等返回成功。
     * </p>
     *
     * @param orderId      订单ID
     * @param refundReason 退款原因
     * @param userId       当前操作用户ID
     * @return 包含申请结果的操作结果对象
     */
    @Override
    @Transactional
    public Result applyRefund(Long orderId, String refundReason, Long userId) {
        Order order = getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在！");
        }
        if (!order.getUserId().equals(userId)) {
            return Result.fail("无权操作该订单！");
        }
        int status = order.getStatus();
        if (status == REFUND_APPLYING.getCode()) {
            return Result.ok();
        }
        if (status == REFUND_AUDITING.getCode() || status == REFUNDED.getCode()) {
            return Result.fail("订单已处于退款流程中");
        }
        if (status != PAID.getCode() && status != SHIPPED.getCode()
                && status != COMPLETED.getCode() && status != REVIEWED.getCode()) {
            return Result.fail("当前订单状态不支持申请退款");
        }
        boolean success = update()
                .set("status", REFUND_APPLYING.getCode())
                .set("refund_reason", refundReason)
                .set("refund_prev_status", status)
                .set("refund_apply_time", LocalDateTime.now())
                .eq("id", orderId)
                .eq("status", status)
                .update();
        if (!success) {
            return Result.fail("退款申请失败，请重试");
        }
        OrderEmailUtil.sendOrderStatus(emailService, userMapper, order, "退款申请已提交",
                "您的订单 #" + order.getId() + " 退款申请已提交，我们将在审核后尽快处理。",
                "🔄", refundReason);
        // 通知管理员有新退款申请
        adminNotifyService.sendNotification(NotifySettingServiceImpl.TYPE_REFUND, "🛡️ 新退款申请",
                "用户申请了退款，请及时审核。\n订单号：" + orderId + "\n退款原因：" + refundReason);
        log.info("用户申请退款: orderId={}, userId={}, reason={}", orderId, userId, refundReason);
        return Result.ok();
    }

    /**
     * 确认收货。
     * 仅允许订单所属用户在已发货状态下操作，使用 CAS 乐观锁将状态更新为已完成，
     * 并记录收货时间。确认成功后累加对应商品的销量计数，并发送确认收货邮件通知。
     *
     * @param id 订单 ID
     * @return Result 确认成功返回 ok；若订单已确认或已评价则幂等返回成功
     * @throws BusinessException 当 CAS 更新失败且订单状态未变为已完成/已评价时抛出
     */
    @Override
    @Transactional
    public Result confirmReceipt(Long id) {
        Long userId = UserHolder.getUser().getId();
        Order order = getById(id);
        if (order == null) {
            return Result.fail("订单不存在！");
        }
        if (!order.getUserId().equals(userId)) {
            return Result.fail("无权操作该订单！");
        }
        int status = order.getStatus();
        if (status != SHIPPED.getCode()) {
            if (status == PENDING_PAYMENT.getCode()) {
                return Result.fail("请先支付！");
            } else if (status == PAID.getCode()) {
                return Result.fail("订单还未发货，请不要随意收货哦！");
            } else if (status == COMPLETED.getCode() || status == REVIEWED.getCode()) {
                return Result.ok();
            } else if (status == REFUND_APPLYING.getCode()
                    || status == REFUND_AUDITING.getCode()
                    || status == REFUNDED.getCode()) {
                return Result.fail("订单处于退款流程中，无法确认收货！");
            } else {
                return Result.fail("订单已经取消，请重新下单！");
            }
        }
        boolean success = update()
                .set("status", COMPLETED.getCode())
                .set("receive_time", LocalDateTime.now())
                .eq("id", id)
                .eq("status", SHIPPED.getCode())
                .update();
        if (!success) {
            Order updated = getById(id);
            if (updated.getStatus() == COMPLETED.getCode() || updated.getStatus() == REVIEWED.getCode()) {
                return Result.ok();
            }
            throw new BusinessException("确认收货失败，请稍后重试或联系平台客服！");
        }
        try {
            List<OrderItem> items = orderItemService.lambdaQuery()
                    .eq(OrderItem::getOrderId, id).list();
            for (OrderItem item : items) {
                if (item.getFurnitureId() != null && item.getQuantity() != 0) {
                    furnitureMapper.incrementSaleCount(item.getFurnitureId(), item.getQuantity());
                }
            }
        } catch (Exception e) {
            log.error("更新销量失败, orderId={}", id, e);
        }
        OrderEmailUtil.sendOrderStatus(emailService, userMapper, order, "订单已收货",
                "您的订单 #" + order.getId() + " 已确认收货，感谢您的购买！",
                "✅", null);
        log.info("订单确认收货: orderId={}, userId={}", id, userId);
        return Result.ok();
    }

    /**
     * 更新 Redis 中的家具缓存。
     * 仅当缓存已存在时才更新，避免写入无效缓存；缓存有效期为 1 小时。
     * 更新失败仅记录日志，不影响主业务流程。
     *
     * @param furniture 家具对象，包含最新的库存、价格等信息
     */
    private void updateFurnitureCache(Furniture furniture) {
        String key = CACHE_FURNITURE_KEY + furniture.getId();
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(cached)) {
            try {
                RedisData redisData = new RedisData();
                redisData.setData(furniture);
                redisData.setExpireTime(LocalDateTime.now().plusSeconds(3600));
                stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
            } catch (Exception e) {
                log.error("更新家具缓存失败: furnitureId={}", furniture.getId(), e);
            }
        }
    }

    /**
     * 根据 SKU ID 构建规格文本描述。
     * 通过批量查询规格组名称和规格值名称，组装为 "规格组:规格值,规格组:规格值" 格式的字符串。
     * 若该 SKU 没有关联规格，则返回 null。
     *
     * @param skuId SKU ID
     * @return 规格文本描述，如 "颜色:红色,尺寸:大号"；若该 SKU 无规格关联则返回 null
     */
    private String buildSkuSpecText(Long skuId) {
        List<SkuSpec> specs = skuSpecMapper.selectList(
                new LambdaQueryWrapper<SkuSpec>().eq(SkuSpec::getSkuId, skuId));
        if (specs.isEmpty()) return null;

        // 批量加载规格组和规格值，避免循环内逐条 selectById（N+1）
        List<Long> groupIds = specs.stream().map(SkuSpec::getSpecGroupId).distinct().collect(Collectors.toList());
        List<Long> valueIds = specs.stream().map(SkuSpec::getSpecValueId).distinct().collect(Collectors.toList());
        Map<Long, String> groupNames = specGroupMapper.selectByIds(groupIds).stream()
                .collect(Collectors.toMap(SpecGroup::getId, SpecGroup::getGroupName));
        Map<Long, String> valueNames = specValueMapper.selectByIds(valueIds).stream()
                .collect(Collectors.toMap(SpecValue::getId, SpecValue::getValueName));

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < specs.size(); i++) {
            SkuSpec ss = specs.get(i);
            String groupName = groupNames.get(ss.getSpecGroupId());
            String valueName = valueNames.get(ss.getSpecValueId());
            if (groupName != null && valueName != null) {
                if (i > 0) sb.append(",");
                sb.append(groupName).append(":").append(valueName);
            }
        }
        return sb.toString();
    }
}

