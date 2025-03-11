package notsotiny.lang.compiler.aasm;

import notsotiny.lang.ir.parts.IRIdentifier;
import notsotiny.lang.ir.parts.IRIdentifierClass;
import notsotiny.lang.ir.parts.IRType;

/**
 * Abstract registers
 * @param id LOCAL Identifier
 * @param type type being referenced. For halves, e.g. of an I32 this field would be I16
 * @param half If true, this references a half of the value
 * @param upper If half is true, indicates whether the half is the upper half (true) or lower half (false)
 */
public record AASMAbstractRegister(IRIdentifier id, IRType type, boolean half, boolean upper) implements AASMRegister {
    
    public AASMAbstractRegister(IRIdentifier id, IRType type) {
        this(id, type, false, false);
    }
    
    public AASMAbstractRegister {
        // Ensure the ID is local
        if(id.getIDClass() != IRIdentifierClass.LOCAL) {
            throw new IllegalArgumentException("Abstract registers must be locals");
        }
    }
    
    @Override
    public IRType getType() { return this.type; }
    
    @Override
    public String toString() {
        return this.id.toString() + (half ? (upper ? "<high>" : "<low>") : "");
    }
    
}
