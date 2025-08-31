package com.siberalt.singularity.presenter.google.series;

import java.util.Optional;

public interface SeriesProvider {
    Optional<SeriesChunk> provide(long start, long end, long stepInterval);
}
