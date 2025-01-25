package notsotiny.lang.compiler.codegen.dag;

/**
 * Operations which do not produce values
 */
public enum ISelDAGTerminatorOperation {
    // Linear operations 
    STORE,  // Store to memory
    CALLN,  // Function Call without Return
    
    // Branch operations
    JMP,    // Unconditional Jump
    JCC,    // Conditional Jump
    RET,    // Return
    
    // DAG operations
    OUT,    // Assign to live-out
    ;
}
