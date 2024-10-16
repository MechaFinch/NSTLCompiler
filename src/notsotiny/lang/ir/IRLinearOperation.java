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
    TRUNC,      // Truncate                     1
    SX,         // Sign-Extend                  1
    ZX,         // Zero-Extend                  1
    LOAD,       // Load from memory             1
    STORE,      // Store to memory              2       No destination
    SELECT,     // Conditional Selection        4       Has condition
    
    ADD,        // Addition                     2
    SUB,        // Subtraction                  2
    MULU,       // Unsigned Multiply            2
    MULS,       // Signed Multiply              2
    DIVU,       // Unsigned Divide              2
    DIVS,       // Signed Divide                2
    REMU,       // Unsigned Remainder           2
    REMS,       // Signed Remainder             2
    
    SHL,        // Shift Left                   2
    SHR,        // Logical Shift Right          2
    SAR,        // Arithmetic Shift Right       2
    ROL,        // Rotate Left                  2
    ROR,        // Rotate Right                 2
    
    AND,        // Bitwise AND                  2
    OR,         // Bitwise OR                   2
    XOR,        // Bitwise XOR                  2
    NOT,        // Bitwise NOT                  1
    NEG,        // Arithmetic Negation          1
    
    CALLR,      // Function Call with Return    1       Has argument map
    CALLN,      // Function Call without Return 1       Has argument map, no destination
    ;
}
