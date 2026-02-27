package com.jisoo.burp.unicodejson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageTransformerTest {
    private final MessageTransformer transformer = new MessageTransformer();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void decodeUnicodeEscapesShouldDecodeSimpleSequence() {
        assertEquals("中文", MessageTransformer.decodeChineseUnicodeEscapes("\\u4e2d\\u6587"));
    }

    @Test
    void decodeUnicodeEscapesShouldKeepEvenBackslashPrefix() {
        assertEquals("\\\\u4e2d", MessageTransformer.decodeChineseUnicodeEscapes("\\\\u4e2d"));
    }

    @Test
    void decodeUnicodeEscapesShouldKeepNonChineseUnicodeEscaped() {
        assertEquals("\\u003c", MessageTransformer.decodeChineseUnicodeEscapes("\\u003c"));
    }

    @Test
    void transformBodyShouldExpandNestedJsonString() throws Exception {
        String input = "{\"123\":\"{\\\"321\\\":\\\"\\\\u4f60\\\\u597d\\\"}\"}";
        String output = transformer.transformBodyForDisplay(input, "application/json");

        JsonNode root = objectMapper.readTree(output);
        assertTrue(root.get("123").isObject());
        assertEquals("你好", root.get("123").get("321").asText());
    }

    @Test
    void transformBodyShouldDecodeUnicodeForNonJson() {
        String input = "message=\\u4e2d\\u6587";
        assertEquals("message=中文", transformer.transformBodyForDisplay(input, "text/plain"));
    }

    @Test
    void decodeBodyByContentTypeShouldFixUtf8MojibakeForJson() throws Exception {
        String json = "{\"err_msg\":\"参数错误\"}";
        byte[] utf8Bytes = json.getBytes(StandardCharsets.UTF_8);
        String mojibake = new String(utf8Bytes, StandardCharsets.ISO_8859_1);

        String decoded = transformer.decodeBodyByContentType(
                utf8Bytes,
                mojibake,
                "application/json; charset=UTF-8");
        JsonNode root = objectMapper.readTree(decoded);
        assertEquals("参数错误", root.get("err_msg").asText());
    }
}
