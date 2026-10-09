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
 * 用户收货地址控制器，操作均基于当前登录用户。
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
     * 查询当前用户的收货地址列表。
     */
    @Operation(summary = "查询当前用户的收货地址列表")
    @GetMapping("/list")
    public Result list() {
        Long userId = UserHolder.getUser().getId();
        return addressService.getAddressList(userId);
    }

    /**
     * 新增或更新收货地址，地址ID不存在则新增，存在则更新。
     */
    @Operation(summary = "新增或更新收货地址")
    @PostMapping("/save")
    public Result save(@Parameter(description = "请求体") @Valid @RequestBody UserAddress addr) {
        Long userId = UserHolder.getUser().getId();
        return addressService.saveAddress(addr, userId);
    }

    /**
     * 根据地址ID删除收货地址。
     */
    @Operation(summary = "删除收货地址")
    @DeleteMapping("/delete/{id}")
    public Result delete(@Parameter(description = "地址ID") @PathVariable Long id) {
        return addressService.deleteAddress(id);
    }

    /**
     * 将指定地址设为默认，并取消该用户其他地址的默认状态。
     */
    @Operation(summary = "设为默认收货地址")
    @PutMapping("/default/{id}")
    public Result setDefault(@Parameter(description = "地址ID") @PathVariable Long id) {
        Long userId = UserHolder.getUser().getId();
        return addressService.setDefaultAddress(id, userId);
    }
}
