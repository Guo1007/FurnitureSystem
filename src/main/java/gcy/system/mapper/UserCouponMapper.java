package gcy.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import gcy.system.entity.pojo.UserCoupon;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户领取记录数据访问层接口。
 * <p>继承 MyBatis-Plus 的 BaseMapper，自动拥有基础 CRUD 能力。</p>
 *
 * @author 郭名城
 * @date 2026-09-20
 */
@Mapper
public interface UserCouponMapper extends BaseMapper<UserCoupon> {
}