package gcy.system.controller.admin;

import gcy.system.aspect.OperationLog;
import gcy.system.entity.dto.Result;
import gcy.system.entity.dto.admin.AdminCouponFormDTO;
import gcy.system.service.admin.ICouponManageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端优惠券控制器，挂在 /admin/coupons。
 *
 * @author 郭名城
 * @date 2026-09-20
 */
@Tag(name = "优惠券管理", description = "管理端优惠券管理相关接口")
@RestController
@Validated
@RequestMapping("/admin/coupons")
@RequiredArgsConstructor
public class CouponManageController {

    private final ICouponManageService couponManageService;

    @Operation(summary = "分页查询优惠券列表")
    @GetMapping("/list")
    public Result list(@Parameter(description = "当前页码") @RequestParam(defaultValue = "1") Integer current,
                       @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Integer size,
                       @Parameter(description = "券名称模糊搜索") @RequestParam(required = false) String name) {
        return couponManageService.page(current, size, name);
    }

    @OperationLog("新增优惠券")
    @Operation(summary = "新增优惠券")
    @PostMapping("/add")
    public Result add(@Parameter(description = "请求体") @Valid @RequestBody AdminCouponFormDTO dto) {
        return couponManageService.add(dto);
    }

    @OperationLog("编辑优惠券")
    @Operation(summary = "编辑优惠券")
    @PutMapping("/update")
    public Result update(@Parameter(description = "请求体") @Valid @RequestBody AdminCouponFormDTO dto) {
        return couponManageService.update(dto);
    }

    @OperationLog("删除优惠券")
    @Operation(summary = "删除优惠券")
    @DeleteMapping("/delete/{id}")
    public Result delete(@Parameter(description = "优惠券ID") @PathVariable Long id) {
        return couponManageService.delete(id);
    }

    @OperationLog("切换优惠券状态")
    @Operation(summary = "切换优惠券启用状态")
    @PutMapping("/toggle/{id}")
    public Result toggle(@Parameter(description = "优惠券ID") @PathVariable Long id) {
        return couponManageService.toggleStatus(id);
    }

    @Operation(summary = "获取优惠券详情")
    @GetMapping("/info/{id}")
    public Result info(@Parameter(description = "优惠券ID") @PathVariable Long id) {
        return couponManageService.info(id);
    }
}