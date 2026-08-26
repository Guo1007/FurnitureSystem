package gcy.system.entity.vo.admin;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 昵称审核列表视图对象（VO），用于展示昵称审核记录及其关联的审核用户信息。
 *
 * @author 郭名城
 * @date 2026-08-26
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NicknameReviewVO {

    /**
     * 昵称审核记录主键ID
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
     * 待审核的新昵称
     */
    private String pendingNickname;

    /**
     * 审核状态（0-已通过，1-待审核，2-已拒绝，3-待复审）
     */
    private Integer reviewStatus;

    /**
     * AI审核拒绝原因
     */
    private String aiRejectReason;

    /**
     * 人工审核拒绝原因
     */
    private String manualRejectReason;
}