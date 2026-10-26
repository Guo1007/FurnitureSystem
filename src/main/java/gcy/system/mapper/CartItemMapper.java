package gcy.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import gcy.system.entity.pojo.CartItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 购物车项 Mapper。
 *
 * @author 郭名城
 * @date 2026-10-23
 */
@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {
}
