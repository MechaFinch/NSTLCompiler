package notsotiny.lang.compiler.optimization.gvnpre;

import notsotiny.lang.ir.parts.IRType;

/**
 * A numbered value; either an expression, phi, or (IR) value
 */
public interface GVNElement {
    
    /**
     * @return The type of the element
     */
    public IRType type();
    
}
