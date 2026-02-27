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

import java.util.Locale;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
        String transformedBody = transformBodyForDisplay(request.bodyToString(), request.headerValue("Content-Type"));
        return renderEnvelope(envelope, transformedBody);
    }

    String renderResponse(HttpResponse response) {
        if (response == null) {
            return "";
        }

        MessageEnvelope envelope = splitHeadAndBody(response.toString());
        String transformedBody = transformBodyForDisplay(response.bodyToString(), response.headerValue("Content-Type"));
        return renderEnvelope(envelope, transformedBody);
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

            if (index + 4 < input.length()
                    && input.charAt(index) == 'u'
                    && slashCount % 2 == 1
                    && isHexSequence(input, index + 1)) {
                int codePoint = parseHex4(input, index + 1);
                if (isChineseCodePoint(codePoint)) {
                    for (int i = 0; i < slashCount / 2; i++) {
                        output.append('\\');
                    }
                    output.append((char) codePoint);
                } else {
                    for (int i = 0; i < slashCount; i++) {
                        output.append('\\');
                    }
                    output.append('u').append(input, index + 1, index + 5);
                }
                index += 5;
            } else {
                for (int i = 0; i < slashCount; i++) {
                    output.append('\\');
                }
            }
        }

        return output.toString();
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

    private boolean looksLikeJson(String contentType, String body) {
        if (contentType != null) {
            String ctLower = contentType.toLowerCase(Locale.ROOT);
            if (ctLower.contains("application/json")
                    || ctLower.contains("+json")
                    || ctLower.contains("text/json")) {
                return true;
            }
        }

        String trimmed = body.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        return (trimmed.charAt(0) == '{' && trimmed.charAt(trimmed.length() - 1) == '}')
                || (trimmed.charAt(0) == '[' && trimmed.charAt(trimmed.length() - 1) == ']');
    }

    private JsonNode normalize(JsonNode node) {
        if (node.isObject()) {
            ObjectNode objectNode = (ObjectNode) node.deepCopy();
            Iterator<String> iterator = objectNode.fieldNames();
            List<String> fields = new ArrayList<>();
            while (iterator.hasNext()) {
                fields.add(iterator.next());
            }
            for (String field : fields) {
                objectNode.set(field, normalize(objectNode.get(field)));
            }
            return objectNode;
        }

        if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node.deepCopy();
            for (int i = 0; i < arrayNode.size(); i++) {
                arrayNode.set(i, normalize(arrayNode.get(i)));
            }
            return arrayNode;
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
        return (codePoint >= 0x3400 && codePoint <= 0x4DBF)
                || (codePoint >= 0x4E00 && codePoint <= 0x9FFF)
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF)
                || (codePoint >= 0x3000 && codePoint <= 0x303F);
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
