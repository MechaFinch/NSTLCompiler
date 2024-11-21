package notsotiny.lang.ir;

/**
 * Possible Conditions
 * 
 * @author Mechafinch
 */
public enum IRCondition {
    E,      // Equal
    NE,     // Not Equal
    A,      // Above
    AE,     // Above or Equal
    B,      // Below
    BE,     // Below or Equal
    G,      // Greater
    GE,     // Greater or Equal
    L,      // Less
    LE,     // Less or Equal
    NONE,   // Unconditional
    ;
    
    /**
     * Returns true if this condition is true with the given arguments
     * @param left
     * @param right
     * @return
     */
    public boolean conditionTrue(IRConstant left, IRConstant right) {
        IRType t = left.getType();
        int signedL = left.getValue();
        int signedR = right.getValue();
        long unsignedL = (long)(signedL & t.getMask()) & 0x00000000FFFFFFFFl;
        long unsignedR = (long)(signedR & t.getMask()) & 0x00000000FFFFFFFFl;
        
        return switch(this) {
            case E      -> signedL == signedR;
            case NE     -> signedL != signedR;
            case A      -> unsignedL > unsignedR;
            case AE     -> unsignedL >= unsignedR;
            case B      -> unsignedL < unsignedR;
            case BE     -> unsignedL <= unsignedR;
            case G      -> signedL > signedR;
            case GE     -> signedL >= signedR;
            case L      -> signedL < signedR;
            case LE     -> signedL <= signedR;
            case NONE   -> true;
        };
    }
}
