package gcy.system.entity.vo.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 头像审核列表视图对象（VO），用于展示头像审核记录及其关联的审核用户信息。
 *
 * @author 郭名城
 * @date 2026-08-26
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IconReviewVO {

    /**
     * 头像审核记录主键ID
     */
    private Long logId;

    /**
     * 待审核的操作用户ID
     */
    private Long userId;

    /**
     * 当前正式昵称
     */
    private String userName;

    /**
     * 待审核的新头像URL
     */
    private String pendingIcon;

    /**
     * 审核状态（0-已通过，1-待审核，2-已拒绝）
     */
    private Integer reviewStatus;
}