package notsotiny.lang.ir.parts;

import notsotiny.sim.ops.Opcode;

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
        int signedL = left.getValue();
        int signedR = right.getValue();
        long unsignedL = left.getUnsignedValue();
        long unsignedR = right.getUnsignedValue();
        
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
    
    /**
     * Returns the equivalent condition when arguments are swapped
     * e.g. A > B <-> B < A
     * @return
     */
    public IRCondition swapped() {
        return switch(this) {
            case A  -> B;
            case AE -> BE;
            case B  -> A;
            case BE -> BE;
            case G  -> L;
            case GE -> LE;
            case L  -> G;
            case LE -> GE;
            default -> this; // Remains the same when args swapped
        };
    }
    
    /**
     * Returns the opcode of the corresponding JCC_RIM instruction
     * @return
     */
    public Opcode toJCCOpcode() {
        return switch(this) {
            case E  -> Opcode.JZ_RIM;
            case NE -> Opcode.JNZ_RIM;
            case A  -> Opcode.JA_RIM;
            case AE -> Opcode.JNC_RIM;
            case B  -> Opcode.JC_RIM;
            case BE -> Opcode.JBE_RIM;
            case G  -> Opcode.JG_RIM;
            case GE -> Opcode.JGE_RIM;
            case L  -> Opcode.JL_RIM;
            case LE -> Opcode.JLE_RIM;
            default -> Opcode.JMP_RIM;
        };
    }
}
