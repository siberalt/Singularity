package investtech.broker.contract.service.exception;

public class NotFoundException extends AbstractException {
    public NotFoundException(ErrorCode errorCode) {
        super(errorCode, errorCode.getMessagePattern());
    }

    public NotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
