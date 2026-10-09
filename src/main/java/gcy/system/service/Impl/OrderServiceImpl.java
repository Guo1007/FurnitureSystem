package gcy.system.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import gcy.system.entity.dto.CartFormDTO;
import gcy.system.entity.dto.OrderEstimateDTO;
import gcy.system.entity.dto.OrderItemDTO;
import gcy.system.entity.dto.Result;
import gcy.system.entity.dto.StockDeltaDTO;
import gcy.system.entity.dto.UserDTO;
import gcy.system.entity.pojo.*;
import gcy.system.entity.vo.CouponEstimateVO;
import gcy.system.entity.vo.CouponOptionVO;
import gcy.system.entity.vo.OrderVO;
import gcy.system.exception.BusinessException;
import gcy.system.integration.EmailService;
import gcy.system.mapper.*;
import gcy.system.service.ICouponRuleConfigService;
import gcy.system.service.IOrderItemService;
import gcy.system.service.IOrderService;
import gcy.system.service.admin.AdminNotifyService;
import gcy.system.service.admin.Impl.NotifySettingServiceImpl;
import gcy.system.utils.OrderEmailUtil;
import gcy.system.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

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
     * 发货后自动确认收货的天数，取自 {@code order.auto-receive-days}（与 AutoReceiveScheduler 同源）。
     * 仅用于向前端下发「预计自动收货时间」，真正的自动收货仍由调度器执行。
     */
    @org.springframework.beans.factory.annotation.Value("${order.auto-receive-days:10}")
    private int autoReceiveDays;

    /**
     * 事务管理器。用于显式控制下单事务边界，使「加锁 → 开事务 → 提交 → 解锁」顺序可控。
     * 注意：本类内部方法互调不会经过 Spring 代理，@Transactional 会失效，故不使用注解。
     */
    private final PlatformTransactionManager txManager;

    /**
     * 优惠券叠加规则配置（最大叠加张数、总抵扣上限比例），后台可配。
     * 读取失败时服务内部回落到默认值，不会阻断下单。
     */
    private final ICouponRuleConfigService couponRuleConfigService;

    /**
     * 创建订单。
     * <p>
     * 使用 Redisson 分布式锁防止同一用户并发重复下单。锁必须位于事务之外：
     * 若事务注解加在本方法上，事务会在方法返回后才提交，而解锁写在 finally 中必然早于提交，
     * 导致并发请求在事务未提交时拿到锁并读到旧快照。这里用事务模板把事务收在内层，
     * 保证「提交之后才解锁」。
     * </p>
     * 校验收货信息完整性后遍历购物车：校验商品是否存在、库存是否充足（支持 SKU 规格与无规格两种模式），
     * 扣减库存并计算订单总金额，最终保存订单主体及订单明细。
     *
     * @param dto 购物车下单数据传输对象，包含收货人、收货地址、联系电话和商品列表
     * @return Result 成功时返回订单 ID，失败时返回错误提示信息
     * @throws BusinessException 当商品数量无效、商品不存在或已下架、规格不匹配、库存不足、订单明细保存失败时抛出
     */
    @Override
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
            // 事务在锁的内层开启：提交完成后 unlock 才执行
            return new TransactionTemplate(txManager).execute(status -> doCreateOrder(dto, userId));
        } finally {
            lock.unlock();
        }
    }

    /**
     * 下单核心逻辑，由 {@link #createOrder(CartFormDTO)} 通过事务模板调用，必须运行在事务内。
     */
    private Result doCreateOrder(CartFormDTO dto, Long userId) {
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
        // 算价：与「下单试算」共用同一段逻辑（纯读、无副作用）。
        // 商品/规格/库存的校验都内含其中，扣库存留在下面单独做。
        PricedCart cart = priceCart(items);
        BigDecimal totalAmount = cart.goodsTotal;

        // 库存发生变动的商品，待事务提交后统一失效缓存
        Set<Long> touchedFurnitureIds = new HashSet<>();
        List<OrderItem> orderItems = new ArrayList<>();

        // 扣库存。库存充足性校验已在 priceCart 内完成，这里只保留以「影响行数」
        // 兜底的并发 CAS：校验与扣减之间商品可能被别人买走，失败即回滚整个下单事务。
        for (PricedItem pi : cart.items) {
            if (pi.skuId != null) {
                int rows = skuMapper.decrementStock(pi.skuId, pi.quantity);
                if (rows == 0) {
                    throw new BusinessException("商品 " + pi.furniture.getFName() + " 库存发生变化，请重新下单");
                }
                // 同步扣减家具总库存，失败则回滚整个下单事务（防止 SKU 已扣而总库存未扣的台账不一致）
                int furRows = furnitureMapper.decrementStock(pi.furnitureId, pi.quantity);
                if (furRows == 0) {
                    throw new BusinessException("商品 " + pi.furniture.getFName() + " 库存发生变化，请重新下单");
                }
            } else {
                int rows = furnitureMapper.decrementStock(pi.furnitureId, pi.quantity);
                if (rows == 0) {
                    throw new BusinessException("商品 " + pi.furniture.getFName() + " 库存发生变化，请重新下单");
                }
            }
            // 记录库存变动涉及的商品，提交后再失效缓存：
            // 不在事务内写缓存，事务回滚时缓存无法同步回滚，会留下脏数据
            touchedFurnitureIds.add(pi.furnitureId);
        }

        // 组装订单明细
        for (PricedItem pi : cart.items) {
            OrderItem orderItem = new OrderItem();
            orderItem.setFurnitureId(pi.furnitureId);
            orderItem.setSkuId(pi.skuId);
            orderItem.setPrice(pi.price);
            orderItem.setQuantity(pi.quantity);
            orderItem.setItemTotalPrice(pi.itemTotal);
            orderItem.setFurnitureName(pi.furniture.getFName());
            orderItem.setFurnitureIcon(pi.furniture.getFIcon());
            if (pi.skuId != null) {
                orderItem.setSkuSpec(buildSkuSpecText(pi.skuId));
            }
            orderItems.add(orderItem);
        }
        order.setTotalPrice(totalAmount);
        LocalDateTime now = LocalDateTime.now();
        // 优惠券抵扣（支持多张：可叠加的券可同时使用，不可叠加的券只能单独用）
        List<Long> userCouponIds = collectCouponIds(dto);
        if (!userCouponIds.isEmpty()) {
            // 叠加张数上限取自后台配置，未配置时使用服务内置默认值
            int maxStackCount = couponRuleConfigService.getMaxStackCount();
            List<Coupon> usedCoupons = validateCoupons(userId, userCouponIds, totalAmount,
                    cart.itemTypeIds, cart.subTotalByType, now, maxStackCount);
            BigDecimal discount = calcCouponsDiscount(usedCoupons, totalAmount, cart.subTotalByType,
                    couponRuleConfigService.getMaxDiscountRatio());
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
        // 订单创建成功后，将已用优惠券置为已用并关联订单（一条 IN(...) 批量 UPDATE，替代逐条核销）
        markCouponsUsed(userCouponIds, orderId, now);
        // 库存已落库，提交后再失效缓存（由读路径自动重建）
        evictFurnitureCacheAfterCommit(touchedFurnitureIds);
        log.info("订单创建成功: orderId={}, userId={}, amount={}", orderId, userId, order.getTotalPrice());
        // 通知管理员有新订单（金额为优惠后实付，避免与订单实付不符）
        adminNotifyService.sendNotification(NotifySettingServiceImpl.TYPE_NEW_ORDER, "🛒 新订单通知",
                "系统产生了新订单，请及时处理。\n订单号：" + orderId + "\n实付金额：¥" + order.getTotalPrice());
        return Result.ok(orderId);
    }

    // ==================== 算价（下单与试算共用） ====================

    /**
     * 一条已计价的购物车明细。
     * <p>
     * 由 {@link #priceCart(List)} 产出，同时供「下单」（扣库存、落库）和「试算接口」使用，
     * 两条路径共用同一份算价结果，金额不可能对不上。
     * </p>
     */
    private static class PricedItem {
        /** 商品ID */
        final Long furnitureId;
        /** 规格ID（无规格为 null） */
        final Long skuId;
        /** 购买数量 */
        final int quantity;
        /** 命中的商品（取名称/图标，以及扣库存失败时的报错文案） */
        final Furniture furniture;
        /** 单价：有规格取规格价，否则取商品价 */
        final BigDecimal price;
        /** 小计 = price × quantity */
        final BigDecimal itemTotal;

        PricedItem(Long furnitureId, Long skuId, int quantity, Furniture furniture,
                   BigDecimal price, BigDecimal itemTotal) {
            this.furnitureId = furnitureId;
            this.skuId = skuId;
            this.quantity = quantity;
            this.furniture = furniture;
            this.price = price;
            this.itemTotal = itemTotal;
        }
    }

    /**
     * 一次算价的完整产物。
     */
    private static class PricedCart {
        /** 商品总额 */
        final BigDecimal goodsTotal;
        /** 本单命中的商品分类ID集合，供分类券适用性校验 */
        final Set<Long> itemTypeIds;
        /** 各分类小计，供分类券门槛与抵扣上限使用 */
        final Map<Long, BigDecimal> subTotalByType;
        /** 逐条明细 */
        final List<PricedItem> items;

        PricedCart(BigDecimal goodsTotal, Set<Long> itemTypeIds,
                   Map<Long, BigDecimal> subTotalByType, List<PricedItem> items) {
            this.goodsTotal = goodsTotal;
            this.itemTypeIds = itemTypeIds;
            this.subTotalByType = subTotalByType;
            this.items = items;
        }
    }

    /**
     * 购物车算价：校验商品/规格/库存并算出金额。
     * <p>
     * <b>纯读、无任何副作用</b> —— 不扣库存、不落库、不加锁。正因如此，「下单试算」
     * 可以直接复用本方法，这也是试算金额与下单金额必然一致的根本原因：
     * 它们本来就是同一段代码。
     * </p>
     * <p>
     * 校验失败一律抛 {@link BusinessException}，用户可见文案与下单时保持一致。
     * 库存只做「读取判断」，真正地并发兜底由下单流程随后执行的 CAS 扣减负责。
     * </p>
     *
     * @param items 购物车明细（来自请求体，只含「买什么、买几件」，单价一律查库）
     * @return 商品总额、分类小计、命中分类与逐条计价明细
     */
    private PricedCart priceCart(List<OrderItemDTO> items) {
        if (items == null || items.isEmpty()) {
            throw new BusinessException("购物车为空");
        }
        BigDecimal goodsTotal = BigDecimal.ZERO;
        Set<Long> itemTypeIds = new HashSet<>();
        // 按分类汇总商品小计，供分类券校验门槛与抵扣上限使用（分类券只对本分类金额生效）
        Map<Long, BigDecimal> subTotalByType = new HashMap<>();
        List<PricedItem> priced = new ArrayList<>();

        // 循环外批量预加载：原实现在循环内对每件商品查家具、查规格、再 COUNT 一次规格，
        // 20 件商品 ≈ 100+ 次 DB 往返且全部串行在一个事务里。这里压成 3 条查询。
        Set<Long> furnitureIdSet = items.stream().map(OrderItemDTO::getFurnitureId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Furniture> furnitureMap = furnitureIdSet.isEmpty() ? Map.of()
                : furnitureMapper.selectByIds(furnitureIdSet).stream()
                        .collect(Collectors.toMap(Furniture::getId, f -> f));

        Set<Long> skuIdSet = items.stream().map(OrderItemDTO::getSkuId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, Sku> skuMap = skuIdSet.isEmpty() ? Map.of()
                : skuMapper.selectByIds(skuIdSet).stream()
                        .collect(Collectors.toMap(Sku::getId, s -> s));

        // 「该商品是否存在规格」一次 IN 查询判完，替代逐条 COUNT(*)
        Set<Long> noSkuFurnitureIds = items.stream()
                .filter(i -> i.getSkuId() == null && i.getFurnitureId() != null)
                .map(OrderItemDTO::getFurnitureId)
                .collect(Collectors.toSet());
        Set<Long> furnitureIdsWithSku = noSkuFurnitureIds.isEmpty() ? Set.of()
                : skuMapper.selectList(new LambdaQueryWrapper<Sku>()
                        .select(Sku::getFurnitureId)
                        .in(Sku::getFurnitureId, noSkuFurnitureIds))
                        .stream().map(Sku::getFurnitureId).collect(Collectors.toSet());

        for (OrderItemDTO itemDto : items) {
            Long furnitureId = itemDto.getFurnitureId();
            Long skuId = itemDto.getSkuId();
            Integer quantityRaw = itemDto.getQuantity();
            int quantity = quantityRaw == null ? 0 : quantityRaw;
            if (quantity <= 0) {
                throw new BusinessException("商品数量必须大于0");
            }
            Furniture furniture = furnitureMap.get(furnitureId);
            if (furniture == null) {
                throw new BusinessException("商品不存在或已下架");
            }
            if (furniture.getTypeId() != null) {
                itemTypeIds.add(furniture.getTypeId());
            }
            BigDecimal itemPrice;
            if (skuId != null) {
                Sku sku = skuMap.get(skuId);
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
                itemPrice = sku.getPrice();
            } else {
                if (furnitureIdsWithSku.contains(furnitureId)) {
                    throw new BusinessException("商品「" + furniture.getFName() + "」有多个规格，请选择具体规格后下单");
                }
                if (furniture.getStock() < quantity) {
                    throw new BusinessException("商品 " + furniture.getFName() + " 库存不足，当前库存: " + furniture.getStock());
                }
                itemPrice = furniture.getPrice();
            }
            BigDecimal itemTotal = itemPrice.multiply(new BigDecimal(quantity));
            goodsTotal = goodsTotal.add(itemTotal);
            if (furniture.getTypeId() != null) {
                subTotalByType.merge(furniture.getTypeId(), itemTotal, BigDecimal::add);
            }
            priced.add(new PricedItem(furnitureId, skuId, quantity, furniture, itemPrice, itemTotal));
        }
        return new PricedCart(goodsTotal, itemTypeIds, subTotalByType, priced);
    }

    /**
     * 校验优惠券是否可用于本单：归属、未用、有效期内、门槛、适用范围。不满足抛异常。
     * <p>
     * 门槛按「适用基数」判断：全场券用整单金额，分类券（scope=1）只用该分类的商品小计，
     * 否则会出现「订单里只有一件该分类的低价商品，却能使用高额分类券」的漏洞。
     * </p>
     *
     * @param subTotalByType 各分类的商品小计（typeId -> 金额）
     */
    private Coupon validateCoupon(Long userId, Long userCouponId, BigDecimal goodsTotal, Set<Long> itemTypeIds,
                                  Map<Long, BigDecimal> subTotalByType, LocalDateTime now) {
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
        // 分类券以该分类小计为基数校验门槛与抵扣，全场券以整单金额为基数
        BigDecimal applicableBase = resolveCouponBase(c, goodsTotal, subTotalByType);
        BigDecimal threshold = c.getMinThreshold() == null ? BigDecimal.ZERO : c.getMinThreshold();
        if (applicableBase.compareTo(threshold) < 0) {
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
     * 计算单张券的优惠金额：满减/无门槛取面额；折扣券按折扣率并受最高优惠上限约束。
     * 抵扣上限为「适用基数」——分类券不得超过该分类小计，全场券不得超过订单剩余金额。
     */
    private BigDecimal calcCouponDiscount(Coupon c, BigDecimal applicableBase) {
        BigDecimal discount;
        if (c.getType() != null && c.getType() == 2 && c.getDiscount() != null) {
            discount = applicableBase.multiply(BigDecimal.ONE.subtract(c.getDiscount()));
            if (c.getCapAmount() != null && discount.compareTo(c.getCapAmount()) > 0) {
                discount = c.getCapAmount();
            }
        } else {
            discount = c.getAmount() == null ? BigDecimal.ZERO : c.getAmount();
        }
        discount = discount.max(BigDecimal.ZERO).min(applicableBase);
        return discount.setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 确定某张券的适用基数：分类券为该分类商品小计，全场券为整单金额。
     */
    private BigDecimal resolveCouponBase(Coupon c, BigDecimal goodsTotal, Map<Long, BigDecimal> subTotalByType) {
        if (c.getScope() != null && c.getScope() == 1 && c.getTypeId() != null) {
            if (subTotalByType == null) {
                return BigDecimal.ZERO;
            }
            return subTotalByType.getOrDefault(c.getTypeId(), BigDecimal.ZERO);
        }
        return goodsTotal == null ? BigDecimal.ZERO : goodsTotal;
    }

    /**
     * 下单成功后，将使用的优惠券置为已用并关联订单。
     * 必须校验影响行数：若并发下该券已被核销，此处会静默更新 0 行而不报错，
     * 导致同一张券被多笔订单同时使用。
     * 批量核销本单用到的优惠券：一条 {@code IN(...)} UPDATE 完成，避免 N 张券 N 次往返。
     * <p>
     * 仍然带 {@code status = 0} 的 CAS 条件并校验影响行数：并发下若某张券已被核销，
     * 影响行数会小于券数，此时直接抛异常回滚，不会静默「一券多用」。
     * </p>
     */
    private void markCouponsUsed(List<Long> userCouponIds, Long orderId, LocalDateTime now) {
        if (userCouponIds == null || userCouponIds.isEmpty()) {
            return;
        }
        int rows = userCouponMapper.update(null, new LambdaUpdateWrapper<UserCoupon>()
                .in(UserCoupon::getId, userCouponIds)
                .eq(UserCoupon::getStatus, 0)
                .set(UserCoupon::getStatus, 1)
                .set(UserCoupon::getUseTime, now)
                .set(UserCoupon::getOrderId, orderId));
        if (rows != userCouponIds.size()) {
            throw new BusinessException("优惠券已被使用，请重新下单");
        }
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
     * 批量校验多张优惠券，并施加叠加规则：
     * 不可叠加券只能单独用一张；可叠加券总数受后台配置的「最大叠加张数」限制。
     * <p>
     * 注意 1：不对同一券模板去重——同一张券领取 N 份即对应 user_coupon 中 N 条独立记录，
     * 本就应当可以分别使用；抵扣失控由「折扣券限 1 张」「最大叠加张数」「总抵扣上限比例」共同兜底。
     * </p>
     * <p>
     * 注意 2：折扣券（type=2）每单限用 1 张。折扣券是比例型券，叠加是乘法链式衰减
     * （两张 8 折 = 0.64 即 6.4 折，三张 = 5.12 折），且让利随订单金额线性放大，
     * 与满减券「固定面额线性相加」的性质完全不同，运营无法预估让利规模，故不开放叠加。
     * 折扣券仍可与满减券／无门槛券同用。
     * </p>
     */
    private List<Coupon> validateCoupons(Long userId, List<Long> userCouponIds, BigDecimal goodsTotal,
                                         Set<Long> itemTypeIds, Map<Long, BigDecimal> subTotalByType,
                                         LocalDateTime now, int maxStackCount) {
        List<Coupon> coupons = new ArrayList<>();
        boolean hasExclusive = false;
        for (Long id : userCouponIds) {
            Coupon c = validateCoupon(userId, id, goodsTotal, itemTypeIds, subTotalByType, now);
            boolean stackable = c.getStackable() != null && c.getStackable() == 1;
            if (!stackable) {
                hasExclusive = true;
            }
            coupons.add(c);
        }
        if (coupons.size() > 1 && hasExclusive) {
            throw new BusinessException("不可叠加类优惠券不能与其他优惠券同时使用");
        }
        // 折扣券每单限 1 张：比例型券叠加为乘方衰减（两张 8 折 = 6.4 折），
        // 让利随订单金额放大且运营难以预估，因此折扣券之间不开放叠加。
        long discountCount = coupons.stream()
                .filter(c -> c.getType() != null && c.getType() == 2)
                .count();
        if (discountCount > 1) {
            throw new BusinessException("折扣券每单限使用 1 张");
        }
        if (coupons.size() > maxStackCount) {
            throw new BusinessException("最多叠加使用 " + maxStackCount + " 张优惠券");
        }
        return coupons;
    }

    /**
     * 计算多张券的总抵扣。
     * <p>
     * 每张券按各自的「适用基数」抵扣：分类券只抵扣该分类小计，全场券抵扣订单剩余金额。
     * 逐券递减各自的剩余额度，保证多张分类券不会互相把对方的分类额度吃掉。
     * 最终还受两道封顶：不超过商品总额；不超过后台配置的「总抵扣上限比例」
     * （{@link ICouponRuleConfigService#getMaxDiscountRatio()}），避免多张券叠加把订单抵成 0 元。
     * </p>
     */
    private BigDecimal calcCouponsDiscount(List<Coupon> coupons, BigDecimal goodsTotal,
                                           Map<Long, BigDecimal> subTotalByType, BigDecimal ratio) {
        // 各分类剩余可抵扣额度
        Map<Long, BigDecimal> remainByType = subTotalByType == null
                ? new HashMap<>() : new HashMap<>(subTotalByType);
        BigDecimal remainTotal = goodsTotal;
        BigDecimal total = BigDecimal.ZERO;
        for (Coupon c : coupons) {
            boolean scoped = c.getScope() != null && c.getScope() == 1 && c.getTypeId() != null;
            BigDecimal base = scoped
                    ? remainByType.getOrDefault(c.getTypeId(), BigDecimal.ZERO)
                    : remainTotal;
            BigDecimal discount = calcCouponDiscount(c, base);
            total = total.add(discount);
            if (scoped) {
                remainByType.put(c.getTypeId(), base.subtract(discount).max(BigDecimal.ZERO));
            }
            remainTotal = remainTotal.subtract(discount).max(BigDecimal.ZERO);
        }
        total = total.max(BigDecimal.ZERO).min(goodsTotal);
        // 总抵扣比例封顶：多张券叠加时，实付不低于「1 - 上限比例」。
        // 比例由调用方传入：本方法会被「最优组合」搜索调用上百次，不能每次都去读配置。
        if (ratio != null && ratio.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal cap = goodsTotal.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
            if (total.compareTo(cap) > 0) {
                // 搜索过程中封顶是常态，用 debug 避免刷屏
                log.debug("优惠券叠加抵扣超出上限比例 {}，已封顶: 原抵扣={}, 封顶后={}", ratio, total, cap);
                total = cap;
            }
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    // ==================== 下单试算 ====================

    /**
     * 一张券在某单下的评估结果。
     * <p>
     * 与 {@link #validateCoupon} 的关系：判定条件一一对应，区别只在于
     * <b>这里返回原因字符串而不是抛异常</b>。试算要一次性评估用户的全部券，
     * 一张不可用不该让整个请求失败。
     * </p>
     */
    private static class CouponEvaluation {
        /** 用户券记录 */
        final UserCoupon userCoupon;
        /** 券模板；模板缺失时为 null */
        final Coupon coupon;
        /** 不可用原因；null 或空串表示可用 */
        final String reason;

        CouponEvaluation(UserCoupon userCoupon, Coupon coupon, String reason) {
            this.userCoupon = userCoupon;
            this.coupon = coupon;
            this.reason = reason;
        }

        boolean usable() {
            return reason == null || reason.isEmpty();
        }
    }

    /**
     * 「最优组合」搜索的候选：一张具体的用户券。
     * <p>
     * 刻意不复用 {@link Coupon} 本身做身份 —— 同一张模板可能被同一用户领了多份
     * （user_coupon 多条记录共用一条 coupon），按对象身份去重会算错。
     * </p>
     */
    private static class CouponCandidate {
        final Long userCouponId;
        final Coupon coupon;

        CouponCandidate(Long userCouponId, Coupon coupon) {
            this.userCouponId = userCouponId;
            this.coupon = coupon;
        }
    }

    /** 回溯搜索的可变状态容器（Java 没有闭包捕获可变变量） */
    private static class SearchState {
        List<CouponCandidate> best;
        BigDecimal bestVal;
    }

    /**
     * 下单试算：算商品金额、评估每张券、算出当前所选抵扣、并给出最优组合。
     * <p>
     * 全程只读：不扣库存、不落库、不核销券。算价走的是 {@link #priceCart(List)}，
     * 与下单同一段代码，所以试算结果与下单实收必然一致（前提是购物车与券没变）。
     * </p>
     * <p>
     * 与后端的约定：本接口是前端展示金额的<b>唯一</b>来源，前端不再保留任何抵扣算法。
     * </p>
     */
    @Override
    public Result estimate(OrderEstimateDTO dto) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("请先登录");
        }
        Long userId = user.getId();
        if (dto == null || dto.getItemList() == null || dto.getItemList().isEmpty()) {
            return Result.fail("购物车为空");
        }

        // 与下单共用同一段算价逻辑（纯读、无副作用）
        PricedCart cart = priceCart(dto.getItemList());

        LocalDateTime now = LocalDateTime.now();
        int maxStackCount = couponRuleConfigService.getMaxStackCount();
        BigDecimal maxRatio = couponRuleConfigService.getMaxDiscountRatio();

        // 当前用户的全部券：已用/过期的也要评估，前端「不可用」分组需要展示它们
        List<UserCoupon> userCoupons = userCouponMapper.selectList(
                new LambdaQueryWrapper<UserCoupon>()
                        .eq(UserCoupon::getUserId, userId)
                        .orderByDesc(UserCoupon::getGotTime));
        Map<Long, Coupon> templateMap = loadCouponTemplates(userCoupons);
        List<CouponEvaluation> evaluations = userCoupons.stream()
                .map(uc -> evaluateCoupon(uc, templateMap.get(uc.getCouponId()),
                        cart.goodsTotal, cart.itemTypeIds, cart.subTotalByType, now))
                .collect(Collectors.toList());

        // 已勾选且确实可用的券（前端只会勾可用的，这里仍做一次过滤兜底）
        Set<Long> selectedIds = dto.getUserCouponIds() == null ? Set.of()
                : dto.getUserCouponIds().stream().filter(Objects::nonNull).collect(Collectors.toSet());
        List<CouponCandidate> selected = evaluations.stream()
                .filter(CouponEvaluation::usable)
                .filter(e -> selectedIds.contains(e.userCoupon.getId()))
                .map(e -> new CouponCandidate(e.userCoupon.getId(), e.coupon))
                .collect(Collectors.toList());

        BigDecimal totalDiscount = discountOfCandidates(selected, cart.goodsTotal,
                cart.subTotalByType, maxRatio);

        // 每张券的边际抵扣：抵扣(已选其它 + 本券) - 抵扣(已选其它)，
        // 与前端卡片上「本单可抵 ¥X」的口径一致
        List<CouponOptionVO> options = new ArrayList<>();
        for (CouponEvaluation e : evaluations) {
            BigDecimal marginal = BigDecimal.ZERO;
            if (e.usable()) {
                List<CouponCandidate> others = selected.stream()
                        .filter(x -> !x.userCouponId.equals(e.userCoupon.getId()))
                        .collect(Collectors.toList());
                BigDecimal without = discountOfCandidates(others, cart.goodsTotal,
                        cart.subTotalByType, maxRatio);
                List<CouponCandidate> with = new ArrayList<>(others);
                with.add(new CouponCandidate(e.userCoupon.getId(), e.coupon));
                BigDecimal withIt = discountOfCandidates(with, cart.goodsTotal,
                        cart.subTotalByType, maxRatio);
                marginal = withIt.subtract(without).max(BigDecimal.ZERO);
            }
            options.add(new CouponOptionVO(
                    e.userCoupon.getId(),
                    e.usable(),
                    e.reason == null ? "" : e.reason,
                    marginal));
        }

        List<Long> bestIds = pickBestCouponIds(evaluations, cart.goodsTotal,
                cart.subTotalByType, maxStackCount, maxRatio);
        BigDecimal payable = cart.goodsTotal.subtract(totalDiscount).max(BigDecimal.ZERO);

        return Result.ok(new CouponEstimateVO(cart.goodsTotal, totalDiscount, payable,
                maxStackCount, options, bestIds));
    }

    /**
     * 批量加载券模板（一次 IN 查询），替代逐张 user_coupon 回表查模板。
     * 沿用 {@code CouponServiceImpl.getMyCoupons} 的「两次查询 + 内存 join」模式。
     */
    private Map<Long, Coupon> loadCouponTemplates(List<UserCoupon> userCoupons) {
        if (userCoupons == null || userCoupons.isEmpty()) {
            return Map.of();
        }
        List<Long> couponIds = userCoupons.stream()
                .map(UserCoupon::getCouponId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (couponIds.isEmpty()) {
            return Map.of();
        }
        return couponMapper.selectByIds(couponIds).stream()
                .collect(Collectors.toMap(Coupon::getId, c -> c));
    }

    /**
     * 评估一张券能否用于本单，返回原因而不是抛异常。
     * <p>
     * 判定顺序与前端 {@code couponReason} 对齐，保证前端「不可用」分组里
     * 展示的原因文案与用户此前看到的一致。
     * </p>
     */
    private CouponEvaluation evaluateCoupon(UserCoupon uc, Coupon c, BigDecimal goodsTotal,
                                            Set<Long> itemTypeIds, Map<Long, BigDecimal> subTotalByType,
                                            LocalDateTime now) {
        // 1. 券本身的状态（已用 / 已过期，getMyCoupons 会把过期券纠正为 2）
        if (uc.getStatus() != null && uc.getStatus() != 0) {
            return new CouponEvaluation(uc, c, uc.getStatus() == 1 ? "已使用" : "已过期");
        }
        // 2. 模板缺失或已停用
        if (c == null || c.getStatus() == null || c.getStatus() != 1) {
            return new CouponEvaluation(uc, c, "优惠券已停用");
        }
        // 3. 有效期
        if (uc.getExpireTime() != null && uc.getExpireTime().isBefore(now)) {
            return new CouponEvaluation(uc, c, "已过期");
        }
        // 4. 分类券：本单必须含该分类商品
        boolean scoped = c.getScope() != null && c.getScope() == 1 && c.getTypeId() != null;
        if (scoped && (itemTypeIds == null || !itemTypeIds.contains(c.getTypeId()))) {
            return new CouponEvaluation(uc, c, "本单无该分类商品");
        }
        // 5. 门槛（按「适用基数」判断：分类券看分类小计，全场券看整单）
        BigDecimal applicableBase = resolveCouponBase(c, goodsTotal, subTotalByType);
        BigDecimal threshold = c.getMinThreshold() == null ? BigDecimal.ZERO : c.getMinThreshold();
        if (applicableBase.compareTo(threshold) < 0) {
            BigDecimal gap = threshold.subtract(applicableBase)
                    .setScale(0, RoundingMode.CEILING).setScale(2, RoundingMode.HALF_UP);
            return new CouponEvaluation(uc, c, "差 ¥" + gap.toPlainString() + " 可用");
        }
        // 6. 关键字段缺失（数据异常兜底）
        if (c.getType() == null || (c.getType() == 2 && c.getDiscount() == null)
                || (c.getType() != 2 && c.getAmount() == null)) {
            return new CouponEvaluation(uc, c, "优惠券配置异常，请联系管理员");
        }
        return new CouponEvaluation(uc, c, null);
    }

    /** 是否可叠加（stackable=1） */
    private boolean isStackable(Coupon c) {
        return c.getStackable() != null && c.getStackable() == 1;
    }

    /** 是否折扣券（type=2） */
    private boolean isDiscountCoupon(Coupon c) {
        return c.getType() != null && c.getType() == 2;
    }

    /** 单张券单独使用时的抵扣，仅用于搜索里的排序启发 */
    private BigDecimal singleDiscount(Coupon c, BigDecimal goodsTotal,
                                      Map<Long, BigDecimal> subTotalByType) {
        return calcCouponDiscount(c, resolveCouponBase(c, goodsTotal, subTotalByType));
    }

    /** 把候选券映射成 Coupon 列表后走与下单完全相同的抵扣计算 */
    private BigDecimal discountOfCandidates(List<CouponCandidate> candidates, BigDecimal goodsTotal,
                                            Map<Long, BigDecimal> subTotalByType, BigDecimal maxRatio) {
        if (candidates == null || candidates.isEmpty()) {
            return BigDecimal.ZERO;
        }
        List<Coupon> list = candidates.stream().map(x -> x.coupon).collect(Collectors.toList());
        return calcCouponsDiscount(list, goodsTotal, subTotalByType, maxRatio);
    }

    /**
     * 求解「最优券组合」，返回选中的 userCouponId 列表。
     * <p>
     * 「不可叠加券与任何其它券互斥」这条约束决定了合法组合只有两种形态：
     * ① 单独使用一张不可叠加券；② 使用若干张可叠加券（张数 ≤ maxStackCount，折扣券最多 1 张）。
     * 两种形态各求最优再比较即可覆盖全部合法组合，无需穷举。
     * </p>
     * <p>
     * 算法是「带排序启发的贪心 + 小规模排列择优」，不保证数学最优；
     * 券种少、张数上限小，实际已足够。与前端原 {@code pickBestCoupons} 行为一致。
     * </p>
     */
    private List<Long> pickBestCouponIds(List<CouponEvaluation> evaluations, BigDecimal goodsTotal,
                                         Map<Long, BigDecimal> subTotalByType, int maxStackCount,
                                         BigDecimal maxRatio) {
        List<CouponCandidate> usable = evaluations.stream()
                .filter(CouponEvaluation::usable)
                .filter(e -> e.coupon != null)
                .map(e -> new CouponCandidate(e.userCoupon.getId(), e.coupon))
                .toList();
        if (usable.isEmpty() || goodsTotal.compareTo(BigDecimal.ZERO) <= 0 || maxStackCount < 1) {
            return new ArrayList<>();
        }

        List<CouponCandidate> best = new ArrayList<>();
        BigDecimal bestVal = BigDecimal.ZERO;

        // 形态①：单独使用一张不可叠加券
        for (CouponCandidate c : usable) {
            if (isStackable(c.coupon)) {
                continue;
            }
            BigDecimal v = discountOfCandidates(List.of(c), goodsTotal, subTotalByType, maxRatio);
            if (v.compareTo(bestVal) > 0) {
                bestVal = v;
                best = new ArrayList<>(List.of(c));
            }
        }

        // 形态②：可叠加券组合。折扣券优先（折扣应作用于尽可能大的基数），同类按单张抵扣降序
        List<CouponCandidate> ranked = usable.stream()
                .filter(c -> isStackable(c.coupon)).sorted((a, b) -> {
                    int da = isDiscountCoupon(a.coupon) ? 0 : 1;
                    int db = isDiscountCoupon(b.coupon) ? 0 : 1;
                    if (da != db) {
                        return da - db;
                    }
                    // BigDecimal 不能直接相减，用 compareTo 表示降序
                    return singleDiscount(b.coupon, goodsTotal, subTotalByType)
                            .compareTo(singleDiscount(a.coupon, goodsTotal, subTotalByType));
                }).toList();

        List<CouponCandidate> picked = new ArrayList<>();
        for (CouponCandidate c : ranked) {
            if (picked.size() >= maxStackCount) {
                break;
            }
            // 折扣券每单限 1 张
            if (isDiscountCoupon(c.coupon)
                    && picked.stream().anyMatch(x -> isDiscountCoupon(x.coupon))) {
                continue;
            }
            List<CouponCandidate> next = new ArrayList<>(picked);
            next.add(c);
            if (discountOfCandidates(next, goodsTotal, subTotalByType, maxRatio)
                    .compareTo(discountOfCandidates(picked, goodsTotal, subTotalByType, maxRatio)) > 0) {
                picked = next;
            }
        }
        picked = bestPermutation(picked, goodsTotal, subTotalByType, maxRatio);
        if (discountOfCandidates(picked, goodsTotal, subTotalByType, maxRatio).compareTo(bestVal) > 0) {
            best = picked;
        }
        return best.stream().map(x -> x.userCouponId).collect(Collectors.toList());
    }

    /**
     * 组合内顺序会影响结果（折扣券的基数随其它券的抵扣递减），枚举排列取最优顺序。
     * 最多 5 张（120 种排列），超过 5 张直接返回原顺序，避免阶乘爆炸。
     */
    private List<CouponCandidate> bestPermutation(List<CouponCandidate> list, BigDecimal goodsTotal,
                                                  Map<Long, BigDecimal> subTotalByType,
                                                  BigDecimal maxRatio) {
        if (list == null || list.size() < 2 || list.size() > 5) {
            return list;
        }
        SearchState state = new SearchState();
        state.best = list;
        state.bestVal = discountOfCandidates(list, goodsTotal, subTotalByType, maxRatio);
        walkPermutations(list, new ArrayList<>(), state, goodsTotal, subTotalByType, maxRatio);
        return state.best;
    }

    /** 排列回溯：每层都克隆列表，避免共享引用被后续层改坏 */
    private void walkPermutations(List<CouponCandidate> rest, List<CouponCandidate> current,
                                  SearchState state, BigDecimal goodsTotal,
                                  Map<Long, BigDecimal> subTotalByType, BigDecimal maxRatio) {
        if (rest.isEmpty()) {
            BigDecimal v = discountOfCandidates(current, goodsTotal, subTotalByType, maxRatio);
            if (v.compareTo(state.bestVal) > 0) {
                state.bestVal = v;
                state.best = new ArrayList<>(current);
            }
            return;
        }
        for (int i = 0; i < rest.size(); i++) {
            List<CouponCandidate> nextRest = new ArrayList<>(rest);
            CouponCandidate pickedOne = nextRest.remove(i);
            List<CouponCandidate> nextCur = new ArrayList<>(current);
            nextCur.add(pickedOne);
            walkPermutations(nextRest, nextCur, state, goodsTotal, subTotalByType, maxRatio);
        }
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
                .map(order -> {
                    OrderVO vo = OrderVO.from(order, itemMap.getOrDefault(order.getId(), Collections.emptyList()));
                    vo.fillAutoReceiveTime(autoReceiveDays);
                    return vo;
                })
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
     * 恢复指定订单占用的库存：遍历订单明细恢复 SKU 库存和家具总库存。
     * 库存落库后于事务提交时统一失效缓存，由读路径重建。供订单取消和退款审核通过复用。
     *
     * @param orderId 订单 ID
     * @throws BusinessException 当商品不存在或库存恢复失败时抛出
     */
    @Override
    public void restoreStock(Long orderId) {
        LambdaQueryWrapper<OrderItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrderItem::getOrderId, orderId);
        List<OrderItem> items = orderItemService.list(wrapper);
        Set<Long> touchedFurnitureIds = new HashSet<>();
        // 先按 id 聚合数量（同一件商品可能在明细里出现多次），再用两条批量 SQL 一次回库，
        // 替代原先「每件明细 1~2 条 UPDATE」的循环写法。
        Map<Long, Integer> skuQty = new HashMap<>();
        Map<Long, Integer> furnitureQty = new HashMap<>();
        for (OrderItem item : items) {
            int quantity = item.getQuantity();
            if (quantity == 0 || item.getFurnitureId() == null) {
                continue;
            }
            if (item.getSkuId() != null) {
                skuQty.merge(item.getSkuId(), quantity, Integer::sum);
            }
            furnitureQty.merge(item.getFurnitureId(), quantity, Integer::sum);
            touchedFurnitureIds.add(item.getFurnitureId());
        }
        if (!skuQty.isEmpty()) {
            // 批量 SQL 同样无 deleted 过滤，软删除商品也能正常恢复
            skuMapper.batchIncrementStock(toStockDeltas(skuQty));
        }
        if (!furnitureQty.isEmpty()) {
            furnitureMapper.batchIncrementStock(toStockDeltas(furnitureQty));
        }
        evictFurnitureCacheAfterCommit(touchedFurnitureIds);
    }

    /**
     * 把「id -> 增量」的聚合结果转成批量 UPDATE 的入参。
     */
    private List<StockDeltaDTO> toStockDeltas(Map<Long, Integer> qtyMap) {
        List<StockDeltaDTO> list = new ArrayList<>(qtyMap.size());
        qtyMap.forEach((id, qty) -> list.add(new StockDeltaDTO(id, qty)));
        return list;
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
     * 用户撤销退款申请，订单回退到申请退款前的状态。
     * <p>
     * 此前 {@code REFUND_APPLYING} 状态的唯一出口是管理员审核，用户误申请后
     * 无法自行撤销，只能等管理员处理。
     *
     * @param orderId 订单ID
     * @param userId  操作用户ID（用于归属校验）
     * @return 操作结果
     */
    @Override
    public Result cancelRefund(Long orderId, Long userId) {
        Order order = getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在！");
        }
        if (!order.getUserId().equals(userId)) {
            return Result.fail("无权操作该订单！");
        }
        if (order.getStatus() != REFUND_APPLYING.getCode()) {
            return Result.fail("当前订单状态不支持撤销退款申请");
        }
        // 回退目标必须是合法的非退款态；脏数据（NULL 或本身是退款态）兜底为已支付，
        // 避免撤销后订单停留在一个非法状态上。
        Integer prev = order.getRefundPrevStatus();
        if (prev == null || !isValidRefundPrevStatus(prev)) {
            log.warn("订单退款前状态异常，撤销时兜底为已支付: orderId={}, refundPrevStatus={}", orderId, prev);
            prev = PAID.getCode();
        }
        boolean success = update()
                .set("status", prev)
                // 一并清空退款痕迹，避免下次申请时读到上一条申请的原因与时间
                .set("refund_prev_status", null)
                .set("refund_reason", null)
                .set("refund_apply_time", null)
                .eq("id", orderId)
                .eq("status", REFUND_APPLYING.getCode())
                .update();
        if (!success) {
            return Result.fail("撤销退款申请失败，请重试");
        }
        log.info("用户撤销退款申请: orderId={}, userId={}, 回退到状态={}", orderId, userId, prev);
        return Result.ok();
    }

    /**
     * 判断某个状态是否为「可回退的合法非退款态」。
     * 退款态（6/7/8）与取消态（4）都不能作为撤销退款后的目标状态。
     */
    private boolean isValidRefundPrevStatus(int status) {
        return status == PAID.getCode()
                || status == SHIPPED.getCode()
                || status == COMPLETED.getCode()
                || status == REVIEWED.getCode();
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
        return doConfirmReceipt(order);
    }

    /**
     * 确认收货的核心逻辑，不校验操作者身份。
     * <p>
     * 抽出该方法是为了让「自动确认收货」调度器复用同一套结算逻辑
     * （状态 CAS、销量累加、缓存失效、邮件通知），避免调度器另写一份导致行为不一致。
     * 对外接口 {@link #confirmReceipt(Long)} 负责先做归属与状态校验。
     *
     * @param order 已校验过归属与状态的订单
     * @return 操作结果
     */
    private Result doConfirmReceipt(Order order) {
        Long id = order.getId();
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
        Set<Long> saleTouchedIds = new HashSet<>();
        try {
            List<OrderItem> items = orderItemService.lambdaQuery()
                    .eq(OrderItem::getOrderId, id).list();
            // 同 id 合并后一条批量 UPDATE，替代逐条累加（20 件明细 = 20 次往返）
            Map<Long, Integer> saleQty = new HashMap<>();
            for (OrderItem item : items) {
                if (item.getFurnitureId() != null && item.getQuantity() != 0) {
                    saleQty.merge(item.getFurnitureId(), item.getQuantity(), Integer::sum);
                    saleTouchedIds.add(item.getFurnitureId());
                }
            }
            if (!saleQty.isEmpty()) {
                furnitureMapper.batchIncrementSaleCount(toStockDeltas(saleQty));
            }
        } catch (Exception e) {
            log.error("更新销量失败, orderId={}", id, e);
        }
        // 销量已变更，提交后失效缓存（销量不影响库存与价格，失败也不阻断主流程）
        if (!saleTouchedIds.isEmpty()) {
            evictFurnitureCacheAfterCommit(saleTouchedIds);
        }
        OrderEmailUtil.sendOrderStatus(emailService, userMapper, order, "订单已收货",
                "您的订单 #" + order.getId() + " 已确认收货，感谢您的购买！",
                "✅", null);
        log.info("订单确认收货: orderId={}, userId={}", id, order.getUserId());
        return Result.ok();
    }

    /**
     * 供调度器调用：自动确认收货（不校验操作者身份）。
     * <p>
     * 发货后若用户一直不点确认，订单会永久停留在「已发货」：不结算、不能评价、
     * 售后窗口也无法关闭。这里由调度器在超过配置天数后代为确认。
     *
     * @param orderId 订单ID
     * @return 操作结果
     */
    @Override
    public Result autoConfirmReceipt(Long orderId) {
        Order order = getById(orderId);
        if (order == null) {
            return Result.fail("订单不存在！");
        }
        if (order.getStatus() != SHIPPED.getCode()) {
            // 状态已变更（用户已确认 / 已退款等），无需处理
            return Result.fail("订单当前状态不是已发货，跳过自动确认收货，status=" + order.getStatus());
        }
        return doConfirmReceipt(order);
    }

    /**
     * 在事务提交之后失效家具缓存（只删除，不回写）。
     * <p>
     * 为什么不在事务内写缓存：
     * 事务内的库存/价格变更尚未提交，一旦后续校验失败回滚，数据库恢复原值，
     * 而 Redis 中已写入的值不会回滚，会留下脏数据；且早期实现遗漏了物理 TTL，
     * 脏数据会永久驻留。这里改为仅删除，由读路径
     * （互斥锁 + 双检 + 逻辑过期 + 物理 TTL）在下一次访问时按数据库最新值重建。
     * </p>
     * 若当前不存在事务（如单测或独立调用），则立即删除。
     *
     * @param furnitureIds 库存或信息发生变动的家具 ID
     */
    private void evictFurnitureCacheAfterCommit(Set<Long> furnitureIds) {
        if (furnitureIds == null || furnitureIds.isEmpty()) {
            return;
        }
        Set<Long> ids = new HashSet<>(furnitureIds);
        Runnable evict = () -> {
            for (Long id : ids) {
                try {
                    stringRedisTemplate.delete(CACHE_FURNITURE_KEY + id);
                } catch (Exception e) {
                    // 缓存失效失败不影响主流程，最多是下次读到旧值后由逻辑过期触发重建
                    log.warn("提交后失效家具缓存失败: furnitureId={}", id, e);
                }
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict.run();
                }
            });
        } else {
            evict.run();
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

