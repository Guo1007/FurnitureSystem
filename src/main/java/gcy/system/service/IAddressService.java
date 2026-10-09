package gcy.system.service;

import gcy.system.entity.dto.Result;
import gcy.system.entity.pojo.UserAddress;

/**
 * 用户地址管理服务接口。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
public interface IAddressService {

    /**
     * 根据用户ID获取该用户的所有收货地址列表。
     */
    Result getAddressList(Long userId);

    /**
     * 保存或更新收货地址；已存在则更新，否则新增。
     */
    Result saveAddress(UserAddress addr, Long userId);

    /**
     * 根据地址ID删除指定收货地址。
     */
    Result deleteAddress(Long id);

    /**
     * 将指定地址设为默认地址；该用户原有默认地址将被取消。
     */
    Result setDefaultAddress(Long id, Long userId);
}
