package notsotiny.lang.compiler.aasm;

import notsotiny.lang.ir.parts.IRType;

/**
 * A compile-time constant in the AbstractAssembly
 */
public record AASMCompileConstant(int value, IRType type) implements AASMConstant {
    
    public static final AASMCompileConstant ZERO = new AASMCompileConstant(0, IRType.NONE);
    public static final AASMCompileConstant ONE = new AASMCompileConstant(1, IRType.NONE);
    
    public AASMCompileConstant(int value) {
        this(value, IRType.NONE);
    }
    
    @Override
    public IRType getType() { return this.type; }
    
    @Override
    public String toString() {
        return this.type + " " + this.value;
    }
    
}
