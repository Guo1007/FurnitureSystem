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
     * 查询商品全部规格与SKU，不区分可用状态；规格为空时仅返回SKU基本信息。
     */
    @Override
    public Result getSpecAndSkuByFurnitureId(Long furnitureId) {
        return buildSpecVO(furnitureId, false);
    }

    /**
     * 查询商品可售规格与SKU，仅返回上架且库存大于零的SKU。
     */
    @Override
    public Result getAvailableSpecAndSku(Long furnitureId) {
        return buildSpecVO(furnitureId, true);
    }

    /**
     * 构建规格视图 VO；规格组为空时仅返回 SKU 基本信息。
     *
     * @param onlyAvailable 为 true 时仅保留上架且库存大于零的 SKU
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
     * 保存商品的规格与SKU，采用先删后增的整表替换：清空该商品原有规格组/规格值/SKU/关联后按 DTO 重建。
     * 规格或SKU为空时创建一个默认SKU；关联优先按 groupName+valueName 精确匹配(specs)，回退按旧ID映射(specValueIds)。
     * 保存后刷新商品主表价格与库存。
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
        // 名称 → 新ID映射（按 groupName + valueName 精确匹配）
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
     * 将商品主表价格/库存刷新为该商品下SKU的最低售价/总库存，并清除对应 Redis 缓存。
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