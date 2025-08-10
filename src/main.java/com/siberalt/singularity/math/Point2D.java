package com.siberalt.singularity.math;

public record Point2D<T>(T x, T y) {

    @Override
    public String toString() {
        return "Point2D{" +
            "x=" + x +
            ", y=" + y +
            '}';
    }
}
