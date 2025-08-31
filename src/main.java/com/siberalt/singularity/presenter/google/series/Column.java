package com.siberalt.singularity.presenter.google.series;

public record Column(ColumnType type, ColumnRole role, String label) {
    public Column(ColumnType type, ColumnRole role) {
        this(type, role, null);
    }
}
