package gcy.system.aspect;

import gcy.system.entity.dto.Result;
import gcy.system.entity.dto.UserDTO;
import gcy.system.entity.pojo.OperationLogPO;
import gcy.system.mapper.UserMapper;
import gcy.system.service.IOperationLogService;
import gcy.system.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.StringJoiner;

/**
 * 操作日志切面，自动拦截带有 {@link OperationLog} 注解的方法并记录操作日志。
 * 日志同时输出到控制台和持久化到数据库 operation_log 表。
 *
 * @author 郭名城
 * @date 2026-08-03
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class OperationLogAspect {

    private final IOperationLogService operationLogService;

    private final UserMapper userMapper;

    /**
     * 环绕通知，拦截带有 {@link OperationLog} 注解的方法。
     *
     * @param pjp   切点连接点，封装了被拦截方法的信息
     * @param opLog 操作日志注解，提供操作描述
     * @return 被拦截方法的原始返回值
     * @throws Throwable 被拦截方法可能抛出的异常
     */
    @Around("@annotation(opLog)")
    public Object around(ProceedingJoinPoint pjp, OperationLog opLog) throws Throwable {
        UserDTO user = UserHolder.getUser();
        String userName = user != null ? user.getUserName() : "匿名";
        Long userId = user != null ? user.getId() : null;

        // 未登录场景（登录/注册），尝试从请求体提取账号
        if (user == null) {
            userName = extractAccount(pjp);
        }

        // 提取关键参数（过滤掉 HttpServletRequest/Response、MultipartFile 等非业务参数）
        String params = extractParams(pjp);

        // 获取客户端 IP
        String ip = getClientIp();

        log.info("【操作日志】用户ID:{} | 用户名:{} | 操作:{} | 参数:{}", userId, userName, opLog.value(), params);

        long start = System.currentTimeMillis();
        Object result;
        String resultStatus;
        String resultMsg;
        try {
            result = pjp.proceed();
            long cost = System.currentTimeMillis() - start;

            resultStatus = "失败";
            resultMsg = "";
            if (result instanceof Result r) {
                resultStatus = Boolean.TRUE.equals(r.getSuccess()) ? "成功" : "失败";
                resultMsg = r.getMsg() != null ? r.getMsg() : "";
            }

            log.info("【操作日志】用户ID:{} | 用户名:{} | 操作:{} | 耗时:{}ms | 结果:{} | 提示:{}",
                    userId, userName, opLog.value(), cost, resultStatus, resultMsg);

            // 登录/注册成功后，通过账号反查用户真实信息
            if (user == null && "成功".equals(resultStatus) && !"匿名".equals(userName)) {
                gcy.system.entity.pojo.User dbUser = lookupUser(userName);
                if (dbUser != null) {
                    userId = dbUser.getId();
                    userName = dbUser.getUserName();
                }
            }

            // 持久化到数据库
            saveToDatabase(userId, userName, opLog.value(), params, (int) cost, resultStatus, resultMsg, ip);

            return result;
        } catch (Throwable e) {
            long cost = System.currentTimeMillis() - start;
            resultStatus = "失败";
            resultMsg = e.getMessage() != null ? e.getMessage() : "";

            log.info("【操作日志】用户ID:{} | 用户名:{} | 操作:{} | 耗时:{}ms | 结果:{} | 提示:{}",
                    userId, userName, opLog.value(), cost, resultStatus, resultMsg);

            // 持久化失败日志
            saveToDatabase(userId, userName, opLog.value(), params, (int) cost, resultStatus, resultMsg, ip);

            throw e;
        }
    }

    /**
     * 将操作日志持久化到数据库。
     */
    private void saveToDatabase(Long userId, String userName, String operation,
                                String params, int duration, String resultStatus,
                                String resultMsg, String ip) {
        try {
            OperationLogPO entity = new OperationLogPO();
            entity.setUserId(userId);
            entity.setUserName(userName);
            entity.setOperation(operation);
            entity.setParams(params != null && params.length() > 1000 ? params.substring(0, 1000) : params);
            entity.setDuration(duration);
            entity.setResultStatus(resultStatus);
            entity.setResultMsg(resultMsg != null && resultMsg.length() > 500 ? resultMsg.substring(0, 500) : resultMsg);
            entity.setIp(ip);
            entity.setCreateTime(LocalDateTime.now());
            operationLogService.save(entity);
        } catch (Exception e) {
            log.error("保存操作日志到数据库失败: operation={}", operation, e);
        }
    }

    /**
     * 获取客户端 IP 地址。
     */
    private String getClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return "-";
            HttpServletRequest request = attrs.getRequest();
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("X-Real-IP");
            }
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }
            return ip;
        } catch (Exception e) {
            return "-";
        }
    }

    /**
     * 提取方法的关键业务参数，过滤掉框架对象和文件对象，并对密码类字段脱敏。
     */
    private String extractParams(ProceedingJoinPoint pjp) {
        Object[] args = pjp.getArgs();
        if (args == null || args.length == 0) {
            return "无";
        }

        MethodSignature signature = (MethodSignature) pjp.getSignature();
        String[] paramNames = signature.getParameterNames();
        StringJoiner sj = new StringJoiner(", ");

        for (int i = 0; i < args.length; i++) {
            if (args[i] == null) continue;
            // 过滤非业务参数
            if (args[i] instanceof HttpServletRequest
                    || args[i] instanceof HttpServletResponse
                    || args[i] instanceof MultipartFile) {
                continue;
            }
            String name = paramNames != null && i < paramNames.length ? paramNames[i] : "arg" + i;
            // 参数名为密码类：整个值脱敏
            if (isSensitiveParamName(name)) {
                sj.add(name + "=***");
                continue;
            }
            sj.add(name + "=" + sanitize(String.valueOf(args[i])));
        }

        return sj.length() > 0 ? sj.toString() : "无";
    }

    /**
     * 判断参数名是否为敏感字段（整个值直接脱敏）。
     * <p>
     * 除密码类外，还需覆盖验证码类字段：邮箱/短信登录与注册的 6 位验证码参数名为 {@code code}，
     * 此前未被识别，会原样写入 operation_log.params，翻阅日志即可拿到他人验证码。
     */
    private boolean isSensitiveParamName(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.contains("password") || lower.contains("passwd") || lower.contains("pwd")
                || lower.contains("code") || lower.contains("captcha") || lower.contains("verifycode");
    }

    /**
     * 对字符串中的敏感字段值做脱敏。
     * 兼容 Lombok toString 格式（newPassword=xxx）与 JSON 格式（"newPassword":"xxx"）。
     */
    private String sanitize(String text) {
        if (text == null) return "";
        return text.replaceAll("(?i)(\\b[a-z]*(?:password|passwd|pwd|code|captcha|verifycode)[a-z]*\\s*[=:]\\s*)([^,\"}\\s]+)", "$1***");
    }

    /**
     * 从未登录请求体中提取账号信息（用于登录/注册等操作）。
     * 通过反射查找 DTO 中的 account、email、userName 等字段。
     */
    private String extractAccount(ProceedingJoinPoint pjp) {
        Object[] args = pjp.getArgs();
        if (args == null) return "匿名";
        for (Object arg : args) {
            if (arg == null) continue;
            if (arg instanceof HttpServletRequest || arg instanceof HttpServletResponse || arg instanceof MultipartFile) {
                continue;
            }
            // 按优先级尝试获取账号字段
            String account = getFieldValue(arg, "account");
            if (account != null && !account.isEmpty()) return account;
            account = getFieldValue(arg, "userName");
            if (account != null && !account.isEmpty()) return account;
            account = getFieldValue(arg, "phone");
            if (account != null && !account.isEmpty()) return account;
            account = getFieldValue(arg, "email");
            if (account != null && !account.isEmpty()) return account;
        }
        return "匿名";
    }

    /**
     * 通过反射获取对象的 getter 方法返回值。
     */
    private String getFieldValue(Object obj, String fieldName) {
        try {
            String getter = "get" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
            Method method = obj.getClass().getMethod(getter);
            Object value = method.invoke(obj);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 登录/注册成功后，通过账号（邮箱或手机号）反查用户记录，获取真实 userName 和 userId。
     */
    private gcy.system.entity.pojo.User lookupUser(String account) {
        try {
            return userMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<gcy.system.entity.pojo.User>()
                            .eq(gcy.system.entity.pojo.User::getEmail, account)
                            .or()
                            .eq(gcy.system.entity.pojo.User::getPhone, account));
        } catch (Exception e) {
            log.warn("反查用户失败: account={}", account, e);
            return null;
        }
    }
}