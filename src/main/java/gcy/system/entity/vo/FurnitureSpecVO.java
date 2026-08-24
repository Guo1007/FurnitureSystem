package gcy.system.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 商品规格与SKU数据VO（前端展示用）
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FurnitureSpecVO {

    /**
     * 规格分组列表
     */
    private List<SpecGroupVO> specGroups;

    /**
     * SKU列表
     */
    private List<SkuVO> skuList;

    /**
     * 规格分组VO（前端展示用）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpecGroupVO {
        /**
         * 规格分组ID
         */
        private Long id;
        /**
         * 规格分组名称
         */
        private String groupName;
        /**
         * 排序序号
         */
        private Integer sort;
        /**
         * 该分组下的规格值列表
         */
        private List<SpecValueVO> values;
    }

    /**
     * 规格值VO（前端展示用）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpecValueVO {
        /**
         * 规格值ID
         */
        private Long id;
        /**
         * 规格值名称
         */
        private String valueName;
        /**
         * 规格值图片
         */
        private String valueImage;
        /**
         * 排序序号
         */
        private Integer sort;
    }

    /**
     * SKU VO（前端展示用）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkuVO {
        /**
         * SKU ID
         */
        private Long id;
        /**
         * 价格
         */
        private BigDecimal price;
        /**
         * 库存数量
         */
        private Integer stock;
        /**
         * SKU图片
         */
        private String skuImage;
        /**
         * 状态
         */
        private Integer status;

        /**
         * 规格键值对映射，key为规格组名，value为规格值名
         */
        private Map<String, String> specMap;

        /**
         * 规格文本描述
         */
        private String specText;
    }
}