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
     */
    public IRConstant(int value, IRType type) {
        this.value = value;
        this.type = type;
    }
    
    @Override
    public String toString() {
        return this.type + " " + this.value;
    }
    
    public int getValue() { return this.value; }
    public IRType getType() { return this.type; }
    
}
