package notsotiny.lang.compiler.aasm;

import notsotiny.lang.compiler.codegen.alloc.RARegisterClass;
import notsotiny.lang.ir.parts.IRIdentifier;
import notsotiny.lang.ir.parts.IRIdentifierClass;
import notsotiny.lang.ir.parts.IRType;

/**
 * Abstract registers
 * @param id LOCAL Identifier
 * @param type Type being referenced. Passed as the size of the full register, read as the size of the referenced register (pass I32, half=true -> read I16)
 * @param half If true, this references a half of the value
 * @param upper If half is true, indicates whether the half is the upper half (true) or lower half (false)
 */
public record AASMAbstractRegister(IRIdentifier id, IRType type, boolean half, boolean upper) implements AASMRegister {
    
    public AASMAbstractRegister(IRIdentifier id, IRType type) {
        this(id, type, false, false);
    }
    
    public AASMAbstractRegister {
        // Convert type
        if(half) {
            type = switch(type) {
                case I16    -> IRType.I8;
                case I32    -> IRType.I16;
                default     -> throw new IllegalArgumentException("Cannot take half of " + type);
            };
        }
        
        // Ensure the ID is local
        if(id.getIDClass() != IRIdentifierClass.LOCAL) {
            throw new IllegalArgumentException(id + ": Abstract registers must be locals");
        }
        
        // Ensure the type is not NONE
        if(type == IRType.NONE || type == null) {
            throw new IllegalArgumentException(id + ": Abstract registers cannot have type NONE");
        }
    }
    
    /**
     * @return RegisterClass of this register according to type and half
     */
    public RARegisterClass getRegisterClass() {
        return switch(this.type) {
            case I8     -> this.half ? RARegisterClass.I16_HALF : RARegisterClass.I8;
            case I16    -> this.half ? RARegisterClass.I32 : RARegisterClass.I16;
            case I32    -> RARegisterClass.I32;
            default     -> throw new IllegalArgumentException("Unexpected value: " + this.type);
        };
    }
    
    @Override
    public IRType getType() { return this.type; }
    
    @Override
    public String toString() {
        return this.id.toString() + (half ? (upper ? "<high>" : "<low>") : "");
    }
    
}
