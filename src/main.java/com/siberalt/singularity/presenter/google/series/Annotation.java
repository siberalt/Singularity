package com.siberalt.singularity.presenter.google.series;

public record Annotation(String label, String text) {
    public Annotation(String label) {
        this(label, null);
    }
}
