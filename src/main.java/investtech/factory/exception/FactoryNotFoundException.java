package investtech.factory.exception;

public class FactoryNotFoundException extends Exception {
    String serviceId;

    public FactoryNotFoundException(String serviceId)
    {
        this.serviceId = serviceId;
    }

    public String getServiceId() {
        return serviceId;
    }
}
