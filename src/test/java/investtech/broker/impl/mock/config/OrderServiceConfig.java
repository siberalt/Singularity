package investtech.broker.impl.mock.config;

public class OrderServiceConfig {
    protected double commissionRatio = 0.003;

    public double getCommissionRatio() {
        return commissionRatio;
    }

    public void setCommissionRatio(double commissionRatio) {
        this.commissionRatio = commissionRatio;
    }
}
