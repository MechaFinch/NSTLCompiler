package notsotiny.lang.util;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Prints to a Logger
 */
public class LogPrinter implements Printer {
    
    private Logger log;
    
    private Level level;
    
    private StringBuilder lineBuffer;
    
    /**
     * @param log
     * @param level
     */
    public LogPrinter(Logger log, Level level) {
        this.log = log;
        this.level = level;
        this.lineBuffer = new StringBuilder();
    }
    
    /**
     * @return True if log will log anything
     */
    public boolean isLoggable() {
        return this.log.isLoggable(this.level);
    }

    @Override
    public void print(String s) {
        if(s.endsWith("\n")) {
            this.println(s.substring(0, s.length() - 1));
        } else {
            this.lineBuffer.append(s);
        }
    }

    @Override
    public void println(String s) {
        this.log.log(this.level, lineBuffer.toString() + s);
        this.lineBuffer.setLength(0);
    }
    
}
