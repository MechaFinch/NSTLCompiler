package notsotiny.lang.compiler.types;

import java.util.List;

import notsotiny.lang.compiler.CompilationException;
import notsotiny.lang.ir.IRType;

/**
 * Describes a type
 * 
 * @author Mechafinch
 */
public interface NSTLType {
    
    /**
     * Converts to the equivalent IRType
     * @return
     * @throws CompilationException if not convertible
     */
    public IRType getIRType() throws CompilationException;
    
    /**
     * Gets the non-alias type this is
     * @return
     */
    public default NSTLType getRealType() {
        if(this instanceof AliasType at) {
            return at.getRealType();
        } else {
            return this;
        }
    }
    
    /**
     * @return the name of this type
     */
    public String getName();
    
    /**
     * @return total size in bytes of the type
     */
    public int getSize();
    
    /**
     * Updates the size of the type
     * 
     * @return true if the size changed
     */
    public boolean updateSize(List<String> updatedNames);
    
    public boolean equals(NSTLType t);
}
