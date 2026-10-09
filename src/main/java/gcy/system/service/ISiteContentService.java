package gcy.system.service;

import gcy.system.entity.dto.Result;
import gcy.system.entity.pojo.SiteContent;

/**
 * 站点内容（首页板块、公告等）服务接口。
 *
 * @author 郭名城
 * @date 2026-08-12
 */
public interface ISiteContentService {

    /**
     * 获取所有启用的站点内容，按 sectionGroup 分组归类。
     */
    Result getActiveSiteContentGrouped();

    /**
     * 查询所有站点内容，按板块分组及排序序号升序。
     */
    Result listAll();

    /**
     * 保存站点内容，按 sectionKey 判断新增或更新。
     */
    Result saveOrUpdateContent(SiteContent form);

    /**
     * 切换站点内容启用状态，返回切换后的状态值（0/1）。
     */
    Result toggleStatus(Long id);

    /**
     * 删除指定 ID 的站点内容记录。
     */
    Result deleteById(Long id);
}