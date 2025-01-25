package notsotiny.lang.ir.parts;

/**
 * Possible operations for a linear instruction
 * 
 * @author Mechafinch
 */
public enum IRLinearOperation {
    // Source Labeling:
    // 1: SourceA
    // 2: SourceA, SourceB
    // 4: ComapreA, CompareB, SourceA, SourceB
    
                        // Description                  #src    Notes
    TRUNC   (1, true),  // Truncate                     1
    SX      (1, true),  // Sign-Extend                  1
    ZX      (1, true),  // Zero-Extend                  1
    LOAD    (1, true),  // Load from memory             1
    STORE   (2, false), // Store to memory              2       No destination
    SELECT  (4, true),  // Conditional Selection        4       Has condition
    STACK   (1, true),  // Stack slot pointer creation  1       Yields an I32 which points to a stack slot with the given size. Size must be constant. 
    
    ADD     (2, true),  // Addition                     2
    SUB     (2, true),  // Subtraction                  2
    MULU    (2, true),  // Unsigned Multiply            2
    MULS    (2, true),  // Signed Multiply              2
    DIVU    (2, true),  // Unsigned Divide              2
    DIVS    (2, true),  // Signed Divide                2
    REMU    (2, true),  // Unsigned Remainder           2
    REMS    (2, true),  // Signed Remainder             2
    
    SHL     (2, true),  // Shift Left                   2
    SHR     (2, true),  // Logical Shift Right          2
    SAR     (2, true),  // Arithmetic Shift Right       2
    ROL     (2, true),  // Rotate Left                  2
    ROR     (2, true),  // Rotate Right                 2
    
    AND     (2, true),  // Bitwise AND                  2
    OR      (2, true),  // Bitwise OR                   2
    XOR     (2, true),  // Bitwise XOR                  2
    NOT     (1, true),  // Bitwise NOT                  1
    NEG     (1, true),  // Arithmetic Negation          1
    
    CALLR   (1, true),  // Function Call with Return    1       Has argument map
    CALLN   (1, false), // Function Call without Return 1       Has argument map, no destination
    ;
    
    private int sourceCount;
    private boolean hasDestination;
    
    private IRLinearOperation(int sourceCount, boolean hasDestination) {
        this.sourceCount = sourceCount;
        this.hasDestination = hasDestination;
    }
    
    public int getSourceCount() { return this.sourceCount; }
    public boolean hasDestination() { return this.hasDestination; }
    
    /**
     * Apply SELECT sl cond sr, a, b
     * @param a
     * @param b
     * @param sl
     * @param sr
     * @return
     */
    public static IRConstant applySelect(IRType destType, IRConstant a, IRConstant b, IRConstant sl, IRConstant sr, IRCondition cond) {
        return cond.conditionTrue(sl, sr) ? a : b;
    }
    
    /**
     * Apply a unary operation to a constant
     * @param a
     * @param b
     * @return
     */
    public IRConstant applyUnary(IRType destType, IRConstant a) {
        int val = switch(this) {
            case TRUNC  -> a.getValue();
            
            case SX     -> switch(a.getType()) {
                case NONE, I32  -> a.getValue();
                case I16        -> ((a.getValue() & a.getType().getMask()) << 16) >> 16;
                case I8         -> ((a.getValue() & a.getType().getMask()) << 24) >> 24;
            };
            
            case ZX     -> a.getValue() & a.getType().getMask();
            case NOT    -> ~a.getValue();
            case NEG    -> -a.getValue();
            default     -> throw new IllegalArgumentException("applyUnary is not applicable to " + this);
        };
        
        return new IRConstant(val, destType);
    }
    
    /**
     * Apply a binary operation to two constants
     * @param a
     * @param b
     * @return
     */
    public IRConstant applyBinary(IRType destType, IRConstant a, IRConstant b) {
        int val = switch(this) {
            case ADD    -> a.getValue() + b.getValue();
            case SUB    -> a.getValue() - b.getValue();
            case MULU   -> (int)(a.getUnsignedValue() * b.getUnsignedValue());
            case MULS   -> a.getValue() * b.getValue();
            case DIVU   -> (int)(a.getUnsignedValue() / b.getUnsignedValue());
            case DIVS   -> a.getValue() / b.getValue();
            case REMU   -> (int)(a.getUnsignedValue() % b.getUnsignedValue());
            case REMS   -> a.getValue() % b.getValue();
            
            case SHL    -> {
                int s = b.getValue() & b.getType().getShiftMask();
                yield a.getValue() << s; 
            }
            
            case SHR    -> {
                int s = b.getValue() & b.getType().getShiftMask();
                yield a.getValue() >>> s; 
            }
            
            case SAR    -> {
                int s = b.getValue() & b.getType().getShiftMask();
                yield a.getValue() >> s; 
            }
            
            case ROL    -> {
                int s = b.getValue() & b.getType().getShiftMask();
                yield (a.getValue() << s) | (a.getValue() >>> ((-s) & b.getType().getShiftMask())); 
            }
            
            case ROR    -> {
                int s = b.getValue() & b.getType().getShiftMask();
                yield (a.getValue() >>> s) | (a.getValue() << ((-s) & b.getType().getShiftMask())); 
            }
            
            case AND    -> a.getValue() & b.getValue();
            case OR     -> a.getValue() | b.getValue();
            case XOR    -> a.getValue() ^ b.getValue();
            
            default     -> throw new IllegalArgumentException("applyBinary is not applicable to " + this);
        };
        
        return new IRConstant(val, destType);
    }
}
