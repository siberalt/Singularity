package com.siberalt.singularity.math;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IncrementalLinearRegressionTest {
    private IncrementalLinearRegression regression;

    @BeforeEach
    void setup() {
        regression = new IncrementalLinearRegression(0.15);
    }

    @Test
    void addsPointsBelowThreshold() {
        boolean addedFirst = regression.addPoint(new Point2D<>(1.0, 2.0));
        boolean addedSecond = regression.addPoint(new Point2D<>(2.0, 3.0));
        boolean addedThird = regression.addPoint(new Point2D<>(3.0, 3.5));

        Assertions.assertTrue(addedFirst);
        Assertions.assertTrue(addedSecond);
        Assertions.assertTrue(addedThird);
        Assertions.assertTrue(regression.isModelValid());
        Assertions.assertEquals(3, regression.getInliers().size());
    }

    @Test
    void rejectsPointsAboveThreshold() {
        regression.addPoint(new Point2D<>(1.0, 2.0));
        regression.addPoint(new Point2D<>(2.0, 3.0));
        boolean addedThird = regression.addPoint(new Point2D<>(3.0, 5.0));

        Assertions.assertFalse(addedThird);
        Assertions.assertEquals(2, regression.getInliers().size());
    }

    @Test
    void predictsCorrectlyForValidModel() {
        regression.addPoint(new Point2D<>(1.0, 2.0));
        regression.addPoint(new Point2D<>(2.0, 4.0));
        regression.addPoint(new Point2D<>(3.0, 5.8));
        regression.addPoint(new Point2D<>(4.0, 8.1));
        regression.addPoint(new Point2D<>(5.0, 10.5));

        double prediction = regression.predict(4.0);
        Assertions.assertEquals(8.0, prediction, 0.2);
    }

    @Test
    void throwsExceptionWhenPredictingWithoutModel() {
        IllegalStateException exception = Assertions.assertThrows(IllegalStateException.class, () -> regression.predict(1.0));
        Assertions.assertEquals("Модель не инициализирована. Нужно минимум 2 точки.", exception.getMessage());
    }

    @Test
    void resetsModelCorrectly() {
        regression.addPoint(new Point2D<>(1.0, 2.0));
        regression.addPoint(new Point2D<>(2.0, 3.0));
        regression.reset();

        Assertions.assertFalse(regression.isModelValid());
        Assertions.assertEquals(0, regression.getInliers().size());
    }

    @Test
    void handlesVerticalLineCase() {
        regression.addPoint(new Point2D<>(1.0, 2.0));
        regression.addPoint(new Point2D<>(1.0, 3.0));

        Assertions.assertFalse(regression.isModelValid());
    }
}
