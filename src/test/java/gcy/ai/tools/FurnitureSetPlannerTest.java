package gcy.ai.tools;

import gcy.system.entity.pojo.Furniture;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link FurnitureSetPlanner} 单元测试。
 */
class FurnitureSetPlannerTest {

    @Test
    void picksCoreFurnitureBeforeAccessories() {
        List<Furniture> candidates = List.of(
                f(748, "复古床尾凳", "958.71", 1, 30),
                f(660, "带LED镜梳妆台", "1187.53", 1, 30),
                f(760, "实木边框穿衣镜", "141.91", 1, 28),
                f(669, "白色烤漆梳妆台", "1999.41", 1, 28),
                f(471, "奶油风双人床", "1304.29", 0, 0),
                f(460, "松木双人床", "7538.90", 1, 10),
                f(3, "席梦思床垫", "100.00", 1, 0),
                f(5, "衣柜", "500.00", 0, 0),
                f(554, "开放式衣柜", "1551.06", 0, 0),
                f(619, "带USB床头柜", "165.36", 0, 6)
        );

        FurnitureSetPlanner.Plan plan =
                FurnitureSetPlanner.plan("卧室系列", candidates, 5000, Set.of());

        List<String> names = plan.picked().stream().map(Furniture::getFName).toList();
        assertEquals(List.of("奶油风双人床", "席梦思床垫", "衣柜", "带USB床头柜"),
                names.subList(0, 4));
        assertTrue(plan.total().compareTo(BigDecimal.valueOf(5000)) <= 0);
        assertTrue(plan.missingCore().isEmpty());
    }

    @Test
    void doesNotFallBackToAccessoriesWhenCoreIsUnaffordable() {
        List<Furniture> candidates = List.of(
                f(1, "奶油风双人床", "1304.29", 0, 0),
                f(748, "复古床尾凳", "958.71", 1, 30)
        );

        FurnitureSetPlanner.Plan plan =
                FurnitureSetPlanner.plan("卧室系列", candidates, 500, Set.of());

        assertTrue(plan.picked().isEmpty());
        assertFalse(plan.missingCore().isEmpty());
        assertEquals("床", plan.missingCore().get(0).name());
        assertEquals(0, plan.coreMinimum().compareTo(new BigDecimal("1304.29")));
    }

    @Test
    void returnsEmptyWhenPrimaryCoreCannotFit() {
        List<Furniture> candidates = List.of(
                f(1, "奶油风双人床", "1304.29", 0, 0),
                f(3, "席梦思床垫", "100.00", 1, 0),
                f(5, "衣柜", "500.00", 0, 0)
        );

        FurnitureSetPlanner.Plan plan =
                FurnitureSetPlanner.plan("卧室系列", candidates, 1000, Set.of());

        assertTrue(plan.picked().isEmpty());
        assertTrue(plan.missingCore().stream().anyMatch(m -> "床".equals(m.name())));
        assertEquals(0, plan.coreMinimum().compareTo(new BigDecimal("1904.29")));
    }

    @Test
    void keepsPartialCoreSetWhenCompleteSetIsUnaffordable() {
        List<Furniture> candidates = List.of(
                f(1, "奶油风双人床", "1304.29", 0, 0),
                f(3, "席梦思床垫", "100.00", 1, 0),
                f(5, "衣柜", "500.00", 0, 0),
                f(619, "带USB床头柜", "165.36", 0, 6)
        );

        FurnitureSetPlanner.Plan plan =
                FurnitureSetPlanner.plan("卧室系列", candidates, 1600, Set.of());

        List<String> names = plan.picked().stream().map(Furniture::getFName).toList();
        assertEquals(3, names.size());
        assertTrue(names.contains("奶油风双人床"));
        assertTrue(names.contains("席梦思床垫"));
        assertTrue(names.contains("带USB床头柜"));
        assertTrue(plan.missingCore().stream().anyMatch(m -> "衣柜".equals(m.name())));
        assertTrue(plan.total().compareTo(BigDecimal.valueOf(1600)) <= 0);
    }

