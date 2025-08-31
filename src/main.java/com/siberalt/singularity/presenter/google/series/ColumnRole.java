package com.siberalt.singularity.presenter.google.series;

public enum ColumnRole {
    ANNOTATION("annotation"),
    ANNOTATION_TEXT("annotationText"),
    DATA("data"),
    DOMAIN("domain");

    private final String name;

    ColumnRole(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
