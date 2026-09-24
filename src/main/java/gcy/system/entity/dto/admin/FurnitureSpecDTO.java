package gcy.system.entity.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 管理端 - 商品规格与SKU组合保存DTO
 * <p>
 * 用于管理后台接收商品规格（规格组与规格值）以及对应SKU信息的保存请求数据。
 * 包含规格组定义、规格值明细和SKU库存价格等完整的规格管理数据结构。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FurnitureSpecDTO {

    @Schema(description = "商品ID")
    @NotNull(message = "商品ID不能为空")
    private Long furnitureId;

    @Schema(description = "规格组列表，包含每组下的规格值")
    private List<SpecGroupDTO> specGroups;

    @Schema(description = "SKU列表，包含价格、库存及规格关联信息")
    private List<SkuDTO> skuList;

    /**
     * 规格组DTO
     * <p>
     * 表示一个规格分组（如"颜色"、"尺寸"），包含该分组下的所有规格值。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpecGroupDTO {

        @Schema(description = "规格组ID（编辑时有值，新增时为null）")
        private Long id;

        @Schema(description = "规格组名称（如颜色、尺寸）")
        private String groupName;

        @Schema(description = "排序序号")
        private Integer sort;

        @Schema(description = "该规格组下的规格值列表")
        private List<SpecValueDTO> values;
    }

    /**
     * 规格值DTO
     * <p>
     * 表示规格组下的一个具体规格值（如"红色"、"XL"）。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpecValueDTO {

        @Schema(description = "规格值ID（编辑时有值，新增时为null）")
        private Long id;

        @Schema(description = "规格值名称（如红色、XL）")
        private String valueName;

        @Schema(description = "规格值对应的图片地址")
        private String valueImage;

        @Schema(description = "排序序号")
        private Integer sort;
    }

    /**
     * 规格组合键值对
     * <p>
     * 通过规格组名称和规格值名称来描述一个SKU的规格维度，用于名称匹配而非ID匹配，
     * 避免因规格值ID变动导致关联丢失。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpecPair {

        @Schema(description = "规格组名称（如颜色）")
        private String groupName;

        @Schema(description = "规格值名称（如红色）")
        private String valueName;
    }

    /**
     * SKU数据传输对象
     * <p>
     * 承载单个SKU的完整信息，包括价格、库存、图片以及关联的规格组合。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkuDTO {

        @Schema(description = "SKU ID（编辑时有值，新增时为null）")
        private Long id;

        @Schema(description = "销售价格")
        private BigDecimal price;

        @Schema(description = "库存数量")
        private Integer stock;

        @Schema(description = "SKU图片地址")
        private String skuImage;

        @Schema(description = "状态（如上下架等）")
        private Integer status;

        @Schema(description = "选中的规格值ID列表，用于生成sku_spec关联")
        private List<Long> specValueIds;

        @Schema(description = "规格组合列表，通过规格组名称+值名称进行精确匹配")
        private List<SpecPair> specs;
    }
}
