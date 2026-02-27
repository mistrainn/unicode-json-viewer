package com.jisoo.burp.unicodejson;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.Selection;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpResponseEditor;

import java.awt.Component;

final class UnicodeJsonResponseEditor implements ExtensionProvidedHttpResponseEditor {
    private final JsonViewerPane viewerPane;
    private final MessageTransformer transformer;
    private HttpResponse currentResponse;

    UnicodeJsonResponseEditor(MontoyaApi api, MessageTransformer transformer) {
        this.viewerPane = new JsonViewerPane(api);
        this.transformer = transformer;
    }

    @Override
    public HttpResponse getResponse() {
        return currentResponse;
    }

    @Override
    public void setRequestResponse(HttpRequestResponse requestResponse) {
        currentResponse = requestResponse == null ? null : requestResponse.response();
        String rendered = transformer.renderResponse(currentResponse);
        viewerPane.setContent(rendered);
    }

    @Override
    public boolean isEnabledFor(HttpRequestResponse requestResponse) {
        return requestResponse != null && requestResponse.response() != null;
    }

    @Override
    public String caption() {
        return "Decoded JSON";
    }

    @Override
    public Component uiComponent() {
        return viewerPane.component();
    }

    @Override
    public Selection selectedData() {
        return null;
    }

    @Override
    public boolean isModified() {
        return false;
    }
}
