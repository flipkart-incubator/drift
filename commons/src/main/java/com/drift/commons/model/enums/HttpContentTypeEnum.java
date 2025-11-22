package com.drift.commons.model.enums;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum HttpContentTypeEnum {
    @JsonProperty("application/json")
    APPLICATION_JSON("application/json"),

    @JsonProperty("application/x-www-form-urlencoded")
    APPLICATION_X_WWW_FORM_URLENCODED("application/x-www-form-urlencoded"),

    @JsonProperty("application/xml")
    APPLICATION_XML("application/xml"),

    @JsonProperty("text/html")
    TEXT_HTML("text/html"),

    @JsonProperty("text/plain")
    TEXT_PLAIN("text/plain");

    private final String type;

    HttpContentTypeEnum(String s) {
        type = s;
    }

    public String toString() {
        return this.type;
    }

}
