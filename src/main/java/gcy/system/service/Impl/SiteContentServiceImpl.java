package gcy.system.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import gcy.system.entity.dto.Result;
import gcy.system.entity.pojo.SiteContent;
import gcy.system.mapper.SiteContentMapper;
import gcy.system.service.ISiteContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 站点内容服务实现类，封装站点内容的查询与管理业务逻辑。
 *
 * @author 郭名城
 * @date 2026-08-12
 */
@Service
@RequiredArgsConstructor
public class SiteContentServiceImpl implements ISiteContentService {

    private final SiteContentMapper siteContentMapper;

    @Override
    public Result getActiveSiteContentGrouped() {
        List<SiteContent> list = siteContentMapper.selectList(
                new LambdaQueryWrapper<SiteContent>()
                        .eq(SiteContent::getIsActive, 1)
                        .orderByAsc(SiteContent::getSortOrder)
        );

        // 按 sectionGroup 分组并保持插入顺序；需先滤除 null 分组，否则 groupingBy 遇 null key 会抛 NPE
        Map<String, List<SiteContent>> grouped = list.stream()
                .filter(s -> s.getSectionGroup() != null)
                .collect(Collectors.groupingBy(
                        SiteContent::getSectionGroup,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return Result.ok(grouped);
    }

    @Override
    public Result listAll() {
        List<SiteContent> list = siteContentMapper.selectList(
                new LambdaQueryWrapper<SiteContent>()
                        .orderByAsc(SiteContent::getSectionGroup)
                        .orderByAsc(SiteContent::getSortOrder));
        return Result.ok(list);
    }

    @Override
    public Result saveOrUpdateContent(SiteContent form) {
        if (form.getSectionKey() == null || form.getSectionKey().isBlank()) {
            return Result.fail("sectionKey 不能为空");
        }
        SiteContent exist = siteContentMapper.selectOne(
                new LambdaQueryWrapper<SiteContent>().eq(SiteContent::getSectionKey, form.getSectionKey()));
        form.setUpdatedAt(LocalDateTime.now());
        if (exist != null) {
            form.setId(exist.getId());
            siteContentMapper.updateById(form);
        } else {
            siteContentMapper.insert(form);
        }
        return Result.ok();
    }

    @Override
    public Result toggleStatus(Long id) {
        SiteContent sc = siteContentMapper.selectById(id);
        if (sc == null) return Result.fail("记录不存在");
        sc.setIsActive(Integer.valueOf(1).equals(sc.getIsActive()) ? 0 : 1);
        sc.setUpdatedAt(LocalDateTime.now());
        siteContentMapper.updateById(sc);
        return Result.ok(sc.getIsActive());
    }

    @Override
    public Result deleteById(Long id) {
        siteContentMapper.deleteById(id);
        return Result.ok();
    }
}