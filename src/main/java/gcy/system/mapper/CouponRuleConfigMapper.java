package gcy.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import gcy.system.entity.pojo.CouponRuleConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 优惠券叠加规则配置数据访问接口。
 *
 * @author 郭名城
 * @date 2026-09-30
 */
@Mapper
public interface CouponRuleConfigMapper extends BaseMapper<CouponRuleConfig> {
}
