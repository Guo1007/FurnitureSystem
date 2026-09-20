package gcy.system.service.admin;

import gcy.system.entity.dto.Result;
import gcy.system.entity.dto.admin.AdminCouponFormDTO;

/**
 * 管理端优惠券服务接口。
 *
 * @author 郭名城
 * @date 2026-09-20
 */
public interface ICouponManageService {

    Result page(Integer current, Integer size, String name);

    Result add(AdminCouponFormDTO dto);

    Result update(AdminCouponFormDTO dto);

    Result delete(Long id);

    Result toggleStatus(Long id);

    Result info(Long id);
}