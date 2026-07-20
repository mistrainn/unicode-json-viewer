package com.jisoo.burp.unicodejson;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Locale;
import java.util.Iterator;
import java.util.Map;

final class MessageTransformer {
    private static final String CRLF_CRLF = "\r\n\r\n";
    private static final String LF_LF = "\n\n";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ObjectWriter prettyWriter = objectMapper.writer(prettyPrinter());

    String renderRequest(HttpRequest request) {
        if (request == null) {
            return "";
        }

        MessageEnvelope envelope = splitHeadAndBody(request.toString());
        String transformedBody = transformBodyForDisplay(
                request.body().getBytes(),
                request.bodyToString(),
                request.headerValue("Content-Type"));
        return renderEnvelope(envelope, transformedBody);
    }

    String renderResponse(HttpResponse response) {
        if (response == null) {
            return "";
        }

        MessageEnvelope envelope = splitHeadAndBody(response.toString());
        String transformedBody = transformBodyForDisplay(
                response.body().getBytes(),
                response.bodyToString(),
                response.headerValue("Content-Type"));
        return renderEnvelope(envelope, transformedBody);
    }

    String transformBodyForDisplay(byte[] bodyBytes, String bodyToString, String contentType) {
        String safeBody = decodeBodyByContentType(bodyBytes, bodyToString, contentType);
        return transformBodyForDisplay(safeBody, contentType);
    }

    String transformBodyForDisplay(String body, String contentType) {
        String safeBody = body == null ? "" : body;
        if (safeBody.isEmpty()) {
            return safeBody;
        }

        if (looksLikeJson(contentType, safeBody)) {
            JsonNode topLevel = parseJson(safeBody);
            if (topLevel == null) {
                topLevel = parseJson(decodeChineseUnicodeEscapes(safeBody));
            }

            if (topLevel != null) {
                try {
                    JsonNode normalized = normalize(topLevel);
                    return prettyWriter.writeValueAsString(normalized);
                } catch (JsonProcessingException ignored) {
                    return decodeChineseUnicodeEscapes(safeBody);
                }
            }
        }

        return decodeChineseUnicodeEscapes(safeBody);
    }

    static String decodeChineseUnicodeEscapes(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }

        StringBuilder output = new StringBuilder(input.length());
        int index = 0;
        while (index < input.length()) {
            char current = input.charAt(index);
            if (current != '\\') {
                output.append(current);
                index++;
                continue;
            }

            int slashStart = index;
            while (index < input.length() && input.charAt(index) == '\\') {
                index++;
            }
            int slashCount = index - slashStart;

            // Only an odd number of backslashes forms a real escape; the char after
            // the run now sits at `index`.
            if (slashCount % 2 == 1 && isUnicodeEscapeAt(input, index)) {
                int firstUnit = parseHex4(input, index + 1);
                int consumed = 5;
                int codePoint = firstUnit;

                // Combine a UTF-16 surrogate pair so supplementary CJK (e.g. Ext B) decodes.
                if (Character.isHighSurrogate((char) firstUnit)
                        && isUnicodeEscapeAt(input, index + 5)) {
                    int secondUnit = parseHex4(input, index + 6);
                    if (Character.isLowSurrogate((char) secondUnit)) {
                        codePoint = Character.toCodePoint((char) firstUnit, (char) secondUnit);
                        consumed = 10;
                    }
                }

                if (isChineseCodePoint(codePoint)) {
                    appendBackslashes(output, slashCount / 2);
                    output.appendCodePoint(codePoint);
                } else {
                    appendBackslashes(output, slashCount);
                    output.append(input, index, index + consumed);
                }
                index += consumed;
            } else {
                appendBackslashes(output, slashCount);
            }
        }

