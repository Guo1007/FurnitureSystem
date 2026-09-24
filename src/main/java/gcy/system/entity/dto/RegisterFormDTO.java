package gcy.system.entity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户注册表单数据传输对象，用于接收前端提交的注册请求参数。
 *
 * @author 郭名城
 * @date 2026-07-30
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegisterFormDTO {

    @Schema(description = "用户注册邮箱地址")
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @Schema(description = "邮箱验证码")
    private String code;

    @Schema(description = "登录密码")
    private String password;

    @Schema(description = "确认密码，需与密码一致")
    private String confirmPassword;

}
