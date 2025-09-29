package com.siberalt.singularity.strategy.upside;

/**
 * Represents an upside calculation result with a signal and strength.
 *
 * @param signal  The upside signal, typically a value in the range [-1, 1],
 *                indicating the direction and magnitude of the upside.
 * @param strength The strength of the upside signal, representing the confidence
 *                 or weight of the calculation.
 */
public record Upside(double signal, double strength) {
}
