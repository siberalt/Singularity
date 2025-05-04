package com.siberalt.singularity.entity.candle;

public interface WriteCandleRepository {
    /**
     * Save a candle to the repository.
     *
     * @param candle the candle to save
     */
    void save(Candle candle);

    /**
     * Delete a candle from the repository.
     *
     * @param candle the candle to delete
     */
    void delete(Candle candle);
}
