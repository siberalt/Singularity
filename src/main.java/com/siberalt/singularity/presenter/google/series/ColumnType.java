package com.siberalt.singularity.presenter.google.series;

public enum ColumnType {
    DATE("date"),
    NUMBER("number"),
    STRING("string"),
    BOOLEAN("boolean");

    private final String type;

    ColumnType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