        return output.toString();
    }

    private static void appendBackslashes(StringBuilder output, int count) {
        for (int i = 0; i < count; i++) {
            output.append('\\');
        }
    }

    private static boolean isUnicodeEscapeAt(String input, int pos) {
        return pos >= 0 && pos < input.length() && input.charAt(pos) == 'u' && isHexSequence(input, pos + 1);
    }

    private String renderEnvelope(MessageEnvelope envelope, String transformedBody) {
        String decodedHead = decodeChineseUnicodeEscapes(envelope.head());
        if (!envelope.hasSeparator()) {
            if (transformedBody == null || transformedBody.isEmpty()) {
                return decodedHead;
            }
            return decodedHead + CRLF_CRLF + transformedBody;
        }
        return decodedHead + envelope.separator() + (transformedBody == null ? "" : transformedBody);
    }

    private MessageEnvelope splitHeadAndBody(String rawMessage) {
        String safeRaw = rawMessage == null ? "" : rawMessage;
        int crlfIndex = safeRaw.indexOf(CRLF_CRLF);
        if (crlfIndex >= 0) {
            return new MessageEnvelope(safeRaw.substring(0, crlfIndex), CRLF_CRLF);
        }
        int lfIndex = safeRaw.indexOf(LF_LF);
        if (lfIndex >= 0) {
            return new MessageEnvelope(safeRaw.substring(0, lfIndex), LF_LF);
        }
        return new MessageEnvelope(safeRaw, "");
    }

    String decodeBodyByContentType(byte[] bodyBytes, String bodyToString, String contentType) {
        String fallback = bodyToString == null ? "" : bodyToString;
        if (bodyBytes == null || bodyBytes.length == 0) {
            return fallback;
        }

        Charset charset = resolveCharset(contentType);
        if (charset == null && isJsonContentType(contentType)) {
            charset = StandardCharsets.UTF_8;
        }
        if (charset == null) {
            return fallback;
        }

        String decodedCandidate;
        try {
            decodedCandidate = new String(bodyBytes, charset);
        } catch (RuntimeException ignored) {
            return fallback;
        }

        if (!isJsonContentType(contentType)) {
            return decodedCandidate;
        }

        JsonNode fallbackJson = parseJson(fallback);
        JsonNode decodedJson = parseJson(decodedCandidate);

        if (decodedJson != null && fallbackJson == null) {
            return decodedCandidate;
        }
        if (decodedJson == null && fallbackJson != null) {
            return fallback;
        }
        if (decodedJson == null) {
            return fallback;
        }

        return shouldPreferDecodedText(fallback, decodedCandidate) ? decodedCandidate : fallback;
    }

    private boolean looksLikeJson(String contentType, String body) {
        if (isJsonContentType(contentType)) {
            return true;
        }

        String trimmed = body.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        return (trimmed.charAt(0) == '{' && trimmed.charAt(trimmed.length() - 1) == '}')
                || (trimmed.charAt(0) == '[' && trimmed.charAt(trimmed.length() - 1) == ']');
    }

    private boolean isJsonContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        String ctLower = contentType.toLowerCase(Locale.ROOT);
        return ctLower.contains("application/json")
                || ctLower.contains("+json")
                || ctLower.contains("text/json");
    }

    private Charset resolveCharset(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }

        String[] tokens = contentType.split(";");
        for (int i = 1; i < tokens.length; i++) {
            String token = tokens[i].trim();
            int equalsIndex = token.indexOf('=');
            if (equalsIndex < 0) {
                continue;
            }

            String name = token.substring(0, equalsIndex).trim();
            if (!"charset".equalsIgnoreCase(name)) {
                continue;
            }

            String value = token.substring(equalsIndex + 1).trim();
            if (value.length() >= 2
                    && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1).trim();
            }
            if (value.isEmpty()) {
                return null;
            }

            try {
                return Charset.forName(value);
            } catch (IllegalCharsetNameException | UnsupportedCharsetException ignored) {
                return null;
            }
        }

        return null;
    }

    private boolean shouldPreferDecodedText(String fallback, String decodedCandidate) {
        if (decodedCandidate.equals(fallback)) {
            return false;
        }

        int fallbackChinese = chineseCodePointCount(fallback);
        int decodedChinese = chineseCodePointCount(decodedCandidate);
        if (decodedChinese > fallbackChinese) {
            return true;
        }

        boolean fallbackControl = hasLatin1ControlChars(fallback);
        boolean decodedControl = hasLatin1ControlChars(decodedCandidate);
        if (fallbackControl && !decodedControl) {
            return true;
        }

        return false;
    }

    private JsonNode normalize(JsonNode node) {
        if (node.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                result.set(entry.getKey(), normalize(entry.getValue()));
            }
            return result;
        }

        if (node.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            for (JsonNode child : node) {
                result.add(normalize(child));
            }
            return result;
        }

        if (node.isTextual()) {
            String text = node.asText();
            String decoded = decodeChineseUnicodeEscapes(text);
            JsonNode nested = parseJson(decoded);
            if (nested != null) {
                return normalize(nested);
            }
            if (!decoded.equals(text)) {
                return TextNode.valueOf(decoded);
            }
        }

        return node;
    }

    private JsonNode parseJson(String candidate) {
        if (candidate == null) {
            return null;
        }

        String trimmed = candidate.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        boolean isObject = trimmed.charAt(0) == '{' && trimmed.charAt(trimmed.length() - 1) == '}';
        boolean isArray = trimmed.charAt(0) == '[' && trimmed.charAt(trimmed.length() - 1) == ']';
        if (!isObject && !isArray) {
            return null;
        }

        try {
            return objectMapper.readTree(trimmed);
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private static boolean isHexSequence(String input, int offset) {
        if (offset + 3 >= input.length()) {
            return false;
        }
        for (int i = 0; i < 4; i++) {
            if (hexValue(input.charAt(offset + i)) < 0) {
                return false;
            }
        }
        return true;
    }

    private static int parseHex4(String input, int offset) {
        int value = 0;
        for (int i = 0; i < 4; i++) {
            value = (value << 4) + hexValue(input.charAt(offset + i));
        }
        return value;
    }

    private static boolean isChineseCodePoint(int codePoint) {
        return (codePoint >= 0x3000 && codePoint <= 0x303F)   // CJK symbols and punctuation
                || (codePoint >= 0x3400 && codePoint <= 0x4DBF)   // CJK Extension A
                || (codePoint >= 0x4E00 && codePoint <= 0x9FFF)   // CJK Unified Ideographs
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF)   // CJK Compatibility Ideographs
                || (codePoint >= 0x20000 && codePoint <= 0x2A6DF) // CJK Extension B
                || (codePoint >= 0x2A700 && codePoint <= 0x2EBEF) // CJK Extension C-F
                || (codePoint >= 0x2F800 && codePoint <= 0x2FA1F) // CJK Compatibility Supplement
                || (codePoint >= 0x30000 && codePoint <= 0x3134F); // CJK Extension G
    }

    private static int chineseCodePointCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int count = 0;
        for (int index = 0; index < text.length(); ) {
            int codePoint = text.codePointAt(index);
            if (isChineseCodePoint(codePoint)) {
                count++;
            }
            index += Character.charCount(codePoint);
        }
        return count;
    }

    private static boolean hasLatin1ControlChars(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= 0x0080 && ch <= 0x009F) {
                return true;
            }
        }
        return false;
    }

    private static int hexValue(char c) {
        if (c >= '0' && c <= '9') {
            return c - '0';
        }
        if (c >= 'a' && c <= 'f') {
            return 10 + (c - 'a');
        }
        if (c >= 'A' && c <= 'F') {
            return 10 + (c - 'A');
        }
        return -1;
    }

    private static DefaultPrettyPrinter prettyPrinter() {
        DefaultIndenter indenter = DefaultIndenter.SYSTEM_LINEFEED_INSTANCE;
        return new DefaultPrettyPrinter()
                .withObjectIndenter(indenter)
                .withArrayIndenter(indenter);
    }

    private record MessageEnvelope(String head, String separator) {
        boolean hasSeparator() {
            return separator != null && !separator.isEmpty();
        }
    }
}
