package com.siberalt.singularity.math.function;

import org.junit.jupiter.api.Test;

import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CompositeFunctionTest {
    private final CompositeFunction<Integer, String> compositeFunction = new CompositeFunction<>(
        Comparator.comparingInt(Integer::intValue)
    );

    @Test
    void appliesFunctionWithinDefinedRange() {
        compositeFunction.addFunction(0, 10, x -> "Range 0-10: " + x);

        assertEquals("Range 0-10: 5", compositeFunction.apply(5));
    }

    @Test
    void returnsDefaultValueWhenInputOutsideDefinedRange() {
        compositeFunction.addFunction(0, 10, x -> "Range 0-10: " + x);
        assertNull(compositeFunction.apply(15));
    }

    @Test
    void appliesCorrectFunctionForOverlappingRanges() {
        compositeFunction.addFunction(0, 10, x -> "Range 0-10: " + x);
        compositeFunction.addFunction(10, 20, x -> "Range 10-20: " + x);

        assertEquals("Range 0-10: 8", compositeFunction.apply(8));
        assertEquals("Range 10-20: 15", compositeFunction.apply(15));
    }

    @Test
    void equalsDefaultValueWhenNoFunctionDefined() {
        assertNull(compositeFunction.apply(5));
    }

    @Test
    void appliesFunctionAtRangeBoundary() {
        compositeFunction.addFunction(0, 10, x -> "Range 0-10: " + x);

        assertEquals("Range 0-10: 0", compositeFunction.apply(0));
        assertEquals("Range 0-10: 10", compositeFunction.apply(10));
    }
}