package gcy.system.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 重置密码表单请求DTO，承载用户重置密码时提交的表单数据。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordFormDTO {

    /**
     * 用户邮箱地址
     */
    @Schema(description = "用户邮箱地址")
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /**
     * 邮箱验证码
     */
    @Schema(description = "邮箱验证码")
    private String code;

    /**
     * 新密码
     */
    @Schema(description = "新密码")
    private String newPassword;

    /**
     * 确认新密码，需与newPassword一致
     */
    @Schema(description = "确认新密码")
    private String confirmPassword;

}
