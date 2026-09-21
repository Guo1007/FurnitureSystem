package gcy.system.service.admin.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import gcy.system.entity.dto.Result;
import gcy.system.entity.dto.admin.AdminCouponFormDTO;
import gcy.system.entity.pojo.Coupon;
import gcy.system.mapper.CouponMapper;
import gcy.system.service.admin.ICouponManageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static gcy.system.utils.RedisConstants.COUPON_COUNT_KEY;

/**
 * 管理端优惠券服务实现。
 *
 * @author 郭名城
 * @date 2026-09-20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponManageServiceImpl implements ICouponManageService {

    private final CouponMapper couponMapper;

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public Result page(Integer current, Integer size, String name) {
        Page<Coupon> page = couponMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<Coupon>()
                        .like(name != null && !name.isEmpty(), Coupon::getName, name)
                        .orderByDesc(Coupon::getId));
        return Result.ok(page.getRecords(), page.getTotal());
    }

    @Override
    public Result add(AdminCouponFormDTO dto) {
        Coupon c = new Coupon();
        copyForm(dto, c);
        c.setCreateTime(LocalDateTime.now());
        c.setUpdateTime(LocalDateTime.now());
        couponMapper.insert(c);
        // 初始化领取计数（不存在则不覆盖）
        stringRedisTemplate.opsForValue().setIfAbsent(COUPON_COUNT_KEY + c.getId(), "0");
        return Result.okMsg("新增成功");
    }

    @Override
    public Result update(AdminCouponFormDTO dto) {
        Coupon exist = couponMapper.selectById(dto.getId());
        if (exist == null) {
            return Result.fail("优惠券不存在");
        }
        Coupon c = new Coupon();
        copyForm(dto, c);
        c.setCreateTime(exist.getCreateTime());
        c.setUpdateTime(LocalDateTime.now());
        couponMapper.updateById(c);
        stringRedisTemplate.opsForValue().setIfAbsent(COUPON_COUNT_KEY + c.getId(), "0");
        return Result.okMsg("修改成功");
    }

    @Override
    public Result delete(Long id) {
        Coupon exist = couponMapper.selectById(id);
        if (exist == null) {
            return Result.fail("优惠券不存在");
        }
        couponMapper.deleteById(id);
        return Result.okMsg("删除成功");
    }

    @Override
    public Result toggleStatus(Long id) {
        Coupon exist = couponMapper.selectById(id);
        if (exist == null) {
            return Result.fail("优惠券不存在");
        }
        int newStatus = exist.getStatus() != null && exist.getStatus() == 1 ? 0 : 1;
        couponMapper.update(null, new LambdaUpdateWrapper<Coupon>()
                .eq(Coupon::getId, id)
                .set(Coupon::getStatus, newStatus));
        return Result.okMsg(newStatus == 1 ? "已启用" : "已停用");
    }

    @Override
    public Result info(Long id) {
        return Result.ok(couponMapper.selectById(id));
    }

    private void copyForm(AdminCouponFormDTO dto, Coupon c) {
        c.setId(dto.getId());
        c.setName(dto.getName());
        c.setType(dto.getType());
        c.setMinThreshold(dto.getMinThreshold() == null ? java.math.BigDecimal.ZERO : dto.getMinThreshold());
        c.setAmount(dto.getAmount());
        c.setDiscount(dto.getDiscount());
        c.setCapAmount(dto.getCapAmount());
        c.setScope(dto.getScope() == null ? 0 : dto.getScope());
        c.setTypeId(dto.getTypeId());
        c.setTotalCount(dto.getTotalCount());
        c.setPerUserLimit(dto.getPerUserLimit() == null ? 1 : dto.getPerUserLimit());
        c.setClaimStart(dto.getClaimStart());
        c.setClaimEnd(dto.getClaimEnd());
        c.setValidType(dto.getValidType());
        c.setValidStart(dto.getValidStart());
        c.setValidEnd(dto.getValidEnd());
        c.setValidDays(dto.getValidDays());
        c.setTargetType(dto.getTargetType() == null ? 0 : dto.getTargetType());
        c.setTargetDays(dto.getTargetDays());
        c.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
    }
}