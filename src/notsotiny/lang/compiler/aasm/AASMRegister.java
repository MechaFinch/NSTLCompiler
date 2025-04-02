package notsotiny.lang.compiler.aasm;

import notsotiny.lang.ir.parts.IRIdentifier;

/**
 * A register in the AbstractAssembly
 */
public interface AASMRegister extends AASMValue {
    
    public IRIdentifier id();
    
}
