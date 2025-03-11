package notsotiny.lang.compiler.aasm;

import notsotiny.lang.ir.parts.IRType;

/**
 * A location in the AbstractAssembly
 */
public interface AASMLocation extends AASMPart {
    
    /**
     * Gets the IRType associated with the location
     * @return
     */
    public IRType getType();
    
}
