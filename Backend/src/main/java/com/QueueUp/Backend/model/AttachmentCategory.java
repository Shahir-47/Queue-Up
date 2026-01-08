package com.QueueUp.Backend.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum AttachmentCategory {
    IMAGE, VIDEO, AUDIO, PDF, SPREADSHEET, PRESENTATION, WORD, ARCHIVE, OTHER;

    @JsonValue
    public String toValue() {
        return this.name().toLowerCase();
    }
}