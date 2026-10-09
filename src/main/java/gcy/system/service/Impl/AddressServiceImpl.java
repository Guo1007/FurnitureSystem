package gcy.system.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import gcy.system.entity.dto.Result;
import gcy.system.entity.dto.UserDTO;
import gcy.system.entity.pojo.UserAddress;
import gcy.system.mapper.UserAddressMapper;
import gcy.system.service.IAddressService;
import gcy.system.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户收货地址服务实现。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AddressServiceImpl extends ServiceImpl<UserAddressMapper, UserAddress> implements IAddressService {

    private final UserAddressMapper addressMapper;

    /**
     * 查询该用户的地址列表，默认地址置顶，其余按创建时间倒序。
     */
    @Override
    public Result getAddressList(Long userId) {
        LambdaQueryWrapper<UserAddress> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserAddress::getUserId, userId)
                .orderByDesc(UserAddress::getIsDefault)
                .orderByDesc(UserAddress::getCreateTime);
        return Result.ok(addressMapper.selectList(wrapper));
    }

    /**
     * 新增或更新收货地址。校验归属权限；新增时若为该用户首个地址，自动置为默认；
     * 若标记为默认，则清除该用户其余地址的默认标记。
     */
    @Override
    @Transactional
    public Result saveAddress(UserAddress addr, Long userId) {
        UserDTO currentUser = UserHolder.getUser();
        if (!currentUser.getId().equals(userId)) {
            return Result.fail("无权操作");
        }
        addr.setUserId(userId);
        if (addr.getIsDefault() == null) {
            addr.setIsDefault(0);
        }
        if (addr.getId() != null) {
            UserAddress existing = addressMapper.selectById(addr.getId());
            if (existing == null) {
                return Result.fail("地址不存在");
            }
            if (!existing.getUserId().equals(currentUser.getId())) {
                return Result.fail("无权修改该地址");
            }
            addressMapper.updateById(addr);
        } else {
            long count = addressMapper.selectCount(
                    new LambdaQueryWrapper<UserAddress>().eq(UserAddress::getUserId, userId)
            );
            if (count == 0) {
                addr.setIsDefault(1);
            }
            addressMapper.insert(addr);
        }
        if (addr.getIsDefault() == 1) {
            addressMapper.clearDefaultExcept(userId, addr.getId());
        }
        return Result.ok();
    }

    /**
     * 删除指定地址，仅限本人。
     */
    @Override
    public Result deleteAddress(Long id) {
        UserDTO currentUser = UserHolder.getUser();
        UserAddress addr = addressMapper.selectById(id);
        if (addr == null) {
            return Result.fail("地址不存在");
        }
        if (!addr.getUserId().equals(currentUser.getId())) {
            return Result.fail("无权删除该地址");
        }
        addressMapper.deleteById(id);
        return Result.ok();
    }

    /**
     * 设为默认地址。先清除该用户其余地址的默认标记，再置当前地址为默认。
     */
    @Override
    @Transactional
    public Result setDefaultAddress(Long id, Long userId) {
        UserDTO currentUser = UserHolder.getUser();
        if (!currentUser.getId().equals(userId)) {
            return Result.fail("无权操作");
        }
        UserAddress addr = addressMapper.selectById(id);
        if (addr == null || !addr.getUserId().equals(currentUser.getId())) {
            return Result.fail("地址不存在或无权操作");
        }
        addressMapper.clearDefault(userId);
        UserAddress updateAddr = new UserAddress();
        updateAddr.setId(id);
        updateAddr.setIsDefault(1);
        addressMapper.updateById(updateAddr);
        return Result.ok();
    }
}
