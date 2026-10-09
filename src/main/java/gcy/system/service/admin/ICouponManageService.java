package gcy.system.service.admin;

import gcy.system.entity.dto.CouponGrantDTO;
import gcy.system.entity.dto.Result;
import gcy.system.entity.dto.admin.AdminCouponFormDTO;

/**
 * 管理端优惠券服务接口。
 *
 * @author 郭名城
 * @date 2026-09-20
 */
public interface ICouponManageService {

    /**
     * 分页查询券模板。
     *
     * @param issueType 发放方式过滤：1-公开领取，2-定向发放；null 返回全部。
     *                  「定向发放」弹窗要列出可发的券，靠它筛，避免前端拉全量再过滤。
     */
    Result page(Integer current, Integer size, String name, Integer issueType);

    Result add(AdminCouponFormDTO dto);

    Result update(AdminCouponFormDTO dto);

    Result delete(Long id);

    Result toggleStatus(Long id);

    Result info(Long id);

    /**
     * 定向发放优惠券给指定用户（补偿 / 关怀 / 通用），可同时发站内通知与邮件。
     * <p>
     * <b>只接受「定向发放」类型的券</b>（{@code issue_type=2}）：公开领取券的发放总量与
     * 每人限领由 Redis 两个计数器维护，绕过 {@code claim()} 直接发券不增加计数，会导致
     * 后续公开领取超发；定向券不进公开领取流程，无此问题。
     * </p>
     *
     * @return 成功时返回「已向 N 位用户各发放 M 张」的提示
     */
    Result grantCoupons(CouponGrantDTO dto);
}