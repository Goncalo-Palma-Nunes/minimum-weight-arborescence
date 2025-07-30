package optimalarborescence.exception;

public class NotImplementedException extends RuntimeException {

    /**
     * Constructs a NotImplementedException with the specified detail message.
     *
     * @param message the detail message
     */
    public NotImplementedException(String message) {
        super(message);
    }

    /**
     * Constructs a NotImplementedException with no detail message.
     */
    public NotImplementedException() {
        super("This feature is not implemented yet.");
    }
    
}
