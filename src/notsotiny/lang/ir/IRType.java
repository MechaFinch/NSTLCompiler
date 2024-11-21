package notsotiny.lang.ir;

/**
 * Integer types.
 * 
 * @author Mechafinch
 */
public enum IRType {
    I32     (0xFFFFFFFF),
    I16     (0x0000FFFF),
    I8      (0x000000FF),
    NONE    (0xFFFFFFFF),
    ;
    
    private int mask;
    
    private IRType(int mask) {
        this.mask = mask;
    }
    
    public int getMask() { return this.mask; }
    
    /**
     * Trims val such that the returned integer is equivalent to the IR value of val
     * @param val
     * @param type
     * @return
     */
    public int trim(int val) {
        int masked = val & this.getMask();
        
        return switch(this) {
            case NONE, I32  -> masked;
            case I16        -> (masked << 16) >> 16;
            case I8         -> (masked << 24) >> 24;
        };
    }
}
