package notsotiny.lang.util;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Prints to an output stream
 */
public class StreamPrinter implements Printer {
    
    private OutputStream stream;
    
    /**
     * @param stream
     */
    public StreamPrinter(OutputStream stream) {
        this.stream = stream;
    }

    @Override
    public void print(String s) throws IOException {
        this.stream.write(s.getBytes());
    }

    @Override
    public void println(String s) throws IOException {
        this.print(s);
        this.stream.write((byte) '\n');
    }
    
    
}
