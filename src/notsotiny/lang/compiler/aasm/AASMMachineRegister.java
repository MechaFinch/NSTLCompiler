package notsotiny.lang.compiler.aasm;

import notsotiny.asm.Register;
import notsotiny.lang.ir.parts.IRIdentifier;
import notsotiny.lang.ir.parts.IRIdentifierClass;
import notsotiny.lang.ir.parts.IRType;

/**
 * Container for a machine register
 * AbstractRegisters are converted to this as the final step of
 * register allocation
 */
public record AASMMachineRegister(Register reg, IRIdentifier id) implements AASMRegister {
    
    public AASMMachineRegister(Register reg) {
        this(reg, new IRIdentifier(reg.name(), IRIdentifierClass.SPECIAL));
    }
    
    @Override
    public IRType getType() {
        return switch(reg.size()) {
            case 1  -> IRType.I8;
            case 2  -> IRType.I16;
            case 4  -> IRType.I32;
            default -> IRType.NONE;
        };
    }
    
    @Override
    public String toString() {
        return this.reg.toString();
    }
    
}
