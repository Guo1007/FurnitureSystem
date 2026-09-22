package gcy.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import gcy.system.entity.pojo.Payment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付流水数据访问层接口，提供支付记录的基础数据库操作。
 *
 * @author 郭名城
 * @date 2026-09-22
 */
@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {

}