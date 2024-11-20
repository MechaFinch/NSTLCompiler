package notsotiny.lang.compiler;

/**
 * General purpose indication of errors
 */
public class CompilationException extends Exception {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;
    
    public CompilationException() {
        super("");
    }
    
    public CompilationException(String message) {
        super(message);
    }
    
}
