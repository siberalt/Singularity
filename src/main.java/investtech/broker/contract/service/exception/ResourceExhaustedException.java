package investtech.broker.contract.service.exception;

public class ResourceExhaustedException extends AbstractException {
    public ResourceExhaustedException(ErrorCode errorCode) {
        super(errorCode);
    }

    public ResourceExhaustedException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
