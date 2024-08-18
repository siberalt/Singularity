package investtech.broker.contract.service.exception;

public class UnavailableException extends AbstractException {
    public UnavailableException(String message) {
        super(ErrorCode.UNAVAILABLE, message);
    }

    public UnavailableException() {
        super(ErrorCode.UNAVAILABLE);
    }
}
