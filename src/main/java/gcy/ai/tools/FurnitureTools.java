package gcy.ai.tools;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import gcy.system.entity.dto.OrderEstimateDTO;
import gcy.system.entity.dto.OrderItemDTO;
import gcy.system.entity.dto.Result;
import gcy.system.entity.pojo.*;
import gcy.system.entity.vo.CouponEstimateVO;
import gcy.system.entity.vo.CouponOptionVO;
import gcy.system.entity.vo.TopFurnitureVO;
import gcy.system.entity.vo.UserCouponVO;
import gcy.system.mapper.*;
import gcy.system.service.ICouponService;
import gcy.system.service.IOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static gcy.system.utils.RedisConstants.AI_CHAT_MEMORY_KEY;

/**
 * AI家具查询工具类。
 * <p>
 * 为LangChain4j Agent提供工具方法，使AI能够查询商品列表、搜索商品、
 * 查看SKU规格信息、获取库存概况等。所有方法均标注@Tool供AI调用。
 * </p>
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FurnitureTools {

    private final FurnitureMapper furnitureMapper;

    private final FurnitureTypeMapper furnitureTypeMapper;

    private final SkuMapper skuMapper;

    private final SkuSpecMapper skuSpecMapper;

    private final SpecGroupMapper specGroupMapper;

    private final SpecValueMapper specValueMapper;

    private final FavoriteMapper favoriteMapper;

    private final IOrderService orderService;

    private final ICouponService couponService;

    private final OrderItemMapper orderItemMapper;

    private final CartItemMapper cartItemMapper;

    /**
     * 单次工具调用返回的商品条数上限。
     * <p>
     * 工具方法的返回值会作为文本直接进模型上下文并被记忆窗口保留，而
     * {@code LIKE '%关键词%'} 的前导通配符用不上索引、又没有 LIMIT 时，
     * 模型传一个宽泛词（或空串）就会把整库商品拼成文本塞回去，
     * 既浪费 token 也污染后续每一轮的上下文。
     * </p>
     */
    private static final int TOOL_RESULT_LIMIT = 10;

    /**
     * 根据商品名称模糊搜索商品。
     *
     * @param name 搜索关键词
     * @return 匹配的商品列表文本
     */
    @Tool("根据商品名称模糊搜索商品，仅返回名称、ID和价格")
    public String searchFurniture(String name) {
        log.debug("调用searchFurniture, name={}", name);
        // 空关键词不能丢进 like：会退化成 LIKE '%%' 全表扫，把整库商品都捞回来
        if (name == null || name.trim().isEmpty()) {
            return "请告诉我您想找什么家具，比如「沙发」「餐桌」「床」～";
        }
        List<Furniture> list = furnitureMapper.selectList(
                new LambdaQueryWrapper<Furniture>()
                        .select(Furniture::getId, Furniture::getFName, Furniture::getPrice, Furniture::getStock)
                        .like(Furniture::getFName, name.trim())
                        .last("LIMIT " + TOOL_RESULT_LIMIT)
        );
        if (list.isEmpty()) {
            return "未找到名称中包含「" + name + "」的商品";
        }
        StringBuilder sb = new StringBuilder("【搜索结果】\n");
        for (Furniture f : list) {
            sb.append(String.format("- %s [商品:%d] | ¥%s | 库存:%d件\n",
                    f.getFName(), f.getId(), f.getPrice(), f.getStock()));
        }
        if (list.size() >= TOOL_RESULT_LIMIT) {
            sb.append(String.format("（结果较多，仅列出前 %d 条，可提供更具体的关键词）\n", TOOL_RESULT_LIMIT));
        }
        return sb.toString();
    }

    /**
     * 查询系统当前所有可用的家具分类（系列）及其宣传语。
     * <p>
     * 从数据库实时获取全部未删除的家具分类，用于回答"系统有哪些系列/分类"等问题。
     * 不作硬编码，以数据库为准。
     * </p>
     *
     * @return 家具分类清单文本
     */
    @Tool("查询系统当前所有家具分类（系列）及其宣传语，用于回答系统有哪些系列/分类")
    public String queryFurnitureTypes() {
        log.debug("调用queryFurnitureTypes");
        List<FurnitureType> types = furnitureTypeMapper.selectList(
                new LambdaQueryWrapper<FurnitureType>()
                        .orderByAsc(FurnitureType::getId));
        if (types.isEmpty()) {
            return "当前暂无家具分类";
        }
        StringBuilder sb = new StringBuilder("【家具分类】\n");
        for (FurnitureType t : types) {
            sb.append("- ").append(t.getName());
            if (t.getTitle() != null && !t.getTitle().isEmpty()) {
                sb.append("（").append(t.getTitle()).append("）");
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    /**
     * 查询指定商品的SKU规格、库存和价格信息。
     *
     * @param furnitureName 商品名称
     * @return 包含所有SKU的规格、价格、库存信息文本
     */
    @Tool("查询指定商品的所有SKU规格及每个SKU的库存、价格信息。返回中 [SKU:id] 是规格编号，用户指定具体规格时据此确认")
    public String querySkuInfo(String furnitureName) {
        log.debug("调用querySkuInfo, furnitureName={}", furnitureName);
        // 空关键词会把整库商品当候选，下面「找到多个匹配」的分支会拼出极长文本
        if (furnitureName == null || furnitureName.trim().isEmpty()) {
            return "请告诉我具体是哪款商品，比如「北欧沙发」～";
        }

        List<Furniture> furnitureList = furnitureMapper.selectList(
                new LambdaQueryWrapper<Furniture>()
                        .select(Furniture::getId, Furniture::getFName)
                        .like(Furniture::getFName, furnitureName.trim())
                        .last("LIMIT " + TOOL_RESULT_LIMIT)
        );
        if (furnitureList.isEmpty()) {
            return "未找到名称中包含「" + furnitureName + "」的商品";
        }

        if (furnitureList.size() > 1) {
            String names = furnitureList.stream()
                    .map(Furniture::getFName)
                    .collect(Collectors.joining("、"));
            return "找到多个匹配的商品，请指定具体名称：\n" + names;
        }
        Furniture furniture = furnitureList.get(0);
        List<Sku> skus = skuMapper.selectList(
                new LambdaQueryWrapper<Sku>()
                        .eq(Sku::getFurnitureId, furniture.getId()));
        if (skus.isEmpty()) {
            return "该商品没有多规格SKU，使用统一价格和库存";
        }
        List<Long> skuIds = skus.stream().map(Sku::getId).collect(Collectors.toList());
        List<SkuSpec> skuSpecs = skuSpecMapper.selectList(
                new LambdaQueryWrapper<SkuSpec>().in(SkuSpec::getSkuId, skuIds));
        StringBuilder sb = new StringBuilder("【").append(furniture.getFName()).append(" 规格库存信息】\n");
        if (skuSpecs.isEmpty()) {
            for (Sku sku : skus) {
                sb.append(String.format("[SKU:%d] 价格: ¥%s | 库存: %d件\n",
                        sku.getId(), sku.getPrice(), sku.getStock()));
            }
            return sb.toString();
        }
        Map<Long, List<SkuSpec>> specMap = skuSpecs.stream()
                .collect(Collectors.groupingBy(SkuSpec::getSkuId));
        List<Long> groupIds = skuSpecs.stream().map(SkuSpec::getSpecGroupId).distinct().collect(Collectors.toList());
        List<Long> valueIds = skuSpecs.stream().map(SkuSpec::getSpecValueId).distinct().collect(Collectors.toList());
        Map<Long, String> groupNames = specGroupMapper.selectByIds(groupIds).stream()
                .collect(Collectors.toMap(SpecGroup::getId, SpecGroup::getGroupName));
        Map<Long, String> valueNames = specValueMapper.selectByIds(valueIds).stream()
                .collect(Collectors.toMap(SpecValue::getId, SpecValue::getValueName));
        for (Sku sku : skus) {
            sb.append(String.format("[SKU:%d] 价格: ¥%s | 库存: %d件",
                    sku.getId(), sku.getPrice(), sku.getStock()));
            List<SkuSpec> specs = specMap.get(sku.getId());
            if (specs != null && !specs.isEmpty()) {
                sb.append(" | 规格: ");
                for (int i = 0; i < specs.size(); i++) {
                    SkuSpec ss = specs.get(i);
                    String gn = groupNames.getOrDefault(ss.getSpecGroupId(), "");
                    String vn = valueNames.getOrDefault(ss.getSpecValueId(), "");
                    if (i > 0) sb.append(", ");
                    sb.append(gn).append(":").append(vn);
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String getTypeName(Long typeId) {
        if (typeId == null) return "未分类";
        FurnitureType type = furnitureTypeMapper.selectById(typeId);
        return type != null ? type.getName() : "未分类";
    }

    /**
     * 根据用户场景（客厅/卧室/书房/餐厅）推荐对应的家具组合。
     * <p>
     * 通过场景关键词匹配家具分类（门厅系列→客厅、卧室系列→卧室、书房系列→书房、餐厅系列→餐厅），
     * 返回该分类下所有在售商品的名称、价格、库存信息，并附带整体推荐语。
     * </p>
     *
     * @param scene 用户场景关键词，如"客厅"、"卧室"、"书房"、"餐厅"
     * @return 该场景下推荐的商品列表文本，包含推荐语
     */
    @Tool("根据用户需求场景（客厅/卧室/书房/餐厅）推荐对应的家具组合，包含推荐语和商品列表")
    public String recommendByScene(String scene) {
        log.debug("调用recommendByScene, scene={}", scene);
        if (scene == null || scene.trim().isEmpty()) {
            return "请告诉我您想布置哪个场景呢？比如：客厅、卧室、书房、餐厅～";
        }
        String typeName = matchSceneToType(scene.trim());
        if (typeName == null) {
            return "抱歉，我目前支持按「客厅」「卧室」「书房」「餐厅」四个场景推荐，您想了解哪个场景呢？";
        }
        FurnitureType type = furnitureTypeMapper.selectList(
                        new LambdaQueryWrapper<FurnitureType>().eq(FurnitureType::getName, typeName))
                .stream().findFirst().orElse(null);
        if (type == null) {
            return "暂时无法获取「" + scene + "」场景的分类信息，请联系管理员。";
        }
        // 不加 LIMIT 时，分类下商品一多就会把整批文本塞进模型上下文，污染后续每一轮对话
        List<Furniture> furnitureList = furnitureMapper.selectList(
                new LambdaQueryWrapper<Furniture>()
                        .select(Furniture::getId, Furniture::getFName, Furniture::getPrice, Furniture::getStock)
                        .eq(Furniture::getTypeId, type.getId())
                        .last("LIMIT " + TOOL_RESULT_LIMIT)
        );
        if (furnitureList.isEmpty()) {
            return "「" + scene + "」场景下暂时没有在售商品，请稍后再来看看～";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【").append(scene).append("场景推荐】\n");
        if (type.getTitle() != null) {
            sb.append(type.getTitle()).append("\n");
        }
        sb.append("为您推荐以下商品：\n\n");
        for (Furniture f : furnitureList) {
            sb.append(String.format("· %s [商品:%d] | ¥%s | 库存: %d件\n",
                    f.getFName(), f.getId(), f.getPrice(), f.getStock()));
        }
        sb.append("点击商品卡片可查看详情，需要我帮您对比哪几款吗？");
        return sb.toString();
    }

    /**
     * 场景关键词到分类名称的映射。
     * 从数据库查询所有分类进行模糊匹配，匹配不到时使用此映射兜底。
     */
    private String matchSceneToType(String scene) {
        // 先从数据库所有分类中模糊匹配
        List<FurnitureType> allTypes = furnitureTypeMapper.selectList(
                new LambdaQueryWrapper<FurnitureType>().eq(FurnitureType::getDeleted, 0));
        FurnitureType dbMatch = allTypes.stream()
                .filter(t -> t.getName() != null && t.getName().contains(scene))
                .findFirst().orElse(null);
        if (dbMatch != null) {
            return dbMatch.getName();
        }
        // 数据库匹配不到，使用硬编码映射兜底
        if (scene.contains("客厅") || scene.contains("门厅")) {
            return "门厅系列";
        }
        if (scene.contains("卧室")) {
            return "卧室系列";
        }
        if (scene.contains("书房")) {
            return "书房系列";
        }
        if (scene.contains("餐厅") || scene.contains("厨房")) {
            return "餐厅系列";
        }
        return null;
    }

    /**
     * 对比两款家具商品的规格、价格、库存和适用场景。
     * <p>
     * 通过商品名称模糊匹配查找两款商品，从价格、库存、品牌、简介等维度进行对比，
     * 帮助用户做出购买决策。若匹配到多个商品，会提示用户指定具体名称。
     * </p>
     *
     * @param name1 第一款商品名称关键词
     * @param name2 第二款商品名称关键词
     * @return 两款商品的多维度对比文本
     */
    @Tool("对比两款家具商品的规格、价格、库存、适用场景，帮助用户做出购买决策。需要传入两个商品名称")
    public String compareFurniture(String name1, String name2) {
        log.debug("调用compareFurniture, name1={}, name2={}", name1, name2);
        if (name1 == null || name1.trim().isEmpty() || name2 == null || name2.trim().isEmpty()) {
            return "请提供需要对比的两款商品名称～";
        }
        Furniture f1 = findOneFurniture(name1);
        Furniture f2 = findOneFurniture(name2);
        if (f1 == null && f2 == null) {
            return "未找到「" + name1 + "」和「" + name2 + "」这两款商品，请确认名称后重试。";
        }
        if (f1 == null) {
            return "未找到「" + name1 + "」，请确认名称后重试。已找到「" + f2.getFName() + "」[商品:" + f2.getId() + "]。";
        }
        if (f2 == null) {
            return "未找到「" + name2 + "」，请确认名称后重试。已找到「" + f1.getFName() + "」[商品:" + f1.getId() + "]。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【商品对比】\n\n");
        sb.append(String.format("%s [商品:%d]：¥%s | %d件库存 | %s\n",
                f1.getFName(), f1.getId(), f1.getPrice(), f1.getStock(),
                getTypeName(f1.getTypeId())));
        sb.append(String.format("%s [商品:%d]：¥%s | %d件库存 | %s\n",
                f2.getFName(), f2.getId(), f2.getPrice(), f2.getStock(),
                getTypeName(f2.getTypeId())));
        if (f1.getPrice() != null && f2.getPrice() != null) {
            try {
                int p1 = f1.getPrice().intValue();
                int p2 = f2.getPrice().intValue();
                if (p1 < p2) {
                    sb.append(String.format("\n💰 %s 比 %s 便宜 ¥%d，性价比更高\n",
                            f1.getFName(), f2.getFName(), p2 - p1));
                } else if (p2 < p1) {
                    sb.append(String.format("\n💰 %s 比 %s 便宜 ¥%d，性价比更高\n",
                            f2.getFName(), f1.getFName(), p1 - p2));
                }
            } catch (NumberFormatException e) {
                log.debug("价格格式解析失败，跳过价格比较");
            }
        }
        sb.append("\n点击商品卡片可查看详情，需要进一步了解哪一款呢？");
        return sb.toString();
    }

    /**
     * 根据商品名称模糊查找唯一商品，匹配到多个时返回 null。
     * <p>
     * 先按精确名称查一次：命中即唯一，省掉「LIKE 捞一批再遍历找精确项」那一步；
     * 兜底的 LIKE 带 LIMIT，避免宽泛关键词全表扫。语义与原先一致——
     * 存在精确匹配时取精确项，反之若模糊匹配不唯一则返回 null。
     * </p>
     */
    private Furniture findOneFurniture(String name) {
        String keyword = name.trim();
        Furniture exact = furnitureMapper.selectOne(
                new LambdaQueryWrapper<Furniture>()
                        .select(Furniture::getId, Furniture::getFName, Furniture::getPrice,
                                Furniture::getStock, Furniture::getTypeId)
                        .eq(Furniture::getFName, keyword)
                        .last("LIMIT 1")
        );
        if (exact != null) return exact;
        List<Furniture> list = furnitureMapper.selectList(
                new LambdaQueryWrapper<Furniture>()
                        .select(Furniture::getId, Furniture::getFName, Furniture::getPrice,
                                Furniture::getStock, Furniture::getTypeId)
                        .like(Furniture::getFName, keyword)
                        .last("LIMIT " + TOOL_RESULT_LIMIT)
        );
        if (list.isEmpty()) return null;
        if (list.size() > 1) {
            // 精确匹配已在上面返回，走到这里说明模糊命中多个且无一精确
            log.debug("findOneFurniture: 匹配到多个商品, name={}", name);
            return null;
        }
        return list.get(0);
    }

    /**
     * 查询当前登录用户的收藏商品列表。
     * <p>
     * 通过会话标识解析当前登录用户，然后查询其所有收藏的家具商品。
     * 若用户未登录则返回提示信息，引导用户登录后使用收藏功能。
     * </p>
     *
     * @return 用户收藏的商品列表文本，包含商品名称、价格、库存信息
     */
    @Tool("查询当前用户已收藏的商品列表，用于了解用户偏好并提供个性化推荐")
    public String queryUserFavorites(@ToolMemoryId String memoryId) {
        log.debug("调用queryUserFavorites, memoryId={}", memoryId);
        // 工具运行在流式响应线程上，UserHolder（ThreadLocal）取不到登录用户，只能从会话标识解析
        Long userId = resolveUserId(memoryId);
        if (userId == null) {
            return "您当前未登录，登录后可以查看收藏商品哦～";
        }
        List<Favorite> favorites = favoriteMapper.selectList(
                new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, userId));
        if (favorites.isEmpty()) {
            return "您还没有收藏任何商品，在商品详情页点击收藏按钮即可添加～";
        }
        // 一次批量取回所有收藏的商品，替代原先「每条收藏一次 selectOne」的 N+1
        List<Long> furnitureIds = favorites.stream()
                .map(Favorite::getFurnitureId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, Furniture> furnitureMap = furnitureIds.isEmpty() ? Map.of()
                : furnitureMapper.selectByIds(furnitureIds).stream()
                        .collect(Collectors.toMap(Furniture::getId, f -> f));
        StringBuilder sb = new StringBuilder("【我的收藏商品】\n");
        for (Favorite fav : favorites) {
            Furniture f = furnitureMap.get(fav.getFurnitureId());
            if (f != null) {
                sb.append(String.format("· %s [商品:%d] | ¥%s | 库存: %d件\n",
                        f.getFName(), f.getId(), f.getPrice(), f.getStock()));
            }
        }
        return sb.toString();
    }

    // ==================== 会话上下文与套装规划 ====================

    /**
     * 读取用户购物车，转成下单与试算用的明细结构。
     * <p>
     * 直接查 cart_item 表：购物车已在服务端持久化，不再依赖前端随消息上传的快照，
     * 因此 AI 看到的就是用户页面上同一份数据。
     */
    private List<OrderItemDTO> loadCartByUser(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<CartItem> rows = cartItemMapper.selectList(new LambdaQueryWrapper<CartItem>()
                .eq(CartItem::getUserId, userId)
                .orderByAsc(CartItem::getId));
        return rows.stream().map(row -> {
            OrderItemDTO dto = new OrderItemDTO();
            dto.setFurnitureId(row.getFurnitureId());
            // 服务端用 0 表示无规格，对外传 null，与下单接口的口径保持一致
            dto.setSkuId(row.getSkuId() != null && row.getSkuId() > 0 ? row.getSkuId() : null);
            dto.setQuantity(row.getQuantity() == null ? 1 : row.getQuantity());
            return dto;
        }).collect(Collectors.toList());
    }

    /**
     * 从会话记忆ID解析用户ID。
     * <p>
     * memoryId 形如 {@code ai:chat:{userId}:{conversationId}}，由服务端生成、模型改不了，
     * 因此可作可信身份来源。绝不能让模型自己传 userId——那是可以被提示注入伪造的。
     */
    private Long resolveUserId(String memoryId) {
        if (memoryId == null || !memoryId.startsWith(AI_CHAT_MEMORY_KEY)) {
            return null;
        }
        String rest = memoryId.substring(AI_CHAT_MEMORY_KEY.length());
        int sep = rest.indexOf(':');
        if (sep <= 0) {
            return null;
        }
        try {
            return Long.parseLong(rest.substring(0, sep));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 按购物车条目批量取商品，避免逐条查库。
     * <p>
     * 价格一律取数据库值：localStorage 里的价格可能是旧快照，不能用来算钱。
     */
    private Map<Long, Furniture> loadFurnitureMap(List<OrderItemDTO> items) {
        return loadFurnitureByIds(items.stream()
                .map(OrderItemDTO::getFurnitureId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList()));
    }

    /**
     * 按商品ID批量取商品，供榜单等没有购物车条目的场景使用。
     */
    private Map<Long, Furniture> loadFurnitureByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return furnitureMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(Furniture::getId, f -> f));
    }

    /**
     * 查询当前会话购物车里的商品清单。
     *
     * @param memoryId 会话记忆ID，由框架注入
     * @return 购物车清单文本
     */
    @Tool("查询用户当前购物车里的商品清单。需要按购物车做整单计算（试算、算券）时先调用它")
    public String queryCart(@ToolMemoryId String memoryId) {
        log.debug("调用queryCart, memoryId={}", memoryId);
        Long userId = resolveUserId(memoryId);
        if (userId == null) {
            return "请先登录后再查询购物车。";
        }
        List<OrderItemDTO> items = loadCartByUser(userId);
        if (items.isEmpty()) {
            return "购物车是空的。请提示用户先把商品加入购物车。";
        }
        Map<Long, Furniture> furnitureMap = loadFurnitureMap(items);
        StringBuilder sb = new StringBuilder("【购物车】\n");
        BigDecimal total = BigDecimal.ZERO;
        for (OrderItemDTO item : items) {
            Furniture f = furnitureMap.get(item.getFurnitureId());
            if (f == null) {
                continue;
            }
            int qty = item.getQuantity() == null ? 1 : item.getQuantity();
            BigDecimal price = f.getPrice() == null ? BigDecimal.ZERO : f.getPrice();
            total = total.add(price.multiply(BigDecimal.valueOf(qty)));
            sb.append(String.format("- %s [商品:%d] | ¥%s × %d | 库存%d件\n",
                    f.getFName(), f.getId(), price, qty, f.getStock()));
        }
        sb.append(String.format("合计 ¥%s（%d 条明细）", total, items.size()));
        return sb.toString();
    }

    /**
     * 按场景与预算规划套装。
     * <p>
     * 组合与算价全部在后端完成，模型只负责传场景和预算——让大模型做加法必然算错。
     * 按优先级顺序累加（贪心），不做任何搜索：用户要的是协调的一套家具，不是把预算花光的数学最优解。
     * <p>
     * 
     *
     * @param scene  场景关键词
     * @param budget 预算（元），可为空
     * @return 套装清单与套装标记
     */
    @Tool("""
            按场景和预算规划一套家具。仅当用户表达整屋或成套购买意图时调用。
            scene 只能取 客厅、卧室、书房、餐厅 之一；budget 为人民币整数元，用户没说就传 null。
            avoidIds 传用户明确不想再要的商品ID（逗号分隔，如 1417,1420），没有就不传。
            返回套装清单、商品总价与预算余额，末尾附套装标记。
            严禁自行编造商品，也严禁改动返回中的任何金额。
            """)
    public String planFurnitureSet(String scene, Integer budget, String avoidIds) {
        log.debug("调用planFurnitureSet, scene={}, budget={}, avoidIds={}", scene, budget, avoidIds);
        String typeName = matchSceneToType(scene == null ? "" : scene.trim());
        if (typeName == null) {
            return "目前只支持按「客厅」「卧室」「书房」「餐厅」四个场景搭配，请先确认用户想布置哪个场景。";
        }
        // 模型可能把「八千」解析成 8 或 800000，钳制到可解释区间，避免生成荒唐方案
        int cap = budget == null ? 8000 : Math.max(500, Math.min(budget, 100000));

        FurnitureType type = furnitureTypeMapper.selectList(
                        new LambdaQueryWrapper<FurnitureType>().eq(FurnitureType::getName, typeName))
                .stream().findFirst().orElse(null);
        if (type == null) {
            return "暂时取不到「" + scene + "」的分类信息，请稍后再试。";
        }
        List<Furniture> candidates = furnitureMapper.selectList(
                new LambdaQueryWrapper<Furniture>()
                        .select(Furniture::getId, Furniture::getFName, Furniture::getPrice,
                                Furniture::getStock, Furniture::getIsRecommended, Furniture::getSaleCount)
                        .eq(Furniture::getTypeId, type.getId())
                        .gt(Furniture::getStock, 0)
                        .orderByDesc(Furniture::getIsRecommended)
                        .orderByDesc(Furniture::getSaleCount)
                        .orderByAsc(Furniture::getPrice)
                        .last("LIMIT 30")
        );
        if (candidates.isEmpty()) {
            return "「" + typeName + "」下暂时没有在售商品，建议推荐其他系列。";
        }

        Set<Long> avoid = parseAvoidIds(avoidIds);
        List<Furniture> picked = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;
        BigDecimal capValue = BigDecimal.valueOf(cap);
        // 达到预算 85% 就收手，避免为凑满预算硬塞用户不一定需要的小件
        BigDecimal stopLine = capValue.multiply(new BigDecimal("0.85"));
        for (Furniture f : candidates) {
            if (avoid.contains(f.getId())) {
                continue;
            }
            BigDecimal price = f.getPrice() == null ? BigDecimal.ZERO : f.getPrice();
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (sum.add(price).compareTo(capValue) > 0) {
                continue;
            }
            picked.add(f);
            sum = sum.add(price);
            if (sum.compareTo(stopLine) >= 0) {
                break;
            }
        }
        if (picked.isEmpty()) {
            if (!avoid.isEmpty()) {
                return String.format("排除你指定的商品后，「%s」在 ¥%d 预算内凑不出方案。要不要放宽排除范围，或提高预算？",
                        typeName, cap);
            }
            BigDecimal min = candidates.stream().map(Furniture::getPrice)
                    .filter(Objects::nonNull).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            return String.format("预算 ¥%d 不够，「%s」最低单件 ¥%s。建议提高预算或换其他系列。",
                    cap, typeName, min);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("【%s套装规划】预算 ¥%d，选中 %d 件：\n", scene, cap, picked.size()));
        for (Furniture f : picked) {
            sb.append(String.format("- %s [商品:%d] ¥%s（库存%d件）\n",
                    f.getFName(), f.getId(), f.getPrice(), f.getStock()));
        }
        sb.append(String.format("商品总价 ¥%s，预算剩余 ¥%s\n", sum, capValue.subtract(sum)));

        // 套装标记由后端生成、模型原样转述，金额不经过模型计算
        Map<String, Object> payload = new HashMap<>();
        payload.put("scene", scene);
        payload.put("budget", cap);
        payload.put("total", sum);
        List<Map<String, Object>> itemNodes = new ArrayList<>();
        for (Furniture f : picked) {
            Map<String, Object> node = new HashMap<>();
            node.put("id", f.getId());
            node.put("qty", 1);
            itemNodes.add(node);
        }
        payload.put("items", itemNodes);
        sb.append("[套装]").append(JSONUtil.toJsonStr(payload)).append("[/套装]");
        return sb.toString();
    }

    /**
     * 查询当前用户在本单可用的优惠券及抵扣额。
     * <p>
     * 可用性判断复用 {@code estimate} 的券评估，不在此重写任何算价逻辑，
     * 保证 AI 说的「能省多少」与下单实收永远同源。
     *
     * @param memoryId 会话记忆ID，由框架注入
     * @return 可用券与不可用券说明
     */
    @Tool("""
            查询当前用户在本单（购物车）可用的优惠券：每张券能抵多少、为何不可用。
            仅当用户问「我有什么券能用」「用券能省多少」时调用，需要先有购物车数据。
            """)
    public String queryMyCoupons(@ToolMemoryId String memoryId) {
        log.debug("调用queryMyCoupons, memoryId={}", memoryId);
        Long userId = resolveUserId(memoryId);
        if (userId == null) {
            return "请先登录后再查询优惠券。";
        }
        List<OrderItemDTO> cart = loadCartByUser(userId);
        if (cart.isEmpty()) {
            return "购物车是空的，无法评估券。请先提示用户把商品加入购物车。";
        }
        Result est = orderService.estimateForUser(userId, new OrderEstimateDTO(cart, null));
        Object estData = est.getData();
        if (!Boolean.TRUE.equals(est.getSuccess()) || !(estData instanceof CouponEstimateVO)) {
            return "试算失败：" + (est.getMsg() == null ? "请稍后再试" : est.getMsg());
        }
        CouponEstimateVO vo = (CouponEstimateVO) estData;

        Map<Long, UserCouponVO> couponMap = new HashMap<>();
        Result mine = couponService.getMyCoupons(userId, null);
        Object mineData = mine.getData();
        if (mineData instanceof List) {
            for (Object o : (List<?>) mineData) {
                if (o instanceof UserCouponVO) {
                    UserCouponVO uc = (UserCouponVO) o;
                    couponMap.put(uc.getUserCouponId(), uc);
                }
            }
        }

        List<String> usableLines = new ArrayList<>();
        List<String> unusableLines = new ArrayList<>();
        List<CouponOptionVO> usableOptions = new ArrayList<>();
        if (vo.getCouponOptions() != null) {
            for (CouponOptionVO opt : vo.getCouponOptions()) {
                UserCouponVO uc = couponMap.get(opt.getUserCouponId());
                String name = uc == null ? ("券#" + opt.getUserCouponId()) : uc.getName();
                String amount = (uc == null || uc.getAmountText() == null) ? "" : "｜" + uc.getAmountText();
                if (Boolean.TRUE.equals(opt.getUsable())) {
                    usableLines.add(String.format("· %s%s｜本单可抵 ¥%s", name, amount, opt.getEstimate()));
                    // 边际抵扣为 0 的（加进去也不多省）不进建议：勾了只会白占叠加张数
                    if (opt.getEstimate() != null && opt.getEstimate().compareTo(BigDecimal.ZERO) > 0) {
                        usableOptions.add(opt);
                    }
                } else {
                    String reason = (opt.getReason() == null || opt.getReason().isEmpty())
                            ? "本单不可用" : opt.getReason();
                    unusableLines.add(String.format("· %s%s｜%s", name, amount, reason));
                }
            }
        }

        // 建议集合：按单张可抵金额降序，取前 maxStackCount 张，折扣券最多 1 张。
        // 刻意不做组合搜索——单张排序取前 N 讲得清；组合择优（已移除的 pickBestCoupons）讲不清。
        // 也不把各张抵扣相加对外宣称「合计省 X」：券有封顶比例，边际值相加不等于实际抵扣。
        usableOptions.sort((a, b) -> b.getEstimate().compareTo(a.getEstimate()));
        int stackCap = vo.getMaxStackCount() == null ? 1 : vo.getMaxStackCount();
        List<Long> suggestIds = new ArrayList<>();
        int discountCount = 0;
        for (CouponOptionVO opt : usableOptions) {
            if (suggestIds.size() >= stackCap) {
                break;
            }
            UserCouponVO uc = couponMap.get(opt.getUserCouponId());
            if (uc != null && Integer.valueOf(2).equals(uc.getType())) {
                if (discountCount >= 1) {
                    continue;
                }
                discountCount++;
            }
            suggestIds.add(opt.getUserCouponId());
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("【本单商品 ¥%s，可用券 %d 张】\n", vo.getGoodsTotal(), usableLines.size()));
        if (usableLines.isEmpty()) {
            sb.append("暂无可用券。\n");
        } else {
            usableLines.forEach(line -> sb.append(line).append('\n'));
        }
        if (!unusableLines.isEmpty()) {
            sb.append("不可用：\n");
            unusableLines.forEach(line -> sb.append(line).append('\n'));
        }
        if (vo.getMaxStackCount() != null) {
            sb.append(String.format("规则提示：最多叠加 %d 张，折扣券每单限用 1 张。\n", vo.getMaxStackCount()));
        }
        // 用券建议由前端按钮触发预勾选，用户仍可在选券弹窗里取消，AI 不直接改单
        if (!suggestIds.isEmpty()) {
            sb.append(String.format("建议先叠这 %d 张（按单张可抵从高到低，未做组合择优，结算页可自行调整）。\n",
                    suggestIds.size()));
            sb.append("[用券]")
                    .append(suggestIds.stream().map(String::valueOf).collect(Collectors.joining(",")))
                    .append("[/用券]");
        }
        return sb.toString();
    }

    // ==================== 榜单 ====================

    /**
     * 查询家具榜单：销量 / 新品 / 推荐位。
     * <p>
     * 三者合并为一个工具而非拆成三个：模型可选的工具越少，选错工具的概率越低。
     *
     * @param type  榜单类型，sales/new/recommended
     * @param limit 返回条数，为空取 5，上限 10
     * @return 榜单文本
     */
    @Tool("""
            查询家具榜单。type 只能是 sales（销量榜）、new（新品）、recommended（推荐位）三者之一。
            用户问「哪款卖得好」「有什么新品」「有什么推荐」时调用。limit 为条数，默认 5，最多 10。
            """)
    public String queryFurnitureRanking(String type, Integer limit) {
        log.debug("调用queryFurnitureRanking, type={}, limit={}", type, limit);
        int n = limit == null ? 5 : Math.max(1, Math.min(limit, 10));
        String kind = type == null ? "sales" : type.trim().toLowerCase();
        switch (kind) {
            case "sales":
                return topSellingText(n);
            case "new":
                return newArrivalText(n);
            case "recommended":
                return recommendedText(n);
            default:
                return "榜单类型只支持 sales（销量榜）、new（新品）、recommended（推荐位）。";
        }
    }

    /**
     * 销量榜。只统计已支付及之后状态的订单，成交数据不足时如实说明，不编造销量。
     */
    private String topSellingText(int limit) {
        List<TopFurnitureVO> list = orderItemMapper.selectTopSelling(limit);
        if (list.isEmpty()) {
            return "暂时没有足够的成交数据生成销量榜，可以看看推荐位，或按分类浏览。";
        }
        List<Long> ids = list.stream().map(TopFurnitureVO::getFurnitureId)
                .filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, Furniture> map = loadFurnitureByIds(ids);
        StringBuilder sb = new StringBuilder("【销量榜】\n");
        int rank = 1;
        for (TopFurnitureVO item : list) {
            Furniture f = map.get(item.getFurnitureId());
            sb.append(String.format("%d. %s [商品:%d]", rank++, item.getFurnitureName(), item.getFurnitureId()));
            if (f != null && f.getPrice() != null) {
                sb.append(" ¥").append(f.getPrice());
            }
            sb.append(" 已售 ").append(item.getTotalSold()).append(" 件\n");
        }
        return sb.toString();
    }

    /**
     * 新品。优先取近 30 天上架的；老库里商品多为同批导入，窗口内查不到时退回纯按上架时间倒序，并说明口径。
     */
    private String newArrivalText(int limit) {
        List<Furniture> list = selectByCreateTimeDesc(limit, true);
        boolean withinWindow = !list.isEmpty();
        if (!withinWindow) {
            list = selectByCreateTimeDesc(limit, false);
        }
        if (list.isEmpty()) {
            return "当前没有可展示的新品。";
        }
        StringBuilder sb = new StringBuilder(withinWindow ? "【近 30 天上架新品】\n" : "【最新上架】\n");
        appendFurnitureLines(sb, list);
        return sb.toString();
    }

    private List<Furniture> selectByCreateTimeDesc(int limit, boolean within30Days) {
        LambdaQueryWrapper<Furniture> wrapper = new LambdaQueryWrapper<Furniture>()
                .select(Furniture::getId, Furniture::getFName, Furniture::getPrice,
                        Furniture::getStock, Furniture::getCreateTime);
        if (within30Days) {
            wrapper.ge(Furniture::getCreateTime, LocalDateTime.now().minusDays(30));
        }
        wrapper.orderByDesc(Furniture::getCreateTime).last("LIMIT " + limit);
        return furnitureMapper.selectList(wrapper);
    }

    private String recommendedText(int limit) {
        List<Furniture> list = furnitureMapper.selectList(
                new LambdaQueryWrapper<Furniture>()
                        .select(Furniture::getId, Furniture::getFName, Furniture::getPrice,
                                Furniture::getStock, Furniture::getSaleCount)
                        .eq(Furniture::getIsRecommended, 1)
                        .gt(Furniture::getStock, 0)
                        .orderByDesc(Furniture::getSaleCount)
                        .last("LIMIT " + limit)
        );
        if (list.isEmpty()) {
            return "当前没有设置推荐位商品。";
        }
        StringBuilder sb = new StringBuilder("【推荐位】\n");
        appendFurnitureLines(sb, list);
        return sb.toString();
    }

    private void appendFurnitureLines(StringBuilder sb, List<Furniture> list) {
        for (Furniture f : list) {
            sb.append(String.format("- %s [商品:%d] ¥%s（库存%d件）\n",
                    f.getFName(), f.getId(), f.getPrice(), f.getStock()));
        }
    }

    /**
     * 解析「不想再要的商品ID」。
     * <p>
     * 模型可能传 {@code 1417,1420}、{@code [1417,1420]} 甚至带引号，这里统一清洗后再解析。
     * 解析失败的片段直接跳过——宁可少排除，也不要因为格式问题让整次规划失败。
     */
    private Set<Long> parseAvoidIds(String avoidIds) {
        if (avoidIds == null || avoidIds.isBlank()) {
            return Set.of();
        }
        Set<Long> ids = new HashSet<>();
        for (String token : avoidIds.replaceAll("[\\[\\]{}\"']", "").split("[,\\s]+")) {
            if (token.isBlank()) {
                continue;
            }
            try {
                ids.add(Long.parseLong(token.trim()));
            } catch (NumberFormatException ignored) {
                // 解析不了的片段跳过，不打断规划
            }
        }
        return ids;
    }
}
