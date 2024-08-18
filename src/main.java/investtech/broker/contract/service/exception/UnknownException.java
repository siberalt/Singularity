package investtech.broker.contract.service.exception;

public class UnknownException extends AbstractException {
    public UnknownException(String message) {
        super(ErrorCode.UNKNOWN, message);
    }

    public UnknownException() {
        super(ErrorCode.UNKNOWN);
    }
}
