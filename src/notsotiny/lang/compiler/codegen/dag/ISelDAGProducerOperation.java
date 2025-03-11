package notsotiny.lang.compiler.codegen.dag;

import notsotiny.lang.ir.parts.IRLinearOperation;

/**
 * Operations which produce output
 */
public enum ISelDAGProducerOperation implements ISelDAGOperation {
    
    // Linear Operations
    TRUNC   (1, false),    // Truncate
    SX      (1, false),    // Sign-Extend
    ZX      (1, false),    // Zero-Extend
    LOAD    (1, false),    // Load from memory
    SELECT  (4, true),     // Conditional Selection
    STACK   (1, false),    // Stack slot pointer creation
    
    ADD     (2, false),    // Addition
    SUB     (2, true),     // Subtraction
    MULU    (2, false),    // Unsigned Multiply
    MULS    (2, false),    // Signed Multiply
    DIVU    (2, true),     // Unsigned Divide
    DIVS    (2, true),     // Signed Divide
    REMU    (2, true),     // Unsigned Remainder
    REMS    (2, true),     // Signed Remainder
    
    SHL     (2, true),     // Shift Left
    SHR     (2, true),     // Logical Shift Right
    SAR     (2, true),     // Arithmetic Shift Right
    ROL     (2, true),     // Rotate Left
    ROR     (2, true),     // Rotate Right
    
    AND     (2, false),    // Bitwise AND
    OR      (2, false),    // Bitwise OR
    XOR     (2, false),    // Bitwise XOR
    NOT     (1, false),    // Bitwise NOT
    NEG     (1, false),    // Arithmetic Negation
    
    CALLR   (0, true),     // Function Call with Return
    
    // DAG Operations
    IN      (0, false),    // Live-in
    ARG     (0, false),    // Function argument
    VALUE   (0, false),    // Constant or Global (constant address)
    PUSH    (1, false),    // CALL argument push
    ;
    
    private int argCount;
    
    private boolean ordered;
    
    private ISelDAGProducerOperation(int argCount, boolean ordered) {
        this.argCount = argCount;
        this.ordered = ordered;
    }
    
    public int getArgCount() { return this.argCount; }
    public boolean isOrdered() { return this.ordered; }
    
    public static ISelDAGProducerOperation get(IRLinearOperation op) {
        return switch(op) {
            case TRUNC  -> TRUNC;
            case SX     -> SX;
            case ZX     -> ZX;
            case LOAD   -> LOAD;
            case SELECT -> SELECT;
            case STACK  -> STACK;
            case ADD    -> ADD;
            case SUB    -> SUB;
            case MULU   -> MULU;
            case MULS   -> MULS;
            case DIVU   -> DIVU;
            case DIVS   -> DIVS;
            case REMU   -> REMU;
            case REMS   -> REMS;
            case SHL    -> SHL;
            case SHR    -> SHR;
            case SAR    -> SAR;
            case ROL    -> ROL;
            case ROR    -> ROR;
            case AND    -> AND;
            case OR     -> OR;
            case XOR    -> XOR;
            case NOT    -> NOT;
            case NEG    -> NEG;
            case CALLR  -> CALLR;
            default -> throw new IllegalArgumentException(op + " does not produce a value");
        };
    }
    
}
