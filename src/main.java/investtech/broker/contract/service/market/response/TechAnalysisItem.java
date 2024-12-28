package investtech.broker.contract.service.market.response;


import investtech.broker.contract.value.quotation.Quotation;

import java.time.Instant;

public class TechAnalysisItem {
    protected Instant timestamp;
    protected Quotation middleBand;
    protected Quotation upperBand;
    protected Quotation lowerBand;
    protected Quotation signal;
    protected Quotation macd;

    public Instant getTimestamp() {
        return timestamp;
    }

    public TechAnalysisItem setTimestamp(Instant Instant) {
        this.timestamp = Instant;
        return this;
    }

    public Quotation getMiddleBand() {
        return middleBand;
    }

    public TechAnalysisItem setMiddleBand(Quotation middleBand) {
        this.middleBand = middleBand;
        return this;
    }

    public Quotation getUpperBand() {
        return upperBand;
    }

    public TechAnalysisItem setUpperBand(Quotation upperBand) {
        this.upperBand = upperBand;
        return this;
    }

    public Quotation getLowerBand() {
        return lowerBand;
    }

    public TechAnalysisItem setLowerBand(Quotation lowerBand) {
        this.lowerBand = lowerBand;
        return this;
    }

    public Quotation getSignal() {
        return signal;
    }

    public TechAnalysisItem setSignal(Quotation signal) {
        this.signal = signal;
        return this;
    }

    public Quotation getMacd() {
        return macd;
    }

    public TechAnalysisItem setMacd(Quotation macd) {
        this.macd = macd;
        return this;
    }

    @Override
    public String toString() {
        return String.join("",
                String.format("timestamp: %s\n", timestamp),
                String.format("middleBand: %s\n", middleBand),
                String.format("upperBand: %s\n", upperBand),
                String.format("lowerBand: %s\n", lowerBand),
                String.format("signal: %s\n", signal),
                String.format("macd: %s\n", macd)
        );
    }
}
