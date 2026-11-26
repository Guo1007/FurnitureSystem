package gcy.ai.tools;

import gcy.system.entity.pojo.Furniture;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 套装选品器。
 * <p>
 * 原实现按「推荐位 > 销量 > 价格」取前 30 条后贪心累加，排在前面的往往是床尾凳、
 * 穿衣镜、梳妆台这类高销量配件；真正的基础家具要么排在 30 条之外，要么单价过高
 * 被预算检查跳过，最终会配出一套没有床/没有沙发的「配件组合」。
 * </p>
 * <p>
 * 这里改成两段式：先按场景的核心品类各选一件（床、床垫、衣柜、床头柜；
 * 沙发、茶几、电视柜、鞋柜……），再用剩余预算补配件；同一配件品类最多一件，
 * 单套最多 {@link #MAX_ITEMS} 件。核心件本身放不进预算时，返回缺少的品类和
 * 最低价格，由调用方明确告知用户，而不是继续用配件凑数。
 * </p>
 *
 * @author 郭名城
 */
public final class FurnitureSetPlanner {

    /**
     * 单套最多包含的商品件数，避免把剩余预算全塞成配件。
     */
    private static final int MAX_ITEMS = 6;

    /**
     * 预算使用上限比例：达到 85% 就收手，避免为凑满预算硬塞用户不一定需要的小件。
     */
    private static final BigDecimal STOP_RATIO = new BigDecimal("0.85");

    /**
     * 各场景的核心品类，key 为 furniture_type.name。
     * <p>
     * 床的匹配要排除床垫、床头柜、床尾凳——它们名字里都带「床」。
     * 椅子要排除餐椅和沙发，避免把餐厅/客厅的椅子混进书房。
     * </p>
     */
    private static final Map<String, List<CoreGroup>> CORE_GROUPS = Map.of(
            "卧室系列", List.of(
                    new CoreGroup("床", name -> name.contains("床")
                            && !name.contains("床垫")
                            && !name.contains("床头柜")
                            && !name.contains("床尾凳")),
                    new CoreGroup("床垫", name -> name.contains("床垫")),
                    new CoreGroup("衣柜", name -> name.contains("衣柜")),
                    new CoreGroup("床头柜", name -> name.contains("床头柜"))
            ),
            "门厅系列", List.of(
                    new CoreGroup("沙发", name -> name.contains("沙发")),
                    new CoreGroup("茶几", name -> name.contains("茶几")),
                    new CoreGroup("电视柜", name -> name.contains("电视柜")),
                    new CoreGroup("鞋柜", name -> name.contains("鞋柜"))
            ),
            "书房系列", List.of(
                    new CoreGroup("书桌", name -> name.contains("书桌") || name.contains("电脑桌")),
                    new CoreGroup("书柜", name -> name.contains("书柜") || name.contains("书架")),
                    new CoreGroup("椅子", name -> name.contains("椅")
                            && !name.contains("餐椅")
                            && !name.contains("沙发"))
            ),
            "餐厅系列", List.of(
                    new CoreGroup("餐桌", name -> name.contains("餐桌")),
                    new CoreGroup("餐椅", name -> name.contains("餐椅")),
                    new CoreGroup("餐边柜", name -> name.contains("餐边柜"))
            )
    );

    /**
     * 把商品名归到配件品类，用于同类去重（两个梳妆台、两面镜子、两个斗柜只保留一件）。
     */
    private static String categoryKey(String name) {
        if (name.contains("梳妆台")) return "梳妆台";
        if (name.contains("床尾凳")) return "床尾凳";
        if (name.contains("床头柜")) return "床头柜";
        if (name.contains("餐边柜")) return "餐边柜";
        if (name.contains("电视柜")) return "电视柜";
        if (name.contains("鞋柜")) return "鞋柜";
        if (name.contains("衣柜")) return "衣柜";
        if (name.contains("书柜") || name.contains("书架")) return "书柜";
        if (name.contains("书桌") || name.contains("电脑桌")) return "书桌";
        if (name.contains("餐椅")) return "餐椅";
        if (name.contains("椅")) return "椅子";
        if (name.contains("斗柜") || name.contains("储物柜")) return "储物柜";
        if (name.contains("床垫")) return "床垫";
        if (name.contains("镜")) return "镜子";
        if (name.contains("沙发")) return "沙发";
        if (name.contains("茶几")) return "茶几";
        if (name.contains("餐桌")) return "餐桌";
        if (name.contains("床")) return "床";
        return name;
    }

    /**
     * 配件优先级：推荐位 > 销量 > 价格。
     */
    private static final Comparator<Furniture> PREFERENCE =
            Comparator.comparingInt((Furniture f) -> f.getIsRecommended() == null ? 0 : f.getIsRecommended())
                    .reversed()
                    .thenComparing(Comparator.comparingInt(
                            (Furniture f) -> f.getSaleCount() == null ? 0 : f.getSaleCount()).reversed())
                    .thenComparing(FurnitureSetPlanner::priceOf);

    private FurnitureSetPlanner() {
    }

    /**
     * 规划一套家具。
     *
     * @param typeName   场景对应的 furniture_type.name
     * @param candidates 该场景下所有在售商品
     * @param budget     预算上限（元）
     * @param avoidIds   用户明确不要的商品ID
     * @return 选品结果；核心件一件都放不进预算时 picked 为空
     */
    public static Plan plan(String typeName, List<Furniture> candidates, int budget, Set<Long> avoidIds) {
        List<CoreGroup> coreGroups = CORE_GROUPS.getOrDefault(typeName, List.of());
        BigDecimal cap = BigDecimal.valueOf(budget);

        // 先按品类整理候选，并算出配齐全部核心品类的最低总价
        List<GroupCandidates> groups = new ArrayList<>();
        BigDecimal coreMinimum = BigDecimal.ZERO;
        for (CoreGroup group : coreGroups) {
            List<Furniture> items = candidates.stream()
                    .filter(f -> f.getId() != null && !avoidIds.contains(f.getId()))
                    .filter(f -> f.getFName() != null && group.matcher().test(f.getFName()))
                    .filter(f -> priceOf(f).signum() > 0)
                    .sorted(PREFERENCE)
                    .toList();
            if (items.isEmpty()) {
                continue;
            }
            BigDecimal minPrice = items.stream()
                    .map(FurnitureSetPlanner::priceOf)
                    .min(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);
            coreMinimum = coreMinimum.add(minPrice);
            groups.add(new GroupCandidates(group, items, minPrice));
        }
        if (groups.isEmpty()) {
            return new Plan(List.of(), BigDecimal.ZERO, List.of(), coreMinimum);
        }

        // 先尝试配齐全部核心品类：给后面的品类预留最低价，避免一张贵床把衣柜预算吃光。
        Selection selection = select(groups, cap, true);
        if (selection.primaryFailed()) {
            // 预算不足以配齐时，退回不做预留的贪心方案：至少保证床/沙发这类主件，
            // 缺的品类如实写进 missingCore，由工具层告知用户。
            selection = select(groups, cap, false);
        }
        if (selection.primaryFailed()) {
            return new Plan(List.of(), BigDecimal.ZERO, selection.missing(), coreMinimum);
        }

        List<Furniture> picked = new ArrayList<>(selection.picked());
        Set<Long> pickedIds = new HashSet<>();
        for (Furniture f : picked) {
            pickedIds.add(f.getId());
        }
        BigDecimal total = selection.total();

        // 核心件选齐后，用剩余预算补配件；同一品类最多一件，单套最多 MAX_ITEMS 件。
        BigDecimal stopLine = cap.multiply(STOP_RATIO);
        Set<String> accessoryCategories = new HashSet<>();
        for (Furniture f : candidates.stream().sorted(PREFERENCE).toList()) {
            if (picked.size() >= MAX_ITEMS || total.compareTo(stopLine) >= 0) {
                break;
            }
            if (f.getId() == null || pickedIds.contains(f.getId()) || avoidIds.contains(f.getId())) {
                continue;
            }
            String name = f.getFName();
            if (name == null || name.isBlank() || matchesAnyCoreGroup(coreGroups, name)) {
                continue;
            }
            BigDecimal price = priceOf(f);
            if (price.signum() <= 0 || total.add(price).compareTo(cap) > 0) {
                continue;
            }
            if (!accessoryCategories.add(categoryKey(name))) {
                continue;
            }
            picked.add(f);
            pickedIds.add(f.getId());
            total = total.add(price);
        }

        return new Plan(List.copyOf(picked), total, selection.missing(), coreMinimum);
    }

    /**
     * 在每个核心品类里选一件。
     *
     * @param groups 有候选的核心品类，第一个为该场景的主件（床/沙发/书桌/餐桌）
     * @param cap    预算
     * @param reserve 是否为后续品类预留最低价
     */
    private static Selection select(List<GroupCandidates> groups, BigDecimal cap, boolean reserve) {
        List<Furniture> picked = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        List<MissingCore> missing = new ArrayList<>();
        boolean primaryFailed = false;
        for (int idx = 0; idx < groups.size(); idx++) {
            GroupCandidates gc = groups.get(idx);
            BigDecimal reserved = BigDecimal.ZERO;
            if (reserve) {
                for (int j = idx + 1; j < groups.size(); j++) {
                    reserved = reserved.add(groups.get(j).minPrice());
                }
            }
            BigDecimal available = cap.subtract(total).subtract(reserved);
            Furniture chosen = null;
            for (Furniture f : gc.items()) {
                if (priceOf(f).compareTo(available) <= 0) {
                    chosen = f;
                    break;
                }
            }
            if (chosen == null) {
                missing.add(new MissingCore(gc.group().name(), gc.minPrice()));
                if (idx == 0) {
                    primaryFailed = true;
                }
                continue;
            }
            picked.add(chosen);
            total = total.add(priceOf(chosen));
        }
        return new Selection(picked, total, List.copyOf(missing), primaryFailed);
    }

    private static boolean matchesAnyCoreGroup(List<CoreGroup> groups, String name) {
        return groups.stream().anyMatch(group -> group.matcher().test(name));
    }

    private static BigDecimal priceOf(Furniture f) {
        return f.getPrice() == null ? BigDecimal.ZERO : f.getPrice();
    }

    /**
     * 核心品类。
     *
     * @param name    展示名称
     * @param matcher 商品名匹配规则
     */
    private record CoreGroup(String name, Predicate<String> matcher) {
    }

    /**
     * 某个核心品类及其候选。
     *
     * @param group    品类
     * @param items    候选，已按推荐位/销量/价格排序
     * @param minPrice 该品类最低价
     */
    private record GroupCandidates(CoreGroup group, List<Furniture> items, BigDecimal minPrice) {
    }

    /**
     * 核心品类选品中间结果。
     *
     * @param picked        已选商品
     * @param total         总价
     * @param missing       没选上的品类
     * @param primaryFailed 主件（床/沙发/书桌/餐桌）没选上
     */
    private record Selection(List<Furniture> picked, BigDecimal total,
                             List<MissingCore> missing, boolean primaryFailed) {
    }

    /**
     * 预算内没配齐的核心品类。
     *
     * @param name         品类名
     * @param minimumPrice 该品类的最低价格
     */
    public record MissingCore(String name, BigDecimal minimumPrice) {
    }

    /**
     * 选品结果。
     *
     * @param picked      选中的商品，核心件在前、配件在后
     * @param total       总价
     * @param missingCore 预算内没配齐的核心品类
     * @param coreMinimum 配齐全部核心品类的最低总价
     */
    public record Plan(List<Furniture> picked, BigDecimal total,
                       List<MissingCore> missingCore, BigDecimal coreMinimum) {
    }
}