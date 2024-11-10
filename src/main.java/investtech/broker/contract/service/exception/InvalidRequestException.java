package investtech.broker.contract.service.exception;

public class InvalidRequestException extends AbstractException {
    protected String invalidAttribute;

    public InvalidRequestException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public InvalidRequestException(ErrorCode errorCode) {
        super(errorCode, errorCode.getMessagePattern());
    }

    public String getInvalidAttribute() {
        return invalidAttribute;
    }

    public void setInvalidAttribute(String invalidAttribute) {
        this.invalidAttribute = invalidAttribute;
    }
}
