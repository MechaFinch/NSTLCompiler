package notsotiny.lang.compiler.aasm;

import notsotiny.lang.ir.parts.IRIdentifier;
import notsotiny.lang.ir.parts.IRIdentifierClass;
import notsotiny.lang.ir.parts.IRType;

/**
 * A link-time constant in the AbstractAssembly (non-LOCAL ID)
 */
public record AASMLinkConstant(IRIdentifier id) implements AASMConstant {
    
    public AASMLinkConstant {
        if(id.getIDClass() == IRIdentifierClass.LOCAL) {
            throw new IllegalArgumentException("Link-time constants cannot be locals");
        }
    }

    @Override
    public IRType getType() { return IRType.I32; }
    
    @Override
    public String toString() {
        return this.id.toString();
    }
    
}