    @Test
    void prefersCompleteCoreSetOverPremiumPrimaryItem() {
        List<Furniture> candidates = List.of(
                f(460, "松木双人床", "7538.90", 1, 10),
                f(471, "奶油风双人床", "1304.29", 0, 0),
                f(529, "乳胶床垫", "700.56", 1, 23),
                f(569, "实木衣柜", "4530.12", 1, 6),
                f(597, "北欧床头柜", "272.65", 1, 23)
        );

        FurnitureSetPlanner.Plan plan =
                FurnitureSetPlanner.plan("卧室系列", candidates, 8000, Set.of());

        List<Long> ids = plan.picked().stream().map(Furniture::getId).toList();
        assertTrue(ids.contains(471L));
        assertFalse(ids.contains(460L));
        assertTrue(ids.contains(569L));
        assertTrue(plan.missingCore().isEmpty());
        assertTrue(plan.total().compareTo(BigDecimal.valueOf(8000)) <= 0);
    }

    @Test
    void respectsAvoidIds() {
        List<Furniture> candidates = List.of(
                f(1, "奶油风双人床", "1304.29", 0, 0),
                f(2, "橡胶木大床", "1502.48", 0, 0)
        );

        FurnitureSetPlanner.Plan plan =
                FurnitureSetPlanner.plan("卧室系列", candidates, 5000, Set.of(1L));

        assertEquals(1, plan.picked().size());
        assertEquals(2L, plan.picked().get(0).getId());
    }

    @Test
    void dedupesAccessoryCategories() {
        List<Furniture> candidates = List.of(
                f(471, "奶油风双人床", "1304.29", 0, 0),
                f(3, "席梦思床垫", "100.00", 1, 0),
                f(5, "衣柜", "500.00", 0, 0),
                f(619, "带USB床头柜", "165.36", 0, 6),
                f(10, "简约梳妆台", "600.00", 0, 0),
                f(11, "轻奢梳妆台", "700.00", 0, 0),
                f(12, "实木边框穿衣镜", "141.91", 0, 0),
                f(13, "可旋转穿衣镜", "150.00", 0, 0)
        );

        FurnitureSetPlanner.Plan plan =
                FurnitureSetPlanner.plan("卧室系列", candidates, 5000, Set.of());

        long vanityCount = plan.picked().stream()
                .filter(f -> f.getFName().contains("梳妆台")).count();
        long mirrorCount = plan.picked().stream()
                .filter(f -> f.getFName().contains("镜")).count();
        assertEquals(1, vanityCount);
        assertEquals(1, mirrorCount);
        assertTrue(plan.picked().size() <= 6);
    }

    @Test
    void neverExceedsBudget() {
        List<Furniture> candidates = List.of(
                f(748, "复古床尾凳", "958.71", 1, 30),
                f(660, "带LED镜梳妆台", "1187.53", 1, 30),
                f(471, "奶油风双人床", "1304.29", 0, 0),
                f(3, "席梦思床垫", "100.00", 1, 0),
                f(5, "衣柜", "500.00", 0, 0),
                f(619, "带USB床头柜", "165.36", 0, 6)
        );

        for (int budget : new int[]{500, 1000, 2000, 5000, 10000}) {
            FurnitureSetPlanner.Plan plan =
                    FurnitureSetPlanner.plan("卧室系列", candidates, budget, Set.of());
            assertTrue(plan.total().compareTo(BigDecimal.valueOf(budget)) <= 0,
                    "budget=" + budget + ", total=" + plan.total());
        }
    }

    private static Furniture f(long id, String name, String price, int recommended, int sales) {
        Furniture furniture = new Furniture();
        furniture.setId(id);
        furniture.setFName(name);
        furniture.setPrice(new BigDecimal(price));
        furniture.setStock(10);
        furniture.setIsRecommended(recommended);
        furniture.setSaleCount(sales);
        return furniture;
    }
}