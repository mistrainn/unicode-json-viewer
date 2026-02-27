package com.jisoo.burp.unicodejson;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;

public final class UnicodeJsonViewerExtension implements BurpExtension {
    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("Unicode JSON Viewer");

        MessageTransformer transformer = new MessageTransformer();
        api.userInterface().registerHttpRequestEditorProvider(
                creationContext -> new UnicodeJsonRequestEditor(api, transformer));
        api.userInterface().registerHttpResponseEditorProvider(
                creationContext -> new UnicodeJsonResponseEditor(api, transformer));

        api.logging().logToOutput("Unicode JSON Viewer loaded. Author: @mistrainn (๑•̀ㅂ•́)و✧");
        api.logging().logToOutput("Tips: Open the \"Decoded JSON\" tab to decode Chinese \\uXXXX and expand nested JSON.");
    }
}
