package gcy.system.service.Impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import gcy.system.entity.dto.Result;
import gcy.system.entity.dto.admin.FurnitureSpecDTO;
import gcy.system.entity.pojo.*;
import gcy.system.entity.vo.FurnitureSpecVO;
import gcy.system.mapper.*;
import gcy.system.service.ISpecService;
import gcy.system.utils.RedisConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 规格服务实现类。
 * <p>
 * 负责商品的规格组、规格值、SKU及其关联关系的增删改查操作。
 * 提供规格查询、SKU管理以及商品价格和库存的刷新功能。
 * </p>
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpecServiceImpl implements ISpecService {

    private final SpecGroupMapper specGroupMapper;

    private final SpecValueMapper specValueMapper;

    private final SkuMapper skuMapper;

    private final SkuSpecMapper skuSpecMapper;

    private final FurnitureMapper furnitureMapper;

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 根据家具ID获取该商品的所有规格组、规格值及SKU列表，不区分可用状态。
     *
     * @param furnitureId 商品（家具）ID
     * @return 包含规格分组和SKU列表的结果对象，规格分组为空时仅返回SKU基本信息
     */
    @Override
    public Result getSpecAndSkuByFurnitureId(Long furnitureId) {
        return buildSpecVO(furnitureId, false);
    }

    /**
     * 根据家具ID获取该商品的可售规格及SKU列表，仅返回状态为上架且库存大于零的SKU。
     *
     * @param furnitureId 商品（家具）ID
     * @return 包含可用规格分组和可售SKU列表的结果对象
     */
    @Override
    public Result getAvailableSpecAndSku(Long furnitureId) {
        return buildSpecVO(furnitureId, true);
    }

    /**
     * 构建规格视图对象的通用方法。
     * <p>
     * 依次查询规格组、规格值、SKU及SKU-规格关联关系，组装为前端可用的VO结构。
     * 当规格组为空时，仅返回SKU基本信息列表而不构建规格分组。
     * </p>
     *
     * @param furnitureId   商品（家具）ID
     * @param onlyAvailable 是否仅返回可售SKU，为 true 时过滤下架状态或无库存的SKU
     * @return 包含规格分组和SKU列表的结果对象
     */
    private Result buildSpecVO(Long furnitureId, boolean onlyAvailable) {
        // 查规格组
        List<SpecGroup> groups = specGroupMapper.selectList(
                new LambdaQueryWrapper<SpecGroup>()
                        .eq(SpecGroup::getFurnitureId, furnitureId)
                        .orderByAsc(SpecGroup::getSort));

        if (groups.isEmpty()) {
            List<Sku> skus = skuMapper.selectList(
                    new LambdaQueryWrapper<Sku>()
                            .eq(Sku::getFurnitureId, furnitureId)
                            .eq(onlyAvailable, Sku::getStatus, 1)
                            .gt(onlyAvailable, Sku::getStock, 0));
            List<FurnitureSpecVO.SkuVO> skuVOs = new ArrayList<>();
            for (Sku s : skus) {
                FurnitureSpecVO.SkuVO skuVO = new FurnitureSpecVO.SkuVO();
                skuVO.setId(s.getId());
                skuVO.setPrice(s.getPrice());
                skuVO.setStock(s.getStock());
                skuVO.setSkuImage(s.getSkuImage());
                skuVO.setStatus(s.getStatus());
                skuVO.setSpecMap(Collections.emptyMap());
                skuVO.setSpecText("");
                skuVOs.add(skuVO);
            }
            FurnitureSpecVO vo = new FurnitureSpecVO();
            vo.setSpecGroups(Collections.emptyList());
            vo.setSkuList(skuVOs);
            return Result.ok(vo);
        }

        List<Long> groupIds = groups.stream().map(SpecGroup::getId).collect(Collectors.toList());
        List<SpecValue> allValues = specValueMapper.selectList(
                new LambdaQueryWrapper<SpecValue>()
                        .in(SpecValue::getSpecGroupId, groupIds)
                        .orderByAsc(SpecValue::getSort));
        Map<Long, List<SpecValue>> valuesByGroup = allValues.stream()
                .collect(Collectors.groupingBy(SpecValue::getSpecGroupId));

        List<FurnitureSpecVO.SpecGroupVO> groupVOs = new ArrayList<>();
        for (SpecGroup g : groups) {
            FurnitureSpecVO.SpecGroupVO gvo = new FurnitureSpecVO.SpecGroupVO();
            gvo.setId(g.getId());
            gvo.setGroupName(g.getGroupName());
            gvo.setSort(g.getSort());
            List<SpecValue> vals = valuesByGroup.getOrDefault(g.getId(), Collections.emptyList());
            List<FurnitureSpecVO.SpecValueVO> valueVOs = new ArrayList<>();
            for (SpecValue v : vals) {
                FurnitureSpecVO.SpecValueVO vvo = new FurnitureSpecVO.SpecValueVO();
                vvo.setId(v.getId());
                vvo.setValueName(v.getValueName());
                vvo.setValueImage(v.getValueImage());
                vvo.setSort(v.getSort());
                valueVOs.add(vvo);
            }
            gvo.setValues(valueVOs);
            groupVOs.add(gvo);
        }

        LambdaQueryWrapper<Sku> skuWrapper = new LambdaQueryWrapper<Sku>()
                .eq(Sku::getFurnitureId, furnitureId);
        if (onlyAvailable) {
            skuWrapper.eq(Sku::getStatus, 1).gt(Sku::getStock, 0);
        }
        List<Sku> skus = skuMapper.selectList(skuWrapper);

        List<Long> skuIds = skus.stream().map(Sku::getId).collect(Collectors.toList());
        Map<Long, List<SkuSpec>> skuSpecMap;
        Map<Long, Map<String, String>> skuSpecTextMap = new HashMap<>();
        if (!skuIds.isEmpty()) {
            List<SkuSpec> allSkuSpecs = skuSpecMapper.selectList(
                    new LambdaQueryWrapper<SkuSpec>().in(SkuSpec::getSkuId, skuIds));
            skuSpecMap = allSkuSpecs.stream().collect(Collectors.groupingBy(SkuSpec::getSkuId));

            Map<Long, String> groupNameMap = groups.stream()
                    .collect(Collectors.toMap(SpecGroup::getId, SpecGroup::getGroupName));
            Map<Long, String> valueNameMap = allValues.stream()
                    .collect(Collectors.toMap(SpecValue::getId, SpecValue::getValueName));

            for (Map.Entry<Long, List<SkuSpec>> entry : skuSpecMap.entrySet()) {
                Long skuId = entry.getKey();
                Map<String, String> specMap = new LinkedHashMap<>();
                for (SkuSpec ss : entry.getValue()) {
                    String gName = groupNameMap.get(ss.getSpecGroupId());
                    String vName = valueNameMap.get(ss.getSpecValueId());
                    if (gName != null && vName != null) {
                        specMap.put(gName, vName);
                    }
                }
                skuSpecTextMap.put(skuId, specMap);
            }
        }

        List<FurnitureSpecVO.SkuVO> skuVOs = new ArrayList<>();
        for (Sku s : skus) {
            FurnitureSpecVO.SkuVO svo = new FurnitureSpecVO.SkuVO();
            svo.setId(s.getId());
            svo.setPrice(s.getPrice());
            svo.setStock(s.getStock());
            svo.setSkuImage(s.getSkuImage());
            svo.setStatus(s.getStatus());
            Map<String, String> specMap = skuSpecTextMap.getOrDefault(s.getId(), Collections.emptyMap());
            svo.setSpecMap(specMap);
            if (!specMap.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, String> e : specMap.entrySet()) {
                    if (!sb.isEmpty()) sb.append(",");
                    sb.append(e.getKey()).append(":").append(e.getValue());
                }
                svo.setSpecText(sb.toString());
            } else {
                svo.setSpecText("");
            }
            skuVOs.add(svo);
        }

        FurnitureSpecVO vo = new FurnitureSpecVO();
        vo.setSpecGroups(groupVOs);
        vo.setSkuList(skuVOs);
        return Result.ok(vo);
    }

    /**
     * 保存或更新商品的规格和SKU数据。
     * <p>
     * 采用先删后增的完整替换策略：先清除该商品已有的规格组、规格值、SKU及SKU-规格关联关系，
     * 再根据传入的DTO重新创建全部数据。如果传入的规格组或SKU列表为空，则创建一个默认SKU。
     * 支持两种关联方式：优先使用按规格组名称和规格值名称的精确匹配（specs字段），
     * 回退使用按旧ID映射的方式（specValueIds字段）。
     * 保存成功后调用刷新方法更新商品主表的价格和库存。
     * </p>
     *
     * @param dto 包含规格组列表和SKU列表的数据传输对象
     * @return 操作结果，成功返回ok
     */
    @Override
    @Transactional
    public Result saveSpecAndSku(FurnitureSpecDTO dto) {
        Long furnitureId = dto.getFurnitureId();
        if (furnitureId == null) {
            return Result.fail("商品ID不能为空");
        }
        Furniture furniture = furnitureMapper.selectById(furnitureId);
        if (furniture == null) {
            return Result.fail("商品不存在");
        }

        List<Sku> oldSkus = skuMapper.selectList(
                new LambdaQueryWrapper<Sku>().eq(Sku::getFurnitureId, furnitureId));
        List<Long> oldSkuIds = oldSkus.stream().map(Sku::getId).collect(Collectors.toList());
        if (!oldSkuIds.isEmpty()) {
            skuSpecMapper.delete(
                    new LambdaQueryWrapper<SkuSpec>().in(SkuSpec::getSkuId, oldSkuIds));
            skuMapper.delete(
                    new LambdaQueryWrapper<Sku>().eq(Sku::getFurnitureId, furnitureId));
        }
        List<SpecGroup> oldGroups = specGroupMapper.selectList(
                new LambdaQueryWrapper<SpecGroup>().eq(SpecGroup::getFurnitureId, furnitureId));
        List<Long> oldGroupIds = oldGroups.stream().map(SpecGroup::getId).collect(Collectors.toList());
        if (!oldGroupIds.isEmpty()) {
            specValueMapper.delete(
                    new LambdaQueryWrapper<SpecValue>().in(SpecValue::getSpecGroupId, oldGroupIds));
        }
        specGroupMapper.delete(
                new LambdaQueryWrapper<SpecGroup>().eq(SpecGroup::getFurnitureId, furnitureId));

        List<FurnitureSpecDTO.SpecGroupDTO> groups = dto.getSpecGroups();
        List<FurnitureSpecDTO.SkuDTO> skuDTOs = dto.getSkuList();

        if (groups == null || groups.isEmpty() || skuDTOs == null || skuDTOs.isEmpty()) {
            Sku defaultSku = new Sku();
            defaultSku.setFurnitureId(furnitureId);
            defaultSku.setPrice(furniture.getPrice());
            defaultSku.setStock(furniture.getStock() != null ? furniture.getStock() : 0);
            defaultSku.setStatus(1);
            defaultSku.setCreateTime(LocalDateTime.now());
            skuMapper.insert(defaultSku);
            return Result.ok();
        }

        // 旧ID → 新ID映射（兼容旧版前端不传specs的情况）
        Map<Long, Long> valueIdMap = new HashMap<>();
        // 名称 → 新ID映射（核心方案：按 groupName + valueName 精确匹配）
        Map<String, Map<String, Long>> nameGroupMap = new HashMap<>();
        Map<String, Long> nameGroupIdMap = new HashMap<>();

        for (FurnitureSpecDTO.SpecGroupDTO groupDTO : groups) {
            SpecGroup group = new SpecGroup();
            group.setFurnitureId(furnitureId);
            group.setGroupName(groupDTO.getGroupName());
            group.setSort(groupDTO.getSort() != null ? groupDTO.getSort() : 0);
            group.setCreateTime(LocalDateTime.now());
            specGroupMapper.insert(group);
            Long newGroupId = group.getId();
            nameGroupIdMap.put(groupDTO.getGroupName(), newGroupId);

            Map<String, Long> valueNameToId = new HashMap<>();
            if (groupDTO.getValues() != null) {
                for (FurnitureSpecDTO.SpecValueDTO valueDTO : groupDTO.getValues()) {
                    SpecValue value = new SpecValue();
                    value.setSpecGroupId(newGroupId);
                    value.setValueName(valueDTO.getValueName());
                    value.setValueImage(valueDTO.getValueImage());
                    value.setSort(valueDTO.getSort() != null ? valueDTO.getSort() : 0);
                    specValueMapper.insert(value);
                    valueNameToId.put(valueDTO.getValueName(), value.getId());
                    if (valueDTO.getId() != null) {
                        valueIdMap.put(valueDTO.getId(), value.getId());
                    }
                }
            }
            nameGroupMap.put(groupDTO.getGroupName(), valueNameToId);
        }

        for (FurnitureSpecDTO.SkuDTO skuDTO : skuDTOs) {
            Sku sku = new Sku();
            sku.setFurnitureId(furnitureId);
            sku.setPrice(skuDTO.getPrice());
            sku.setStock(skuDTO.getStock() != null ? skuDTO.getStock() : 0);
            sku.setSkuImage(skuDTO.getSkuImage());
            sku.setStatus(skuDTO.getStatus() != null ? skuDTO.getStatus() : 1);
            sku.setCreateTime(LocalDateTime.now());
            skuMapper.insert(sku);
            Long newSkuId = sku.getId();
            // 优先使用 specs（按名称精确匹配），回退到 specValueIds（按ID映射）
            List<FurnitureSpecDTO.SpecPair> specs = skuDTO.getSpecs();
            if (specs != null && !specs.isEmpty()) {
                for (FurnitureSpecDTO.SpecPair pair : specs) {
                    String gn = pair.getGroupName();
                    String vn = pair.getValueName();
                    if (StrUtil.isBlank(gn) || StrUtil.isBlank(vn)) continue;
                    Map<String, Long> valueMap = nameGroupMap.get(gn);
                    if (valueMap == null) continue;
                    Long valueId = valueMap.get(vn);
                    Long groupId = nameGroupIdMap.get(gn);
                    if (valueId == null || groupId == null) continue;
                    SkuSpec skuSpec = new SkuSpec();
                    skuSpec.setSkuId(newSkuId);
                    skuSpec.setSpecGroupId(groupId);
                    skuSpec.setSpecValueId(valueId);
                    skuSpecMapper.insert(skuSpec);
                }
            } else if (skuDTO.getSpecValueIds() != null) {
                List<Long> uniqueValueIds = skuDTO.getSpecValueIds().stream().distinct().collect(Collectors.toList());
                Map<Long, SpecValue> svMap = specValueMapper.selectByIds(uniqueValueIds).stream()
                        .collect(Collectors.toMap(SpecValue::getId, v -> v));
                for (Long tempValueId : skuDTO.getSpecValueIds()) {
                    Long realValueId = valueIdMap.getOrDefault(tempValueId, tempValueId);
                    SpecValue sv = svMap.get(realValueId);
                    if (sv == null) continue;
                    SkuSpec skuSpec = new SkuSpec();
                    skuSpec.setSkuId(newSkuId);
                    skuSpec.setSpecGroupId(sv.getSpecGroupId());
                    skuSpec.setSpecValueId(realValueId);
                    skuSpecMapper.insert(skuSpec);
                }
            }
        }

        refreshFurniturePriceAndStock(furnitureId);

        log.info("保存规格SKU成功: furnitureId={}, groups={}, skus={}", furnitureId,
                groups.size(), skuDTOs.size());
        return Result.ok();
    }


    /**
     * 刷新商品主表的价格和库存。
     * <p>
     * 查询该商品下所有SKU的最低售价和总库存量，更新到商品主表的价格和库存字段中。
     * 更新完成后清除该商品对应的Redis缓存，确保下次查询时获取到最新数据。
     * </p>
     *
     * @param furnitureId 商品（家具）ID
     */
    public void refreshFurniturePriceAndStock(Long furnitureId) {
        BigDecimal minPrice = skuMapper.minPriceByFurnitureId(furnitureId);
        int totalStock = skuMapper.sumStockByFurnitureId(furnitureId);

        LambdaUpdateWrapper<Furniture> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Furniture::getId, furnitureId);
        if (minPrice != null && minPrice.compareTo(BigDecimal.ZERO) > 0) {
            wrapper.set(Furniture::getPrice, minPrice);
        }
        wrapper.set(Furniture::getStock, totalStock);
        furnitureMapper.update(null, wrapper);

        stringRedisTemplate.delete(RedisConstants.CACHE_FURNITURE_KEY + furnitureId);
    }
}