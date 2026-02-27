package com.jisoo.burp.unicodejson;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.ui.Selection;
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpRequestEditor;

import java.awt.Component;

final class UnicodeJsonRequestEditor implements ExtensionProvidedHttpRequestEditor {
    private final JsonViewerPane viewerPane;
    private final MessageTransformer transformer;
    private HttpRequest currentRequest;

    UnicodeJsonRequestEditor(MontoyaApi api, MessageTransformer transformer) {
        this.viewerPane = new JsonViewerPane(api);
        this.transformer = transformer;
    }

    @Override
    public HttpRequest getRequest() {
        return currentRequest;
    }

    @Override
    public void setRequestResponse(HttpRequestResponse requestResponse) {
        currentRequest = requestResponse == null ? null : requestResponse.request();
        String rendered = transformer.renderRequest(currentRequest);
        viewerPane.setContent(rendered);
    }

    @Override
    public boolean isEnabledFor(HttpRequestResponse requestResponse) {
        return requestResponse != null && requestResponse.request() != null;
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
