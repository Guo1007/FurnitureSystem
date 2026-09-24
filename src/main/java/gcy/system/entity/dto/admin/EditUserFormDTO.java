package gcy.system.entity.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 编辑用户表单请求DTO，用于承载编辑用户信息时提交的表单数据。
 * 仅包含用户身份变更，不包含密码重置（密码重置使用独立的 {@link AdminResetPasswordDTO}）。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EditUserFormDTO {

    @Schema(description = "用户ID")
    @NotNull(message = "用户ID不能为空")
    private Long id;

    @Schema(description = "是否为管理员（1-是，0-否）")
    @NotNull(message = "管理员标识不能为空")
    private Integer isAdmin;
}