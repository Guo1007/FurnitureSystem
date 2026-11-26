package gcy.ai.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ToolArgumentSanitizer} 单元测试。
 */
class ToolArgumentSanitizerTest {

    private final ToolArgumentSanitizer sanitizer = new ToolArgumentSanitizer();

    @Test
    void keepsValidJsonUnchanged() {
        String raw = "{\"scene\":\"卧室\",\"budget\":8000,\"avoidIds\":\"\"}";
        assertEquals(raw, sanitizer.sanitize("planFurnitureSet", raw));
    }

    @Test
    void replacesPythonNone() {
        String raw = "{\"scene\": \"卧室\", \"budget\": None, \"avoidIds\": \"\"}";
        assertEquals("{\"scene\": \"卧室\", \"budget\": null, \"avoidIds\": \"\"}",
                sanitizer.sanitize("planFurnitureSet", raw));
    }

    @Test
    void fillsMissingValue() {
        String raw = "{\"scene\": \"卧室\", \"budget\": ,     \"avoidIds\": \"\"}";
        assertEquals("{\"scene\": \"卧室\", \"budget\": null,     \"avoidIds\": \"\"}",
                sanitizer.sanitize("planFurnitureSet", raw));
    }

    @Test
    void removesTrailingComma() {
        String raw = "{\"scene\":\"卧室\",}";
        assertEquals("{\"scene\":\"卧室\"}", sanitizer.sanitize("planFurnitureSet", raw));
    }

    @Test
    void replacesPythonBooleans() {
        assertEquals("{\"a\": true, \"b\": false}",
                sanitizer.sanitize("t", "{\"a\": True, \"b\": False}"));
    }

    @Test
    void doesNotTouchQuotedLiterals() {
        String raw = "{\"name\": \"None\", \"note\": \"True story\"}";
        assertEquals(raw, sanitizer.sanitize("t", raw));
    }

    @Test
    void stripsCodeFence() {
        String raw = "```json\n{\"scene\": \"卧室\", \"budget\": None}\n```";
        assertEquals("{\"scene\": \"卧室\", \"budget\": null}", sanitizer.sanitize("t", raw));
    }

    @Test
    void fallsBackToEmptyObjectWhenUnfixable() {
        assertEquals("{}", sanitizer.sanitize("t", "{\"scene\": 卧室}"));
    }

    @Test
    void blankBecomesEmptyObject() {
        assertEquals("{}", sanitizer.sanitize("t", "  "));
        assertEquals("{}", sanitizer.sanitize("t", null));
    }
}