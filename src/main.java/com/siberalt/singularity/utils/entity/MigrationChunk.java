package com.siberalt.singularity.utils.entity;

import java.time.Instant;

public record MigrationChunk(Instant from, Instant to) {
}
