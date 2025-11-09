package com.siberalt.singularity.presenter.google.series;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class FunctionGroupSeriesProviderTest {

    @Test
    void addFunctionHandlesFunctionsWithSameStartIndex() {
        FunctionGroupSeriesProvider provider = new FunctionGroupSeriesProvider("Test Series");
        Function<Double, Double> func1 = x -> x * 2;
        Function<Double, Double> func2 = x -> x + 10;

        // Add two functions with the same start index but different end indices
        provider.addFunction(0, 50, func1);
        provider.addFunction(0, 100, func2);

        Optional<SeriesChunk> result = provider.provide(0, 100, 10);

        assertTrue(result.isPresent());
        SeriesChunk chunk = result.get();

        // Verify the number of columns (two functions, so two columns)
        assertEquals(2, chunk.columns().size());

        // Verify the data for the first function
        assertEquals(0.0, chunk.data()[0][0]); // First function at x=0
        assertEquals(100.0, chunk.data()[5][0]); // First function at x=50

        // Verify the data for the second function
        assertEquals(10.0, chunk.data()[0][1]); // Second function at x=0
        assertEquals(110.0, chunk.data()[10][1]); // Second function at x=100
    }

    @Test
    void provideReturnsEmptyWhenNoFunctionsAdded() {
        FunctionGroupSeriesProvider provider = new FunctionGroupSeriesProvider("Test Series");
        Optional<SeriesChunk> result = provider.provide(0, 100, 10);
        assertTrue(result.isEmpty());
    }

    @Test
    void provideThrowsExceptionWhenStartGreaterThanEnd() {
        FunctionGroupSeriesProvider provider = new FunctionGroupSeriesProvider("Test Series");
        assertThrows(IllegalArgumentException.class, () -> provider.provide(100, 50, 10));
    }

    @Test
    void provideThrowsExceptionWhenStepIntervalIsZero() {
        FunctionGroupSeriesProvider provider = new FunctionGroupSeriesProvider("Test Series");
        assertThrows(IllegalArgumentException.class, () -> provider.provide(0, 100, 0));
    }

    @Test
    void addFunctionIgnoresSubsetFunctions() {
        FunctionGroupSeriesProvider provider = new FunctionGroupSeriesProvider("Test Series");
        Function<Double, Double> func = x -> x * 2;
        FunctionGroupSeriesProvider.FunctionDetails function1 = FunctionGroupSeriesProvider
            .newFunctionBuilder(0, 100, func)
            .build();

        FunctionGroupSeriesProvider.FunctionDetails function2 = FunctionGroupSeriesProvider
            .newFunctionBuilder(20, 80, func)
            .build();

        provider.addFunction(function1);
        provider.addFunction(function2);

        Optional<SeriesChunk> result = provider.provide(0, 100, 10);
        assertTrue(result.isPresent());
        assertEquals(1, result.get().columns().size());
    }

    @Test
    void addFunctionReplacesSubsetFunctions() {
        FunctionGroupSeriesProvider provider = new FunctionGroupSeriesProvider("Test Series");
        Function<Double, Double> func = x -> x * 2;

        provider.addFunction(20, 80, func);
        provider.addFunction(0, 100, func);

        Optional<SeriesChunk> result = provider.provide(0, 100, 10);
        assertTrue(result.isPresent());
        assertEquals(1, result.get().columns().size());
    }

    @Test
    void provideGeneratesCorrectDataForSingleFunction() {
        FunctionGroupSeriesProvider provider = new FunctionGroupSeriesProvider("Test Series");

        provider.addFunction(0, 100, x -> x * 2);
        Optional<SeriesChunk> result = provider.provide(0, 100, 10);

        assertTrue(result.isPresent());
        SeriesChunk chunk = result.get();
        assertEquals(11, chunk.data().length); // 0 to 100 with step 10
        assertEquals(0.0, chunk.data()[0][0]);
        assertEquals(200.0, chunk.data()[10][0]);
    }

    @Test
    void provideIncludesAnnotationsInData() {
        FunctionGroupSeriesProvider provider = new FunctionGroupSeriesProvider("Test Series");
        FunctionGroupSeriesProvider.FunctionDetails function = FunctionGroupSeriesProvider
            .newFunctionBuilder(0, 100, x -> x * 2)
            .addAnnotation(20L, new Annotation("Label", "Text"))
            .build();

        provider.addFunction(function);
        Optional<SeriesChunk> result = provider.provide(0, 100, 10);

        assertTrue(result.isPresent());
        SeriesChunk chunk = result.get();
        assertEquals("Label", chunk.data()[2][1]);
        assertEquals("Text", chunk.data()[2][2]);
    }

    @Test
    void addFunctionHandlesDifferentFunctions() {
        FunctionGroupSeriesProvider provider = new FunctionGroupSeriesProvider("Test Series");
        Function<Double, Double> func1 = x -> x * 2;
        Function<Double, Double> func2 = x -> x + 10;

        FunctionGroupSeriesProvider.FunctionDetails function1 = FunctionGroupSeriesProvider
            .newFunctionBuilder(0, 50, func1)
            .build();

        FunctionGroupSeriesProvider.FunctionDetails function2 = FunctionGroupSeriesProvider
            .newFunctionBuilder(50, 100, func2)
            .build();

        provider.addFunction(function1);
        provider.addFunction(function2);

        Optional<SeriesChunk> result = provider.provide(0, 100, 10);

        assertTrue(result.isPresent());
        SeriesChunk chunk = result.get();
        assertEquals(2, chunk.columns().size()); // Two functions, so two columns
        assertEquals(0.0, chunk.data()[0][0]); // First function at x=0
        assertEquals(100.0, chunk.data()[5][0]); // First function at x=50
        assertEquals(70.0, chunk.data()[6][1]); // Second function at x=60
        assertEquals(110.0, chunk.data()[10][1]); // Second function at x=100
    }

    @Test
    void addFunctionHandlesDifferentFunctionsWithAnnotations() {
        FunctionGroupSeriesProvider provider = new FunctionGroupSeriesProvider("Test Series");
        Function<Double, Double> func1 = x -> x * 2;
        Function<Double, Double> func2 = x -> x + 10;

        FunctionGroupSeriesProvider.FunctionDetails function1 = FunctionGroupSeriesProvider
            .newFunctionBuilder(0, 50, func1)
            .addAnnotation(10L, new Annotation("Annotation1", "At x=10"))
            .addAnnotation(30L, new Annotation("Annotation2", "At x=30"))
            .build();


        provider.addFunction(function1);
        provider.addFunction(50, 100, func2);

        Optional<SeriesChunk> result = provider.provide(0, 100, 10);

        assertTrue(result.isPresent());
        SeriesChunk chunk = result.get();
        assertEquals(4, chunk.columns().size()); // Two functions, so two columns

        // Verify first function values
        assertEquals(0.0, chunk.data()[0][0]); // First function at x=0
        assertEquals(20.0, chunk.data()[1][0]); // First function at x=10
        assertEquals(100.0, chunk.data()[5][0]); // First function at x=50

        // Verify annotations for the first function
        assertEquals("Annotation1", chunk.data()[1][1]); // Annotation at x=10
        assertEquals("At x=10", chunk.data()[1][2]); // Annotation text at x=10
        assertEquals("Annotation2", chunk.data()[3][1]); // Annotation at x=30
        assertEquals("At x=30", chunk.data()[3][2]); // Annotation text at x=30

        // Verify second function values
        assertEquals(70.0, chunk.data()[6][3]); // Second function at x=60
        assertEquals(110.0, chunk.data()[10][3]); // Second function at x=100
    }
}