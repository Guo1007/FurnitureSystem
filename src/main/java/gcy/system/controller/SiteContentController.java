package gcy.system.controller;

import gcy.system.entity.dto.Result;
import gcy.system.security.Anonymous;
import gcy.system.service.ISiteContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站点内容控制器
 * <p>
 * 提供公开的站点内容查询接口，用于获取所有已启用的站点内容，
 * 并按分组归类返回，供前端展示使用。
 * </p>
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Tag(name = "站点内容", description = "站点内容相关接口")
@RestController
@RequiredArgsConstructor
public class SiteContentController {

    private final ISiteContentService siteContentService;

    /**
     * 获取所有启用的站点内容，按分组归类
     * <p>
     * 查询数据库中所有标记为启用（isActive = 1）的站点内容记录，
     * 按排序字段升序排列后，以 sectionGroup 为键进行分组，
     * 返回一个分组后的有序映射结构。
     * </p>
     *
     * @return 包含分组后的站点内容数据的结果对象，key 为分组名，value 为该分组下的内容列表
     */
    @Operation(summary = "获取所有启用的站点内容")
    @Anonymous
    @GetMapping("/site-content")
    public Result getSiteContent() {
        return siteContentService.getActiveSiteContentGrouped();
    }
}