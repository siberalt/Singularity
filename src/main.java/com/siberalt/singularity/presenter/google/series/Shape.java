package com.siberalt.singularity.presenter.google.series;

public enum Shape {
    CIRCLE("circle"),
    SQUARE("square"),
    TRIANGLE("triangle"),
    DIAMOND("diamond"),
    STAR("start");

    private final String name;

    Shape(String shape) {
        this.name = shape;
    }

    public String getName() {
        return name;
    }
}
