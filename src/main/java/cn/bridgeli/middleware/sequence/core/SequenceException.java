package cn.bridgeli.middleware.sequence.core;

/**
 * @author bridgeli
 */
public class SequenceException extends RuntimeException {

    private static final long serialVersionUID = -4687678102769966858L;

    public SequenceException(String message) {
        super(message);
    }

    public SequenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
