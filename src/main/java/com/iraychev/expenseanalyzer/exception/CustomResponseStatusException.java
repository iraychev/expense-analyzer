@Getter
public class CustomResponseStatusException extends ResponseStatusException {
    private final String errorCode;
    private final String message;

    public CustomResponseStatusException(HttpStatusCode status, String errorCode, String reason, String message) {
        super(status, reason);
        this.errorCode = errorCode;
        this.message = message;
    }
}
