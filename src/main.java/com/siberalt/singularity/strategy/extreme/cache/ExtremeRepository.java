package com.siberalt.singularity.strategy.extreme.cache;

import com.siberalt.singularity.entity.candle.Candle;

import java.util.List;

/**
 * The ExtremeRepository interface defines the contract for managing and querying extreme data.
 * It provides methods for saving, retrieving, and deleting extreme-related data, as well as
 * retrieving specific ranges of data.
 */
public interface ExtremeRepository {

    /**
     * Saves a batch of extremes associated with a specific outer range.
     *
     * @param range The range to which the extremes belong.
     * @param extremes The list of extreme candles to be saved.
     */
    void saveBatch(ExtremeRange range, List<Candle> extremes);

    /**
     * Retrieves the inner range that is defined by the first and last extreme indexes
     * within the specified outer range.
     *
     * @param outerRange The outer range within which the inner range is to be determined.
     * @return The inner range defined by the first and last extreme indexes under the outer range.
     */
    ExtremeRange getInnerRange(ExtremeRange outerRange);

    /**
     * Retrieves a list of candles that fall within the specified range.
     *
     * @param range The range for which candles are to be retrieved.
     * @return A list of candles within the specified range.
     */
    List<Candle> getByRange(ExtremeRange range);

    /**
     * Deletes extremes within the specified ranges from the repository.
     *
     * @param ranges A list of ranges whose associated extremes are to be deleted.
     */
    void deleteBatch(List<ExtremeRange> ranges);
}
