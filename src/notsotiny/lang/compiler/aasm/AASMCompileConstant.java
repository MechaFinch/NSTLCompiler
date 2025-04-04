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
    
    /**
     * @return long equal to this as an unsigned value
     */
    public long signedLongValue() {
        return (long) this.type.trim(this.value);
    }
    
    /**
     * @return long equal to this as a signed value
     */
    public long unsignedLongValue() {
        return ((long) (this.value & this.type.getMask())) & 0x00000000FFFFFFFFl;
    }
    
    /**
     * @return value as a signed integer
     */
    public int signedValue() {
        return this.type.trim(this.value);
    }
    
    @Override
    public IRType getType() { return this.type; }
    
    @Override
    public String toString() {
        return this.type + " " + this.value;
    }
    
}
