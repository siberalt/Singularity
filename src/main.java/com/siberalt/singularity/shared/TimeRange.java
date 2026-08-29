package com.siberalt.singularity.shared;

import java.time.Instant;

public record TimeRange(Instant from, Instant to) {
    public static final TimeRange MAX = new TimeRange(Instant.MIN, Instant.MAX);
}
