package notsotiny.lang.compiler.codegen.dag;

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
    
}
