package notsotiny.lang.ir.parts;

/**
 * Integer types.
 * 
 * @author Mechafinch
 */
public enum IRType {
    I32     (4, 0xFFFFFFFF, 0x1F),
    I16     (2, 0x0000FFFF, 0x0F),
    I8      (1, 0x000000FF, 0x07),
    NONE    (0, 0xFFFFFFFF, 0x1F),
    ;
    
    private int size;
    private int mask;
    private int shiftMask;
    
    private IRType(int size, int mask, int shiftMask) {
        this.size = size;
        this.mask = mask;
        this.shiftMask = shiftMask;
    }
    
    public int getSize() { return this.size; }
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
    
    /**
     * Converts a type name to type object
     * @param typeName
     * @return
     */
    public static IRType fromString(String typeName) {
        String upper = typeName.toUpperCase().trim();
        
        try {
            // normal names
            return IRType.valueOf(upper);
        } catch(IllegalArgumentException e) {
            // allow ANY -> NONE
            return switch(upper) {
                case "ANY"  -> NONE;
                default     -> throw e;
            };
        }
    }
}
