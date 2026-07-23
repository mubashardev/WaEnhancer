package com.waex.api.model;

public class CustomPrivacyDTO {
    private final String jsonString;

    public CustomPrivacyDTO(String jsonString) {
        this.jsonString = jsonString;
    }

    public String getJsonString() { return jsonString; }
}
