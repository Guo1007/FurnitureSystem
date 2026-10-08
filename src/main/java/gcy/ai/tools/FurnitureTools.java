package gcy.ai.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import dev.langchain4j.agent.tool.Tool;
import gcy.system.entity.dto.UserDTO;
import gcy.system.entity.pojo.*;
import gcy.system.mapper.*;
import gcy.system.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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
    @Tool("查询指定商品的所有SKU规格及每个SKU的库存、价格信息。需要传入商品名称")
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
                sb.append(String.format("价格: ¥%s | 库存: %d件\n",
                        sku.getPrice(), sku.getStock()));
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
            sb.append(String.format("价格: ¥%s | 库存: %d件",
                    sku.getPrice(), sku.getStock()));
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

    /**
     * 查询所有商品的库存概况。
     *
     * @return 包含每个商品名称、库存量和SKU数量的库存概况文本
     */
    @Tool("查询所有商品的总库存概况，包含每个商品的名称、总库存量和SKU数量")
    public String queryStockSummary() {
        log.debug("调用queryStockSummary");
        List<Furniture> furnitureList = furnitureMapper.selectList(
                new LambdaQueryWrapper<Furniture>()
                        .select(Furniture::getId, Furniture::getFName, Furniture::getStock)
        );
        if (furnitureList.isEmpty()) {
            return "暂无商品数据";
        }
        // 一次 GROUP BY 取回所有商品的 SKU 数量，替代原先「每个商品一条 COUNT(*)」的 N+1
        // （商品表 1000 条时原实现要跑 1001 条串行 SQL，且全在 AI 对话线程上）
        // 注意：这里刻意**不加** status 过滤，与原 selectCount 的语义保持一致
        //（SkuMapper.sumStockByFurnitureId 只统计 status=1，两者口径不同，别混用）
        Map<Long, Long> skuCountByFurniture = new HashMap<>();
        try {
            List<Long> furnitureIds = furnitureList.stream()
                    .map(Furniture::getId)
                    .collect(Collectors.toList());
            QueryWrapper<Sku> qw = new QueryWrapper<Sku>()
                    .select("furniture_id", "COUNT(*) AS cnt")
                    .in("furniture_id", furnitureIds)
                    .groupBy("furniture_id");
            for (Map<String, Object> row : skuMapper.selectMaps(qw)) {
                Object fid = row.get("furniture_id");
                Object cnt = row.get("cnt");
                if (fid instanceof Number && cnt instanceof Number) {
                    skuCountByFurniture.put(((Number) fid).longValue(), ((Number) cnt).longValue());
                }
            }
        } catch (Exception e) {
            // 统计失败只影响 SKU 个数展示，库存总数仍要正常返回
            log.warn("批量统计SKU数量失败，SKU个数将显示为0: {}", e.getMessage());
        }
        StringBuilder sb = new StringBuilder("【库存概况】\n");
        int totalStock = 0;
        for (Furniture f : furnitureList) {
            int skuCount = skuCountByFurniture.getOrDefault(f.getId(), 0L).intValue();
            sb.append(String.format("- %s: 库存%d件%s\n",
                    f.getFName(), f.getStock(), skuCount > 0 ? " (" + skuCount + "个SKU)" : ""));
            totalStock += f.getStock();
        }
        sb.append("\n总库存: ").append(totalStock).append("件");
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
        List<Furniture> furnitureList = furnitureMapper.selectList(
                new LambdaQueryWrapper<Furniture>()
                        .select(Furniture::getId, Furniture::getFName, Furniture::getPrice, Furniture::getStock)
                        .eq(Furniture::getTypeId, type.getId())
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
     * 通过 UserHolder 获取当前登录用户，然后查询其所有收藏的家具商品。
     * 若用户未登录则返回提示信息，引导用户登录后使用收藏功能。
     * </p>
     *
     * @return 用户收藏的商品列表文本，包含商品名称、价格、库存信息
     */
    @Tool("查询当前用户已收藏的商品列表，用于了解用户偏好并提供个性化推荐")
    public String queryUserFavorites() {
        log.debug("调用queryUserFavorites");
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return "您当前未登录，登录后可以查看收藏商品哦～";
        }
        List<Favorite> favorites = favoriteMapper.selectList(
                new LambdaQueryWrapper<Favorite>()
                        .eq(Favorite::getUserId, user.getId()));
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
        StringBuilder sb = new StringBuilder("【");
        sb.append(user.getUserName() != null ? user.getUserName() : "您");
        sb.append("的收藏商品】\n");
        for (Favorite fav : favorites) {
            Furniture f = furnitureMap.get(fav.getFurnitureId());
            if (f != null) {
                sb.append(String.format("· %s [商品:%d] | ¥%s | 库存: %d件\n",
                        f.getFName(), f.getId(), f.getPrice(), f.getStock()));
            }
        }
        return sb.toString();
    }
}
