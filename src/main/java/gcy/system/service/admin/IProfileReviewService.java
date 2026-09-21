package gcy.system.service.admin;

import gcy.system.entity.dto.Result;

/**
 * 用户资料审核服务接口。
 *
 * @author 郭名城
 * @date 2026-08-18
 */
public interface IProfileReviewService {

    /**
     * 分页获取昵称审核列表。
     *
     * @param page   页码
     * @param size   每页条数
     * @param status 状态筛选：空=全部，逗号分隔
     * @return 分页数据
     */
    Result getNicknameList(Integer page, Integer size, String status);

    /**
     * 分页获取头像审核列表。
     *
     * @param page   页码
     * @param size   每页条数
     * @param status 状态筛选：空=全部，逗号分隔
     * @return 分页数据
     */
    Result getIconList(Integer page, Integer size, String status);

    /**
     * 审核通过昵称。
     */
    Result approveNickname(Long userId);

    /**
     * 拒绝昵称。
     */
    Result rejectNickname(Long userId, String reason);

    /**
     * 审核通过头像。
     */
    Result approveIcon(Long userId);

    /**
     * 拒绝头像。
     */
    Result rejectIcon(Long userId, String reason);

    /**
     * 获取待审核数量（昵称待审+待复审，头像待审）。
     */
    Result getPendingCount();

    /**
     * 获取昵称/头像审核各自的状态数量统计（全部/待审核/已通过/已拒绝/待复审）。
     *
     * @return 包含昵称、头像各状态数量的Result对象
     */
    Result getStatusCounts();
}