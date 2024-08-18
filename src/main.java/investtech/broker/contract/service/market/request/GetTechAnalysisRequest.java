package investtech.broker.contract.service.market.request;
import java.time.Instant;

public class GetTechAnalysisRequest {
    protected IndicatorType indicatorType;
    protected String instrumentUid;
    protected Instant from;
    protected Instant to;
    protected IndicatorInterval interval;
    protected PriceType priceType;
    protected int length;

    public IndicatorType getIndicatorType() {
        return indicatorType;
    }

    public GetTechAnalysisRequest setIndicatorType(IndicatorType indicatorType) {
        this.indicatorType = indicatorType;
        return this;
    }

    public String getInstrumentUid() {
        return instrumentUid;
    }

    public GetTechAnalysisRequest setInstrumentUid(String instrumentUid) {
        this.instrumentUid = instrumentUid;
        return this;
    }

    public Instant getFrom() {
        return from;
    }

    public GetTechAnalysisRequest setFrom(Instant from) {
        this.from = from;
        return this;
    }

    public Instant getTo() {
        return to;
    }

    public GetTechAnalysisRequest setTo(Instant to) {
        this.to = to;
        return this;
    }

    public IndicatorInterval getInterval() {
        return interval;
    }

    public GetTechAnalysisRequest setInterval(IndicatorInterval interval) {
        this.interval = interval;
        return this;
    }

    public PriceType getPriceType() {
        return priceType;
    }

    public GetTechAnalysisRequest setPriceType(PriceType priceType) {
        this.priceType = priceType;
        return this;
    }

    public int getLength() {
        return length;
    }

    public GetTechAnalysisRequest setLength(int length) {
        this.length = length;
        return this;
    }
}
