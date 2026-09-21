package gcy.ai.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import dev.langchain4j.agent.tool.Tool;
import gcy.system.entity.dto.UserDTO;
import gcy.system.entity.pojo.*;
import gcy.system.mapper.*;
import gcy.system.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
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
     * 根据商品名称模糊搜索商品。
     *
     * @param name 搜索关键词
     * @return 匹配的商品列表文本
     */
    @Tool("根据商品名称模糊搜索商品，仅返回名称、ID和价格")
    public String searchFurniture(String name) {
        log.debug("调用searchFurniture");
        List<Furniture> list = furnitureMapper.selectList(
                new LambdaQueryWrapper<Furniture>()
                        .select(Furniture::getId, Furniture::getFName, Furniture::getPrice, Furniture::getStock)
                        .like(Furniture::getFName, name)
        );
        if (list.isEmpty()) {
            return "未找到名称中包含「" + name + "」的商品";
        }
        StringBuilder sb = new StringBuilder("【搜索结果】\n");
        for (Furniture f : list) {
            sb.append(String.format("- %s [商品:%d] | ¥%s | 库存:%d件\n",
                    f.getFName(), f.getId(), f.getPrice(), f.getStock()));
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

        List<Furniture> furnitureList = furnitureMapper.selectList(
                new LambdaQueryWrapper<Furniture>()
                        .select(Furniture::getId, Furniture::getFName)
                        .like(Furniture::getFName, furnitureName)
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
        StringBuilder sb = new StringBuilder("【库存概况】\n");
        int totalStock = 0;
        for (Furniture f : furnitureList) {
            int skuCount = skuMapper.selectCount(
                    new LambdaQueryWrapper<Sku>()
                            .eq(Sku::getFurnitureId, f.getId())).intValue();
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
     */
    private Furniture findOneFurniture(String name) {
        List<Furniture> list = furnitureMapper.selectList(
                new LambdaQueryWrapper<Furniture>()
                        .select(Furniture::getId, Furniture::getFName, Furniture::getPrice, Furniture::getStock, Furniture::getTypeId)
                        .like(Furniture::getFName, name.trim())
        );
        if (list.isEmpty()) return null;
        if (list.size() > 1) {
            log.debug("findOneFurniture: 匹配到多个商品, name={}", name);
            for (Furniture f : list) {
                if (f.getFName().equals(name.trim())) return f;
            }
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
        StringBuilder sb = new StringBuilder("【");
        sb.append(user.getUserName() != null ? user.getUserName() : "您");
        sb.append("的收藏商品】\n");
        for (Favorite fav : favorites) {
            Furniture f = furnitureMapper.selectOne(
                    new LambdaQueryWrapper<Furniture>()
                            .select(Furniture::getId, Furniture::getFName, Furniture::getPrice, Furniture::getStock)
                            .eq(Furniture::getId, fav.getFurnitureId()));
            if (f != null) {
                sb.append(String.format("· %s [商品:%d] | ¥%s | 库存: %d件\n",
                        f.getFName(), f.getId(), f.getPrice(), f.getStock()));
            }
        }
        return sb.toString();
    }
}
