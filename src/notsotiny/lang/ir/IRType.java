package notsotiny.lang.ir;

/**
 * Integer types.
 * 
 * @author Mechafinch
 */
public enum IRType {
    I32     (0xFFFFFFFF, 0x1F),
    I16     (0x0000FFFF, 0x0F),
    I8      (0x000000FF, 0x07),
    NONE    (0xFFFFFFFF, 0x1F),
    ;
    
    private int mask;
    private int shiftMask;
    
    private IRType(int mask, int shiftMask) {
        this.mask = mask;
        this.shiftMask = shiftMask;
    }
    
    public int getMask() { return this.mask; }
    public int getShiftMask() { return this.shiftMask; }
    
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
