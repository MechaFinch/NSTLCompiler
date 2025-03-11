package notsotiny.lang.compiler.aasm;

import notsotiny.asm.Register;
import notsotiny.lang.ir.parts.IRType;

/**
 * Container for a machine register
 * Most likely just used for BP during instruction selection
 * AbstractRegisters will likely be converted to MachineRegisters during
 * register allocation
 */
public record AASMMachineRegister(Register reg) implements AASMRegister {
    
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
