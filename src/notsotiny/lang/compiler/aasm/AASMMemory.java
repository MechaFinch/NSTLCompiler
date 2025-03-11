package notsotiny.lang.compiler.aasm;

import notsotiny.lang.ir.parts.IRType;

/**
 * A memory reference in the AbstractAssembly
 */
public class AASMMemory implements AASMLocation {
    
    // Register or PatternReference
    private AASMPart base;
    
    // Register, PatternReference, or PatternIndex
    private AASMPart index;
    
    // Constant, PatternReference, or PatternIndex
    private AASMPart scale;
    
    // Constant or PatternReference
    private AASMPart offset;
    
    // Pointed type
    private IRType type;
    
    /**
     * Full constructor
     * @param base
     * @param index
     * @param scale
     * @param offset
     */
    public AASMMemory(AASMPart base, AASMPart index, AASMPart scale, AASMPart offset, IRType type) {
        this.base = base;
        this.index = index;
        this.scale = scale;
        this.offset = offset;
        this.type = type;
    }
    
    public AASMPart getBase() { return this.base; }
    public AASMPart getIndex() { return this.index; }
    public AASMPart getScale() { return this.scale; }
    public AASMPart getOffset() { return this.offset; }
    
    @Override
    public IRType getType() { return this.type; }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("[");
        
        boolean previous = false;
        
        if(this.base != null && this.base != AASMCompileConstant.ZERO) {
            sb.append(this.base);
            previous = true;
        }
        
        if(this.index != null && this.index != AASMCompileConstant.ZERO) {
            if(previous) {
                sb.append(" + ");
            }
            
            sb.append(this.index);
            
            if(this.scale != null) {
                sb.append("*");
                sb.append(this.scale);
            }
            
            previous = true;
        }
        
        if(this.offset != null && this.offset != AASMCompileConstant.ZERO) {
            if(previous) {
                sb.append(" + ");
            }
            
            sb.append(this.offset);
        }
        
        sb.append("]");
        
        return sb.toString();
    }
    
}
