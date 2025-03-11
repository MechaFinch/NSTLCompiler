package notsotiny.lang.compiler.aasm;

/**
 * A scaled index. This exists specifically so that a scaled index can be factored
 * out of the patterns that produce BIO memory accesses
 */
public record AASMPatternIndex(AASMPart index, int scale) implements AASMPart {
    
    public AASMPatternIndex {
        if(scale < 1 || scale > 4 || scale == 3) {
            throw new IllegalArgumentException("invalid scale: " + scale);
        }
    }
    
    /**
     * Get scale as a compile constant
     * @return
     */
    public AASMCompileConstant ccScale() {
        return new AASMCompileConstant(this.scale);
    }
    
    @Override
    public String toString() {
        if(this.scale == 1) {
            return this.index.toString();
        } else {
            return this.index + "*" + this.scale;
        }
    }
    
}
