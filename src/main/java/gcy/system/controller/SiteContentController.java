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
 * 站点内容公开查询接口。
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
     * 获取所有启用的站点内容，按 sectionGroup 分组。
     */
    @Operation(summary = "获取所有启用的站点内容")
    @Anonymous
    @GetMapping("/site-content")
    public Result getSiteContent() {
        return siteContentService.getActiveSiteContentGrouped();
    }
}