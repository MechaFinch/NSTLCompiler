package notsotiny.lang.ir.parts;

import java.nio.file.Path;

/**
 * Classes extending this interface can be queried for source information.
 * 
 * @author Mechafinch
 */
public interface IRSourceInfo {
    
    /**
     * Gets the source file for this object
     * @return
     */
    public Path getSourceFile();
    
    /**
     * Gets the line number for this object
     * @return
     */
    public int getSourceLineNumber();
}
