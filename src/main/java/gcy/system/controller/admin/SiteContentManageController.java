package gcy.system.controller.admin;

import gcy.system.aspect.OperationLog;
import gcy.system.entity.dto.Result;
import gcy.system.entity.pojo.SiteContent;
import gcy.system.integration.OssService;
import gcy.system.service.ISiteContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 网站内容管理控制器。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Tag(name = "网站内容管理", description = "网站内容管理相关接口")
@RestController
@RequestMapping("/admin/site-content")
@RequiredArgsConstructor
public class SiteContentManageController {

    private final ISiteContentService siteContentService;

    private final OssService ossService;

    /**
     * 查询所有网站内容，按板块分组与排序序号升序。
     */
    @Operation(summary = "查询所有网站内容列表")
    @GetMapping
    public Result list() {
        return siteContentService.listAll();
    }

    /**
     * 保存网站内容：按 sectionKey 判断，存在则更新、不存在则新增，sectionKey 必填。
     */
    @OperationLog("保存网站内容")
    @Operation(summary = "保存网站内容")
    @PostMapping
    public Result save(@Parameter(description = "请求体") @Valid @RequestBody SiteContent form) {
        return siteContentService.saveOrUpdateContent(form);
    }

    /**
     * 切换网站内容的启用/禁用状态。
     */
    @OperationLog("切换网站内容状态")
    @Operation(summary = "切换网站内容启用状态")
    @PutMapping("/{id}/toggle")
    public Result toggle(@Parameter(description = "网站内容ID") @PathVariable Long id) {
        return siteContentService.toggleStatus(id);
    }

    /**
     * 删除指定 ID 的网站内容记录。
     */
    @OperationLog("删除网站内容")
    @Operation(summary = "删除网站内容")
    @DeleteMapping("/{id}")
    public Result delete(@Parameter(description = "网站内容ID") @PathVariable Long id) {
        return siteContentService.deleteById(id);
    }

    /**
     * 上传网站内容图片到 OSS。
     */
    @Operation(summary = "上传网站内容图片")
    @PostMapping("/upload")
    public Result uploadImage(@Parameter(description = "图片文件") @RequestParam("file") MultipartFile file) {
        String url = ossService.upload(file, "site");
        return Result.ok(url);
    }
}