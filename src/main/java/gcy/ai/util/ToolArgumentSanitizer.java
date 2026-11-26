package gcy.ai.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 工具调用参数清洗器。
 * <p>
 * 大模型（尤其是 Qwen 系列）在生成 tool call 参数时，偶尔会输出 Python 风格的
 * {@code None}/{@code True}/{@code False}，或者把某个字段的值直接留空
 * （形如 {@code "budget": ,}）。这些内容不是合法 JSON，LangChain4j 在执行工具前
 * 会用 {@code ToolExecutionRequestUtil.argumentsAsMap()} 解析参数，解析失败会直接
 * 让整条流式响应报错。
 * </p>
 * <p>
 * 这里在参数进入记忆、进入工具执行之前做一次兜底：把上述非标准写法规范化成合法
 * JSON；实在修不好就降级为空对象 {@code {}}，让工具按缺省值执行并返回引导语，
 * 而不是让整个会话失败。
 * </p>
 * <p>
 * 只处理字符串外的 token，不会误伤 {@code "None"} 这类正常的字符串值。
 * </p>
 *
 * @author 郭名城
 */
@Slf4j
@Component
public class ToolArgumentSanitizer {

    /**
     * 校验清洗结果是否为合法 JSON 对象。
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 无法修复时使用的空参数。
     */
    private static final String EMPTY_OBJECT = "{}";

    /**
     * 日志中原始参数的最大长度，避免把超长参数整段打进日志。
     */
    private static final int LOG_LIMIT = 500;

    /**
     * 清洗工具调用参数。
     *
     * @param toolName      工具名，仅用于日志
     * @param rawArguments  模型给出的原始参数串
     * @return 合法 JSON 对象字符串；无法修复时返回 {@code {}}
     */
    public String sanitize(String toolName, String rawArguments) {
        if (rawArguments == null || rawArguments.isBlank()) {
            return EMPTY_OBJECT;
        }
        String cleaned = normalize(stripCodeFence(rawArguments.trim()));
        if (isJsonObject(cleaned)) {
            if (!cleaned.equals(rawArguments)) {
                log.warn("工具参数含非标准 JSON，已规范化: tool={}, raw={}, cleaned={}",
                        toolName, abbreviate(rawArguments), abbreviate(cleaned));
            }
            return cleaned;
        }
        log.warn("工具参数无法修复，已降级为空参数（工具将按缺省值执行）: tool={}, raw={}",
                toolName, abbreviate(rawArguments));
        return EMPTY_OBJECT;
    }

    /**
     * 逐字符扫描并规范化，字符串内部的字符原样保留。
     */
    private String normalize(String json) {
        StringBuilder out = new StringBuilder(json.length() + 16);
        boolean inString = false;
        boolean escaped = false;
        int n = json.length();
        for (int i = 0; i < n; i++) {
            char c = json.charAt(i);
            if (inString) {
                out.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                out.append(c);
                continue;
            }
            if (c == ':') {
                out.append(c);
                // 冒号后直接跟分隔符，说明模型漏了值：补 null
                int j = i + 1;
                while (j < n && Character.isWhitespace(json.charAt(j))) {
                    out.append(json.charAt(j));
                    j++;
                }
                if (j < n && (json.charAt(j) == ',' || json.charAt(j) == '}' || json.charAt(j) == ']')) {
                    out.append("null");
                }
                i = j - 1;
                continue;
            }
            if (c == ',') {
                // 丢弃对象/数组末尾多余的逗号
                int j = i + 1;
                while (j < n && Character.isWhitespace(json.charAt(j))) {
                    j++;
                }
                if (j < n && (json.charAt(j) == '}' || json.charAt(j) == ']')) {
                    continue;
                }
                out.append(c);
                continue;
            }
            if (matchesToken(json, i, "None")) {
                out.append("null");
                i += 3;
                continue;
            }
            if (matchesToken(json, i, "True")) {
                out.append("true");
                i += 3;
                continue;
            }
            if (matchesToken(json, i, "False")) {
                out.append("false");
                i += 4;
                continue;
            }
            out.append(c);
        }
        return out.toString().trim();
    }

    /**
     * 判断当前位置是否是一个独立的 Python 常量 token，避免误伤 fooNoneBar 之类的标识符。
     */
    private boolean matchesToken(String s, int i, String token) {
        if (!s.startsWith(token, i)) {
            return false;
        }
        int end = i + token.length();
        if (end < s.length() && isIdentifierChar(s.charAt(end))) {
            return false;
        }
        return i <= 0 || !isIdentifierChar(s.charAt(i - 1));
    }

    private boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    /**
     * 去掉模型偶尔夹带的 Markdown 代码围栏。
     */
    private String stripCodeFence(String s) {
        if (!s.startsWith("```")) {
            return s;
        }
        int firstLineEnd = s.indexOf('\n');
        if (firstLineEnd < 0) {
            return s;
        }
        String body = s.substring(firstLineEnd + 1);
        int close = body.lastIndexOf("```");
        if (close >= 0) {
            body = body.substring(0, close);
        }
        return body.trim();
    }

    /**
     * 用与 LangChain4j 相同的 Jackson 口径校验：必须是 JSON 对象。
     */
    private boolean isJsonObject(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }
        try {
            Object parsed = MAPPER.readValue(json, Map.class);
            return parsed != null;
        } catch (Exception e) {
            return false;
        }
    }

    private String abbreviate(String s) {
        if (s == null) {
            return "null";
        }
        return s.length() <= LOG_LIMIT ? s : s.substring(0, LOG_LIMIT) + "...";
    }
}