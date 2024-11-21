package notsotiny.lang.ir;

/**
 * Contains a single constant integer.
 * 
 * @author Mechafinch
 */
public class IRConstant extends IRValue {
    
    private int value;
    
    private IRType type;
    
    /**
     * Full constructor
     * @param value
     * @param type
     * @param checkBounds
     */
    public IRConstant(int value, IRType type, boolean checkBounds) {
        this.value = type.trim(value);
        this.type = type;
        
        if(checkBounds) {
            if(this.value != value) {
                throw new IllegalArgumentException("Value out of bounds");
            }
        }
    }
    

    /**
     * No bounds chceck constructor
     * @param value
     * @param type
     */
    public IRConstant(int value, IRType type) {
        this(value, type, false);
    }
    
    /**
     * Gets a long with this value unsigned
     * @return
     */
    public long getUnsignedValue() {
        return ((long) this.value & this.type.getMask()) & 0x00000000FFFFFFFFl;
    }
    
    @Override
    public String toString() {
        return this.type + " " + this.value;
    }
    
    public int getValue() { return this.value; }
    public IRType getType() { return this.type; }
    
    @Override
    public boolean equals(Object o) {
        if(o instanceof IRConstant c) {
            return this.type == c.type && this.value == c.value;
        }
        
        return false;
    }
    
}
