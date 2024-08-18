package investtech.broker.contract.service.market.response;

import investtech.broker.contract.value.quatation.Quotation;
import java.time.Instant;

public class HistoricCandle {
    protected Quotation open;
    protected Quotation high;
    protected Quotation low;
    protected Quotation close;
    protected long volume;
    protected Instant time;
    protected boolean isComplete;

    public Quotation getOpen() {
        return open;
    }

    public HistoricCandle setOpen(Quotation open) {
        this.open = open;
        return this;
    }

    public Quotation getHigh() {
        return high;
    }

    public HistoricCandle setHigh(Quotation high) {
        this.high = high;
        return this;
    }

    public Quotation getLow() {
        return low;
    }

    public HistoricCandle setLow(Quotation low) {
        this.low = low;
        return this;
    }

    public Quotation getClose() {
        return close;
    }

    public HistoricCandle setClose(Quotation close) {
        this.close = close;
        return this;
    }

    public long getVolume() {
        return volume;
    }

    public HistoricCandle setVolume(long volume) {
        this.volume = volume;
        return this;
    }

    public Instant getTime() {
        return time;
    }

    public HistoricCandle setTime(Instant time) {
        this.time = time;
        return this;
    }

    public boolean isComplete() {
        return isComplete;
    }

    public HistoricCandle setComplete(boolean complete) {
        isComplete = complete;
        return this;
    }
}
