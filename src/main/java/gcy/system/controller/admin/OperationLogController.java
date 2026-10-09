package gcy.system.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import gcy.system.entity.dto.Result;
import gcy.system.entity.pojo.OperationLogPO;
import gcy.system.service.IOperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 操作日志管理控制器。
 * 深度分页优化：使用 id 阈值子查询替代 OFFSET，避免大偏移量全表扫描。
 *
 * @author 郭名城
 * @date 2026-08-14
 */
@Tag(name = "操作日志管理", description = "操作日志查询接口")
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/operation-logs")
public class OperationLogController {

    private final IOperationLogService operationLogService;

    /**
     * 分页查询操作日志，支持按用户、操作、结果、时间范围筛选。
     */
    @Operation(summary = "分页查询操作日志")
    @GetMapping
    public Result list(
            @Parameter(description = "页码") @RequestParam(defaultValue = "1") Long page,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "10") Long size,
            @Parameter(description = "操作用户名") @RequestParam(required = false) String userName,
            @Parameter(description = "操作描述") @RequestParam(required = false) String operation,
            @Parameter(description = "结果状态") @RequestParam(required = false) String resultStatus,
            @Parameter(description = "开始时间") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @Parameter(description = "结束时间") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {

        LambdaQueryWrapper<OperationLogPO> countWrapper = buildFilter(userName, operation, resultStatus, startTime, endTime);
        long total = operationLogService.count(countWrapper);

        // 深度分页：先取偏移位置的 id，再以 id 为阈值查数据
        long offset = (page - 1) * size;
        LambdaQueryWrapper<OperationLogPO> dataWrapper = buildFilter(userName, operation, resultStatus, startTime, endTime);

        if (offset > 0) {
            // 子查询定位第 offset 条记录的 id，走主键索引
            LambdaQueryWrapper<OperationLogPO> idWrapper = buildFilter(userName, operation, resultStatus, startTime, endTime);
            idWrapper.select(OperationLogPO::getId);
            idWrapper.orderByDesc(OperationLogPO::getId);
            idWrapper.last("LIMIT 1 OFFSET " + offset);
            OperationLogPO threshold = operationLogService.getOne(idWrapper, false);
            if (threshold != null) {
                dataWrapper.le(OperationLogPO::getId, threshold.getId());
            }
        }
        dataWrapper.orderByDesc(OperationLogPO::getId);
        dataWrapper.last("LIMIT " + size);

        List<OperationLogPO> records = operationLogService.list(dataWrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        return Result.ok(result);
    }

    /**
     * 构建筛选条件，供 count 和 data 查询复用。
     */
    private LambdaQueryWrapper<OperationLogPO> buildFilter(
            String userName, String operation, String resultStatus,
            LocalDateTime startTime, LocalDateTime endTime) {
        LambdaQueryWrapper<OperationLogPO> wrapper = new LambdaQueryWrapper<>();
        if (userName != null && !userName.trim().isEmpty()) {
            wrapper.like(OperationLogPO::getUserName, userName.trim());
        }
        if (operation != null && !operation.trim().isEmpty()) {
            wrapper.like(OperationLogPO::getOperation, operation.trim());
        }
        if (resultStatus != null && !resultStatus.trim().isEmpty()) {
            wrapper.eq(OperationLogPO::getResultStatus, resultStatus.trim());
        }
        if (startTime != null) {
            wrapper.ge(OperationLogPO::getCreateTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(OperationLogPO::getCreateTime, endTime);
        }
        return wrapper;
    }
}