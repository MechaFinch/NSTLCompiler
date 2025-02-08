package notsotiny.lang.compiler.codegen.dag;

import notsotiny.lang.ir.parts.IRLinearOperation;

/**
 * Operations which produce output
 */
public enum ISelDAGProducerOperation {
    
    // Linear Operations
    TRUNC,  // Truncate
    SX,     // Sign-Extend
    ZX,     // Zero-Extend
    LOAD,   // Load from memory
    SELECT, // Conditional Selection
    STACK,  // Stack slot pointer creation
    
    ADD,    // Addition
    SUB,    // Subtraction
    MULU,   // Unsigned Multiply
    MULS,   // Signed Multiply
    DIVU,   // Unsigned Divide
    DIVS,   // Signed Divide
    REMU,   // Unsigned Remainder
    REMS,   // Signed Remainder
    
    SHL,    // Shift Left
    SHR,    // Logical Shift Right
    SAR,    // Arithmetic Shift Right
    ROL,    // Rotate Left
    ROR,    // Rotate Right
    
    AND,    // Bitwise AND
    OR,     // Bitwise OR
    XOR,    // Bitwise XOR
    NOT,    // Bitwise NOT
    NEG,    // Arithmetic Negation
    
    CALLR,  // Function Call with Return
    
    // DAG Operations
    IN,     // Live-in
    VALUE,  // Constant or Global (constant address)
    ;
    
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
