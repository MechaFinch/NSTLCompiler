package notsotiny.lang.util;

import java.io.IOException;

/**
 * Prints things
 */
public interface Printer {
    
    /**
     * Prints s
     * @param s
     * @throws IOException
     */
    public void print(String s) throws IOException;
    
    /**
     * Prints s, followed by a newline
     * @param s
     * @throws IOException
     */
    public void println(String s) throws IOException;
}
