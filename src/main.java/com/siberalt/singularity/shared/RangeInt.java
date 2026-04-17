package com.siberalt.singularity.shared;

public record RangeInt(int start, int end) {
    public static final RangeInt EMPTY = new RangeInt(0, 0);
}
