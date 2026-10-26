package gcy.system.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import gcy.system.entity.dto.CartAddDTO;
import gcy.system.entity.dto.Result;
import gcy.system.entity.pojo.CartItem;
import gcy.system.entity.pojo.Furniture;
import gcy.system.entity.pojo.Sku;
import gcy.system.entity.pojo.SkuSpec;
import gcy.system.entity.pojo.SpecGroup;
import gcy.system.entity.pojo.SpecValue;
import gcy.system.entity.vo.CartItemVO;
import gcy.system.mapper.CartItemMapper;
import gcy.system.mapper.FurnitureMapper;
import gcy.system.mapper.SkuMapper;
import gcy.system.mapper.SkuSpecMapper;
import gcy.system.mapper.SpecGroupMapper;
import gcy.system.mapper.SpecValueMapper;
import gcy.system.service.ICartService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 购物车服务实现。
 *
 * @author 郭名城
 * @date 2026-10-23
 */
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements ICartService {

    /**
     * 无规格商品的 skuId 占位值。用 0 而非 null 才能让 uk_user_furniture_sku 去重生效
     * （MySQL 唯一索引对 NULL 不生效），详见 sql/cart-module.sql 文件头。
     */
    private static final long NO_SKU = 0L;

    private final CartItemMapper cartItemMapper;

    private final FurnitureMapper furnitureMapper;

    private final SkuMapper skuMapper;

    private final SkuSpecMapper skuSpecMapper;

    private final SpecGroupMapper specGroupMapper;

    private final SpecValueMapper specValueMapper;

    @Override
    public Result list(Long userId) {
        List<CartItem> items = cartItemMapper.selectList(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .orderByDesc(CartItem::getUpdateTime));
        if (items.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 批量取商品与 SKU，避免逐条查库
        Set<Long> furnitureIds = items.stream()
                .map(CartItem::getFurnitureId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Furniture> furnitureMap = furnitureIds.isEmpty() ? Map.of()
                : furnitureMapper.selectByIds(furnitureIds).stream()
                .collect(Collectors.toMap(Furniture::getId, f -> f));
        Set<Long> skuIds = items.stream()
                .map(CartItem::getSkuId).filter(id -> id != null && id > NO_SKU)
                .collect(Collectors.toSet());
        Map<Long, Sku> skuMap = skuIds.isEmpty() ? Map.of()
                : skuMapper.selectByIds(skuIds).stream()
                .collect(Collectors.toMap(Sku::getId, s -> s));
        Map<Long, String> specTextMap = buildSpecText(skuIds);

        List<CartItemVO> vos = new ArrayList<>(items.size());
        for (CartItem item : items) {
            Furniture f = furnitureMap.get(item.getFurnitureId());
            long skuId = item.getSkuId() == null ? NO_SKU : item.getSkuId();
            Sku sku = skuId > NO_SKU ? skuMap.get(skuId) : null;
            CartItemVO vo = new CartItemVO();
            vo.setId(item.getId());
            vo.setFurnitureId(item.getFurnitureId());
            vo.setSkuId(skuId > NO_SKU ? skuId : null);
            vo.setQuantity(item.getQuantity() == null ? 0 : item.getQuantity());
            vo.setChecked(item.getChecked() != null && item.getChecked() == 1);
            if (f == null) {
                // 商品被物理删除或逻辑删除：保留条目让用户看见，标记失效而不是静默消失
                vo.setFName("该商品已下架");
                vo.setPrice(BigDecimal.ZERO);
                vo.setStock(0);
                vo.setAvailable(false);
                vo.setUnavailableReason("商品已下架");
                vos.add(vo);
                continue;
            }
            vo.setFName(f.getFName());
            // SKU 图优先：同商品不同规格的图不同，与加购时前端取图规则保持一致
            vo.setFIcon(resolveIcon(f, sku));
            vo.setTypeId(f.getTypeId());
            vo.setSpecText(specTextMap.getOrDefault(skuId, ""));
            vo.setPrice(resolvePrice(f, sku));
            vo.setStock(resolveStock(f, sku));

            String reason = resolveUnavailableReason(item, f, sku, skuId);
            vo.setAvailable(reason == null);
            vo.setUnavailableReason(reason == null ? "" : reason);
            vos.add(vo);
        }
        return Result.ok(vos);
    }

    @Override
    public Result addItem(Long userId, CartAddDTO dto) {
        if (dto == null || dto.getFurnitureId() == null) {
            return Result.fail("请选择商品");
        }
        int qty = dto.getQuantity() == null ? 1 : dto.getQuantity();
        if (qty < 1) {
            return Result.fail("数量至少为1");
        }
        long skuId = dto.getSkuId() == null ? NO_SKU : dto.getSkuId();

        Furniture furniture = furnitureMapper.selectById(dto.getFurnitureId());
        if (furniture == null) {
            return Result.fail("商品不存在或已下架");
        }
        Sku sku = null;
        if (skuId > NO_SKU) {
            sku = skuMapper.selectById(skuId);
            if (sku == null || !Objects.equals(sku.getFurnitureId(), furniture.getId())) {
                return Result.fail("规格不存在或与商品不匹配");
            }
            if (sku.getStatus() != null && sku.getStatus() == 0) {
                return Result.fail("该规格已停用");
            }
        }
        int stock = resolveStock(furniture, sku);

        CartItem existing = cartItemMapper.selectOne(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .eq(CartItem::getFurnitureId, furniture.getId())
                .eq(CartItem::getSkuId, skuId)
                .last("LIMIT 1"));
        int target = qty + (existing == null ? 0 : (existing.getQuantity() == null ? 0 : existing.getQuantity()));
        if (target > stock) {
            return Result.fail(String.format("库存不足，当前仅剩 %d 件", stock));
        }

        // 这里刻意不加 @Transactional：单条 insert/update 本身就是原子的，而捕获
        // DuplicateKeyException 后若仍在原事务里继续操作，事务已被标记 rollback-only，
        // 提交时会抛 UnexpectedRollbackException。并发冲突交给唯一索引兜底即可。
        if (existing == null) {
            CartItem item = new CartItem();
            item.setUserId(userId);
            item.setFurnitureId(furniture.getId());
            item.setSkuId(skuId);
            item.setQuantity(target);
            item.setChecked(1);
            try {
                cartItemMapper.insert(item);
            } catch (DuplicateKeyException e) {
                // 并发下同 SKU 同时插入会撞唯一索引，退化为一次数量累加
                cartItemMapper.update(null, new LambdaUpdateWrapper<CartItem>()
                        .eq(CartItem::getUserId, userId)
                        .eq(CartItem::getFurnitureId, furniture.getId())
                        .eq(CartItem::getSkuId, skuId)
                        .setSql("quantity = quantity + " + qty));
            }
        } else {
            cartItemMapper.update(null, new LambdaUpdateWrapper<CartItem>()
                    .eq(CartItem::getId, existing.getId())
                    .set(CartItem::getQuantity, target));
        }
        return Result.okMsg("已加入购物车");
    }

    @Override
    public Result updateQuantity(Long userId, Long id, Integer quantity) {
        if (quantity == null || quantity < 1) {
            return Result.fail("数量至少为1");
        }
        CartItem item = findOwned(userId, id);
        if (item == null) {
            return Result.fail("购物车中没有该商品");
        }
        Furniture furniture = furnitureMapper.selectById(item.getFurnitureId());
        if (furniture == null) {
            return Result.fail("商品已下架");
        }
        long skuId = item.getSkuId() == null ? NO_SKU : item.getSkuId();
        Sku sku = skuId > NO_SKU ? skuMapper.selectById(skuId) : null;
        int stock = resolveStock(furniture, sku);
        if (quantity > stock) {
            return Result.fail(String.format("库存不足，当前仅剩 %d 件", stock));
        }
        cartItemMapper.update(null, new LambdaUpdateWrapper<CartItem>()
                .eq(CartItem::getId, item.getId())
                .set(CartItem::getQuantity, quantity));
        return Result.okMsg("已更新数量");
    }

    @Override
    public Result updateChecked(Long userId, Long id, Integer checked) {
        CartItem item = findOwned(userId, id);
        if (item == null) {
            return Result.fail("购物车中没有该商品");
        }
        cartItemMapper.update(null, new LambdaUpdateWrapper<CartItem>()
                .eq(CartItem::getId, item.getId())
                .set(CartItem::getChecked, checked != null && checked == 1 ? 1 : 0));
        return Result.okMsg("已更新勾选状态");
    }

    @Override
    public Result removeItem(Long userId, Long id) {
        int rows = cartItemMapper.delete(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getId, id)
                .eq(CartItem::getUserId, userId));
        return rows > 0 ? Result.okMsg("已删除") : Result.fail("购物车中没有该商品");
    }

    @Override
    public Result clear(Long userId) {
        cartItemMapper.delete(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId));
        return Result.okMsg("购物车已清空");
    }

    /**
     * 查出属于该用户的购物车项；带 userId 条件是为了防越权改别人的车。
     */
    private CartItem findOwned(Long userId, Long id) {
        if (id == null) {
            return null;
        }
        return cartItemMapper.selectOne(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getId, id)
                .eq(CartItem::getUserId, userId)
                .last("LIMIT 1"));
    }

    private BigDecimal resolvePrice(Furniture furniture, Sku sku) {
        BigDecimal price = sku != null ? sku.getPrice() : (furniture == null ? null : furniture.getPrice());
        return price == null ? BigDecimal.ZERO : price;
    }

    /**
     * 取展示图：SKU 有独立图就用它，否则回落到商品主图。
     * <p>
     * 同款商品不同颜色/材质通常配不同缩略图，统一用主图会让购物车里几行长得一模一样。
     */
    private String resolveIcon(Furniture furniture, Sku sku) {
        if (sku != null && sku.getSkuImage() != null && !sku.getSkuImage().isEmpty()) {
            return sku.getSkuImage();
        }
        if (furniture == null || furniture.getFIcon() == null) {
            return "";
        }
        return furniture.getFIcon();
    }

    private int resolveStock(Furniture furniture, Sku sku) {
        Integer stock = sku != null ? sku.getStock() : (furniture == null ? null : furniture.getStock());
        return stock == null ? 0 : stock;
    }

    /**
     * 判定购物车里这件商品还能不能买；能买返回 null，否则返回原因。
     */
    private String resolveUnavailableReason(CartItem item, Furniture furniture, Sku sku, long skuId) {
        if (skuId > NO_SKU) {
            if (sku == null) {
                return "该规格已下架";
            }
            if (sku.getStatus() != null && sku.getStatus() == 0) {
                return "该规格已停用";
            }
        }
        int stock = resolveStock(furniture, sku);
        if (stock <= 0) {
            return "已售罄";
        }
        int quantity = item.getQuantity() == null ? 0 : item.getQuantity();
        if (quantity > stock) {
            return String.format("库存不足，仅剩 %d 件", stock);
        }
        return null;
    }

    /**
     * 批量拼 SKU 规格文案，如「颜色:胡桃色, 尺寸:三人位」。
     */
    private Map<Long, String> buildSpecText(Set<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return Map.of();
        }
        List<SkuSpec> specs = skuSpecMapper.selectList(new LambdaQueryWrapper<SkuSpec>()
                .in(SkuSpec::getSkuId, skuIds));
        if (specs.isEmpty()) {
            return Map.of();
        }
        Set<Long> groupIds = specs.stream().map(SkuSpec::getSpecGroupId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> valueIds = specs.stream().map(SkuSpec::getSpecValueId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> groupNames = groupIds.isEmpty() ? Map.of()
                : specGroupMapper.selectByIds(groupIds).stream()
                .collect(Collectors.toMap(SpecGroup::getId, SpecGroup::getGroupName));
        Map<Long, String> valueNames = valueIds.isEmpty() ? Map.of()
                : specValueMapper.selectByIds(valueIds).stream()
                .collect(Collectors.toMap(SpecValue::getId, SpecValue::getValueName));

        Map<Long, List<String>> grouped = new HashMap<>();
        for (SkuSpec spec : specs) {
            String groupName = groupNames.getOrDefault(spec.getSpecGroupId(), "");
            String valueName = valueNames.getOrDefault(spec.getSpecValueId(), "");
            grouped.computeIfAbsent(spec.getSkuId(), k -> new ArrayList<>())
                    .add(groupName + ":" + valueName);
        }
        Map<Long, String> result = new HashMap<>();
        grouped.forEach((skuId, parts) -> result.put(skuId, String.join(", ", parts)));
        return result;
    }
}
