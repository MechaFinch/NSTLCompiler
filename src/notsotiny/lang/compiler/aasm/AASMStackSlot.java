package notsotiny.lang.compiler.aasm;

import notsotiny.lang.ir.parts.IRIdentifier;
import notsotiny.lang.ir.parts.IRType;

/**
 * Represents stack space allocated by a STACK instruction
 * transformed to [BP + x] during register allocation
 * Expected to be produced as LEA local, StackSlot
 */
public record AASMStackSlot(IRIdentifier id, int size) implements AASMLocation {
    
    @Override
    public IRType getType() { return IRType.NONE; }
    
}
