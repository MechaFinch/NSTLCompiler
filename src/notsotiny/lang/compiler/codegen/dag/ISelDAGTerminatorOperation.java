package notsotiny.lang.compiler.codegen.dag;

/**
 * Operations which do not produce values
 */
public enum ISelDAGTerminatorOperation implements ISelDAGOperation {
    // Linear operations 
    STORE,  // Store to memory
    CALLN,  // Function Call without Return
    
    // Branch operations
    JMP,    // Unconditional Jump
    JCC,    // Conditional Jump
    RET,    // Return
    
    // DAG operations
    OUT,    // Assign to live-out
    ENTRY,  // Chain end 
    ;
    
    // All operations are ordered, so no variable
    
}
