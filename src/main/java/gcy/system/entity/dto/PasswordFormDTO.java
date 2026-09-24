package gcy.system.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 密码修改表单 DTO，用于承载用户修改密码时提交的请求数据，
 * 包含旧密码、新密码及确认密码三个字段。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PasswordFormDTO {

    /**
     * 用户当前使用的旧密码，用于校验身份。
     */
    @Schema(description = "旧密码")
    private String oldPassword;

    /**
     * 用户要设置的新密码。
     */
    @Schema(description = "新密码")
    @NotBlank(message = "新密码不能为空")
    private String newPassword;

    /**
     * 新密码的二次确认输入，用于校验两次输入是否一致。
     */
    @Schema(description = "确认密码")
    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;

}
