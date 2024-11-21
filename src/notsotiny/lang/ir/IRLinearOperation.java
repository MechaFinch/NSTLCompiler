package notsotiny.lang.ir;

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
    TRUNC   (1),    // Truncate                     1
    SX      (1),    // Sign-Extend                  1
    ZX      (1),    // Zero-Extend                  1
    LOAD    (1),    // Load from memory             1
    STORE   (2),    // Store to memory              2       No destination
    SELECT  (4),    // Conditional Selection        4       Has condition
    STACK   (1),    // Stack Pointer                1       Yields an I32 which points to a stack slot with the given size. Size must be constant. 
    
    ADD     (2),    // Addition                     2
    SUB     (2),    // Subtraction                  2
    MULU    (2),    // Unsigned Multiply            2
    MULS    (2),    // Signed Multiply              2
    DIVU    (2),    // Unsigned Divide              2
    DIVS    (2),    // Signed Divide                2
    REMU    (2),    // Unsigned Remainder           2
    REMS    (2),    // Signed Remainder             2
    
    SHL     (2),    // Shift Left                   2
    SHR     (2),    // Logical Shift Right          2
    SAR     (2),    // Arithmetic Shift Right       2
    ROL     (2),    // Rotate Left                  2
    ROR     (2),    // Rotate Right                 2
    
    AND     (2),    // Bitwise AND                  2
    OR      (2),    // Bitwise OR                   2
    XOR     (2),    // Bitwise XOR                  2
    NOT     (1),    // Bitwise NOT                  1
    NEG     (1),    // Arithmetic Negation          1
    
    CALLR   (1),    // Function Call with Return    1       Has argument map
    CALLN   (1),    // Function Call without Return 1       Has argument map, no destination
    ;
    
    private int sourceCount;
    
    private IRLinearOperation(int sourceCount) {
        this.sourceCount = sourceCount;
    }
    
    public int getSourceCount() { return this.sourceCount; }
    
    /**
     * Apply SELECT sl cond sr, a, b
     * @param a
     * @param b
     * @param sl
     * @param sr
     * @return
     */
    public static IRConstant applySelect(IRConstant a, IRConstant b, IRConstant sl, IRConstant sr, IRCondition cond) {
        
    }
    
    /**
     * Apply a unary operation to a constant
     * @param a
     * @param b
     * @return
     */
    public IRConstant applyUnary(IRConstant a) {
        
    }
    
    /**
     * Apply a binary operation to two constants
     * @param a
     * @param b
     * @return
     */
    public IRConstant applyBinary(IRConstant a, IRConstant b) {
        
    }
}
