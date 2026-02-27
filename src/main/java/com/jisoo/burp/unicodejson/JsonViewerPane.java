package com.jisoo.burp.unicodejson;

import burp.api.montoya.MontoyaApi;

import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.UIManager;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class JsonViewerPane {
    private static final Pattern STRING_PATTERN = Pattern.compile("\"(?:\\\\.|[^\"\\\\])*\"");
    private static final Pattern KEYWORD_PATTERN = Pattern.compile("\\b(?:true|false|null)\\b");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?");

    private final JTextPane textPane = new JTextPane();
    private final JScrollPane scrollPane = new JScrollPane(textPane);
    private final Style baseStyle;
    private final Style keyStyle;
    private final Style stringStyle;
    private final Style numberStyle;
    private final Style keywordStyle;

    JsonViewerPane(MontoyaApi api) {
        textPane.setEditable(false);
        textPane.setDocument(new DefaultStyledDocument());
        api.userInterface().applyThemeToComponent(scrollPane);

        Font editorFont = api.userInterface().currentEditorFont();
        if (editorFont != null) {
            textPane.setFont(editorFont);
        }

        StyledDocument document = textPane.getStyledDocument();
        baseStyle = document.addStyle("base", null);
        keyStyle = document.addStyle("key", null);
        stringStyle = document.addStyle("string", null);
        numberStyle = document.addStyle("number", null);
        keywordStyle = document.addStyle("keyword", null);

        Color base = textPane.getForeground() == null ? UIManager.getColor("TextPane.foreground") : textPane.getForeground();
        if (base == null) {
            base = Color.BLACK;
        }
        Color background = textPane.getBackground() == null ? UIManager.getColor("TextPane.background") : textPane.getBackground();
        boolean dark = background != null && isDarkColor(background);

        StyleConstants.setForeground(baseStyle, base);
        StyleConstants.setForeground(keyStyle, dark ? new Color(111, 170, 255) : new Color(47, 102, 194));
        StyleConstants.setForeground(stringStyle, dark ? new Color(120, 220, 140) : new Color(15, 126, 64));
        StyleConstants.setForeground(numberStyle, dark ? new Color(255, 178, 102) : new Color(169, 74, 0));
        StyleConstants.setForeground(keywordStyle, dark ? new Color(217, 155, 255) : new Color(142, 39, 173));
        StyleConstants.setBold(keyStyle, true);
        StyleConstants.setBold(keywordStyle, true);
    }

    Component component() {
        return scrollPane;
    }

    void setContent(String text) {
        String safeText = text == null ? "" : normalizeLineSeparators(text);
        textPane.setText(safeText);
        highlightJsonIfPossible(safeText);
        textPane.setCaretPosition(0);
    }

    private void highlightJsonIfPossible(String text) {
        StyledDocument document = textPane.getStyledDocument();
        document.setCharacterAttributes(0, text.length(), baseStyle, true);

        if (isLikelyJson(text)) {
            applyJsonHighlight(document, text, 0);
            return;
        }

        int bodyStart = httpBodyStart(text);
        if (bodyStart >= 0 && bodyStart < text.length()) {
            String body = text.substring(bodyStart);
            if (isLikelyJson(body)) {
                applyJsonHighlight(document, body, bodyStart);
            }
        }
    }

    private void applyJsonHighlight(StyledDocument document, String jsonText, int offset) {
        List<Range> keyRanges = new ArrayList<>();
        List<Range> stringRanges = new ArrayList<>();
        Matcher stringMatcher = STRING_PATTERN.matcher(jsonText);
        while (stringMatcher.find()) {
            int start = offset + stringMatcher.start();
            int end = offset + stringMatcher.end();
            if (isJsonKey(jsonText, stringMatcher.end())) {
                int keyEnd = end;
                keyRanges.add(new Range(start, keyEnd));
                document.setCharacterAttributes(start, keyEnd - start, keyStyle, true);
            } else {
                stringRanges.add(new Range(start, end));
                document.setCharacterAttributes(start, end - start, stringStyle, true);
            }
        }

        boolean[] occupied = new boolean[document.getLength()];
        markRanges(occupied, keyRanges);
        markRanges(occupied, stringRanges);

        highlightPattern(document, jsonText, occupied, NUMBER_PATTERN, numberStyle, offset);
        highlightPattern(document, jsonText, occupied, KEYWORD_PATTERN, keywordStyle, offset);
    }

    private void highlightPattern(StyledDocument document, String text, boolean[] occupied, Pattern pattern, Style style, int offset) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            int start = offset + matcher.start();
            int end = offset + matcher.end();
            if (!isRangeOccupied(occupied, start, end)) {
                document.setCharacterAttributes(start, end - start, style, true);
            }
        }
    }

    private static void markRanges(boolean[] occupied, List<Range> ranges) {
        for (Range range : ranges) {
            for (int i = range.start; i < range.end && i < occupied.length; i++) {
                occupied[i] = true;
            }
        }
    }

    private static boolean isRangeOccupied(boolean[] occupied, int start, int end) {
        for (int i = start; i < end && i < occupied.length; i++) {
            if (occupied[i]) {
                return true;
            }
        }
        return false;
    }

    private static boolean isJsonKey(String json, int stringEnd) {
        int index = stringEnd;
        while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
            index++;
        }
        return index < json.length() && json.charAt(index) == ':';
    }

    private static boolean isLikelyJson(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        return (trimmed.charAt(0) == '{' && trimmed.charAt(trimmed.length() - 1) == '}')
                || (trimmed.charAt(0) == '[' && trimmed.charAt(trimmed.length() - 1) == ']');
    }

    private static int httpBodyStart(String text) {
        if (text == null || text.isEmpty()) {
            return -1;
        }
        int crlf = text.indexOf("\r\n\r\n");
        if (crlf >= 0) {
            return crlf + 4;
        }
        int lf = text.indexOf("\n\n");
        if (lf >= 0) {
            return lf + 2;
        }
        return -1;
    }

    private static String normalizeLineSeparators(String input) {
        return input.replace("\r\n", "\n").replace('\r', '\n');
    }

    private static boolean isDarkColor(Color color) {
        int luminance = (int) (0.299 * color.getRed() + 0.587 * color.getGreen() + 0.114 * color.getBlue());
        return luminance < 128;
    }

    private record Range(int start, int end) {
    }
}
