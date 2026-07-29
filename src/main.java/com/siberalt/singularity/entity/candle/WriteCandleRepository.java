package com.siberalt.singularity.entity.candle;

import java.util.List;

public interface WriteCandleRepository {
    /**
     * Save a candle to the repository.
     *
     * @param candle the candle to save
     */
    void save(Candle candle);

    /**
     * Save a list of candles to the repository.
     *
     * @param candles the list of candles to save
     */
    default void saveBatch(List<Candle> candles) {
        for (Candle candle : candles) {
            if (candle != null) {
                save(candle);
            }
        }
    }

    /**
     * Delete a candle from the repository.
     *
     * @param candle the candle to delete
     */
    void delete(Candle candle);
}
