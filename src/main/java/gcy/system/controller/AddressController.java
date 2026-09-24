package gcy.system.controller;

import gcy.system.entity.dto.Result;
import gcy.system.entity.pojo.UserAddress;
import gcy.system.service.IAddressService;
import gcy.system.utils.UserHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户收货地址控制器
 * <p>
 * 处理用户地址的增删改查操作，包括：查询地址列表、新增/修改地址、删除地址、设置默认地址。
 * 所有操作均基于当前登录用户进行权限控制。
 * </p>
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Tag(name = "收货地址", description = "收货地址相关接口")
@RestController
@RequestMapping("/address")
@RequiredArgsConstructor
public class AddressController {

    private final IAddressService addressService;

    /**
     * 查询当前用户的收货地址列表
     * <p>
     * 处理 GET /address/list 请求，从当前登录会话中获取用户ID，
     * 返回该用户的所有收货地址。
     * </p>
     *
     * @return 包含地址列表的操作结果，数据为当前用户的收货地址集合
     */
    @Operation(summary = "查询当前用户的收货地址列表")
    @GetMapping("/list")
    public Result list() {
        Long userId = UserHolder.getUser().getId();
        return addressService.getAddressList(userId);
    }

    /**
     * 新增或更新用户收货地址
     * <p>
     * 处理 POST /address/save 请求，接收JSON格式的地址信息，
     * 若地址ID不存在则新增，存在则更新。操作关联当前登录用户。
     * </p>
     *
     * @param addr 用户提交的收货地址信息，通过请求体（JSON）传入
     * @return 操作结果，包含保存后的地址信息
     */
    @Operation(summary = "新增或更新收货地址")
    @PostMapping("/save")
    public Result save(@Parameter(description = "请求体") @Valid @RequestBody UserAddress addr) {
        Long userId = UserHolder.getUser().getId();
        return addressService.saveAddress(addr, userId);
    }

    /**
     * 根据地址ID删除收货地址
     * <p>
     * 处理 DELETE /address/delete/{id} 请求，根据URL路径中的地址ID
     * 删除指定的收货地址记录。
     * </p>
     *
     * @param id 要删除的地址ID，通过URL路径变量传入
     * @return 操作结果，表示删除是否成功
     */
    @Operation(summary = "删除收货地址")
    @DeleteMapping("/delete/{id}")
    public Result delete(@Parameter(description = "地址ID") @PathVariable Long id) {
        return addressService.deleteAddress(id);
    }

    /**
     * 将指定地址设为当前用户的默认收货地址
     * <p>
     * 处理 PUT /address/default/{id} 请求，将指定ID的地址设为当前登录用户的
     * 默认收货地址，同时取消其他地址的默认状态。
     * </p>
     *
     * @param id 要设为默认的地址ID，通过URL路径变量传入
     * @return 操作结果，包含更新后的地址信息
     */
    @Operation(summary = "设为默认收货地址")
    @PutMapping("/default/{id}")
    public Result setDefault(@Parameter(description = "地址ID") @PathVariable Long id) {
        Long userId = UserHolder.getUser().getId();
        return addressService.setDefaultAddress(id, userId);
    }
}
