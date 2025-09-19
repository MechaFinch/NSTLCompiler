package notsotiny.lang.compiler.codegen.dag;

/**
 * Operations used in patterns. 
 */
public enum ISelDAGPatternOperation implements ISelDAGOperation {
    
    LOCAL,      // Equivalent to any producer operation; a virtual register
    CONSTANT,   // A compile-time constant
    ARG,        // A function argument; [BP + x]
    ;
    
}
